package com.ericsson.nms.rv.core;

import java.util.Map;
import java.util.TreeMap;

import com.ericsson.nms.rv.core.launcher.AppLauncher;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

class AppLauncherReport {
    private static final Logger logger = LogManager.getLogger(AppLauncherReport.class);
    private final Map<String, Integer> httpErrorMap;
    private final Map<String, String> responseMap;
    private final Map<String, Integer> missingLinksMap;
    private final Map<String, String> allAppFailedMap;
    private int count;
    private final int repeatedCount;
    private final boolean failedRequests;
    private final int allAppFailedCount;

    AppLauncherReport(AppLauncher appLauncher) {
        missingLinksMap = appLauncher.getMissingLinksMap();
        responseMap = new TreeMap(appLauncher.getResponseMap());
        httpErrorMap = appLauncher.getCounterMap();
        count = appLauncher.getTotalCounter();
        failedRequests = appLauncher.isFailedRequests();
        repeatedCount = appLauncher.getRepeatedCounter();
        allAppFailedCount = appLauncher.getAllAppFailedCount();
        allAppFailedMap = appLauncher.getAllAppFailedMap();
    }

    String addLauncherRequestValues() {
        final StringBuilder htmlValues = new StringBuilder();
        logger.info("Contents of Missing Links Map {} ", missingLinksMap);
        if (httpErrorMap.isEmpty() && missingLinksMap.isEmpty()) {
            responseMap.forEach((id, uri) -> {
                htmlValues.append("<tr>");
                htmlValues.append("<td>" + id + "</td>");
                htmlValues.append("<td>" + "0/" + repeatedCount + "</td>");
                htmlValues.append("<td>" + "0/" + repeatedCount + "</td>");
                htmlValues.append("</tr>");
            });
        } else {
            htmlValues.append(fillHtmlValuesFromNonEmptyMaps());
        }
        return htmlValues.toString();
    }

    String addLauncherFailedValues() {
        final StringBuilder htmlValues = new StringBuilder();
        htmlValues.append("<tr>");
        htmlValues.append("<td>Total Failed count </td>");
        if (allAppFailedCount > 0) {
            htmlValues.append("<td style=\"cursor:pointer;\"> <a onclick=AllAppFailedFunction()><u>" + allAppFailedCount + "</u></a></td>");
        } else {
            htmlValues.append("<td>" + 0 + "</td>");
        }
        htmlValues.append("</tr>");
        return htmlValues.toString();
    }

    String addLauncherFailedPopupData() {
        StringBuilder functionData = new StringBuilder();
        functionData.append("function AllAppFailedFunction() {\n");
        functionData.append("cleanStart();\n");
        functionData.append("text = \" <table border=\'1\'>");
        functionData.append("<tr id=\'myTable\'>");
        functionData.append("<td>Iteration </td> <td> Time</td>");
        functionData.append("</tr>");
        allAppFailedMap.forEach((window, date) -> {
            functionData.append("<tr>");
            functionData.append("<td>"+ window +"</td>");
            functionData.append("<td>"+date+"</td>");
            functionData.append("</tr>");
        });
        functionData.append("</table>\";\n");
        functionData.append("title = \"All Application Failed Times Window.\";\n");
        functionData.append("backGround = \"#ffe6e6\";\n");
        functionData.append("myFunction();\n");
        functionData.append("}\n\n");
        return functionData.toString();
    }



    private String fillHtmlValuesFromNonEmptyMaps() {
        final StringBuilder htmlValues = new StringBuilder();
        responseMap.forEach((id, uri) -> {
            htmlValues.append("<tr>");
            htmlValues.append("<td>" + id + "</td>");
            final Integer missingInt = missingLinksMap.get(id);
            int totalCount;
            if (missingInt != null && missingInt > 0) {
                htmlValues.append("<td style=\"color: #FF0000;\">" + missingInt+"/" + repeatedCount + "</td>");
                totalCount = (repeatedCount-missingInt);
            } else {
                htmlValues.append("<td>" + "0/" + repeatedCount + "</td>");
                totalCount = repeatedCount;
            }
            final Integer integer = httpErrorMap.get(id);
            if (failedRequests) {
                if (integer != null && integer > 0) {
                    htmlValues.append("<td style=\"color: #FF0000;\">" + integer+"/"+ totalCount + "</td>");
                }
            } else if (integer != null && integer > 0) {
                htmlValues.append("<td>" + integer+"/" + totalCount + "</td>");
            } else {
                htmlValues.append("<td>" + "0/" + totalCount + "</td>");
            }
            htmlValues.append("</tr>");
        });
        return htmlValues.toString();
    }
}
