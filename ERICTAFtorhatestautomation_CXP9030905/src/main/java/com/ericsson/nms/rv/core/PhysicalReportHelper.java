package com.ericsson.nms.rv.core;

import static com.ericsson.nms.rv.core.AvailabilityReportGenerator.dependencyThresholdErrorList;

import java.util.Map;

import com.ericsson.nms.rv.core.upgrade.CommonDependencyDowntimeHelper;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

class PhysicalReportHelper {

    private static final Logger logger = LogManager.getLogger(PhysicalReportHelper.class);
    private static boolean noOfInstNeo4j40k = false;

    /**
     * Input:Physical dependency map(including 40k and 60k capacity)
     * Output:html values added to the html template
     */
    static StringBuilder PhysicalDependencyDowntimeDisplay(final Map<String, String> downtimeMap) {
        final StringBuilder physicalHtmlValues = new StringBuilder();
        try {
            logger.info("Dependency map in physical {}", downtimeMap);
            boolean isRegression = HAPropertiesReader.getTestType().equalsIgnoreCase("Regression");
            downtimeMap.forEach((key, value) -> {
                logger.info("Dependency iteration started for : key {},value {}", key, value);
                long sum = 0L;
                int rowFlag = 0;
                final String[] split = value.split(";");
                if (key.equalsIgnoreCase(HAPropertiesReader.NEO4J)) {
                    noOfInstNeo4j40k = (split.length > 2);
                }
                long thresholdVal;
                if (key.equalsIgnoreCase(HAPropertiesReader.NEO4J_LEADER) || key.equalsIgnoreCase(HAPropertiesReader.NEO4J_FOLLOWER)) {
                    thresholdVal = HAPropertiesReader.dependencyThreshholdPhysical.get(key);
                } else if (key.equalsIgnoreCase(HAPropertiesReader.VISINAMINGNB)) {
                    thresholdVal = 0L;      //no threshold constraint check against the actual dt of visinaming
                } else {
                    thresholdVal = HAPropertiesReader.dependencyThreshholdPhysical.get(key);
                }
                logger.info("Threshold for key {} is {}", key, thresholdVal);
                if (key.equalsIgnoreCase(HAPropertiesReader.NEO4J_LEADER) || key.equalsIgnoreCase(HAPropertiesReader.ELASTICSEARCH) || key.equalsIgnoreCase(HAPropertiesReader.ELASTICSEARCHPROXY) ||
                        key.equalsIgnoreCase(HAPropertiesReader.POSTGRES) || key.equalsIgnoreCase(HAPropertiesReader.ESHISTORY) ) {
                    for (final String s : split) {
                        final String[] strings = s.split(",");
                        sum = sum + Long.parseLong(strings[strings.length - 1]);  //Calculating the sum (Duration) for few dependencies to validate against the threshold  neo4j(40k), neo4jLeader(60k).
                    }
                }
                for (final String s : split) {
                    physicalHtmlValues.append("<tr>");
                    physicalHtmlValues.append("<td >" + key + "</td>");
                    final String[] strings = s.split(",");
                    long duration = Long.parseLong(strings[strings.length - 1]);
                    logger.info("duration : {} {}", duration, key);
                    strings[strings.length - 1] = "-1";
                    if (key.equalsIgnoreCase(HAPropertiesReader.VISINAMINGNB)) {
                        for (final String str : strings) {
                            if (!str.equalsIgnoreCase("-1")) {
                                physicalHtmlValues.append("<td>" + str + "</td>");
                            } else {
                                physicalHtmlValues.append("<td>" + duration + "s" + "</td>");
                            }
                        }
                        physicalHtmlValues.append("<td>" + "N/A" + "</td>");
                    } else {
                        for (final String str : strings) {
                            if (!str.equalsIgnoreCase("-1")) {
                                physicalHtmlValues.append("<td>" + str + "</td>");
                            } else {
                                switch (key) {
                                    case HAPropertiesReader.NEO4J_LEADER:
                                    case HAPropertiesReader.ELASTICSEARCH:
                                    case HAPropertiesReader.ELASTICSEARCHPROXY:
                                    case HAPropertiesReader.POSTGRES:
                                    case HAPropertiesReader.ESHISTORY: {
                                        rowFlag = rowFlag + 1;
                                        if (isRegression) {
                                            physicalHtmlValues.append("<td>" + duration + "s" + "</td>");
                                            if (rowFlag == 1) {
                                                physicalHtmlValues.append("<td rowspan=" + "\"" + split.length + "\"" + ">" + "TBD" + "</td>");
                                                physicalHtmlValues.append("</tr>");
                                            }
                                        } else if (sum > thresholdVal) {
                                            physicalHtmlValues.append("<td style=\"color: #ff0000;\">" + duration + "s" + "</td>");
                                            if (rowFlag == 1) {
                                                physicalHtmlValues.append("<td rowspan=" + "\"" + split.length + "\"" + ">" + thresholdVal + "s" + "</td>");
                                                physicalHtmlValues.append("</tr>");
                                            }
                                            dependencyThresholdErrorList.add(key);
                                        } else {
                                            physicalHtmlValues.append("<td>" + duration + "s" + "</td>");
                                            if (rowFlag == 1) {
                                                physicalHtmlValues.append("<td rowspan=" + "\"" + split.length + "\"" + ">" + thresholdVal + "s" + "</td>");
                                                physicalHtmlValues.append("</tr>");
                                            }
                                        }
                                        break;
                                    }
                                    case HAPropertiesReader.FTS: {
                                        if (isRegression) {
                                            physicalHtmlValues.append("<td>" + duration + "s" + "</td>");
                                            physicalHtmlValues.append("<td>" + "TBD" + "</td>");
                                            physicalHtmlValues.append("</tr>");
                                        } else {
                                            if (HAPropertiesReader.isEnmOnRack()) {
                                                physicalHtmlValues.append("<td>" + duration + "s" + "</td>");
                                            } else if (thresholdVal < duration) {
                                                physicalHtmlValues.append("<td style=\"color: #ff0000;\">" + duration + "s" + "</td>");
                                                dependencyThresholdErrorList.add(key);
                                            } else {
                                                physicalHtmlValues.append("<td>" + duration + "s" + "</td>");
                                            }
                                            physicalHtmlValues.append("<td>" + thresholdVal + "s" + "</td>");
                                            physicalHtmlValues.append("</tr>");
                                            logger.info(" threshold : {} {}", thresholdVal, key);
                                        }
                                        break;
                                    }
                                    case HAPropertiesReader.OPENIDM:
                                    case HAPropertiesReader.HAPROXY:
                                    case HAPropertiesReader.JMS:
                                    case HAPropertiesReader.HAPROXY_INT:
                                    case HAPropertiesReader.NEO4J_FOLLOWER: {
                                        if (isRegression) {
                                            physicalHtmlValues.append("<td>" + duration + "s" + "</td>");
                                            physicalHtmlValues.append("<td>" + "TBD" + "</td>");
                                            physicalHtmlValues.append("</tr>");
                                        } else {
                                            if (thresholdVal < duration) {
                                                physicalHtmlValues.append("<td style=\"color: #ff0000;\">" + duration + "s" + "</td>");
                                                dependencyThresholdErrorList.add(key);
                                            } else {
                                                physicalHtmlValues.append("<td>" + duration + "s" + "</td>");
                                            }
                                            physicalHtmlValues.append("<td>" + thresholdVal + "s" + "</td>");
                                            physicalHtmlValues.append("</tr>");
                                            logger.info(" threshold : {} {}", thresholdVal, key);
                                        }
                                        break;
                                    }
                                    case HAPropertiesReader.NEO4J: {
                                        if (isRegression) {
                                            physicalHtmlValues.append("<td>" + duration + "s" + "</td>");
                                            physicalHtmlValues.append("<td>" + "TBD" + "</td>");
                                            physicalHtmlValues.append("</tr>");
                                        } else {
                                            if (!noOfInstNeo4j40k) {
                                                if (thresholdVal < duration) {
                                                    physicalHtmlValues.append("<td style=\"color: #ff0000;\">" + duration + "s" + "</td>");
                                                    dependencyThresholdErrorList.add(key);
                                                } else {
                                                    physicalHtmlValues.append("<td>" + duration + "s" + "</td>");
                                                }
                                            } else {
                                                physicalHtmlValues.append("<td style=\"color: #ff0000;\">" + duration + "s" + "</td>");
                                                dependencyThresholdErrorList.add(key);
                                            }
                                            physicalHtmlValues.append("<td>" + thresholdVal + "s" + "</td>");
                                            physicalHtmlValues.append("</tr>");
                                            logger.info(" threshold : {} {}", thresholdVal, key);
                                        }
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
            });
            if (!CommonDependencyDowntimeHelper.EmptyDependencyMap.isEmpty()) {
                physicalHtmlValues.append(CommonReportHelper.displayEmptyDependency("physical", CommonDependencyDowntimeHelper.EmptyDependencyMap));
            }
        } catch (final Exception e) {
            logger.warn(e.getMessage());
        }
        return physicalHtmlValues;
    }
}
