package com.ericsson.sut.test.cases.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.ericsson.nms.rv.core.CommonReportHelper;
import com.ericsson.nms.rv.core.upgrade.CommonDependencyDowntimeHelper;
import com.ericsson.nms.rv.core.util.CommonUtils;

public class Neo4jPhysicalCloudDtCalculator {

    private static final Logger logger = LogManager.getLogger(Neo4jPhysicalCloudDtCalculator.class);
    private static final String regex = "\\d+";

    public static List<String> getNeo4jFollowerDt() {
        ArrayList<String> temDTList = new ArrayList<>();
        try {
            Map<String, List<String>> csvFiles = new TreeMap<>();
            for (Map.Entry<String, String> entry : CommonUtils.txtFiles.entrySet()) {
                if (entry.getKey().contains("Neo4jReadOut")) {
                    List<String> list = new ArrayList<>();
                    for (String line : entry.getValue().split("\n")) {
                        if (line.trim().isEmpty()) {
                            continue;
                        }
                        list.add(line);
                    }
                    csvFiles.put(entry.getKey(), list);
                }
            }
            final List<String> neoDtList = generateNeoDtList(csvFiles, "neo4j-follower");
            logger.info("neo4jDtList is {}", neoDtList);
            String[] lfList = new String[neoDtList.size()];
            for (int i = 0; i < lfList.length; i++) {
                lfList[i] = neoDtList.get(i);
            }
            temDTList.addAll(CommonDependencyDowntimeHelper.buildDowntimeList(lfList));
        } catch (Exception e) {
            logger.info("Error occurred in getNeo4jFollowerDt : {}", e.getMessage());
        }
        return temDTList;
    }

    public static List<String> getNeo4jLeaderDt() {
        ArrayList<String> temDTList = new ArrayList<>();
        try {
            Map<String, List<String>> csvFiles = new TreeMap<>();
            for (Map.Entry<String, String> entry : CommonUtils.txtFiles.entrySet()) {
                if (entry.getKey().contains("Neo4jWriteOut")) {
                    List<String> list = new ArrayList<>();
                    for (String line : entry.getValue().split("\n")) {
                        if (line.trim().isEmpty()) {
                            continue;
                        }
                        list.add(line);
                    }
                    csvFiles.put(entry.getKey(), list);
                }
            }
            final List<String> neoDtList = generateNeoDtList(csvFiles, "neo4j-leader");
            logger.info("neo4jDtList is {}", neoDtList);
            String[] lfList = new String[neoDtList.size()];
            for (int i = 0; i < lfList.length; i++) {
                lfList[i] = neoDtList.get(i);
            }
            temDTList.addAll(CommonDependencyDowntimeHelper.buildDowntimeList(lfList));
        } catch (Exception e) {
            logger.info("Error occurred in getNeo4jLeaderDt : {}", e.getMessage());
        }
        return temDTList;
    }

    private static List<String> generateNeoDtList(final Map<String, List<String>> csvFiles, final String dependency) {
        final List<String> fileList = new ArrayList<>();
        final Map<String, List<String>> fileWindowList = new TreeMap<>();

        csvFiles.forEach((file, list) -> {
            final List<String> windowList = new ArrayList<>();
            boolean windowStarted = false;
            String previousLine = "";
            String startWindow = "";
            String stopWindow = "";
            logger.info("Processing file: {}", file);
            for (final String line : list) {
                try {
                    if (previousLine.isEmpty()) {
                        logger.info("First Line for {} is {}", file, line);
                        if (line.contains("false") || line.split(",")[2].matches(regex)) {  //start off window
                            startWindow = line.split(",")[0];
                            windowStarted = true;
                            logger.info("Window for {} started at: {}", file, startWindow);
                        }
                    } else if ((line.contains("false") || line.split(",")[2].matches(regex)) && (previousLine.contains("false") || previousLine.split(",")[2].matches(regex))) {
                        //DO nothing
                    } else if (line.contains("true") && previousLine.contains("true")) {
                        //DO nothing
                    } else if (line.contains("true") && (previousLine.contains("false") || previousLine.split(",")[2].matches(regex)) && windowStarted) {   //stop window & fill window map
                        stopWindow = line.split(",")[0];
                        windowList.add(startWindow.concat(":").concat(stopWindow));
                        windowStarted = false;
                        logger.info("Window for {} stopped at: {}", file, stopWindow);
                    } else if ((line.contains("false") || line.split(",")[2].matches(regex)) && previousLine.contains("true") && !windowStarted) {    //start window
                        startWindow = line.split(",")[0];
                        windowStarted = true;
                        logger.info("Window for {} started at: {}", file, startWindow);
                    }
                    previousLine = line;
                } catch (Exception e) {
                    logger.warn("error in getting {} downtime window: {}", dependency, e.getMessage());
                }
            }
            if (!list.isEmpty() && windowStarted) {    //stop window at the end of file
                final String lastLine = list.get(list.size() - 1);
                logger.info("Last line: {}", lastLine);
                stopWindow = lastLine.split(",")[0];
                windowList.add(startWindow.concat(":").concat(stopWindow));
                logger.info("Window for {} stopped at: {}", file, stopWindow);
            }
            fileWindowList.put(file, windowList);
            fileList.add(file);
            logger.info("OFF window list Map for {},  List : {}", file, windowList);
        });
        logger.info("Total OFF window list for Neo : {},  List : {}", dependency, fileWindowList);
        return compareNeoOffWindows(fileList, fileWindowList, dependency);
    }

    private static List<String> compareNeoOffWindows(final List<String> fileList, final Map<String, List<String>> fileWindowList, final String dependency) {
        final List<String> dtList = new ArrayList<>();
        if (fileList.size() != 3) {
            logger.info("fileList: {}", fileList);
            logger.warn("Neo Data List is incomplete for : {}", dependency);
            return dtList;
        }
        final List<String> neoList1 = fileWindowList.get(fileList.get(0));
        final List<String> neoList2 = fileWindowList.get(fileList.get(1));
        final List<String> neoList3 = fileWindowList.get(fileList.get(2));
        logger.info("{} neoList1: {}", dependency, neoList1);
        logger.info("{} neoList2: {}", dependency, neoList2);
        logger.info("{} neoList3: {}", dependency, neoList3);
        final Map<LocalDateTime, LocalDateTime> commonOffWindows = new TreeMap<>();
        final Map<LocalDateTime, LocalDateTime> finalOffWindows = new TreeMap<>();
        final DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
        //Compare List 1 & 2
        for (String window1 : neoList1) {
            LocalDateTime window1Start = LocalDateTime.parse(window1.split(":")[0], fmt);
            LocalDateTime window1Stop = LocalDateTime.parse(window1.split(":")[1], fmt);
            for (String window2 : neoList2) {
                LocalDateTime window2Start = LocalDateTime.parse(window2.split(":")[0], fmt);
                LocalDateTime window2Stop = LocalDateTime.parse(window2.split(":")[1], fmt);

                logger.info("{} Comparing : {}:{}, {}:{}", dependency, window1Start, window1Stop, window2Start, window2Stop);
                final Map<LocalDateTime, LocalDateTime> commonWindow = CommonReportHelper.getCommonWindow(window1Start,window1Stop,window2Start,window2Stop);
                if (!commonWindow.isEmpty()) {
                    commonOffWindows.putAll(commonWindow);
                }
            }
        }
        logger.info("Common off window of neo_1 and neo_2 data files for {} : {}", dependency, commonOffWindows);
        //Compare List 3 & common window.
        if (!commonOffWindows.isEmpty()) {
            commonOffWindows.forEach((startTime, stopTime) -> {    //Compare window3 with common off windows.
                for (String window3 : neoList3) {
                    LocalDateTime window3Start = LocalDateTime.parse(window3.split(":")[0], fmt);
                    LocalDateTime window3Stop = LocalDateTime.parse(window3.split(":")[1], fmt);
                    logger.info("{} Final Comparing : {}:{}, {}:{}", dependency, startTime, stopTime, window3Start, window3Stop);
                    final Map<LocalDateTime, LocalDateTime> finalWindow = CommonReportHelper.getCommonWindow(startTime, stopTime, window3Start, window3Stop);
                    if (!finalWindow.isEmpty()) {
                        finalOffWindows.putAll(finalWindow);
                    }
                }
            });
            logger.info("Final off window of {} : {}", dependency, finalOffWindows);
            if (!finalOffWindows.isEmpty()) {
                for (Map.Entry<LocalDateTime, LocalDateTime> entry : finalOffWindows.entrySet()) {
                    dtList.add(entry.getKey().format(fmt));
                    dtList.add(entry.getValue().format(fmt));
                }
            }
        }
        logger.info("Neo: {} , dtList : {}", dependency, dtList);
        return dtList;
    }
}


