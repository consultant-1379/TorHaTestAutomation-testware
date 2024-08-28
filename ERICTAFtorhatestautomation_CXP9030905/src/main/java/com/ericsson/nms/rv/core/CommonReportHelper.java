package com.ericsson.nms.rv.core;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class CommonReportHelper {

    private static final Logger logger = LogManager.getLogger(CommonReportHelper.class);

    static StringBuilder displayEmptyDependency(String env, Map<String, String> otherDependency) {
        final StringBuilder emptyDependencyHtmlValues = new StringBuilder();
        logger.warn("otherDependencyMap in common {}", otherDependency);
        TreeMap<String, String> sortedEmptyDependencyMap = new TreeMap<>();
        sortedEmptyDependencyMap.putAll(otherDependency);
        sortedEmptyDependencyMap.remove(HAPropertiesReader.COMAALDAP);
        boolean isRegression = HAPropertiesReader.getTestType().equalsIgnoreCase("Regression");
        sortedEmptyDependencyMap.forEach((key, value) -> {
            try {
                logger.warn("otherDependencyMap key {} value {}", key, value);
                final String[] extractedRow = value.split(";");
                if (extractedRow.length != 0) {
                    final String[] extractedValues = extractedRow[0].split(",");
                    long thresholdVal = 0L;
                    boolean regression = HAPropertiesReader.getTestType().equalsIgnoreCase("Regression");
                    emptyDependencyHtmlValues.append("<tr>");
                    emptyDependencyHtmlValues.append("<td >" + key + "</td>");
                    if (env.equalsIgnoreCase("Physical")) {
                        if (key.equalsIgnoreCase(HAPropertiesReader.NEO4J_LEADER) || key.equalsIgnoreCase(HAPropertiesReader.NEO4J_FOLLOWER)) {
                            thresholdVal = HAPropertiesReader.dependencyThreshholdPhysical.get(key);
                        } else if (key.equalsIgnoreCase(HAPropertiesReader.VISINAMINGNB)) {
                            thresholdVal = 0L;
                        } else {
                            thresholdVal = HAPropertiesReader.dependencyThreshholdPhysical.get(key);
                        }
                        for (String finalValue : extractedValues) {
                            emptyDependencyHtmlValues.append("<td>" + finalValue + "</td>");
                        }
                        if (key.equalsIgnoreCase(HAPropertiesReader.VISINAMINGNB)) {
                            emptyDependencyHtmlValues.append("<td>" + "N/A" + "</td>");
                        } else if(isRegression) {
                            emptyDependencyHtmlValues.append("<td>" + "TBD" + "</td>");
                        } else {
                            emptyDependencyHtmlValues.append("<td>" + thresholdVal + "s" + "</td>");
                        }
                    } else {
                        if (!(key.equalsIgnoreCase(HAPropertiesReader.VISINAMINGNB) || key.equalsIgnoreCase(HAPropertiesReader.MODELS))) {
                            if (HAPropertiesReader.isEnvCloudNative()) {
                                if (HAPropertiesReader.isDeploymentCCD()) {
                                    thresholdVal = HAPropertiesReader.dependencyThreshholdCCD.get(key);
                                } else {
                                    thresholdVal = HAPropertiesReader.dependencyThreshholdCloudNative.get(key);
                                }
                            } else {
                                thresholdVal = HAPropertiesReader.dependencyThreshholdCloud.get(key);
                            }
                        }
                        for (int i = 0; i < extractedValues.length; i++) {
                            if (i == extractedValues.length - 1) {
                                if (!key.startsWith("nfs")) {
                                    if (key.equalsIgnoreCase(HAPropertiesReader.VISINAMINGNB) || key.equalsIgnoreCase(HAPropertiesReader.MODELS)) {
                                        emptyDependencyHtmlValues.append("<td>" + extractedValues[i] + "</td>");
                                        emptyDependencyHtmlValues.append("<td>" + "N/A" + "</td>");
                                    } else if (HAPropertiesReader.VIOENV && (key.equalsIgnoreCase(HAPropertiesReader.HAPROXY))) {
                                        emptyDependencyHtmlValues.append("<td>" + extractedValues[i] + "</td>");
                                        emptyDependencyHtmlValues.append("<td>" + HAPropertiesReader.dependencyThreshholdVio.get(key) + "s" + "</td>");
                                    } else if (HAPropertiesReader.isEnvCloudNative() && regression) {
                                        emptyDependencyHtmlValues.append("<td>" + extractedValues[i] + "</td>");
                                        emptyDependencyHtmlValues.append("<td>" + "TBD" + "</td>");
                                    } else if (HAPropertiesReader.isEnvCloudNative() && key.equalsIgnoreCase(HAPropertiesReader.CNOM) && HAPropertiesReader.disableCnomUsecase) {
                                        emptyDependencyHtmlValues.append("<td>" + extractedValues[i] + "</td>");
                                        emptyDependencyHtmlValues.append("<td>" + "NA" + "</td>");
                                    } else {
                                        emptyDependencyHtmlValues.append("<td>" + extractedValues[i] + "</td>");
                                        emptyDependencyHtmlValues.append("<td>" + thresholdVal + "s" + "</td>");
                                    }
                                } else {
                                    if (HAPropertiesReader.VIOENV) {
                                        emptyDependencyHtmlValues.append("<td>" + extractedValues[i] + "</td>");
                                        emptyDependencyHtmlValues.append("<td>" + HAPropertiesReader.dependencyThreshholdVio.get(key) + "s" + "</td>");
                                    } else {
                                        emptyDependencyHtmlValues.append("<td>" + extractedValues[i] + "</td>");
                                        emptyDependencyHtmlValues.append("<td>" + thresholdVal + "s" + "</td>");
                                    }
                                }
                            } else {
                                emptyDependencyHtmlValues.append("<td>" + extractedValues[i] + "</td>");
                            }
                        }
                    }
                }
            } catch (final Exception e) {
                logger.warn("displayEmptyDependency Exception :: {}", e.getMessage());
            }
        });
        return emptyDependencyHtmlValues;
    }

    public static Duration getCommonOffsetFromTwoDowntimeWindows(final LocalDateTime date1Start, final LocalDateTime date1Stop,
                                                                 final LocalDateTime date2Start, final LocalDateTime date2Stop) {
        Duration offset = Duration.ZERO;
        if (date2Start.isBefore(date1Start) && date2Stop.isAfter(date1Start) &&
                (date2Stop.isBefore(date1Stop) || date2Stop.isEqual(date1Stop))) { //one
            logger.info("condition 1");
            offset = offset.plus(Duration.between(date1Start, date2Stop));
        } else if (date2Start.isBefore(date1Stop) && date2Stop.isAfter(date1Stop) &&
                (date2Start.isAfter(date1Start) || date2Start.isEqual(date1Start))) { //two
            logger.info("condition 2");
            offset = offset.plus(Duration.between(date2Start, date1Stop));
        } else if (date2Start.isBefore(date1Stop) && date2Stop.isBefore(date1Stop) &&
                (date2Start.isAfter(date1Start) || date2Start.isEqual(date1Start))) { //three
            logger.info("condition 3");
            offset = offset.plus(Duration.between(date2Start, date2Stop));
        } else if (date2Start.isAfter(date1Start) && date2Start.isBefore(date1Stop) &&
                (date2Stop.isBefore(date1Stop) || date2Stop.isEqual(date1Stop)) ) {  //four
            logger.info("condition 4");
            offset = offset.plus(Duration.between(date2Start, date2Stop));
        } else if (date2Start.isAfter(date1Start) && date2Stop.isBefore(date1Stop)) { //included in three
            logger.info("condition 5");
            offset = offset.plus(Duration.between(date2Start, date2Stop));
        } else if (date2Start.isBefore(date1Start) && date2Stop.isAfter(date1Stop)) {
            logger.info("condition 6");
            offset = offset.plus(Duration.between(date1Start, date1Stop));
        } else if (date2Start.isEqual(date1Start) && date2Stop.isEqual(date1Stop)) {  //equal window
            logger.info("condition 7");
            offset = offset.plus(Duration.between(date1Start, date1Stop));
        } else if (date2Start.isAfter(date1Stop) || date2Stop.isBefore(date1Start)) { //disjoint
            logger.info("condition 8");
            //Do nothing!
        } else {
            logger.info("no offset ....!");
        }
        logger.info("commonWindow duration : {}s", offset.getSeconds());
        return offset;
    }

    static boolean isCommonOffsetFromTwoDowntimeWindows(final LocalDateTime date1Start, final LocalDateTime date1Stop,
                                                        final LocalDateTime date2Start, final LocalDateTime date2Stop) {
        boolean offset = false;
        if (date2Start.isBefore(date1Start) && date2Stop.isAfter(date1Start) &&
                (date2Stop.isBefore(date1Stop) || date2Stop.isEqual(date1Stop))) { //one
            logger.info("condition 1");
            offset = true;
        } else if (date2Start.isBefore(date1Stop) && date2Stop.isAfter(date1Stop) &&
                (date2Start.isAfter(date1Start) || date2Start.isEqual(date1Start))) { //two
            logger.info("condition 2");
            offset = true;
        } else if (date2Start.isBefore(date1Stop) && date2Stop.isBefore(date1Stop) &&
                (date2Start.isAfter(date1Start) || date2Start.isEqual(date1Start))) { //three
            logger.info("condition 3");
            offset = true;
        } else if (date2Start.isAfter(date1Start) && date2Start.isBefore(date1Stop) &&
                (date2Stop.isBefore(date1Stop) || date2Stop.isEqual(date1Stop)) ) {  //four
            logger.info("condition 4");
            offset = true;
        } else if (date2Start.isAfter(date1Start) && date2Stop.isBefore(date1Stop)) { //included in three
            logger.info("condition 5");
            offset = true;
        } else if (date2Start.isBefore(date1Start) && date2Stop.isAfter(date1Stop)) {
            logger.info("condition 6");
            offset = true;
        } else if (date2Start.isEqual(date1Start) && date2Stop.isEqual(date1Stop)) {  //equal window
            logger.info("condition 7");
            offset = true;
        } else if (date2Start.isAfter(date1Stop) || date2Stop.isBefore(date1Start)) { //disjoint
            logger.info("condition 8");
            //Do nothing!
        } else {
            logger.info("no offset ....!");
        }
        logger.info("commonWindow : {}", offset);
        return offset;
    }

    public static Map<LocalDateTime, LocalDateTime> getCommonWindow(final LocalDateTime date1Start, final LocalDateTime date1Stop,
                                                                    final LocalDateTime date2Start, final LocalDateTime date2Stop) {
        Map<LocalDateTime, LocalDateTime> commonWindow = new TreeMap<>();
        if (date2Start.isBefore(date1Start) && date2Stop.isAfter(date1Start) &&
                (date2Stop.isBefore(date1Stop) || date2Stop.isEqual(date1Stop))) { //one
            logger.info("condition 1");
            commonWindow.put(date1Start,date2Stop);
        } else if (date2Start.isBefore(date1Stop) && date2Stop.isAfter(date1Stop) &&
                (date2Start.isAfter(date1Start) || date2Start.isEqual(date1Start))) { //two
            logger.info("condition 2");
            commonWindow.put(date2Start,date1Stop);
        } else if (date2Start.isBefore(date1Stop) && date2Stop.isBefore(date1Stop) &&
                (date2Start.isAfter(date1Start) || date2Start.isEqual(date1Start))) { //three
            logger.info("condition 3");
            commonWindow.put(date2Start, date2Stop);
        } else if (date2Start.isAfter(date1Start) && date2Start.isBefore(date1Stop) &&
                (date2Stop.isBefore(date1Stop) || date2Stop.isEqual(date1Stop)) ) {  //four
            logger.info("condition 4");
            commonWindow.put(date2Start,date2Stop);
        } else if (date2Start.isAfter(date1Start) && date2Stop.isBefore(date1Stop)) { //included in three
            logger.info("condition 5");
            commonWindow.put(date2Start,date2Stop);
        } else if (date2Start.isBefore(date1Start) && date2Stop.isAfter(date1Stop)) {
            logger.info("condition 6");
            commonWindow.put(date1Start,date1Stop);
        } else if (date2Start.isEqual(date1Start) && date2Stop.isEqual(date1Stop)) {  //equal window
            logger.info("condition 7");
            commonWindow.put(date1Start,date1Stop);
        } else if (date2Start.isAfter(date1Stop) || date2Stop.isBefore(date1Start)) { //disjoint
            logger.info("condition 8");
            //Do nothing!
        } else {
            logger.info("no offset ....!");
        }
        logger.info("commonWindow : {}", commonWindow);
        return commonWindow;
    }

    public static Map<LocalDateTime, LocalDateTime> removeOverlapWindows(Map<LocalDateTime, LocalDateTime> map1) {
        Map<LocalDateTime, LocalDateTime> combinedMap = new TreeMap<>();
        Map<LocalDateTime, LocalDateTime> localMap1 = new TreeMap<>();
        Set<Map.Entry<LocalDateTime, LocalDateTime>> entrySet = map1.entrySet();
        Map.Entry<LocalDateTime, LocalDateTime>[] entryArray = entrySet.toArray(new Map.Entry[entrySet.size()]);
        LocalDateTime d1start = entryArray[0].getKey();
        LocalDateTime d1stop = entryArray[0].getValue();
        combinedMap.put(d1start, d1stop);
        logger.info("combined map : {}", combinedMap);
        localMap1.put(d1start, d1stop);
        logger.info("localMap1 is : {}", localMap1);
        logger.info("removing overlapping windows");
        Map<LocalDateTime, LocalDateTime> localMap = new TreeMap<>(map1);
        for (Map.Entry<LocalDateTime, LocalDateTime> entry1 : localMap.entrySet()) {
            LocalDateTime d2start = entry1.getKey();
            LocalDateTime d2stop = entry1.getValue();
            if ((d1start.isEqual(d2start) && d1stop.isEqual(d2stop))) {
                map1.remove(d2start);
                logger.info("condition 1");
            } else if ((d1start.isBefore(d2start) && d1stop.isAfter(d2stop)) || (d2start.isBefore(d1start) && d2stop.isAfter(d1stop))) {
                map1.remove(d2start);
                logger.info("condition 2");
                combinedMap.put(d1start, d1stop);
            } else if ((d2start.isEqual(d1start) || d2start.isBefore(d1start)) && d2stop.isBefore(d1stop) && !d2stop.isBefore(d1start)) {
                combinedMap.put(d2start, d1stop);
                map1.remove(d2start);
                logger.info("condition 3");
            } else if (d1start.isBefore(d2start) && (d1stop.isBefore(d2stop) || d1stop.isEqual(d2stop)) && !d2start.isAfter(d1stop)) {
                combinedMap.put(d1start, d2stop);
                d1stop = d2stop;
                map1.remove(d2start);
                map1.remove(d1start);
                logger.info("condition 4");
            } else {
                localMap1.put(d2start, d2stop);
                localMap1.remove(d1start);
                if (d1start.isBefore(d2start) && d1stop.isBefore(d2stop)) {
                    combinedMap.put(d1start, d1stop);
                    map1.remove(d1start);
                }
                d1start = d2start;
                d1stop = d2stop;
                logger.info("LocalMap1: {}", localMap1);
            }
        }
        localMap1.clear();
        return combinedMap;
    }
}