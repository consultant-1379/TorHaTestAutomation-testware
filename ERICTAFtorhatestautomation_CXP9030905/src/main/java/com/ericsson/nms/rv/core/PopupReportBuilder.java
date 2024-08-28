package com.ericsson.nms.rv.core;

import com.ericsson.nms.rv.core.upgrade.CloudNativeDependencyDowntimeHelper;
import com.ericsson.nms.rv.core.upgrade.PhysicalDependencyDowntimeHelper;
import com.ericsson.nms.rv.core.upgrade.UpgradeVerificationTasks;
import com.ericsson.nms.rv.core.upgrade.UpgradeVerifier;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Date;
import java.util.Iterator;
import java.util.HashMap;

class PopupReportBuilder {
    private static final Logger logger = LogManager.getLogger(PopupReportBuilder.class);

    private static final String note = "Note : <a href='https://eteamspace.internal.ericsson.com/display/ERSD/Delta+Downtime+Measurement+Process' target='_blank'>Delta DownTime Measurement Process.</a>";
    private static final String isoVersion = HAPropertiesReader.getProperty("to_iso", "-");
    private static final String separator = "==================================================<br>";
    private final Map<String, List<String>> appDateTimeLoggerList;
    private final Map<String, List<String>> appDateTimeTimeoutLoggerList;
    private final Map<String, String> downtimeMap;
    private final Map<String, List<String>> appDependencyMap;
    private final Map<String, Long> dependencyDowntimeValues;
    private final Map<String, Long> thresholdDowntimeValues;
    private final Map<String, Long> thresholdDowntimeTimeoutValues;
    private final Map<String, Long> deltaDTMap;
    private final Map<String, Boolean> areasMap = HAPropertiesReader.getFunctionalAreasMap();

    PopupReportBuilder(final Map<String, List<String>> appDateTimeLoggerList,
                       final Map<String, List<String>> appDateTimeTimeoutLoggerList,
                       final Map<String, String> downtimeMap,
                       final Map<String, Long> dependencyDowntimeValues,
                       final Map<String, Long> thresholdDowntimeValues,
                       final Map<String, Long> thresholdDowntimeTimeoutValues,
                       final Map<String, Long> deltaDTMap) {
        this.appDateTimeLoggerList = appDateTimeLoggerList;
        this.appDateTimeTimeoutLoggerList = appDateTimeTimeoutLoggerList;
        this.downtimeMap = downtimeMap;
        this.appDependencyMap = HAPropertiesReader.getApplicationDependencyMap();
        this.dependencyDowntimeValues = dependencyDowntimeValues;
        this.thresholdDowntimeValues = thresholdDowntimeValues;
        this.thresholdDowntimeTimeoutValues = thresholdDowntimeTimeoutValues;
        this.deltaDTMap = deltaDTMap;
    }

    String buildPopupData() {
        final StringBuilder functionData = new StringBuilder();
        functionData.append(buidData(appDateTimeLoggerList, false,HAPropertiesReader.ignoreErrorDtMap));
        functionData.append(buidData(appDateTimeTimeoutLoggerList, true,HAPropertiesReader.ignoreDtTimeOutMap));
        if (HAPropertiesReader.isEnvCloudNative() && areasMap.get("aaldap") ) {
            functionData.append(buildComAALdapData());
        }
        functionData.append(buildDeltaData());
        return functionData.toString();
    }

    private String buildComAALdapData() {
        final StringBuilder functionData = new StringBuilder();
        final String appName = HAPropertiesReader.COMAALDAP;
        functionData.append("function " + appName + "ErrorData() {\n");
        functionData.append("cleanStart();\n");
        functionData.append("text = \"<p>");
        functionData.append(separator);
        functionData.append(appName + " Error Downtime Breakups Window.<br>");
        functionData.append("ISO Version : " + isoVersion + "<br>");
        functionData.append(separator);
        String comWindows = CloudNativeDependencyDowntimeHelper.comAaLdapDowntimeWindows.get(HAPropertiesReader.COMAALDAP);
        long totalComAaDowntime = 0L;
        if (comWindows != null && !comWindows.isEmpty()) {
            for (String windows : comWindows.split(";")) {
                String[] window = windows.split(",");
                functionData.append("Start time: " + window[0] +", Stop time : " + window[1] + ", [=" + window[2] + "s]<br>");
                totalComAaDowntime = totalComAaDowntime + Long.parseLong(window[2]);
            }
        } else {
            functionData.append("[]<br>");
        }
        functionData.append(separator);
        functionData.append("Total " + appName + " Error Downtime : " + totalComAaDowntime + "s<br>");
        functionData.append(separator);
        functionData.append("</p>\";\n");
        functionData.append("title = \"Error Downtime Breakups [" + appName + "]\";\n");
        functionData.append("backGround = \"#ffe6e6\";\n");
        functionData.append("myFunction();\n");
        functionData.append("}\n\n");

        return functionData.toString();
    }

    private String buidData(final Map<String, List<String>> dataMap, final boolean isTimeoutData,Map<String, Map<Date, Date>> ignoreData) {
        final StringBuilder functionData = new StringBuilder();
        dataMap.forEach((appName, loggerList) -> {
            if(isTimeoutData) {
                functionData.append("function " + appName + "TimeoutData() {\n");
            } else {
                functionData.append("function " + appName + "ErrorData() {\n");
            }
            functionData.append("cleanStart();\n");
            functionData.append("text = \"<p>");
            functionData.append(separator);
            if(isTimeoutData) {
                functionData.append(appName + " Timeout Downtime Breakups Window.<br>");
            } else {
                functionData.append(appName + " Error Downtime Breakups Window.<br>");
            }
            functionData.append("ISO Version : " + isoVersion + "<br>");
            functionData.append(separator);
            Map<Date, Date> LocalTimeoutMap = new HashMap<>();
            if(ignoreData.get(appName)!= null){
                LocalTimeoutMap =(ignoreData.get(appName));
            }

            if (LocalTimeoutMap.size() != 0) {
                logger.info("Before removing ignore windows for {} loggerList : {}", appName, loggerList);
                LocalTimeoutMap.forEach((startDate , stopDate) -> {
                    final LocalDateTime start = startDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
                    final LocalDateTime stop = stopDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
                    final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                    String startChange = start.format(dateTimeFormatter);
                    String stopChange = stop.format(dateTimeFormatter);
                    logger.info("startChange : {}, stopChange : {}", startChange, stopChange);
                    Iterator<String> it = loggerList.iterator();
                    while (it.hasNext()) {
                        String log = it.next();
                        if (log.contains(startChange) && log.contains(stopChange)) {
                            logger.info("removing logger window : {}", log);
                            it.remove();
                        }
                    }
                });
                float totalSeconds = 0;
                for (int i = 0; i < loggerList.size(); i++) {
                    String split = loggerList.get(i).split("\\[")[0];
                    float windowTime = Float.parseFloat(loggerList.get(i).split("\\+")[1].split("s")[0]);
                    totalSeconds = totalSeconds + windowTime;
                    String newLogger = String.format(split + "[+%.02fs], [=%.02fs]", windowTime, totalSeconds);
                    logger.info("old window : " + loggerList.get(i));
                    loggerList.set(i, newLogger);
                    logger.info("new window : " + loggerList.get(i));
                }
                logger.info("After removing ignore windows for {} loggerList : {}", appName, loggerList);
            }

            for (final String log : loggerList) {
                functionData.append(log + "<br>");
            }
            functionData.append(separator);
            final long appTimeout = thresholdDowntimeTimeoutValues.get(appName) == null ? 0L : thresholdDowntimeTimeoutValues.get(appName);
            final long appTotal = thresholdDowntimeValues.get(appName) == null ? 0L : thresholdDowntimeValues.get(appName);
            if(isTimeoutData) {
                functionData.append("Total " + appName + " Timeout Downtime : " + appTimeout + "s<br>");
            } else {
                functionData.append("Total " + appName + " Error Downtime : " + (appTotal - appTimeout) + "s<br>");
            }
            functionData.append(separator);

            functionData.append("</p>\";\n");
            if(isTimeoutData) {
                functionData.append("title = \"Timeout Downtime Breakups [" + appName + "]\";\n");
                functionData.append("backGround = \"#ffffe6\";\n");
            } else {
                functionData.append("title = \"Error Downtime Breakups [" + appName + "]\";\n");
                functionData.append("backGround = \"#ffe6e6\";\n");
            }
            functionData.append("myFunction();\n");
            functionData.append("}\n\n");
        });
        return functionData.toString();
    }

    private String buildDeltaData() {
        final StringBuilder functionData = new StringBuilder();
        HAPropertiesReader.areaMap.forEach((area, appName) -> {
            final List<String> depArray = appDependencyMap.get(appName);
            long totalComAaDowntime = 0L;
            if (depArray != null) {
                String depList = "[";
                for (final String dep : depArray) {
                    depList = depList + "\\\"" + dep + "\\\",";
                }
                depList = depList + "]";
                depList = depList.replace(",]", "]");
                functionData.append("function " + appName + "DeltaData() {\n");
                functionData.append("cleanStart();\n");
                functionData.append("text = \"<p>");
                functionData.append(separator);
                functionData.append(appName + " Delta Downtime Breakups Window.<br>");
                functionData.append("ISO Version : " + isoVersion + "<br>");
                functionData.append(separator);
                functionData.append("Error Downtime Breakups :<br>");
                if (appDateTimeLoggerList.get(appName) != null) {
                    for (final String log : appDateTimeLoggerList.get(appName)) {
                        functionData.append(log + "<br>");
                    }
                }
                if (appName.equalsIgnoreCase(HAPropertiesReader.COMAALDAP) && HAPropertiesReader.isEnvCloudNative()) {
                    String comWindows = CloudNativeDependencyDowntimeHelper.comAaLdapDowntimeWindows.get(HAPropertiesReader.COMAALDAP);
                    if (comWindows != null) {
                        for (String windows : comWindows.split(";")) {
                            String[] window = windows.split(",");
                            functionData.append("Start time: " + window[0] +", Stop time : " + window[1] + ", [=" + window[2] + "s]<br>");
                            totalComAaDowntime = totalComAaDowntime + Long.parseLong(window[2]);
                        }
                    }
                }
                functionData.append(separator);
                functionData.append("Timeout Downtime Breakups :<br>");

                if (appDateTimeTimeoutLoggerList.get(appName) != null) {
                    for (final String log : appDateTimeTimeoutLoggerList.get(appName)) {
                        functionData.append(log + "<br>");
                    }
                }
                functionData.append(separator);
                if(!(HAPropertiesReader.isEnvCloud() || HAPropertiesReader.isEnvCloudNative())) {
                    if (PhysicalDependencyDowntimeHelper.postgresUpliftMap.containsKey(appName)) {
                        logger.info("adding postgres uplift windows in popup window for app : {}", appName);
                        if (PhysicalDependencyDowntimeHelper.postgresUpliftMap.get(appName) != null && !PhysicalDependencyDowntimeHelper.postgresUpliftMap.get(appName).isEmpty()) {
                            functionData.append("postgres uplift windows : <br>");
                            PhysicalDependencyDowntimeHelper.postgresUpliftMap.get(appName).forEach((database, timeList)-> {
                                if(timeList != null && !timeList.isEmpty()) {
                                    functionData.append("for " + database + " : <br>");
                                    for (final String list : timeList.split(";")) {
                                        final String[] startStop = list.split(",");
                                        functionData.append("Start time : " + startStop[0] + ", Stop time : " + startStop[1] + ", [=" + startStop[2] + "s]" + "<br>");
                                    }
                                } else {
                                    functionData.append("for " + database +" : []<br>");
                                }
                            });
                        } else {
                            functionData.append("postgres uplift windows : []<br>");
                        }

                        functionData.append(separator);
                    }
                }
                functionData.append(appName + " dependencies : " + depList + "<br>");
                for (final String dep : depArray) {
                    if (downtimeMap.get(dep) != null && !downtimeMap.get(dep).isEmpty()) {
                        functionData.append("- " + dep + " downtime windows : <br>");
                        for (final String list : downtimeMap.get(dep).split(";")) {
                            final String[] startStop = list.split(",");
                            functionData.append("Start time: " + startStop[0] + ", Stop time : " + startStop[1] + ", [=" + startStop[2] + "s]" + "<br>");
                        }
                    } else {
                        functionData.append("- " + dep + " downtime windows : []<br>");
                    }
                }
                if(!HAPropertiesReader.isEnvCloud() && appName.equalsIgnoreCase(HAPropertiesReader.ENMSYSTEMMONITOR)){
                    List<LocalDateTime> rhelRebootTimings = UpgradeVerifier.getRhelRebootTimings();
                    functionData.append(separator);
                    functionData.append("RHEL patch upgrade timings : <br>");
                    if(!rhelRebootTimings.isEmpty()) {
                        String startTime = rhelRebootTimings.get(0).toString().replace("T", " ");
                        String endTime = rhelRebootTimings.get(1).toString().replace("T", " ");
                        logger.info("start is : {}, end is : {}", startTime, endTime);
                        functionData.append("StartTime : " + startTime + ", StopTime : " + endTime + ", [=" + UpgradeVerifier.rhelRebootDuration + "s]<br>");
                    }
                }
                functionData.append(separator);

                final Long appHaProxyOffset = CalculateHaProxyOffset.appHaProxyOffsetMap.get(appName);
                final Long depHaProxyOffset = CalculateHaProxyOffset.depHaProxyOffsetMap.get(appName);
                final Long deltaDT = deltaDTMap.get(appName);
                final long totalHaProxyOffset = (appHaProxyOffset == null ? 0L : appHaProxyOffset) +
                                                (depHaProxyOffset == null ? 0L : depHaProxyOffset);
                final long totalDeltaDowntime = (thresholdDowntimeValues.get(appName) == null ? 0L : thresholdDowntimeValues.get(appName));
                final long totalDeltaDT = (deltaDT == null ? 0L : deltaDT) - totalHaProxyOffset;
                functionData.append("Total HaProxy Offset (Application + Dependency) : " + totalHaProxyOffset + "s<br>");
                if (appName.equalsIgnoreCase(HAPropertiesReader.COMAALDAP) && HAPropertiesReader.isEnvCloudNative()) {
                    functionData.append("Total " + appName + " DT (Error + Timeout) : " + Math.max(totalComAaDowntime, 0L) + "s<br>");
                } else {
                    functionData.append("Total " + appName + " DT (Error + Timeout) : " + Math.max(totalDeltaDowntime, 0L) + "s<br>");
                }
                functionData.append("Total Dependency DT : " + (dependencyDowntimeValues.get(appName) == null ? 0L : dependencyDowntimeValues.get(appName)) + "s<br>");
                if(!(HAPropertiesReader.isEnvCloud() || HAPropertiesReader.isEnvCloudNative())) {
                    if (PhysicalDependencyDowntimeHelper.postgresUpliftMap.containsKey(appName)) {
                        final Long postgresUpliftOffset = PhysicalDependencyDowntimeHelper.appPostgresUpliftOffsetMap.get(appName);
                        functionData.append("Total Postgres Uplift Offset : " + ((postgresUpliftOffset == null) ? 0 : postgresUpliftOffset) + "s<br>");
                    }
                }
                if(!HAPropertiesReader.isEnvCloud() && appName.equalsIgnoreCase(HAPropertiesReader.ENMSYSTEMMONITOR)){
                    final long rhelEsmOffset = UpgradeVerificationTasks.calculateRhelEsmOffset();
                    functionData.append("Total RHEL ESM Offset: "+ (rhelEsmOffset < 0 ? 0L : rhelEsmOffset) + "s<br>");
                }
                if (appName.equalsIgnoreCase(HAPropertiesReader.COMAALDAP) && HAPropertiesReader.isEnvCloudNative()) {
                    logger.info("totalComAaDowntime : {}", totalComAaDowntime);
                    logger.info("Dependency DT : {}", dependencyDowntimeValues.get(appName));
                    long comDelta = totalComAaDowntime - (dependencyDowntimeValues.get(appName) == null ? 0L : dependencyDowntimeValues.get(appName));
                    functionData.append("Total Delta DT : " + (comDelta < 0 ? 0L : comDelta) + "s<br>");
                } else {
                    functionData.append("Total Delta DT : " + (totalDeltaDT < 0 ? 0L : totalDeltaDT) + "s<br>");
                }
                functionData.append(separator);
                functionData.append(note);
                functionData.append("</p>\";\n");
                functionData.append("title = \"Delta Downtime Breakups [" + appName + "]\";\n");
                functionData.append("backGround = \"#e6f2ff\";\n");
                functionData.append("myFunction();\n");
                functionData.append("}\n\n");
            }
        });
        return functionData.toString();
    }
}
