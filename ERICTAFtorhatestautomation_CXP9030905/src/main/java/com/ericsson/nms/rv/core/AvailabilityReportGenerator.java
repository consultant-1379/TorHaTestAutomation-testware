package com.ericsson.nms.rv.core;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.List;
import java.util.NavigableMap;
import java.util.concurrent.ConcurrentHashMap;

import com.ericsson.nms.rv.core.launcher.AppLauncher;
import com.ericsson.nms.rv.core.upgrade.CloudNativeDependencyDowntimeHelper;
import com.ericsson.nms.rv.core.upgrade.PhysicalDependencyDowntimeHelper;
import com.ericsson.nms.rv.core.upgrade.UpgradeVerificationTasks;
import com.ericsson.nms.rv.core.util.AdminUserUtils;
import com.ericsson.nms.rv.core.util.CommonUtils;
import com.ericsson.nms.rv.core.util.FunctionalArea;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.text.StrSubstitutor;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.ericsson.cifwk.taf.annotations.Attachment;
import com.ericsson.cifwk.taf.annotations.Operator;
import com.ericsson.nms.rv.core.upgrade.UpgradeVerifier;
import com.ericsson.sut.test.cases.HighAvailabilityTestCase;


@Operator
public class AvailabilityReportGenerator {
    private static final String ERROR = String.format("ADU Timed out: ADU exceeded the run time more than %.1f Hours.", HAPropertiesReader.cloudUgFinishTimeLimit / 3600f);
    private static final String ERROR_CONSEQUENCE = "Testware did clean up but can't be guareented.";
    private static final String MANUAL_CLEANUP_STEPS_INFO = "Please follow below mentioned link for manual clean up process for required applications";
    private static final String REFERAL_TO_CLEANUP_INFO = "<a href=\"https://eteamspace.internal.ericsson.com/display/ERSD/ADU+Manual+Clean-up+Process\" target=\"_blank\"> Manual Clean up </a>";
    private static final Logger logger = LogManager.getLogger(AvailabilityReportGenerator.class);
    private static final String tafClusterId = HAPropertiesReader.getTafClusterId();
    private static final String tafConfigDitDeploymentName = HAPropertiesReader.getTafConfigDitDeploymentName();
    private static final String FROM_ISO = HAPropertiesReader.getFromIso();
    private static final String TO_ISO = HAPropertiesReader.getToIso();

    private final Duration duration;
    private String ugRegStartTime;
    private String ugRegEndTime;
    private final Map<String, Long> thresholdDowntimeValues;
    private final Map<String, Long> thresholdDowntimeTimeoutValues;
    private Map<String, Long> deltaDowntimeValues;
    private final Map<String, String> downtimeMap;
    private final Map<String, Map<Date, Date>> appDateTimeMap;
    private final Map<String, Map<Date, Date>> appDateTimeTimeoutMap;
    private Map<String, String> multiMapErrorCollection;
    private AppLauncher appLauncher;
    final Map<String, NavigableMap<Date, Date>> ignoreDTMap;
    public static final List<String> dependencyThresholdErrorList = new ArrayList<>();
    private final Map<String, List<String>> appDateTimeLoggerList;
    private final Map<String, List<String>> appDateTimeTimeoutLoggerList;

    public AvailabilityReportGenerator(final Duration duration,
                                       final String ugRegStartTime,
                                       final String ugRegEndTime,
                                       final Map<String, Long> totalDowntimeValues,
                                       final Map<String, Long> totalTimeoutDowntimeValues,
                                       final Map<String, String> knownDowntimesMap,
                                       final Map<String, Map<Date, Date>> appDateTimeList,
                                       final Map<String, Map<Date, Date>> appDateTimeTimeoutList,
                                       final AppLauncher appLauncher,
                                       Map<String, String> multiMapErrorCollection,
                                       final Map<String, List<String>> appDateTimeLoggerList,
                                       final Map<String, List<String>> appDateTimeTimeoutLoggerList,
                                       final Map<String, NavigableMap<Date, Date>> ignoreDTMap) {
        this.duration = duration;
        this.ugRegStartTime = ugRegStartTime;
        this.ugRegEndTime = ugRegEndTime;
        thresholdDowntimeValues = new TreeMap<>(totalDowntimeValues);
        thresholdDowntimeTimeoutValues = new TreeMap<>(totalTimeoutDowntimeValues);
        deltaDowntimeValues = updateDowntimeValues(totalDowntimeValues, knownDowntimesMap, appDateTimeList);
        appDateTimeMap = appDateTimeList;
        appDateTimeTimeoutMap = appDateTimeTimeoutList;
        this.downtimeMap = new TreeMap(knownDowntimesMap);
        this.appLauncher = appLauncher;
        this.multiMapErrorCollection = multiMapErrorCollection;
        this.appDateTimeLoggerList = appDateTimeLoggerList;
        this.appDateTimeTimeoutLoggerList = appDateTimeTimeoutLoggerList;
        this.ignoreDTMap = ignoreDTMap;
    }

    public static Map<String, Long> updateDowntimeValues(final Map<String, Long> totalDowntimeValues, final Map<String, String> knownDowntimesMap,
                                                         final Map<String, Map<Date, Date>> appDateTimeMapLocal) {
        if(!UpgradeVerificationTasks.isUpgradeFailed()) {
            final Map<String, Long> dependencyDowntimeValues = BuildDowntime.getTotalDTPerComponent(knownDowntimesMap, appDateTimeMapLocal);
            return BuildDowntime.buildDowntimesMap(totalDowntimeValues, dependencyDowntimeValues);
        } else {
            final Map<String, Long> downtimeMap = new HashMap<>();
            return downtimeMap;
        }
    }

    public Map<String, Long> getDeltaDowntimeValues() {
        return deltaDowntimeValues;
    }

    /**
     * Attaches the Availability Tests Reports to Allure Report
     */
    @Attachment(value = "Availability Report", type = "text/html")
    public byte[] attachTestReport() {
        if(!UpgradeVerificationTasks.isUpgradeFailed()) {
            deltaDowntimeValues = CalculateHaProxyOffset.updateDowntimeMap(appDateTimeMap, downtimeMap, deltaDowntimeValues);
            if(!(HAPropertiesReader.isEnvCloud() || HAPropertiesReader.isEnvCloudNative())) {
                deltaDowntimeValues = PhysicalDependencyDowntimeHelper.removePostgresUpliftOffset(appDateTimeMap, deltaDowntimeValues);
            }
        }
        final String htmlReport = processTemplate(getTemplate("ha/HTMLReport.html"), populateHTMLValues());
        if (!(HAPropertiesReader.getTestType().equalsIgnoreCase("Regression"))) {
            CommonUtils.writeResultToWorkLoadVM(htmlReport);
        }
        return htmlReport.getBytes();
    }

    @Attachment(value = "ApplicationLauncher Report", type = "text/html")
    public byte[] attachLauncherReport() {
        final String htmlReport = processTemplate(getTemplate("ha/LAUNCHERHtml.html"), launcherHTMLValues());
        return htmlReport.getBytes();
    }

    /**
     * Attaches the Delayed Response Tracker to Allure Report
     */
    @Attachment(value = "Delayed Response Tracker", type = "text/html")
    public byte[] attachDelayedResponseTracker() {
        final String htmlReport = processTemplate(getTemplate("ha/DelayedTracker.html"), delayedHTMLValues());
        return htmlReport.getBytes();
    }

    public static String getTemplate(final String reportTemplate) {
        String template = "";
        final ClassLoader classLoader = AvailabilityReportGenerator.class.getClassLoader();
        try {
            final String externalForm = classLoader.getResource(reportTemplate).toExternalForm();
            logger.info("Location of the Template file: {}", externalForm);
            template = IOUtils.toString(classLoader.getResourceAsStream(reportTemplate));
        } catch (final IOException e) {
            logger.error("Cannot read the Template file", e);
        }
        return template;
    }

    /**
     * Substitutes the parameters in the template with the values passed
     */
    public static String processTemplate(final String template, final Map<String, String> valuesMap) {
        final StrSubstitutor sub = new StrSubstitutor(valuesMap);
        return sub.replace(template);
    }

    private Map<String, String> launcherHTMLValues() {
        final Map<String, String> values = new HashMap<>();
        if(!HighAvailabilityTestCase.upgradeFailedFlag) {
         try {
             final AppLauncherReport appLauncherReport = new AppLauncherReport(appLauncher);
             values.put("addLauncherRequestValues", appLauncherReport.addLauncherRequestValues());
             values.put("TotalCount",String.valueOf((appLauncher.getTotalCounter())));
             values.put("addLauncherFailedValues", appLauncherReport.addLauncherFailedValues());
             values.put("addAllAppFailedDataPopUp",appLauncherReport.addLauncherFailedPopupData());
         } catch (final Exception e) {
             logger.warn("applauncher report failed because it is disabled in ADU.");
         }
        }
        return values;
    }

    private Map<String,String> delayedHTMLValues() {
        final Map<String, String> values = new ConcurrentHashMap<>();
        if(!HighAvailabilityTestCase.upgradeFailedFlag) {
            try {
                logger.info("Ignore Dt Map...{}",HAPropertiesReader.ignoreDTMap.toString());
                values.put("addDelayedResponseValues", DelayedResponseTracker.addDelayedResponseValues(ignoreDTMap));
                values.put("colSize",DelayedResponseTracker.addColSize(ignoreDTMap));
                values.put("addDelayedDataPopUp", DelayedResponseTracker.addPopUp(ignoreDTMap));
                if (HAPropertiesReader.isEnvCloudNative()) {
                    if(HAPropertiesReader.isDeploymentCCD()) {
                        values.put("Type", "CCD");
                    } else {
                        values.put("Type", "cENM");
                    }
                } else if(HAPropertiesReader.isEnvCloud()) {
                    if(HAPropertiesReader.VIOENV) {
                        values.put("Type", "SIENM(VIO)");
                    } else {
                        values.put("Type", "vENM");
                    }
                } else {
                    if(HAPropertiesReader.neoconfig40KFlag) {
                        values.put("Type", "Stand Alone Cluster(40K or Lower)");
                    } else if(HAPropertiesReader.neoconfig60KFlag){
                        values.put("Type", "Causal Cluster(60K)");
                    }
                }
                if (!"-".equals(tafClusterId)) {
                    values.put("ENV", tafClusterId);
                } else if (!tafConfigDitDeploymentName.isEmpty()) {
                    values.put("ENV", tafConfigDitDeploymentName);
                } else{
                    values.put("ENV", "cENM");
                }
            } catch (final Exception e) {
                logger.info("Delayed Tracker  failed  in ADU.......: {}", e.getMessage());
            }
        }
        return values;
    }

    private Map<String, String> populateHTMLValues() {
        final Map<String, String> values = new HashMap<>();
        if (AdminUserUtils.createFlag.equalsIgnoreCase("fail")) {
            logger.info("User creation is failed");
            values.put("userCreationFailedMessage", AdminUserUtils.userErrorMessage);
        } else {
            values.put("userCreationFailedMessage", " ");
        }
        if(HAPropertiesReader.isEnvCloudNative()) {
            values.put("envcloudnative", "show");
            values.put("envcloud", "none");
            values.put("envphysical", "none");
        } else if (HAPropertiesReader.isEnvCloud()) {
            values.put("envcloudnative", "none");
            values.put("envcloud", "show");
            values.put("envphysical", "none");
        } else {
            values.put("envcloudnative", "none");
            values.put("envcloud", "none");
            values.put("envphysical", "show");
        }
        if (!(HAPropertiesReader.getTestType().equalsIgnoreCase("Regression"))) {
            if (UpgradeVerificationTasks.isUpgradeFailed() || UpgradeVerifier.isUpgradeTimeExceeded) {
                values.putAll(HtmlValuesForFailedUpgrade());
            } else {
                values.putAll(HtmlValues());
            }
        } else {
            values.putAll(HtmlValues());
        }
        return values;
    }

    private String addDowntimeMap() {
        final StringBuilder htmlValuesBinding = new StringBuilder();
        if (HAPropertiesReader.isEnvCloudNative()) {
            final Map<String, Long> infraImageTotalDownloadTimeMap = BuildDowntime.getTotalImagePullTimePerComponent(downtimeMap);
            logger.info("infraImageTotalDownloadTimeMap : {}", infraImageTotalDownloadTimeMap);
            htmlValuesBinding.append(CloudReportHelper.cloudNativeDependencyDowntimeDisplay(downtimeMap, infraImageTotalDownloadTimeMap));
        } else if (HAPropertiesReader.isEnvCloud()) {
            htmlValuesBinding.append(CloudReportHelper.cloudDependencyDowntimeDisplay(downtimeMap));
        } else {
            htmlValuesBinding.append(PhysicalReportHelper.PhysicalDependencyDowntimeDisplay(downtimeMap));
        }
        return htmlValuesBinding.toString();
    }

    private String addDowntimeInfo() {
        final StringBuilder htmlValues = new StringBuilder();
        logger.info("thresholdDowntimeValues : {}", thresholdDowntimeValues.toString());
        logger.info("thresholdDowntimeTimeoutValues : {}", thresholdDowntimeTimeoutValues.toString());
        thresholdDowntimeValues.forEach((key, thresholdTotalValue) -> {
            try {
                if (!(key.equalsIgnoreCase(HAPropertiesReader.ENMSYSTEMMONITOR) && HAPropertiesReader.isEnvCloudNative())) {
                    if (key.equalsIgnoreCase(HAPropertiesReader.SYSTEMVERIFIER)) {
                        thresholdDowntimeTimeoutValues.put(key, 0L);
                    }
                    htmlValues.append("<tr>");
                    if (key.equalsIgnoreCase(HAPropertiesReader.CNOMSYSTEMMONITOR)) {
                        htmlValues.append("<td>" + HAPropertiesReader.ENMSYSTEMMONITOR + " (cnom)</td>");
                    } else {
                        htmlValues.append("<td>" + key + "</td>");
                    }

                    final Map<String, Long> map = HAPropertiesReader.getFunctionalAreasThresholdMap().get(key);
                    final Long deltaThreshold = map.get(HAPropertiesReader.DELTA);

                    logger.info("thresholdDowntimeTimeoutValues key: {}, value: {}", key, thresholdDowntimeTimeoutValues.get(key));
                    final long thresholdTimeoutValueLocal = (thresholdDowntimeTimeoutValues.get(key) == null ? 0L : thresholdDowntimeTimeoutValues.get(key));
                    final long thresholdTimeoutValue = Math.max(thresholdTimeoutValueLocal, 0L);
                    logger.info("thresholdTimeoutValue : {}", thresholdTimeoutValue);
                    if (thresholdTotalValue == -1) {
                        htmlValues.append("<td style=\"color: #808080;\"> N/A" + "</td>");
                        htmlValues.append("<td style=\"color: #895e1e;\"> N/A" + "</td>");
                    } else {
                        final long deltaDtValueLocal = thresholdTotalValue - thresholdTimeoutValue;
                        final long deltaErrorDtValue = Math.max(deltaDtValueLocal, 0L);
                        if (deltaErrorDtValue > 0L) {
                            final String errorUrlString = "<a onclick=\"" + key + "ErrorData()" + "\" ><u>" + deltaErrorDtValue + "s</u></a>";
                            htmlValues.append("<td style=\"color: #808080;cursor:pointer;\">" + errorUrlString + "</td>");
                        } else {
                            htmlValues.append("<td style=\"color: #808080;\">" + deltaErrorDtValue + "s</td>");
                        }
                        if (thresholdTimeoutValue > 0L) {
                            final String timeoutUrlString = "<a onclick=\"" + key + "TimeoutData()" + "\" ><u>" + thresholdTimeoutValue + "s</u></a>";
                            htmlValues.append("<td style=\"color: #895e1e;cursor:pointer;\">" + timeoutUrlString + "</td>");
                        } else {
                            htmlValues.append("<td style=\"color: #895e1e;\">" + thresholdTimeoutValue + "s</td>");
                        }
                    }

                    reportDowntime(htmlValues, key, deltaThreshold);

                    htmlValues.append("</tr>");
                }
            } catch (final Exception e) {
                e.printStackTrace();
            }
        });
        if (HAPropertiesReader.isEnvCloudNative() && HAPropertiesReader.getFunctionalAreasMap().get(FunctionalArea.AALDAP.get())) {
            logger.info("multiMapErrorCollection : {}", multiMapErrorCollection);
            if (multiMapErrorCollection.containsKey(HAPropertiesReader.COMAALDAP)) {
                htmlValues.append(displayComAALdapError());
            } else {
                htmlValues.append(displayComAALdapDowntime());
            }
        }
        return htmlValues.toString();
    }

    private StringBuilder displayComAALdapError() {
        final StringBuilder htmlValues = new StringBuilder();
        htmlValues.append("<tr>");
        htmlValues.append("<td>" + HAPropertiesReader.COMAALDAP + "</td>");
        htmlValues.append("<td style=\"color: #808080;\">N/A</td>");
        htmlValues.append("<td style=\"color: #895e1e;\">N/A</td>");
        htmlValues.append("<td style=\"color: #ff0000;\" colspan=\"2\">" + multiMapErrorCollection.get(HAPropertiesReader.COMAALDAP)+ "</td>");
        htmlValues.append("<tr>");
        return htmlValues;
    }

    private StringBuilder displayComAALdapDowntime() {
        final StringBuilder htmlValues = new StringBuilder();
        long totalComAaLdapDT = 0L;
        final String comDtStrings = CloudNativeDependencyDowntimeHelper.comAaLdapDowntimeWindows.get(HAPropertiesReader.COMAALDAP);
        if (comDtStrings != null) {
            final String[] downtime = comDtStrings.split(";");
            for (final String str : downtime) {
                final String[] strings = str.split(",");
                try {
                    if (strings.length == 3) {
                        totalComAaLdapDT = totalComAaLdapDT + Long.parseLong(strings[2]);
                    }
                } catch (final Exception e) {
                    logger.warn("Message : {}", e.getMessage());
                }
            }
        }
        logger.info("totalComAaLdapDT : {}", totalComAaLdapDT);
        final long comAaErrorDT = Math.max(totalComAaLdapDT, 0L);
        logger.info("comAaErrorDT : {}", comAaErrorDT);
        htmlValues.append("<tr>");
        htmlValues.append("<td>" + HAPropertiesReader.COMAALDAP + "</td>");
        if (comAaErrorDT > 0L) {
            final String errorUrlString = "<a onclick=\"" + HAPropertiesReader.COMAALDAP + "ErrorData()" + "\" ><u>" + comAaErrorDT + "s</u></a>";
            htmlValues.append("<td style=\"color: #808080;cursor:pointer;\">" + errorUrlString + "</td>");
        } else {
            htmlValues.append("<td style=\"color: #808080;\">" + comAaErrorDT + "s</td>");
        }
        htmlValues.append("<td style=\"color: #895e1e;\">" + 0 + "s</td>");
        final long comAaDeltaThreshold = HAPropertiesReader.getFunctionalAreasThresholdMap().get(HAPropertiesReader.COMAALDAP).get(HAPropertiesReader.DELTA);
        logger.info("comAaDeltaThreshold : {}", comAaDeltaThreshold);
        final Long comAaDependencyDT = BuildDowntime.getTotalDTPerComponent(downtimeMap, appDateTimeMap).get(HAPropertiesReader.COMAALDAP); //Total dep DT
        logger.info("Total comAaDependencyDT : {}", comAaDependencyDT);
        final long delta;
        if (comAaDependencyDT != null) { // non-zero dependency DT.
            delta = comAaErrorDT - comAaDependencyDT;
        } else {
            delta = comAaErrorDT;
        }
        logger.info("ComAALdap delta DT : {}", delta);
        long deltaDowntime = Math.max(delta, 0L);
        if (deltaDowntime > comAaDeltaThreshold) {
            htmlValues.append("<td style=\"color: #ff0000;cursor:pointer;\" colspan=\"2\">" + "<a onclick=\"" + HAPropertiesReader.COMAALDAP + "DeltaData()" + "\" ><u>" + deltaDowntime + "s</u></a>("+ comAaDeltaThreshold + "s)" + "</td>");
            dependencyThresholdErrorList.add(HAPropertiesReader.COMAALDAP);
        } else {
            htmlValues.append("<td style=\"cursor:pointer;\" colspan=\"2\">" + "<a onclick=\"" + HAPropertiesReader.COMAALDAP + "DeltaData()" + "\" ><u>" + deltaDowntime +  "s</u></a>("+ comAaDeltaThreshold + "s)" + "</td>");
        }
        htmlValues.append("</tr>");
        return htmlValues;
    }

    private void reportDowntime(final StringBuilder htmlValues, final String key, final Long deltaThreshold) {
        final Long deltaDowntimeLocal = deltaDowntimeValues.get(key);
        Long deltaDowntime = deltaDowntimeLocal > 0L ? deltaDowntimeLocal : 0L;
        if (key.equalsIgnoreCase(HAPropertiesReader.APPLAUNCHER)) {
            if(multiMapErrorCollection.containsKey(key)) {
                if(HAPropertiesReader.isEnvCloudNative()) {
                    htmlValues.append("<td style=\"color: #ff0000;\" colspan=\"2\">" + multiMapErrorCollection.get(key) + "</td>");
                } else {
                    htmlValues.append("<td style=\"color: #ff0000;\">" + multiMapErrorCollection.get(key) + "</td>");
                }
            } else if (appLauncher.getMissingLinksMap().size() > 0 ) {
                if(HAPropertiesReader.isEnvCloudNative()) {
                    htmlValues.append("<td style=\"color: #ff0000;\" colspan=\"2\">" + "UI links for few applications are missing(Refer Missing links column in Application Launcher Report)" + "</td>");
                } else {
                    htmlValues.append("<td style=\"color: #ff0000;\">" + "UI links for few applications are missing(Refer Missing links column in Application Launcher Report)" + "</td>");
                }
            } else if (appLauncher.isFailedRequests()) {
                if(HAPropertiesReader.isEnvCloudNative()) {
                    htmlValues.append("<td style=\"color: #ff0000;\" colspan=\"2\">" + "All applications have failed HTTP Requests(Refer Request column in Application Launcher Report)" + "</td>");
                } else {
                    htmlValues.append("<td style=\"color: #ff0000;\">" + "All applications have failed HTTP Requests(Refer Request column in Application Launcher Report)" + "</td>");
                }
            } else {
                if(HAPropertiesReader.isEnvCloudNative()) {
                    htmlValues.append("<td colspan=\"2\">" + deltaDowntime + "s <font size=\"2\">(" + deltaThreshold + "s)</font></td>");
                } else if (HAPropertiesReader.isEnvCloud()) {
                    htmlValues.append("<td>" + deltaDowntime + "s <font size=\"2\">(" + deltaThreshold + "s)</font></td>");
                } else {
                    htmlValues.append("<td>" + deltaDowntime + "s</td>");
                }
            }
            if (!HAPropertiesReader.isEnvCloud()) {
                htmlValues.append("<td>" + deltaThreshold + "s</td>");
            }
        } else {
            if (multiMapErrorCollection.containsKey(key)) {
                if(HAPropertiesReader.isEnvCloudNative()) {
                    htmlValues.append("<td style=\"color: #ff0000;\" colspan=\"2\">" + multiMapErrorCollection.get(key) + "</td>");
                } else {
                    htmlValues.append("<td style=\"color: #ff0000;\">" + multiMapErrorCollection.get(key) + "</td>");
                }
            } else if (deltaDowntime != null && deltaDowntime > deltaThreshold) {
                if(HAPropertiesReader.isEnvCloudNative()) {
                    htmlValues.append("<td style=\"color: #ff0000;cursor:pointer;\" colspan=\"2\">" + "<a onclick=\"" + key + "DeltaData()" + "\" ><u>" + deltaDowntime + "s</u></a><font size=\"2\">(" + deltaThreshold + "s)</font></td>");
                } else if(HAPropertiesReader.isEnvCloud()) {
                    htmlValues.append("<td style=\"color: #ff0000;cursor:pointer;\">" + "<a onclick=\"" + key + "DeltaData()" + "\" ><u>" + deltaDowntime + "s</u></a><font size=\"2\">(" + deltaThreshold + "s)</font></td>");
                } else {
                    htmlValues.append("<td style=\"color: #ff0000;cursor:pointer;\">" + "<a onclick=\"" + key + "DeltaData()" + "\" ><u>" + deltaDowntime + "s</u></a></td>");
                }
            } else if (deltaDowntime != null) {
                if(HAPropertiesReader.isEnvCloudNative()) {
                    htmlValues.append("<td style=\"cursor:pointer;\" colspan=\"2\">" + "<a onclick=\"" + key + "DeltaData()" + "\" ><u>" + deltaDowntime + "s</u></a><font size=\"2\">(" + deltaThreshold + "s)</font></td>");
                } else if (HAPropertiesReader.isEnvCloud()) {
                    htmlValues.append("<td style=\"cursor:pointer;\">" + "<a onclick=\"" + key + "DeltaData()" + "\" ><u>" + deltaDowntime + "s</u></a><font size=\"2\">(" + deltaThreshold + "s)</font></td>");
                } else {
                    htmlValues.append("<td style=\"cursor:pointer;\">" + "<a onclick=\"" + key + "DeltaData()" + "\" ><u>" + deltaDowntime + "s</u></a></td>");
                }
            } else {
                if(HAPropertiesReader.isEnvCloudNative()) {
                    htmlValues.append("<td colspan=\"2\">NA</td>");
                } else {
                    htmlValues.append("<td>NA</td>");
                }
            }
            if(!HAPropertiesReader.isEnvCloud()) {
                htmlValues.append("<td>" + deltaThreshold + "s</td>");
            }
        }
    }

    private Map<String, String> HtmlValues() {
        final Map<String, String> valuesStorage = new HashMap<>();
        logger.info("Upgrade time is below expected range");
        valuesStorage.put("ShowDT", "show");
        valuesStorage.put("ShowError", "none");
        if (HAPropertiesReader.isEnvCloudNative()) {
            if(HAPropertiesReader.isDeploymentCCD()) {
                valuesStorage.put("Type", "CCD");
            } else {
                valuesStorage.put("Type", "cENM");
            }
        } else if(HAPropertiesReader.isEnvCloud()) {
            if(HAPropertiesReader.VIOENV) {
                valuesStorage.put("Type", "SIENM(VIO)");
            } else {
                valuesStorage.put("Type", "vENM");
            }
        } else {
            if(HAPropertiesReader.neoconfig40KFlag) {
                valuesStorage.put("Type", "Stand Alone Cluster(40K or Lower)");
            } else if (HAPropertiesReader.neoconfig60KFlag) {
                valuesStorage.put("Type", "Causal Cluster(60K)");
            } else {
                valuesStorage.put("Type", "Type Not Available");
            }
        }
        if (!"-".equals(tafClusterId)) {
            valuesStorage.put("ENV", tafClusterId);
        } else if (!tafConfigDitDeploymentName.isEmpty()) {
            valuesStorage.put("ENV", tafConfigDitDeploymentName);
        }
        valuesStorage.put("From_ISO", FROM_ISO);
        valuesStorage.put("To_ISO", TO_ISO);
        valuesStorage.put("testType", "Upgrade");
        if (HAPropertiesReader.getTestType().equalsIgnoreCase("Regression")) {
            valuesStorage.put("testType", "Regression");
        }
        valuesStorage.put("test_Start_Time", ugRegStartTime);
        valuesStorage.put("test_End_Time", ugRegEndTime);
        final String upgradeTime = String.format("%d hours %d mins %d secs", duration.toHours(), duration.toMinutes() % 60, duration.getSeconds() % 3600 % 60);
        valuesStorage.put("upgradeTime", upgradeTime);
        valuesStorage.put("downtimeInfo", addDowntimeInfo());
        valuesStorage.put("downtimeMap", addDowntimeMap());
        valuesStorage.put("popupDowntimeInfo", addPopupDowntimeInfo());
        if (HAPropertiesReader.isEnvCloudNative()) {
            valuesStorage.put("popupImageInfo", addPopupImageInfo());
        } else {
            valuesStorage.put("popupImageInfo", "");
        }
        return valuesStorage;
    }

    private String addPopupImageInfo() {
        final StringBuilder functionData = new StringBuilder();
        if(!UpgradeVerificationTasks.isUpgradeFailed()) {
            logger.info("Dependency map in cloud native {}", downtimeMap);
            final Map<String, Long> totalImagePullTime = BuildDowntime.getTotalImagePullTimePerComponent(downtimeMap);
            ImagePopupReportBuilder popupReportBuilder = new ImagePopupReportBuilder(CloudNativeDependencyDowntimeHelper.infraImagePullData,
                    totalImagePullTime, downtimeMap);
            functionData.append(popupReportBuilder.buildPopupData());
        }
        logger.info("popupImageInfo: {}", functionData);
        return functionData.toString();
    }

    private String addPopupDowntimeInfo() {
        final StringBuilder functionData = new StringBuilder();
        if(!UpgradeVerificationTasks.isUpgradeFailed()) {
            final Map<String, Long> dependencyDowntimeValues = BuildDowntime.getTotalDTPerComponent(downtimeMap, appDateTimeMap);
            Map<String, Long> deltaDTMap = BuildDowntime.buildDowntimesMap(thresholdDowntimeValues, dependencyDowntimeValues);
            if(!(HAPropertiesReader.isEnvCloud() || HAPropertiesReader.isEnvCloudNative())) {
                deltaDTMap = PhysicalDependencyDowntimeHelper.removePostgresUpliftOffset(appDateTimeMap, deltaDTMap);
            }
            PopupReportBuilder popupReportBuilder = new PopupReportBuilder(appDateTimeLoggerList, appDateTimeTimeoutLoggerList,
                    downtimeMap, dependencyDowntimeValues, thresholdDowntimeValues, thresholdDowntimeTimeoutValues, deltaDTMap);
            functionData.append(popupReportBuilder.buildPopupData());
        }
        return functionData.toString();
    }

    private Map<String,String> HtmlValuesForFailedUpgrade() {
        final Map<String, String> valuesStorageForTimeOut = new HashMap<>();
        logger.info("Upgrade Failed !");
        valuesStorageForTimeOut.put("ShowError", "show");
        valuesStorageForTimeOut.put("ShowDT", "none");
        if (HAPropertiesReader.isEnvCloudNative()) {
            if(HAPropertiesReader.isDeploymentCCD()) {
                valuesStorageForTimeOut.put("Type", "CCD");
            } else {
                valuesStorageForTimeOut.put("Type", "cENM");
            }
        } else if(HAPropertiesReader.isEnvCloud()) {
            if(HAPropertiesReader.VIOENV) {
                valuesStorageForTimeOut.put("Type", "SIENM(VIO)");
            } else {
                valuesStorageForTimeOut.put("Type", "vENM");
            }
        } else {
            if(HAPropertiesReader.neoconfig40KFlag) {
                valuesStorageForTimeOut.put("Type", "Stand Alone Cluster(40K or Lower)");
            } else if(HAPropertiesReader.neoconfig60KFlag){
                valuesStorageForTimeOut.put("Type", "Causal Cluster(60K)");
            }
        }
        if (!"-".equals(tafClusterId)) {
            valuesStorageForTimeOut.put("ENV", tafClusterId);
        } else if (!tafConfigDitDeploymentName.isEmpty()) {
            valuesStorageForTimeOut.put("ENV", tafConfigDitDeploymentName);
        }
        valuesStorageForTimeOut.put("From_ISO", FROM_ISO);
        valuesStorageForTimeOut.put("To_ISO", TO_ISO);
        final String upgradeTime = String.format("%d hours %d mins %d secs", duration.toHours(), duration.toMinutes() % 60, duration.getSeconds() % 3600 % 60);
        valuesStorageForTimeOut.put("upgradeTime", upgradeTime);
        if (HighAvailabilityTestCase.failedToWaitForUpgrade) {
            valuesStorageForTimeOut.put("ErrorMessage", "ADU failed as upgrade isn't started within the specified time.");
        } else if ((HAPropertiesReader.isEnvCloudNative() || HAPropertiesReader.isEnvCloud()) && UpgradeVerifier.isUpgradeTimeExceeded) {
            valuesStorageForTimeOut.put("ErrorMessage", ERROR);
        } else {
            valuesStorageForTimeOut.put("ErrorMessage", "ADU failed due to upgrade failure");
        }
        valuesStorageForTimeOut.put("ErrorConsequence", ERROR_CONSEQUENCE);
        valuesStorageForTimeOut.put("ManualCleanUp", MANUAL_CLEANUP_STEPS_INFO);
        valuesStorageForTimeOut.put("ReferalLink", REFERAL_TO_CLEANUP_INFO);
        return valuesStorageForTimeOut;
    }
}