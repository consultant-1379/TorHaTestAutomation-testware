package com.ericsson.nms.rv.core;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.HashMap;
import java.util.List;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class ProcessDelayedResponses {

    private static final Logger logger = LogManager.getLogger(ProcessDelayedResponses.class);

    /**
     * Update NetEx DT with common offset of Timeout and delayed response.
     * @param appTimeoutMap
     * @return
     */
    public static void updateCommonOffset(final String appName, final Map<String, Map<Date, Date>> appTimeoutMap,final Map<String, List<String>> loggerList) {
        logger.info("appName : {} ignoreDTMap: {}", appName, HAPropertiesReader.ignoreDTMap.get(appName));
        if (HAPropertiesReader.ignoreDTMap.get(appName) == null || appTimeoutMap.get(appName) == null) {
            return ;
        }
        ZoneId defaultZoneId = ZoneId.systemDefault();
        final Map<Date, Date> dateTimeTimeoutMap = appTimeoutMap.get(appName);
        Map<Date, Date> LocalTimeoutMap = new HashMap< >();
        logger.info("appName {} dateTimeTimeoutMap: {}", appName , dateTimeTimeoutMap.toString());
        Map<Date, Date> delayedResponseTimeMap = getConsolidatedResponseTimeMap(HAPropertiesReader.ignoreDTMap.get(appName));
        boolean offset = false;
        if (dateTimeTimeoutMap.isEmpty() || delayedResponseTimeMap.isEmpty()) {
            return ;
        }
        float TotalSeconds = 0L;
        for (Map.Entry<Date, Date> startStopTime : dateTimeTimeoutMap.entrySet()) {
            final LocalDateTime start = startStopTime.getKey().toInstant().atZone(defaultZoneId).toLocalDateTime();
            final LocalDateTime stop = startStopTime.getValue().toInstant().atZone(defaultZoneId).toLocalDateTime();
            long seconds = start.until(stop, ChronoUnit.SECONDS);
            for (Map.Entry<Date, Date> startStopResponseTime : delayedResponseTimeMap.entrySet()) {
                final LocalDateTime reqStart = startStopResponseTime.getKey().toInstant().atZone(defaultZoneId).toLocalDateTime();
                final LocalDateTime reqStop = startStopResponseTime.getValue().toInstant().atZone(defaultZoneId).toLocalDateTime();
                offset = (CommonReportHelper.isCommonOffsetFromTwoDowntimeWindows(start, stop, reqStart, reqStop));
                if (offset) {
                    LocalTimeoutMap.put(startStopTime.getKey(), startStopTime.getValue());
                    final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                    String startChange = start.format(dateTimeFormatter);
                    String stopChange = stop.format(dateTimeFormatter);
                    logger.info("startChange : {}, stopChange : {}", startChange, stopChange);
                    for (String log : loggerList.get(appName)) {
                        if (log.contains(startChange) && log.contains(stopChange)) {
                            log = log.split("\\+")[1].split("s")[0];
                            logger.info("secs in every iteration {}", Float.parseFloat(log));
                            TotalSeconds = (TotalSeconds + Float.parseFloat(log));
                        }
                    }
                }
            }
        }
        HAPropertiesReader.ignoreDtTotalSecs.put(appName, (long) TotalSeconds);
        HAPropertiesReader.ignoreDtTimeOutMap.put(appName, LocalTimeoutMap);
        logger.info("ignore windows for {} is  {} total secs {}" ,appName,HAPropertiesReader.ignoreDtTimeOutMap.get(appName).toString(), HAPropertiesReader.ignoreDtTotalSecs.get(appName).toString() );
    }

    public static void appNeo4jClashCheck(final String appName, final Map<String, Map<Date, Date>> appTimeoutMap, final Map<String, List<String>> loggerList, Map<String, String> downTimeMap, boolean timeOut) {
        if (appTimeoutMap.get(appName) == null) {
            return;
        }
        ZoneId defaultZoneId = ZoneId.systemDefault();
        final Map<Date, Date> dateTimeTimeoutMap = appTimeoutMap.get(appName); //List of either dateTime or dateTimeTimeout for NetEx.
        Map<Date, Date> localTimeoutMap = new HashMap< >();
        logger.info(" In appNeo4jClashCheck for app : {} dateTimeTimeoutMap : {}", appName , dateTimeTimeoutMap.toString());
        logger.info(" ignoreDtTimeOutMap: {}", HAPropertiesReader.ignoreDtTimeOutMap.get(appName));
        logger.info(" loggerList : {}", loggerList.get(appName));     //LoggerList is use to ignore timeout windows from dateTime map
        Map<LocalDateTime, LocalDateTime>  neo4jDowntimeMap;
        if (HAPropertiesReader.isEnvCloud()) {
            neo4jDowntimeMap = getDependentDt(downTimeMap.get(HAPropertiesReader.NEO4J_LEADER));
        } else {
           neo4jDowntimeMap = getDependentDt(downTimeMap.get(HAPropertiesReader.NEO4J));
        }
        boolean offset;
        if (dateTimeTimeoutMap.isEmpty() || neo4jDowntimeMap.isEmpty()) {
            return;
        }
        float totalSeconds = 0L;
        for (Map.Entry<Date, Date> startStopTime : dateTimeTimeoutMap.entrySet()) {
            final LocalDateTime start = startStopTime.getKey().toInstant().atZone(defaultZoneId).toLocalDateTime();
            final LocalDateTime stop = startStopTime.getValue().toInstant().atZone(defaultZoneId).toLocalDateTime();
            for (Map.Entry<LocalDateTime, LocalDateTime> neo4jStartStopTime : neo4jDowntimeMap.entrySet()) {
                final LocalDateTime neoStart = neo4jStartStopTime.getKey();
                final LocalDateTime neoStop = neo4jStartStopTime.getValue();
                boolean isWindowAlreadyIgnored = false;
                if (timeOut && HAPropertiesReader.ignoreDtTimeOutMap.get(appName) != null && HAPropertiesReader.ignoreDtTimeOutMap.get(appName).containsKey(startStopTime.getKey())) { //ignore delayed response, already removed.
                    isWindowAlreadyIgnored = true;
                }
                if (!isWindowAlreadyIgnored) {
                    offset = (CommonReportHelper.isCommonOffsetFromTwoDowntimeWindows(start, stop, neoStart, neoStop));
                    if (offset) {
                        localTimeoutMap.put(startStopTime.getKey(), startStopTime.getValue());
                        final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                        String startChange = start.format(dateTimeFormatter);
                        String stopChange = stop.format(dateTimeFormatter);
                        logger.info("startChange : {}, stopChange : {}", startChange, stopChange);
                        if (loggerList.get(appName) != null) {
                            for (String log : loggerList.get(appName)) {
                                if (log.contains(startChange) && log.contains(stopChange)) {        // if start stop times are equal in different windows .. "contains" may give wrong result.
                                    log = log.split("\\+")[1].split("s")[0];
                                    logger.info(" {} secs in every iteration {}", appName, Float.parseFloat(log));
                                    totalSeconds = (totalSeconds + Float.parseFloat(log));
                                }
                            }
                        }
                    }
                }
            }
        }
        if (timeOut && !localTimeoutMap.isEmpty()) {
            logger.info("before LocalTimeoutMap : {}, totalSeconds : {}", localTimeoutMap, totalSeconds);
            HAPropertiesReader.ignoreTimeoutClashNeo4jTotalSecs.put(appName, (long) totalSeconds);
            if (HAPropertiesReader.ignoreDtTimeOutMap.get(appName) != null) {
                localTimeoutMap.putAll(HAPropertiesReader.ignoreDtTimeOutMap.get(appName));
                logger.info("after LocalTimeoutMap : {}, HAPropertiesReader.ignoreDtTimeOutMap : {}", localTimeoutMap.size(), localTimeoutMap);
            }
            HAPropertiesReader.ignoreDtTimeOutMap.put(appName, localTimeoutMap);
            logger.info("Timeout overlap with neo4j ignore windows for : {} is : {}, total secs : {}", appName, HAPropertiesReader.ignoreDtTimeOutMap.get(appName), HAPropertiesReader.ignoreTimeoutClashNeo4jTotalSecs.get(appName));
        } else if (!timeOut) {
            HAPropertiesReader.ignoreErrorClashNeo4jTotalSecs.put(appName, (long) totalSeconds);
            HAPropertiesReader.ignoreErrorDtMap.put(appName, localTimeoutMap);
            logger.info("error  overlap with neo4j ignore windows for : {} is : {}, total secs {}", appName, HAPropertiesReader.ignoreErrorDtMap.get(appName), HAPropertiesReader.ignoreErrorClashNeo4jTotalSecs.get(appName));
        }
    }

    private static  Map<LocalDateTime, LocalDateTime>  getDependentDt(String dateDateNavigableMap) {
        Map<LocalDateTime, LocalDateTime> dependentDt = new HashMap<>();
        if(dateDateNavigableMap == null){
            return dependentDt;
        }
        final DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String[] pair = dateDateNavigableMap.split(";");
        for (final String str : pair) {
            String[] time = str.split(",");
            if (time[0] == null || time[1] == null || time[0].isEmpty() || time[1].isEmpty()) {
                continue;
            }
            LocalDateTime neo4jStart = LocalDateTime.parse(time[0], fmt);
            LocalDateTime neo4jStop = LocalDateTime.parse(time[1], fmt);
            logger.info("neo4jstart : {} neo4jStop : {}", neo4jStart,neo4jStop);
            dependentDt.put(neo4jStart,neo4jStop);
        }
        return dependentDt;
    }

    private static Map<Date, Date> getConsolidatedResponseTimeMap(final NavigableMap<Date, Date> ignoreDTMap) {
        Map<Date, String> dateList = new TreeMap<>();
        Map<Date, Date> consolidatedDTMap = new TreeMap<>();
        ignoreDTMap.forEach((start, end) -> {
            dateList.put(start, "start");
            dateList.put(end, "end");
        });
        logger.info("dateList : {}", dateList.toString());

        Date nextStartEvent = dateList.entrySet().iterator().next().getKey();
        boolean skipFirstEntry = true;
        int countStartStop = 1;
        for (Map.Entry<Date, String> entry : dateList.entrySet()) {
            if (skipFirstEntry) {
                skipFirstEntry = false;
                continue;
            }
            Date currentEvent = entry.getKey();
            if ("start".equals(entry.getValue())) {
                if (countStartStop == 0) {
                    nextStartEvent = currentEvent;
                }
                ++countStartStop;
            } else {
                --countStartStop;
            }
            if (countStartStop == 0) {
                consolidatedDTMap.put(nextStartEvent, currentEvent);
            }
            if (countStartStop < 0) {
                logger.warn("Application contains wrong time line: {}", dateList.toString());
            }
        }
        logger.info("consolidatedDTMap : {}", consolidatedDTMap);
        return consolidatedDTMap;
    }
}
