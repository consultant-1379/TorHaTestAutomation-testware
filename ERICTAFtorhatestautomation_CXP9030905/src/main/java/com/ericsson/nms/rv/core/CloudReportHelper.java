package com.ericsson.nms.rv.core;

import static com.ericsson.nms.rv.core.AvailabilityReportGenerator.dependencyThresholdErrorList;

import java.util.Map;

import com.ericsson.nms.rv.core.upgrade.CommonDependencyDowntimeHelper;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class CloudReportHelper {
    private static final Logger logger = LogManager.getLogger(CloudReportHelper.class);
    private static final String NFS = "nfs";
    private static boolean noOfInstNeo4j = false;

    /**
     * Input:Cloud dependency map
     * Output:html values to added to the html template
     */
    static StringBuilder cloudDependencyDowntimeDisplay(final Map<String, String> downtimeMap) {
        final StringBuilder cloudHtmlValues = new StringBuilder();
        try {
            logger.info("Dependency map in cloud {}", downtimeMap);
            downtimeMap.forEach((key, value) -> {
                final String[] split = value.split(";");
                if (key.equalsIgnoreCase(HAPropertiesReader.NEO4J_LEADER)) {
                    noOfInstNeo4j = (split.length > 3);
                }
                for (final String s : split) {
                    logger.info("Dependency iteration started for in cloud: key {},value {}", key, value);
                    final String[] strings = s.split(",");
                    if (strings.length != 1) {
                        cloudHtmlValues.append("<tr>");
                        cloudHtmlValues.append("<td>" + key + "</td>");
                        long duration = Long.parseLong(strings[strings.length - 1]);
                        strings[strings.length - 1] = "-1";
                        if (key.equalsIgnoreCase(HAPropertiesReader.VISINAMINGNB) || key.equalsIgnoreCase(HAPropertiesReader.MODELS)) {
                            for (final String str : strings) {
                                if (!str.equalsIgnoreCase("-1")) {
                                    cloudHtmlValues.append("<td>" + str + "</td>");
                                } else {
                                    cloudHtmlValues.append("<td>" + duration + "s" + "</td>");
                                    cloudHtmlValues.append("<td>" + "N/A" + "</td>");
                                }
                            }
                            cloudHtmlValues.append("</tr>");
                        } else {
                            for (final String str : strings) {
                                if (!str.equalsIgnoreCase("-1")) {
                                    cloudHtmlValues.append("<td>" + str + "</td>");
                                } else {
                                    try {
                                        String tempKey = "";
                                        if (key.startsWith(NFS) || key.equalsIgnoreCase(HAPropertiesReader.HAPROXY)) {
                                            tempKey = key;
                                            key = NFS;
                                        }
                                        switch (key) {
                                            case NFS: {
                                                if (HAPropertiesReader.VIOENV) {
                                                    if (HAPropertiesReader.dependencyThreshholdVio.get(tempKey) < duration) {
                                                        cloudHtmlValues.append("<td style=\"color: #ff0000;\">" + duration + "s" + "</td>");
                                                        cloudHtmlValues.append("<td>" + HAPropertiesReader.dependencyThreshholdVio.get(tempKey) + "s" + "</td>");
                                                        dependencyThresholdErrorList.add(tempKey);
                                                    } else {
                                                        cloudHtmlValues.append("<td>" + duration + "s" + "</td>");
                                                        cloudHtmlValues.append("<td>" + HAPropertiesReader.dependencyThreshholdVio.get(tempKey) + "s" + "</td>");
                                                    }
                                                } else {
                                                    if (HAPropertiesReader.dependencyThreshholdCloud.get(tempKey) < duration) {
                                                        cloudHtmlValues.append("<td style=\"color: #ff0000;\">" + duration + "s" + "</td>");
                                                        cloudHtmlValues.append("<td>" + HAPropertiesReader.dependencyThreshholdCloud.get(tempKey) + "s" + "</td>");
                                                        dependencyThresholdErrorList.add(tempKey);
                                                    } else {
                                                        cloudHtmlValues.append("<td>" + duration + "s" + "</td>");
                                                        cloudHtmlValues.append("<td>" + HAPropertiesReader.dependencyThreshholdCloud.get(tempKey) + "s" + "</td>");
                                                    }
                                                }
                                                key = tempKey;
                                                break;
                                            }
                                            case HAPropertiesReader.NEO4J_LEADER: {
                                                if (!noOfInstNeo4j) {
                                                    if (HAPropertiesReader.dependencyThreshholdCloud.get(key) < duration) {
                                                        cloudHtmlValues.append("<td style=\"color: #ff0000;\">" + duration + "s" + "</td>");
                                                        cloudHtmlValues.append("<td>" + HAPropertiesReader.dependencyThreshholdCloud.get(key) + "s" + "</td>");
                                                        dependencyThresholdErrorList.add(key);
                                                    } else {
                                                        cloudHtmlValues.append("<td>" + duration + "s" + "</td>");
                                                        cloudHtmlValues.append("<td>" + HAPropertiesReader.dependencyThreshholdCloud.get(key) + "s" + "</td>");
                                                    }
                                                } else {
                                                    cloudHtmlValues.append("<td style=\"color: #ff0000;\">" + duration + "s" + "</td>");
                                                    cloudHtmlValues.append("<td>" + HAPropertiesReader.dependencyThreshholdCloud.get(key) + "s" + "</td>");
                                                    dependencyThresholdErrorList.add(key);
                                                }
                                                break;
                                            }
                                            default: {
                                                if (duration > HAPropertiesReader.dependencyThreshholdCloud.get(key)) {
                                                    cloudHtmlValues.append("<td style=\"color: #ff0000;\">" + duration + "s" + "</td>");
                                                    cloudHtmlValues.append("<td>" + HAPropertiesReader.dependencyThreshholdCloud.get(key) + "s" + "</td>");
                                                    dependencyThresholdErrorList.add(key);
                                                } else {
                                                    cloudHtmlValues.append("<td>" + duration + "s" + "</td>");
                                                    cloudHtmlValues.append("<td>" + HAPropertiesReader.dependencyThreshholdCloud.get(key) + "s" + "</td>");
                                                }
                                            }
                                        }
                                    } catch (final Exception e) {
                                        logger.warn("Key not found exception in cloudDependencyDowntimeDisplay {} ::: {}", key, e.getMessage());

                                    }
                                }
                            }
                            cloudHtmlValues.append("</tr>");
                        }
                    }
                }
            });
            if (!CommonDependencyDowntimeHelper.EmptyDependencyMap.isEmpty()) {
                cloudHtmlValues.append(CommonReportHelper.displayEmptyDependency("cloud", CommonDependencyDowntimeHelper.EmptyDependencyMap));
            }
        } catch (final Exception e) {
            logger.warn("Exception in cloudDependencyDowntimeDisplay {}", e.getMessage());
        }
        return cloudHtmlValues;
    }

    /**
     * cENM comment.
     * Prepare html content for dependency display in cENM env.
     * @param downtimeMap Map<String, String>
     * @return StringBuilder
     */
    static StringBuilder cloudNativeDependencyDowntimeDisplay(final Map<String, String> downtimeMap, final Map<String, Long> imagePullTime) {
        final StringBuilder cloudHtmlValues = new StringBuilder();
        try {
            logger.info("Dependency map in cloud native {}", downtimeMap);
            downtimeMap.forEach((key, value) -> {
                logger.info("Dependency iteration started in cloud native for : key {}, value {}", key, value);
                long totalDowntime = 0;
                boolean isFirstWindow = true;
                final String[] downtimeWindows = value.split(";");
                long thresholdVal;
                boolean regression = HAPropertiesReader.getTestType().equalsIgnoreCase("Regression");
                if (key.equalsIgnoreCase(HAPropertiesReader.JMS) || key.equalsIgnoreCase(HAPropertiesReader.SSO) || key.equalsIgnoreCase(HAPropertiesReader.OPENIDM) ||
                        key.equalsIgnoreCase(HAPropertiesReader.POSTGRES)) {
                    for (final String window : downtimeWindows) {
                        final String[] strings = window.split(",");
                        totalDowntime = totalDowntime + Long.parseLong(strings[strings.length - 1]);
                    }
                }
                long windowPullTime = 0L;
                if (key.equalsIgnoreCase(HAPropertiesReader.JMS) || key.equalsIgnoreCase(HAPropertiesReader.OPENIDM)) {
                    if (imagePullTime.containsKey(key)) {
                        windowPullTime = imagePullTime.get(key);
                    }
                    logger.info("Image pull time for {} is {}", key, windowPullTime);
                }
                final long deltaDowntime = Math.max(totalDowntime - windowPullTime, 0L);
                for (final String window : downtimeWindows) { // loop for each window.
                    logger.info("Downtime window adding to report for key : {}, window : {} ", key, window);
                    final String[] strings = window.split(",");
                    if (strings.length != 1) {
                        cloudHtmlValues.append("<tr>");
                        cloudHtmlValues.append("<td>" + key + "</td>");

                        long windowDuration = Long.parseLong(strings[strings.length - 1]);
                        strings[strings.length - 1] = "-1";

                        if (key.equalsIgnoreCase(HAPropertiesReader.VISINAMINGNB)) {
                            for (final String str : strings) {
                                if (!str.equalsIgnoreCase("-1")) {
                                    cloudHtmlValues.append("<td>" + str + "</td>");
                                } else {
                                    cloudHtmlValues.append("<td>" + windowDuration + "s" + "</td>");
                                    cloudHtmlValues.append("<td>" + "N/A" + "</td>");
                                }
                            }
                            cloudHtmlValues.append("</tr>");
                        } else {
                            if (HAPropertiesReader.isDeploymentCCD()) {
                                thresholdVal = HAPropertiesReader.dependencyThreshholdCCD.get(key);
                            } else {
                                thresholdVal = HAPropertiesReader.dependencyThreshholdCloudNative.get(key);
                            }
                            for (final String str : strings) {
                                if (!str.equalsIgnoreCase("-1")) {
                                    cloudHtmlValues.append("<td>" + str + "</td>");
                                } else {
                                    try {
                                        switch (key) {
                                            case HAPropertiesReader.NEO4J_LEADER: {
                                                final int neo4jInstance = 3;
                                                final int totalDownWindows = downtimeWindows.length;
                                                if (regression) {
                                                    cloudHtmlValues.append("<td>" + windowDuration + "s" + "</td>");
                                                    cloudHtmlValues.append("<td>" + windowDuration + "s (TBD)" + "</td>");
                                                } else if (totalDownWindows > neo4jInstance || windowDuration > thresholdVal) {
                                                    cloudHtmlValues.append("<td>" + windowDuration + "s" + "</td>");
                                                    cloudHtmlValues.append("<td><span style=\"color: #ff0000;\">" + windowDuration + "s </span><span>(" + thresholdVal + "s)</span>" + "</td>");
                                                    dependencyThresholdErrorList.add(key);
                                                } else {
                                                    cloudHtmlValues.append("<td>" + windowDuration + "s" + "</td>");
                                                    cloudHtmlValues.append("<td>" + windowDuration + "s (" + thresholdVal + "s)" + "</td>");
                                                }
                                                break;
                                            }
                                            case HAPropertiesReader.JMS:
                                            case HAPropertiesReader.OPENIDM: {
                                                if (regression) {
                                                    cloudHtmlValues.append("<td>" + windowDuration + "s" + "</td>");
                                                    if (isFirstWindow) {
                                                        cloudHtmlValues.append("<td rowspan=" + "\"" + downtimeWindows.length + "\"" + ">" + deltaDowntime +"s (TBD)" + "</td>");
                                                    }
                                                } else if (deltaDowntime > thresholdVal) {
                                                    cloudHtmlValues.append("<td >" + windowDuration + "s" + "</td>");
                                                    if (isFirstWindow) {
                                                        cloudHtmlValues.append("<td rowspan=" + "\"" + downtimeWindows.length + "\"" + ">" + "<span style=\"color: #ff0000;cursor:pointer;\"><a onclick=\"" + key + "ImageData()" + "\" ><u>" + deltaDowntime + "s </u></a></span><span>(" + thresholdVal + "s)</span></td>");
                                                    }
                                                    dependencyThresholdErrorList.add(key);
                                                } else {
                                                    cloudHtmlValues.append("<td >" + windowDuration + "s" + "</td>");
                                                    if (isFirstWindow) {
                                                        cloudHtmlValues.append("<td rowspan=" + "\"" + downtimeWindows.length + "\"" + ">" + "<span style=\"cursor:pointer;\"><a onclick=\"" + key + "ImageData()" + "\" ><u>" + deltaDowntime + "s </u></a></span><span>(" + thresholdVal + "s)</span></td>");
                                                    }
                                                }
                                                break;
                                            }
                                            case HAPropertiesReader.SSO:
                                            case HAPropertiesReader.POSTGRES: {
                                                if (regression) {
                                                    cloudHtmlValues.append("<td>" + windowDuration + "s" + "</td>");
                                                    if (isFirstWindow) {
                                                        cloudHtmlValues.append("<td rowspan=" + "\"" + downtimeWindows.length + "\"" + ">" + deltaDowntime + "s (TBD)" + "</td>");
                                                    }
                                                } else if (deltaDowntime > thresholdVal) {
                                                    cloudHtmlValues.append("<td>" + windowDuration + "s" + "</td>");
                                                    if (isFirstWindow) {
                                                        cloudHtmlValues.append("<td rowspan=" + "\"" + downtimeWindows.length + "\"" + "><span style=\"color: #ff0000;\">" + deltaDowntime + "s </span><span>(" + thresholdVal + "s)</span>" + "</td>");
                                                    }
                                                    dependencyThresholdErrorList.add(key);
                                                } else {
                                                    cloudHtmlValues.append("<td>" + windowDuration + "s" + "</td>");
                                                    if (isFirstWindow) {
                                                        cloudHtmlValues.append("<td rowspan=" + "\"" + downtimeWindows.length + "\"" + ">" + deltaDowntime + "s (" + thresholdVal + "s)" + "</td>");
                                                    }
                                                }
                                                break;
                                            }
                                            case HAPropertiesReader.CNOM: {
                                                if (regression || HAPropertiesReader.disableCnomUsecase) {
                                                    cloudHtmlValues.append("<td>" + windowDuration + "s" + "</td>");
                                                    cloudHtmlValues.append("<td>" + windowDuration + "s (NA)" + "</td>");
                                                } else if (windowDuration > thresholdVal) {
                                                    cloudHtmlValues.append("<td>" + windowDuration + "s" + "</td>");
                                                    cloudHtmlValues.append("<td><span style=\"color: #ff0000;\">" + windowDuration + "s </span><span>(" + thresholdVal + "s)</span>" + "</td>");
                                                    dependencyThresholdErrorList.add(key);
                                                } else {
                                                    cloudHtmlValues.append("<td>" + windowDuration + "s" + "</td>");
                                                    cloudHtmlValues.append("<td>" + windowDuration + "s (" + thresholdVal + "s)" + "</td>");
                                                }
                                                break;
                                            }
                                            default: {
                                                if (regression) {
                                                    cloudHtmlValues.append("<td>" + windowDuration + "s" + "</td>");
                                                    cloudHtmlValues.append("<td>" + windowDuration + "s (TBD)" + "</td>");
                                                } else if (windowDuration > thresholdVal) {
                                                    cloudHtmlValues.append("<td>" + windowDuration + "s" + "</td>");
                                                    cloudHtmlValues.append("<td><span style=\"color: #ff0000;\">" + windowDuration + "s </span><span>(" + thresholdVal + "s)</span>" + "</td>");
                                                    dependencyThresholdErrorList.add(key);
                                                } else {
                                                    cloudHtmlValues.append("<td>" + windowDuration + "s" + "</td>");
                                                    cloudHtmlValues.append("<td>" + windowDuration + "s (" + thresholdVal + "s)" + "</td>");
                                                }
                                            }
                                        }
                                    } catch (final Exception e) {
                                        logger.warn("Key not found exception in cloudNativeDependencyDowntimeDisplay {} ::: {}", key, e.getMessage());
                                    }
                                }
                            }
                            cloudHtmlValues.append("</tr>");
                        }
                    }
                    isFirstWindow = false;
                }
            });
            if (!CommonDependencyDowntimeHelper.EmptyDependencyMap.isEmpty()) {
                cloudHtmlValues.append(CommonReportHelper.displayEmptyDependency("cloud", CommonDependencyDowntimeHelper.EmptyDependencyMap));
            }
        } catch (final Exception e) {
            logger.warn("Exception in cloudNativeDependencyDowntimeDisplay {}", e.getMessage());
        }
        return cloudHtmlValues;
    }
}
