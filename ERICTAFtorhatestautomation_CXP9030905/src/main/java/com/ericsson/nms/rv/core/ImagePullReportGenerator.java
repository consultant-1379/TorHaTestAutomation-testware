package com.ericsson.nms.rv.core;

import com.ericsson.cifwk.taf.annotations.Attachment;
import com.ericsson.sut.test.cases.HighAvailabilityTestCase;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;


public class ImagePullReportGenerator {

    private static final Logger logger = LogManager.getLogger(ImagePullReportGenerator.class);
    private static final TreeMap<String, String> appData = new TreeMap<>();
    private static final TreeMap<String, String> infraData = new TreeMap<>();

    public ImagePullReportGenerator(final Map<String, String> appDataMap, final Map<String, String> infraDataMap) {
        appData.putAll(appDataMap);
        infraData.putAll(infraDataMap);
    }

    @Attachment(type = "text/html", value = "Image Pull Data Report")
    public byte[] attachTxt()
    {
        final String htmlReport = AvailabilityReportGenerator.processTemplate(AvailabilityReportGenerator.getTemplate("ha/ImagePullReport.html"), imagePullDataHTMLValues());
        return htmlReport.getBytes();
    }

    private Map<String,String> imagePullDataHTMLValues() {
        final Map<String, String> values = new ConcurrentHashMap<>();
        if(!HighAvailabilityTestCase.upgradeFailedFlag) {
            try {
                values.put("addAppImageData", addImageData(appData));
                values.put("addInfraImageData", addImageData(infraData));
            } catch (final Exception e) {
                logger.info("Exception in getting image data : {}", e.getMessage());
            }
        }
        return values;
    }


    private String addImageData(final TreeMap<String, String> imageDataMap) {
        final StringBuilder htmlValues = new StringBuilder();
        final Map<String, String> dataListMap = new TreeMap<>();
        imageDataMap.forEach((image, data) -> {
            if (data != null && !data.isEmpty()) {
                data = data.replace("[", "");
                data = data.replace("]", "");
                data = data.replace(" ", "");
                final String[] dataList = data.split(";");
                for (final String list : dataList) {
                    final String[] listContent = list.split(",");
                    if (dataListMap.containsKey(listContent[0])) {
                        final String currentData = dataListMap.get(listContent[0]);
                        final String newData = currentData + ";" + image + "," + listContent[1] + "," + listContent[2] + "," + listContent[3];
                        dataListMap.put(listContent[0], newData);
                    } else {
                        dataListMap.put(listContent[0], image + "," + listContent[1] + "," + listContent[2] + "," + listContent[3]);
                    }
                }
            }
        });
        logger.info("dataListMap : {}", dataListMap);

        dataListMap.forEach((pod, podData) -> {
            final String[] podDataList = podData.split(";");
            for (String data: podDataList) {
                final String[] listContent = data.split(",");
                htmlValues.append("<tr>");
                htmlValues.append("<td>").append(pod).append("</td>");                 //Pod name
                if (listContent.length == 4) {
                    htmlValues.append("<td>").append(listContent[0]).append("</td>");  //image name
                    htmlValues.append("<td>").append(listContent[1]).append("</td>");  //start time
                    htmlValues.append("<td>").append(listContent[2]).append("</td>");  //end time
                    htmlValues.append("<td>").append(convertTimeToSeconds(listContent[3])).append("s</td>");  //pull time in sec.
                } else {
                    htmlValues.append("<td>NA</td>");
                    htmlValues.append("<td>NA</td>");
                    htmlValues.append("<td>NA</td>");
                    htmlValues.append("<td>NA</td>");
                }
                htmlValues.append("</tr>");
            }
        });
        return htmlValues.toString();
    }

    public static long convertTimeToSeconds(final String pullTime) {
        float floatTime = 0;
        long timeInSec = 0;
        try {
            logger.info("Converting pullTime to sec : {}", pullTime);
            if (pullTime.contains("ms")) {
                final String secString = pullTime.replace("ms", "");
                if (secString.contains("m")) {
                    final String[] splitTime = secString.split("m");
                    float minuteInSec = Integer.parseInt(splitTime[0]) * 60;
                    floatTime = minuteInSec + (Float.parseFloat(splitTime[1]) / 1000);
                } else {
                    floatTime = Float.parseFloat(secString) / 1000;
                }
            } else if (pullTime.contains("m")) {
                final String[] splitTime = pullTime.split("m");
                float minuteInSec = Integer.parseInt(splitTime[0]) * 60;
                final String secString = splitTime[1].replace("s", "");
                float seconds = Float.parseFloat(secString);
                floatTime = minuteInSec + seconds;
            } else if (pullTime.contains("s")) {
                final String secString = pullTime.replace("s", "");
                floatTime = Float.parseFloat(secString);
            }
            timeInSec = Math.round(floatTime);
        } catch (final Exception e) {
            logger.warn("Error while converting pull time to seconds.");
            logger.warn("Message: {}", e.getMessage());
        }
        logger.info("PullTime to sec : {}", timeInSec);
        return timeInSec;
    }

}
