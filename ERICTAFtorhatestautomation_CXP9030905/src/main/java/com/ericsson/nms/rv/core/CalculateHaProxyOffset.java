package com.ericsson.nms.rv.core;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.ericsson.nms.rv.core.upgrade.UpgradeVerifier;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class CalculateHaProxyOffset {

    private static final Logger logger = LogManager.getLogger(CalculateHaProxyOffset.class);
    private static Map<String, Long> haDependencyMap = new HashMap<>();
    static final Map<String, Long> appHaProxyOffsetMap = new HashMap<>();
    static final Map<String, Long> depHaProxyOffsetMap = new HashMap<>();

    public static Map<String, Long> updateDowntimeMap(final Map<String, Map<Date, Date>> appDateTimeMap,
                                                      final Map<String, String> depDowntimeMap,
                                                      final Map<String, Long> deltaDowntimeValues) {

        final Map<String, Long> deltaDTValues = updateMap(appDateTimeMap, depDowntimeMap, deltaDowntimeValues);
        final Map<String, Long> deltaDowntimeAfterHaProxy = calculateHaProxyDependenciesOffset(depDowntimeMap, deltaDTValues);
        logger.info("deltaDowntimeValues after dependency processing: {}", deltaDowntimeAfterHaProxy);
        return deltaDowntimeAfterHaProxy;
    }

    private static  Map<String, Long> updateMap(Map<String, Map<Date, Date>> appDateTimeMap, Map<String, String> depDowntimeMap, Map<String, Long> deltaDowntimeValues) {
        if(appDateTimeMap.isEmpty() || depDowntimeMap.isEmpty()) {
            return deltaDowntimeValues;
        }
        final DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        ZoneId defaultZoneId = ZoneId.systemDefault();
        appDateTimeMap.forEach((appName, appTime) -> {
            Duration haproxyOffset = Duration.ZERO;
            long appNewDeltaDownTime;
            logger.info("HaProxy DT calculation started for : {}", appName);
            for (Map.Entry<Date, Date> startStopTime : appTime.entrySet()) {
                final Date appStartDate = startStopTime.getKey();
                final Date appStopDate = startStopTime.getValue();
                try {
                    for (Map.Entry<String, String> entry : depDowntimeMap.entrySet()) {
                        if (entry.getKey().equalsIgnoreCase(HAPropertiesReader.HAPROXY) && !StringUtils.equalsAnyIgnoreCase(appName, HAPropertiesReader.NBIALARMVERIFIER, HAPropertiesReader.AMOSAPP, HAPropertiesReader.SYSTEMVERIFIER)) {
                            if (entry.getValue() == null || entry.getValue().isEmpty()) {
                                continue;
                            }
                            String[] pair = entry.getValue().split(";");
                            for (final String str : pair) {
                                String[] time = str.split(",");
                                if (time[0] == null || time[1] == null || time[0].isEmpty() || time[1].isEmpty()) {
                                    continue;
                                }
                                LocalDateTime haStart = LocalDateTime.parse(time[0], fmt);
                                LocalDateTime haStop = LocalDateTime.parse(time[1], fmt);
                                LocalDateTime appStart = appStartDate.toInstant().atZone(defaultZoneId).toLocalDateTime();
                                LocalDateTime appStop = appStopDate.toInstant().atZone(defaultZoneId).toLocalDateTime();

                                haproxyOffset = haproxyOffset.plus(CommonReportHelper.getCommonOffsetFromTwoDowntimeWindows(appStart, appStop, haStart, haStop));
                            }
                        }
                    }
                } catch (final Exception ex) {
                    logger.warn(ex.getMessage());
                }
            }
            logger.info("Total haProxyOffset for Application {} : {}", appName, haproxyOffset.getSeconds());
            if (deltaDowntimeValues.get(appName) != null && deltaDowntimeValues.get(appName) != -1) {
                try {
                    if (haproxyOffset.getSeconds() > 0) {
                        logger.info("deltaDowntimeValues for {} is : {}", appName, deltaDowntimeValues.get(appName));
                        long newHaproxyOffset;
                        if(HAPropertiesReader.isEnvCloud()) {
                            logger.info("Total known downtime to be added to haproxy offset is {} sec", 40L);
                            newHaproxyOffset = haproxyOffset.getSeconds() + 40L;
                            appNewDeltaDownTime = deltaDowntimeValues.get(appName) - newHaproxyOffset;
                        } else {
                            logger.info("Total known downtime to be added to haproxy offset is {} sec", UpgradeVerifier.haproxyInstance * 5L);
                            newHaproxyOffset = haproxyOffset.getSeconds() + UpgradeVerifier.haproxyInstance * 5L;
                            appNewDeltaDownTime = deltaDowntimeValues.get(appName) - newHaproxyOffset;
                        }
                        logger.info("NewDeltaDownTimeValue for {} is : {}", appName, appNewDeltaDownTime);
                        deltaDowntimeValues.replace(appName, deltaDowntimeValues.get(appName), (appNewDeltaDownTime > 0 ? appNewDeltaDownTime : 0L));
                        appHaProxyOffsetMap.put(appName, newHaproxyOffset);
                    }
                } catch (final Exception e) {
                    logger.info("Error occurs during DT calculation : {}", e.getMessage());
                }
            }
        });
        return deltaDowntimeValues;
    }

    private static Map<String, Long> calculateHaProxyDependenciesOffset(final Map<String, String> depDowntimeMap, Map<String, Long> deltaDownTimeValues) {
        final DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        try {
            String[] haProxyPairs = depDowntimeMap.get(HAPropertiesReader.HAPROXY).split(";");
            for (Map.Entry<String, String> entry : depDowntimeMap.entrySet()) {
                Duration dependencyOffset = Duration.ZERO;
                if (!entry.getKey().equalsIgnoreCase(HAPropertiesReader.HAPROXY)) {
                    String[] dependencyPairs = entry.getValue().split(";");
                    for (String haproxyDT : haProxyPairs) {
                        for (String depDT : dependencyPairs) {
                            String[] haTime = haproxyDT.split(",");
                            String[] depTime = depDT.split(",");
                            LocalDateTime haStart = LocalDateTime.parse(haTime[0], fmt);
                            LocalDateTime haStop = LocalDateTime.parse(haTime[1], fmt);
                            LocalDateTime depStart = LocalDateTime.parse(depTime[0], fmt);
                            LocalDateTime depStop = LocalDateTime.parse(depTime[1], fmt);

                            dependencyOffset = dependencyOffset.plus(CommonReportHelper.getCommonOffsetFromTwoDowntimeWindows(haStart, haStop, depStart, depStop));
                        }
                    }
                }
                logger.info("HaProxy Offset for dependency : {} is : {}", entry.getKey(), dependencyOffset.getSeconds());
                haDependencyMap.put(entry.getKey(), dependencyOffset.getSeconds());
            }
        } catch (final Exception e) {
            //regression!
        }
        try {
            deltaDownTimeValues.forEach((appName, dtValue) -> {
                if (StringUtils.equalsAnyIgnoreCase(appName, HAPropertiesReader.NBIALARMVERIFIER, HAPropertiesReader.AMOSAPP, HAPropertiesReader.SYSTEMVERIFIER)) {
                    long depOffset = 0L;
                    final List<String> depList = HAPropertiesReader.getApplicationDependencyMap().get(appName);
                    if (depList != null) {
                        for (final String dep : depList) {
                            try{
                                if(!((appName.equalsIgnoreCase(HAPropertiesReader.NETWORKEXPLORER) || appName.equalsIgnoreCase(HAPropertiesReader.FAULTMANAGEMENT)) && dep.contains("neo4j"))) {
                                    depOffset = depOffset + haDependencyMap.get(dep);
                                }
                            } catch (final Exception e) {
                                //regression.
                            }
                        }
                        logger.info("App:{}, HaProxy depOffset is : {}s.", appName, depOffset);
                    }
                    if (deltaDownTimeValues.get(appName) != null && deltaDownTimeValues.get(appName) != -1) {
                        try {
                            if (depOffset > 0) {
                                logger.info("deltaDowntimeValues for {} is : {}", appName, deltaDownTimeValues.get(appName));
                                long appNewDeltaDownTime;
                                if (HAPropertiesReader.isEnvCloud()) {
                                    final long offsetValue = depOffset - 40L;
                                    appNewDeltaDownTime = deltaDownTimeValues.get(appName) - ((offsetValue > 0 ? offsetValue : 0L));
                                    depHaProxyOffsetMap.put(appName, (offsetValue > 0 ? offsetValue : 0L));
                                } else {
                                    appNewDeltaDownTime = deltaDownTimeValues.get(appName) - depOffset;
                                    depHaProxyOffsetMap.put(appName, depOffset);
                                }
                                logger.info("New deltaDowntimeValues for {} is : {}", appName, (appNewDeltaDownTime > 0 ? appNewDeltaDownTime : 0L));
                                deltaDownTimeValues.replace(appName, deltaDownTimeValues.get(appName), (appNewDeltaDownTime > 0 ? appNewDeltaDownTime : 0L));
                            }
                        } catch (final Exception e) {
                            //regression
                        }
                    }
                }
            });
        } catch (final Exception e) {
            logger.info("Exception occurs!");       //regression.
        }
        return deltaDownTimeValues;
    }
}
