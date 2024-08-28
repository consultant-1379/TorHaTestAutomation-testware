package com.ericsson.nms.rv.core;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.ericsson.nms.rv.core.downtime.DowntimeEvent;
import com.ericsson.nms.rv.core.downtime.DowntimeEvent.State;
import com.ericsson.nms.rv.core.upgrade.CloudNativeDependencyDowntimeHelper;
import com.ericsson.nms.rv.core.upgrade.UpgradeVerificationTasks;
import com.ericsson.nms.rv.core.upgrade.UpgradeVerifier;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

class BuildDowntime {

    private static final Logger logger = LogManager.getLogger(BuildDowntime.class);
    private static final String APPLICATION_CONTAINS_WRONG_TIME_LINE = "Application {} contains wrong time line: {}";

    static Map<String, Long> getTotalDTPerComponent(final Map<String, String> knownDowntimesMap, final Map<String, Map<Date, Date>> appDateTimeMap) {
        final Map<String, Long> componentDTimeMap = new HashMap<>();
        final Map<String, List<String>> applicationDependencyMap = HAPropertiesReader.getApplicationDependencyMap();
        applicationDependencyMap.forEach((application, dependencyList) -> {
            long smrsOffset = 0;
            if (dependencyList != null && !dependencyList.isEmpty()) {
                if (application.equalsIgnoreCase(HAPropertiesReader.SHMSMRS)) {
                    smrsOffset = calculateSmrsOffsetDowntime(knownDowntimesMap, appDateTimeMap, dependencyList.get(0));
                }
                final List<DowntimeEvent> downTimes = buildDowntimesList(knownDowntimesMap, dependencyList, application);
                Collections.sort(downTimes, Comparator.comparing(DowntimeEvent::getLocalDateTime));
                logger.info("Sorted list for {} application dependencyList down times {}", application, downTimes);

                if (!downTimes.isEmpty()) {
                    DowntimeEvent firstStartEvent = downTimes.get(0);
                    if (DowntimeEvent.State.STOP.equals(firstStartEvent.getState())) {
                        logger.warn(APPLICATION_CONTAINS_WRONG_TIME_LINE, application, downTimes);
                    } else {
                        if (application.equalsIgnoreCase(HAPropertiesReader.SHMSMRS)) {
                            componentDTimeMap.put(HAPropertiesReader.SHMSMRS, smrsOffset);
                        } else {
                            componentDTimeMap.putAll(buildDowntimesPerComponentMap(application, downTimes, firstStartEvent));
                        }
                    }
                }
            } else {
                logger.info("Sorted list for {} application dependencyList down times is EMPTY", application);
            }
        });
        logger.info("componentDTimeMap: {}", componentDTimeMap);
        return componentDTimeMap;
    }

    private static long calculateSmrsOffsetDowntime(final Map<String, String> knownDowntimesMap, final Map<String, Map<Date, Date>> appDateTimeMap, final String dependency) {
        if (appDateTimeMap.isEmpty() || knownDowntimesMap.isEmpty()) {
            return 0;
        }
        final DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        final ZoneId defaultZoneId = ZoneId.systemDefault();
        final String ftsDependencyWindows = knownDowntimesMap.get(dependency);
        final Map<Date, Date> shmSmrsDtWindows = appDateTimeMap.get(HAPropertiesReader.SHMSMRS);
        if (shmSmrsDtWindows == null || ftsDependencyWindows == null || ftsDependencyWindows.isEmpty() || knownDowntimesMap.isEmpty()) {
            return 0;
        }

        logger.info("ftsDependencyWindows : {}", ftsDependencyWindows);
        logger.info("shmSmrsDtWindows : {}", shmSmrsDtWindows);
        final String[] ftsDtArray = ftsDependencyWindows.split(";");
        long totalOffsetDuration = 0;

        for (Map.Entry<Date, Date> startStopTime : shmSmrsDtWindows.entrySet()) {
            final Date smrsStartDate = startStopTime.getKey();
            final Date smrsStopDate = startStopTime.getValue();
            final LocalDateTime smrsStart = smrsStartDate.toInstant().atZone(defaultZoneId).toLocalDateTime();
            final LocalDateTime smrsStop = smrsStopDate.toInstant().atZone(defaultZoneId).toLocalDateTime();

            long commonDuration = 0;
            final Duration smrsWindowDuration = Duration.between(smrsStart, smrsStop);
            logger.info("smrs DT window: {}/{}, duration: {}", smrsStart.format(fmt), smrsStop.format(fmt), smrsWindowDuration.getSeconds());
            for (final String str : ftsDtArray) {
                String[] time = str.split(",");
                if (time[0] == null || time[1] == null || time[0].isEmpty() || time[1].isEmpty()) {
                    continue;
                }
                LocalDateTime ftsStart = LocalDateTime.parse(time[0], fmt);
                LocalDateTime ftsStop = LocalDateTime.parse(time[1], fmt);

                final Duration offsetDuration = CommonReportHelper.getCommonOffsetFromTwoDowntimeWindows(smrsStart, smrsStop, ftsStart, ftsStop);
                logger.info("For FTS window: {}, offsetDuration : {}", str, offsetDuration.getSeconds());
                if (offsetDuration.getSeconds() > 0) {
                    commonDuration = commonDuration + offsetDuration.getSeconds();
                    logger.info("commonDuration : {}", commonDuration);
                }
            }
            totalOffsetDuration = totalOffsetDuration + commonDuration;
        }
        logger.info("totalOffsetDuration : {}", totalOffsetDuration);
        return totalOffsetDuration;
    }

    static Map<String, Long> getTotalImagePullTimePerComponent(final Map<String, String> knownDowntimesMap) {
        final Map<String, Long> componentTotalImagePullTimeMap = new HashMap<>();
        final Map<String, List<String>> imageDependencyMap = HAPropertiesReader.getImageDependencyMap();
        imageDependencyMap.forEach((component, imageList) -> {
            if (component.equalsIgnoreCase(HAPropertiesReader.JMS) || component.equalsIgnoreCase(HAPropertiesReader.OPENIDM)) {
                logger.info("component : {}, imageList : {}", component, imageList);
                final long totalPullTIme = buildTotalImagePullTimePerComponent(component, imageList, knownDowntimesMap);
                componentTotalImagePullTimeMap.put(component, totalPullTIme);
            }
        });
        logger.info("componentTotalImagePullTimeMap: {}", componentTotalImagePullTimeMap);
        return componentTotalImagePullTimeMap;
    }

    public static String convertReadableTime(final String time) {
        String readableTime = "";
        try {
            readableTime = time.replace("T", " ");
            readableTime = readableTime.replace("Z", "");
            readableTime = readableTime.trim();
        } catch (Exception e) {
            logger.warn("Message : {}", e.getMessage());
        }
        return readableTime;
    }

    private static long buildTotalImagePullTimePerComponent(final String component,
                                                            final List<String> imageList,
                                                            final Map<String, String> knownDowntimeMap) {
        final DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        long totalImagePullTime = 0;
        try {
            for (final String image : imageList) {
                String imageData = CloudNativeDependencyDowntimeHelper.infraImagePullData.get(image);
                logger.info("Image {} Data : {}", image, imageData);
                if (imageData != null && !imageData.isEmpty()) {
                    imageData = imageData.replace("[", "");
                    imageData = imageData.replace("]", "");
                    imageData = imageData.replace(" ", "");
                    logger.info("{} image data : {}", component, imageData);
                    final String[] data = imageData.split(";");
                    for (String singleData : data) {
                        final String[] single = singleData.split(",");
                        if (single.length == 4 && single[0].contains(component)) {
                            LocalDateTime imagePullStart = LocalDateTime.parse(convertReadableTime(single[1]), fmt);
                            LocalDateTime imagePullStop = LocalDateTime.parse(convertReadableTime(single[2]), fmt);
                            logger.info("imagePullStart : {}", imagePullStart.format(fmt));
                            logger.info("imagePullStop : {}", imagePullStop.format(fmt));
                            try {
                                long timeInSec = ImagePullReportGenerator.convertTimeToSeconds(single[3]);
                                logger.info("image: {}, pull time from k8s event logs: {}/{}", image, single[3], timeInSec);
                                long imagePullTime = Duration.between(imagePullStart, imagePullStop).getSeconds();
                                logger.info("image: {}, calculated pull time: {}", image, imagePullTime);
                                logger.info("Adding : {} to total pull time for {}/{}", imagePullTime, component, image);
                                totalImagePullTime = totalImagePullTime + imagePullTime;
                            } catch (final DateTimeParseException e) {
                                logger.warn("DateTimeParseException for {} : {} {} ", image, e.getMessage(), e.getCause());
                            }
                        }
                    }
                }
            }
        } catch (final Exception e) {
            logger.warn("Message : {}", e.getMessage());
        }
        logger.info("totalImagePullTime : {}", totalImagePullTime);
        return totalImagePullTime;
    }

    private static Map<String, Long> buildDowntimesPerComponentMap(final String application, final List<DowntimeEvent> downTimes, final DowntimeEvent firstStartEvent) {
        DowntimeEvent nextStartEvent = firstStartEvent;
        int countStartStop = 1;
        Duration total = Duration.ZERO;
        for (int i = 1; i < downTimes.size(); i++) {
            DowntimeEvent currentEvent = downTimes.get(i);
            if (State.START.equals(currentEvent.getState())) {
                if (countStartStop == 0) {
                    nextStartEvent = currentEvent;
                }
                ++countStartStop;
            } else {
                --countStartStop;
            }
            if (countStartStop == 0) {
                total = total.plus(Duration.between(nextStartEvent.getLocalDateTime(), currentEvent.getLocalDateTime()));
            }
            if (countStartStop < 0) {
                logger.warn(APPLICATION_CONTAINS_WRONG_TIME_LINE, application, downTimes);
                break;
            }
        }
        final Map<String, Long> componentDTimeMap = new HashMap<>();
        if (countStartStop != 0) {
            logger.warn(APPLICATION_CONTAINS_WRONG_TIME_LINE, application, downTimes);
        } else {
            componentDTimeMap.put(application, total.getSeconds());
        }
        return componentDTimeMap;
    }

    private static List<DowntimeEvent> buildDowntimesList(final Map<String, String> knownDowntimesMap, final List<String> dependencyListForApplication,final String application) {
        final List<DowntimeEvent> downTimes = new ArrayList<>();
        final DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        dependencyListForApplication.forEach(dependency -> {
          if(!((application.equalsIgnoreCase(HAPropertiesReader.NETWORKEXPLORER) || (application.equalsIgnoreCase(HAPropertiesReader.FAULTMANAGEMENT))) && dependency.contains("neo4j")) ) {
              logger.info("buildDowntimesList for application : {}, dependency : {}", application, dependency);
              final String downTimeValues = knownDowntimesMap.get(dependency);
              if (downTimeValues != null) {
                  final String[] downtime = downTimeValues.split(";");
                  for (final String str : downtime) {
                      final String[] strings = str.split(",");
                      try {
                          if (strings.length == 3) {
                              final LocalDate startLocalDate = LocalDateTime.parse(strings[0], fmt).toLocalDate();
                              final LocalTime startLocalTime = LocalDateTime.parse(strings[0], fmt).toLocalTime();

                              downTimes.add(new DowntimeEvent(LocalDateTime.of(startLocalDate, startLocalTime), DowntimeEvent.State.START));

                              final LocalDate stopLocalDate = LocalDateTime.parse(strings[1], fmt).toLocalDate();
                              final LocalTime stopLocalTime = LocalDateTime.parse(strings[1], fmt).toLocalTime();

                              downTimes.add(new DowntimeEvent(LocalDateTime.of(stopLocalDate, stopLocalTime), DowntimeEvent.State.STOP));
                          }
                      } catch (final DateTimeParseException pe) {
                          logger.warn("DateTimeParseException for N/A : {} {} ", pe.getMessage(), pe.getCause());
                      }
                  }
              }
          }
        });
        return downTimes;
    }

    static Map<String, Long> buildDowntimesMap(final Map<String, Long> totalDowntimeValues, final Map<String, Long> componentDTValues) {
        final Map<String, Long> downtimesMap = new HashMap<>();

        totalDowntimeValues.forEach((app, correctedDowntime) -> {
            final long depDTValue = componentDTValues.get(app) == null ? 0L : componentDTValues.get(app);
            try {
                long dependencyDT;
                final long value;
                if (!HAPropertiesReader.isEnvCloud()) {
                    if (app.equalsIgnoreCase(HAPropertiesReader.SYSTEMVERIFIER) || app.equalsIgnoreCase(HAPropertiesReader.AMOSAPP)) {
                        logger.info("Fixed HaProxy Offset to be removed from {} is {} sec.", app, UpgradeVerifier.haproxyInstance * 5L);
                        dependencyDT = depDTValue + UpgradeVerifier.haproxyInstance * 5L;
                        logger.info("For App :{} ,total dependency downtime after adding 5sec of known downtime to each instance of haproxy, is {}", app, dependencyDT);
                    } else {
                        dependencyDT = depDTValue;
                    }
                } else {
                    dependencyDT = depDTValue;
                }
                if(!(HAPropertiesReader.isEnvCloudNative() || HAPropertiesReader.isEnvCloud()) && app.equalsIgnoreCase(HAPropertiesReader.ENMSYSTEMMONITOR)){
                    value = correctedDowntime - (UpgradeVerificationTasks.calculateRhelEsmOffset() + Math.max(dependencyDT, 0L));
                    logger.info("ESM downtime after offset removal is {}", value);
                } else {
                    value = correctedDowntime - Math.max(dependencyDT, 0L);
                }
                downtimesMap.put(app, value > 0 ? value : 0L);
            } catch (final Exception e) {
                logger.warn("Dependency for {} is empty with exception {}", app, e.getMessage());
                downtimesMap.put(app, 0L);
                logger.info("For key {} downtime value is {}", app, downtimesMap.get(app));
            }
        });
        logger.info("Map of all downtimes: {}", downtimesMap);
        return downtimesMap;
    }
}
