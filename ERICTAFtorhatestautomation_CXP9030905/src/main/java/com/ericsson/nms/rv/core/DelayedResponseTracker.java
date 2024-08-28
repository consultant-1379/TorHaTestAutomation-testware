package com.ericsson.nms.rv.core;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class DelayedResponseTracker {
    private static final Logger logger = LogManager.getLogger(DelayedResponseTracker.class);
    private static Map<String, NavigableMap<Date, Date>> ignoreDTMap = new ConcurrentHashMap<>();

    static String addDelayedResponseValues(Map<String, NavigableMap<Date, Date>> delayedDTMap) {
        final StringBuilder htmlValues = new StringBuilder();
        logger.info("Contents of Ignore DT Map {} ",delayedDTMap.toString());
        delayedDTMap.forEach((id, uri) -> {
            htmlValues.append("<tr>");
            htmlValues.append("<td style=\"cursor:pointer;\"> <a onclick="+ id +"Function()><u>" + id + "</u> </a> </td>");
                uri.forEach((start,end) ->{
                    long milliseconds = end.getTime() - start.getTime();
                    long seconds = TimeUnit.MILLISECONDS.toSeconds(milliseconds);
                    htmlValues.append("<td>" + seconds + "</td>");
                });
                for(int i=0;i<Integer.parseInt(addColSize(delayedDTMap))-uri.size();i++) {
                   htmlValues.append("<td>" + "-" + "</td>") ;
                }
                htmlValues.append("</tr>");
            });
        return htmlValues.toString();
    }

    static String addColSize(Map<String, NavigableMap<Date, Date>> delayMap){
        final StringBuilder htmlValues = new StringBuilder();
        final Map<String,Integer> colMap = new HashMap<>();
        delayMap.forEach((id, uri) -> {
            colMap.put(id,uri.size());
        });
        htmlValues.append(findMax(colMap));
        return htmlValues.toString();
    }

    public static int findMax(Map<String,Integer> mapValues) {
        Map.Entry<String, Integer> maxEntry = null;
        for (Map.Entry<String, Integer> entry : mapValues.entrySet()) {
            if (maxEntry == null
                    || entry.getValue().compareTo(maxEntry.getValue()) > 0) {
                maxEntry = entry;
            }
        }
        return maxEntry.getValue();
    }

    static String addPopUp(Map<String, NavigableMap<Date, Date>> delayedDTMap){
        StringBuilder functionData = new StringBuilder();
        delayedDTMap.forEach((id,uri)->{
            functionData.append("function "+id + "Function() {\n");
            functionData.append("cleanStart();\n");
            functionData.append("text = \" <table border=\'1\'>");
            functionData.append("<tr id=\'myTable\'>");
            functionData.append("<td>Test Case</td> <td>Start Time</td> <td>End Time</td> <td>Duration in sec</td>");
            functionData.append("</tr>");
            uri.forEach((start,end) -> {
                functionData.append("<tr>");
                functionData.append("<td>"+ id +"</td>");
                functionData.append("<td>"+start+"</td>");
                functionData.append("<td>"+end+"</td>");
                long milliseconds = end.getTime() - start.getTime();
                long seconds = TimeUnit.MILLISECONDS.toSeconds(milliseconds);
                functionData.append("<td>" + seconds + "</td>");
                functionData.append("</tr>");
            });
            functionData.append("</table>\";\n");
            functionData.append("title = \"Delayed Data of "+id+"\";\n");
            functionData.append("backGround = \"#ffe6e6\";\n");
            functionData.append("myFunction();\n");
            functionData.append("}\n\n");
        });

        return functionData.toString();
    }

}
