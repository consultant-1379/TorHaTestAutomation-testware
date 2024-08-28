package com.ericsson.nms.rv.core;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

class ImagePopupReportBuilder {
    private static final Logger logger = LogManager.getLogger(ImagePopupReportBuilder.class);

    private static final String note = "Note : <a href='https://eteamspace.internal.ericsson.com/display/ERSD/Delta+Downtime+Measurement+Process' target='_blank'>Delta DownTime Measurement Process.</a>";
    private static final String isoVersion = HAPropertiesReader.getProperty("to_iso", "-");
    private static final String separator = "==================================================<br>";
    private final Map<String, String> downtimeMap;
    private final Map<String, Long> totalImagePullTimeMap;
    private final Map<String, String> componentImagePullData;

    ImagePopupReportBuilder(final Map<String, String> componentImagePullData,
                            final Map<String, Long> totalImagePullTimeMap,
                            final Map<String, String> downtimeMap) {
        this.componentImagePullData = componentImagePullData;
        this.totalImagePullTimeMap = totalImagePullTimeMap;
        this.downtimeMap = downtimeMap;
    }

    String buildPopupData() {
        final StringBuilder functionData = new StringBuilder();
        functionData.append(buildImageData());
        return functionData.toString();
    }

    private String buildImageData() {
        final StringBuilder functionData = new StringBuilder();
        final DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        final Map<String, List<String>> dependencyImageMap = HAPropertiesReader.getImageDependencyMap();
        downtimeMap.forEach((dependency, data) -> {
            if (dependency.equalsIgnoreCase(HAPropertiesReader.JMS) || dependency.equalsIgnoreCase(HAPropertiesReader.OPENIDM)) {
                functionData.append("function " + dependency + "ImageData() {\n");
                functionData.append("cleanStart();\n");
                functionData.append("text = \"<p>");
                functionData.append(separator);
                functionData.append(dependency + " Delta DownTime Window.<br>");
                functionData.append("ISO Version : " + isoVersion + "<br>");
                functionData.append(separator);
                if (downtimeMap.get(dependency) != null && !downtimeMap.get(dependency).isEmpty()) {
                    functionData.append("- " + dependency + " downtime windows : <br>");
                    for (final String list : downtimeMap.get(dependency).split(";")) {
                        final String[] startStop = list.split(",");
                        functionData.append("Start time: " + startStop[0] + ", Stop time : " + startStop[1] + ", [=" + startStop[2] + "s]" + "<br>");
                    }
                } else {
                    functionData.append("- " + dependency + " downtime windows : []<br>");
                }
                functionData.append(separator);
                final List<String> imageList = dependencyImageMap.get(dependency);
                for (final String image : imageList) {
                    String pullData = componentImagePullData.get(image);
                    if (pullData != null && !pullData.isEmpty()) {
                        pullData = pullData.replace("[", "");
                        pullData = pullData.replace("]", "");
                        pullData = pullData.replace(" ", "");
                        final String[] dataList = pullData.split(";");
                        functionData.append("- Image [" + image + "] Pull time windows : <br>");
                        for (final String list : dataList) {
                            final String[] dataArray = list.split(",");
                            if (dataArray.length == 4) {
                                if (dataArray[0].contains(dependency)) {
                                    LocalDateTime imagePullStart = LocalDateTime.parse(BuildDowntime.convertReadableTime(dataArray[1]), fmt);
                                    LocalDateTime imagePullStop = LocalDateTime.parse(BuildDowntime.convertReadableTime(dataArray[2]), fmt);
                                    final long calculatedPullTime = Duration.between(imagePullStart, imagePullStop).getSeconds();
                                    logger.info("image: {}, calculatedPullTime : {}", image, calculatedPullTime);
                                    functionData.append("Pod: " + dataArray[0] + ", Start time: " + BuildDowntime.convertReadableTime(dataArray[1]) +
                                            ", Stop time : " + BuildDowntime.convertReadableTime(dataArray[2]) + ", [= " + calculatedPullTime + "s]" + "<br>");
                                }
                            }
                        }
                    } else {
                        functionData.append("- " + image + " pull time windows : []<br>");
                    }
                }
                functionData.append(separator);
                long totalDuration = 0L;    //total dependency duration.
                final String[] split = data.split(";");
                for (final String s : split) {
                    final String[] strings = s.split(",");
                    if (strings.length != 1) {
                        totalDuration = totalDuration + Long.parseLong(strings[strings.length - 1]);
                    }
                }

                functionData.append("Total Dependency Downtime : " + totalDuration + "s<br>");
                functionData.append("Total Image Pull Duration : " + totalImagePullTimeMap.get(dependency) + "s<br>");
                final long deltaDependencyDT = totalDuration - totalImagePullTimeMap.get(dependency);
                functionData.append("Total Dependency Delta Downtime : " + Math.max(deltaDependencyDT, 0L) + "s<br>");
                functionData.append(separator);
                functionData.append(note);
                functionData.append("</p>\";\n");
                functionData.append("title = \"Delta Dependency Downtime Breakups [" + dependency + "]\";\n");
                functionData.append("backGround = \"#e6f2ff\";\n");
                functionData.append("myFunction();\n");
                functionData.append("}\n\n");
            }
        });
        return functionData.toString();
    }
}
