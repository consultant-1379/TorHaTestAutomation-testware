package com.ericsson.nms.rv.core;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class ApplicationClashingNeo4j {

    private static final Logger logger = LogManager.getLogger(ApplicationClashingNeo4j.class);

    public static  Map<String, Map<Date, Date>> updateDowntimeMap(String appName, Map<String, Map<Date, Date>> appDateTimeMap, Map<String, String> depDowntimeMap) {
        if(appDateTimeMap.isEmpty() || depDowntimeMap.isEmpty()) {
            return appDateTimeMap;
        }
        try {
            final DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            ZoneId defaultZoneId = ZoneId.systemDefault();
            logger.info("{} clashing neo4j DT calculation started", appName);
            logger.info("before removing neo4j clashing windows appDateTimeMap for {} is : {}", appName, appDateTimeMap.get(appName));
            if (appDateTimeMap.get(appName) == null || appDateTimeMap.get(appName).isEmpty()) {
                return appDateTimeMap;
            }
            Map<Date, Date> appTime = new HashMap<>(appDateTimeMap.get(appName));
            boolean isOffset = false;
            for (Map.Entry<Date, Date> startStopTime : appTime.entrySet()) {
                final Date appStartDate = startStopTime.getKey();
                final Date appStopDate = startStopTime.getValue();
                try {
                    for (Map.Entry<String, String> entry : depDowntimeMap.entrySet()) {
                        logger.info("entered into depDownTimeMap, depName : {}", entry.getKey());
                        if (entry.getKey().contains(HAPropertiesReader.NEO4J) && !entry.getKey().equalsIgnoreCase(HAPropertiesReader.NEO4J_FOLLOWER)) {
                            if (entry.getValue() == null || entry.getValue().isEmpty()) {
                                continue;
                            }
                            logger.info("Neo4j DT has there.....{}", entry.getKey());
                            String[] pair = entry.getValue().split(";");
                            for (final String str : pair) {
                                String[] time = str.split(",");
                                if (time[0] == null || time[1] == null || time[0].isEmpty() || time[1].isEmpty()) {
                                    continue;
                                }
                                LocalDateTime neo4jStart = LocalDateTime.parse(time[0], fmt);
                                LocalDateTime neo4jStop = LocalDateTime.parse(time[1], fmt);
                                LocalDateTime appStart = appStartDate.toInstant().atZone(defaultZoneId).toLocalDateTime();
                                LocalDateTime appStop = appStopDate.toInstant().atZone(defaultZoneId).toLocalDateTime();

                                logger.info("neo4jStart : {}", neo4jStart);
                                logger.info("neo4jStop : {}", neo4jStop);
                                logger.info("appStart : {}", appStart);
                                logger.info("appStop : {}", appStop);

                                isOffset = CommonReportHelper.isCommonOffsetFromTwoDowntimeWindows(appStart, appStop, neo4jStart, neo4jStop);
                                if (isOffset) {
                                    appDateTimeMap.get(appName).remove(startStopTime.getKey());
                                    logger.info("Removing DT window {} clashing with neo4j DT ", appName);
                                    logger.info("{} clashing neo4j DT window removing from : {} to : {}", appName, startStopTime.getKey(), startStopTime.getValue());
                                }
                            }
                        }
                    }
                } catch (final Exception ex) {
                    logger.warn(ex.getMessage());
                }
            }
            logger.info("after removing neo4j clashing windows appDateTimeMap for {} is : {}", appName, appDateTimeMap.get(appName));
        } catch (Exception e) {
            logger.warn("error occurred in ApplicationClashingNeo4j.updateDowntimeMap() : {}", e.getMessage());
        }
        return appDateTimeMap;
    }
}
