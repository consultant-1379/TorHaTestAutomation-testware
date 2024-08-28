package com.ericsson.nms.rv.core.upgrade;

import java.io.BufferedReader;
import java.io.IOException;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.TreeMap;
import java.util.stream.Stream;
import java.util.stream.Collectors;

import com.ericsson.cifwk.taf.tools.http.HttpResponse;
import com.ericsson.cifwk.taf.tools.http.constants.ContentType;
import com.ericsson.cifwk.taf.tools.http.constants.HttpStatus;
import com.ericsson.nms.rv.core.CommonReportHelper;
import com.ericsson.nms.rv.core.HAPropertiesReader;
import com.ericsson.nms.rv.core.restclient.HttpRestServiceClient;
import com.ericsson.nms.rv.core.util.CommonUtils;

import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import com.ericsson.nms.rv.core.EnmSystemException;

public class CloudNativeDependencyDowntimeHelper extends UpgradeVerifier {
    private static final Logger logger = LogManager.getLogger(CloudNativeDependencyDowntimeHelper.class);
    private static final String regex = "\\d+";
    public static final Map<String, String> infraImagePullData = new TreeMap<>();
    public static final Map<String, String> appImagePullData = new TreeMap<>();
    public static final Map<String, String> comAaLdapDowntimeWindows = new TreeMap<>();

    CloudNativeDependencyDowntimeHelper() {
    }

    Map<String, String> buildKnownDowntimeMapForCloudNative() {
        final Map<String, String> downtimeMap = new HashMap<>();
        final List<String> otherDependencies = new ArrayList<>();
        final List<String> podNames = getNeo4jPodNames();
        otherDependencies.add(HAPropertiesReader.POSTGRES);
        otherDependencies.add(HAPropertiesReader.JMS);
        otherDependencies.add(HAPropertiesReader.ESHISTORY);
        otherDependencies.add(HAPropertiesReader.VISINAMINGNB);
        otherDependencies.add(HAPropertiesReader.SSO);
        otherDependencies.add(HAPropertiesReader.ELASTICSEARCH);
        otherDependencies.add(HAPropertiesReader.OPENIDM);
        otherDependencies.add(HAPropertiesReader.NEO4J_LEADER);
        otherDependencies.add(HAPropertiesReader.NEO4J_FOLLOWER);
        otherDependencies.add(HAPropertiesReader.OPENDJ);
        otherDependencies.add(HAPropertiesReader.SECSERV);
        otherDependencies.add(HAPropertiesReader.CNOM);
        otherDependencies.add(HAPropertiesReader.FTS);
        if (getIngressDeploymentInstallStatus()) {
            otherDependencies.add(HAPropertiesReader.INGRESS);
        }
        otherDependencies.forEach(dependency -> {
            List<String> downTimePeriods;
            try {
                if (dependency.contains(HAPropertiesReader.NEO4J)) {
                    downTimePeriods = getNeo4jDownTimePeriodsFromWatcherFiles(dependency, podNames);
                    downtimeMap.putAll(CommonDependencyDowntimeHelper.addComponentToMap(dependency, downTimePeriods));
                } else if (dependency.equalsIgnoreCase(HAPropertiesReader.FTS)) {
                    downTimePeriods = getDownTimePeriodsFromWatcherFiles(dependency + "_1");
                    downTimePeriods.addAll(getDownTimePeriodsFromWatcherFiles(dependency + "_2"));
                    downtimeMap.putAll(CommonDependencyDowntimeHelper.addComponentToMap(dependency, downTimePeriods));
                } else {
                    downTimePeriods = getDownTimePeriodsFromWatcherFiles(dependency);
                    downtimeMap.putAll(CommonDependencyDowntimeHelper.addComponentToMap(dependency, downTimePeriods));
                }
            } catch (final Exception e) {
                logger.info("command failed to execute {}", dependency);
                downTimePeriods = new ArrayList<>();
                downtimeMap.putAll(CommonDependencyDowntimeHelper.addComponentToMap(dependency, downTimePeriods));
            }
        });

        try {
            final List<String> comAaLdapDownTimePeriods = getDownTimePeriodsFromWatcherFiles("com-aa-ldap");
            comAaLdapDowntimeWindows.putAll(CommonDependencyDowntimeHelper.addComponentToMap(HAPropertiesReader.COMAALDAP, comAaLdapDownTimePeriods));
        } catch (final Exception e) {
            logger.info("command failed to execute {}", e.getMessage());
        }
        logger.info("comAaLdapDowntimeWindows : {}", comAaLdapDowntimeWindows);

        getImagePullData("app", HAPropertiesReader.appImageList);
        getImagePullData("infra", HAPropertiesReader.infraImageList);
        copyExtraLogFiles("adu.properties");
        copyExtraLogFiles("image.properties");
        copyExtraLogFiles("endpoint.json");
        if (HAPropertiesReader.isExternalVmUsed()) {
            copyPodDataFiles("general-scripting");
        }
        logger.info("buildKnownDowntimeMapForCloud --- downtimeMap: {}", downtimeMap);
        return downtimeMap;
    }

    private void copyExtraLogFiles(final String fileName) {
        final HttpResponse httpResponse = generateHttpGetResponse("watcher/adu/endpoints/" + fileName);
        if (httpResponse != null && HttpStatus.OK.equals(httpResponse.getResponseCode())) {
            CommonUtils.writeFileToSlave(fileName, httpResponse.getContent());
            try {
                CommonUtils.txtFiles.put(fileName, IOUtils.toString( httpResponse.getContent(), StandardCharsets.UTF_8.name()));
            } catch (final IOException e) {
                logger.info("error in copying ug log file {} : {}", fileName, e.getMessage());
                CommonUtils.isFileCopyFailed=true;
            }
        }
    }

    /**
     * cENM comment.
     * Read downtimes from dependency file in adu-watcher pods.
     * @param dependency
     * @return
     * @throws EnmSystemException
     */
    private static List<String> getDownTimePeriodsFromWatcherFiles(final String dependency) throws EnmSystemException {
        final String uri = "watcher/adu/endpoints/" + dependency;
        HttpResponse httpResponse = generateHttpGetResponse(uri);
        for (int start = 0; start < 3; start++) {
            if (httpResponse == null || !HttpStatus.OK.equals(httpResponse.getResponseCode())) {
                logger.info("failed to calculate DT and generate csv file for {} retrying........", dependency);
                httpResponse = generateHttpGetResponse(uri);
            } else {
                break;
            }
        }
        List<String> resultTimeValues;
        if (httpResponse != null && HttpStatus.OK.equals(httpResponse.getResponseCode())) {
            if (dependency.equalsIgnoreCase("com-aa-ldap")) {
                resultTimeValues = processComAaLdapOutput(getResponseContent(httpResponse));
            } else {
                resultTimeValues = processCommandOutput(dependency, getResponseContent(httpResponse));
            }
            logger.info("List resultTimeValues: {}", resultTimeValues);
            CommonUtils.writeFileToSlave(dependency + ".csv", httpResponse.getContent());
            logger.info("After Copy file for {}", dependency);
            try {
                CommonUtils.txtFiles.put(dependency + ".csv", IOUtils.toString(httpResponse.getContent(), StandardCharsets.UTF_8.name()));
                if (!dependency.equalsIgnoreCase("com-aa-ldap")) {
                    copyPodDataFiles(dependency);
                }
            } catch (final IOException e) {
                logger.info("error in copying {}  file {}", dependency, e.getMessage());
                CommonUtils.isFileCopyFailed = true;
            }
            if (!resultTimeValues.isEmpty()) {
                final String[] dtArr = resultTimeValues.toArray(new String[resultTimeValues.size()]);
                final List<String> dtPeriodsList = buildDowntimeListWatcher(dtArr);
                logger.debug("getDownTimePeriods for {} is {}", dependency, dtPeriodsList);
                return dtPeriodsList;
            } else {
                logger.warn("getDownTimePeriods for {} is EMPTY", dependency);
                return new ArrayList<>();
            }
        } else {
            logger.warn("Failed to get DT for {}", dependency);
            throw new EnmSystemException("command Failed to execute");
        }
    }

    private static void copyPodDataFiles(final String dependency) {
        if (!HAPropertiesReader.multiInstanceMap.containsKey(dependency)) {
            return;
        }
        final List<String> list = new ArrayList<>(HAPropertiesReader.multiInstanceMap.get(dependency));
        if (dependency.equalsIgnoreCase(HAPropertiesReader.SSO)) {
            list.add("cts");
        }
        logger.info("MultiInstanceMap List for {} : {}", dependency, list);
        for (final String dep : list) {
            try {
                final String dataFile = dep + "-pod-status-data";
                logger.info("Copying data file : {}", dataFile);
                final String uri = "watcher/adu/endpoints/" + dataFile;
                final HttpResponse httpResponse = generateHttpGetResponse(uri);
                CommonUtils.sleep(1);
                if (httpResponse != null && HttpStatus.OK.equals(httpResponse.getResponseCode())) {
                    CommonUtils.dataFiles.put(dataFile + ".csv", IOUtils.toString(httpResponse.getContent(), StandardCharsets.UTF_8.name()));
                }
            } catch (final Exception e) {
                logger.warn("Failed to copy data file : {}", e.getMessage() );
            }
        }
    }

    /**
     * cENM comment.
     * GET Ingress "eric-oss-ingress-controller-nx" install status.
     * @return boolean
     */
    private static boolean getIngressDeploymentInstallStatus() {
        try {
            final HttpResponse httpResponse = generateHttpGetResponse("watcher/adu/ingress/available");
            if (httpResponse != null && HttpStatus.OK.equals(httpResponse.getResponseCode())) {
                final Scanner scanner = new Scanner(httpResponse.getContent());
                String status = "";
                if (scanner.hasNext()) {
                    status = scanner.next();
                }
                logger.info("getIngressDeploymentInstallStatus status : {}", status);
                return Boolean.parseBoolean(status);
            }
        } catch (final Exception e) {
            logger.warn("Failed to get ingress deployment status : {}", e.getMessage());
        }
        return false;
    }

    /**
     * cENM comment.
     * GET Http response on cENM env.
     * @param uri
     * @return HttpResponse
     */
    public static HttpResponse generateHttpGetResponse(final String uri) {
        HttpResponse httpResponse = null;
        try {
            logger.info("generateHttpResponse GET URI : {}", uri);
            HttpRestServiceClient httpRestServiceClient = HAPropertiesReader.getHttpRestServiceExternalClient();
            httpResponse = httpRestServiceClient.sendGetRequest(20, null, new String[]{"ContentType", ContentType.APPLICATION_JSON}, null, null, uri);
            logger.info("response for uri {} responseCode : {}", uri, httpResponse.getResponseCode().getCode());
        } catch (Exception e) {
            logger.warn("Error in get request for {} Message : {}", uri, e.getMessage());
        }
        return httpResponse;
    }

    /**
     * cENM comment.
     * POST Http response on cENM env.
     * @param uri
     * @return HttpResponse
     */
    public static HttpResponse generateHttpPostResponse(final String uri) {
        HttpResponse httpResponse = null;
        try {
            logger.info("generateHttpResponse POST URI : {}", uri);
            HttpRestServiceClient httpRestServiceClient = HAPropertiesReader.getHttpRestServiceExternalClient();
            httpResponse = httpRestServiceClient.sendPostRequest(20, null, new String[]{"ContentType", ContentType.APPLICATION_JSON}, null, null, uri);
            logger.info("response for Post uri {} responseCode : {}", uri, httpResponse.getResponseCode().getCode());
        } catch (Exception e) {
            logger.warn("Error in post request for {} Message : {}", uri, e.getMessage());
        }
        return httpResponse;
    }

    public static HttpResponse generateHttpPostResponse(final String uri, final int timeout) {
        HttpResponse httpResponse = null;
        try {
            logger.info("generateHttpResponse POST URI : {}", uri);
            HttpRestServiceClient httpRestServiceClient = HAPropertiesReader.getHttpRestServiceExternalClient();
            httpResponse = httpRestServiceClient.sendPostRequest(timeout, null, new String[]{"ContentType", ContentType.APPLICATION_JSON}, null, null, uri);
            logger.info("response for Post uri {} responseCode : {}", uri, httpResponse.getResponseCode().getCode());
        } catch (Exception e) {
            logger.warn("Error in post request for {} Message : {}", uri, e.getMessage());
        }
        return httpResponse;
    }

    /**
     * cENM comment.
     * Filter DTs within Upgrade/Regression start/stop time.
     * @param dtStrings
     * @return List<String>
     */
    private static List<String> buildDowntimeListWatcher(final String[] dtStrings) {
        return Stream.of(dtStrings).filter(s -> Long.parseLong(s) > upgradeStartTime
                /*&& Long.parseLong(s) < upgradeFinishTime*/)
                .collect(Collectors.toList());
    }

    /**
     * cENM comment.
     * Get DT windows for ComAaLdap application.
     * @param list
     * @return List<String>
     */
    private static List<String> processComAaLdapOutput(final ArrayList<String> list) {
        final List<String> dtList = new ArrayList<>();
        boolean dtStarted = false;
        String previousLine = "";
        for (final String line : list) {
            if (previousLine.isEmpty()) {
                logger.info("First Line for ComAaLdap is {}", line);
            } else if (line.contains("success") && previousLine.contains("success")) {
                //DO nothing
            } else if (line.contains("failed") && previousLine.contains("failed")) {
                //DO nothing
            } else if (line.contains("failed") && previousLine.contains("success") && !dtStarted) {    //start DT
                dtList.add(line.split(",")[0]);
                logger.info("DownTimer ComAaLdap Started at: {}", line.split(",")[0]);
                dtStarted = true;
            } else if (line.contains("success") && previousLine.contains("failed") && dtStarted) {    //stop DT
                dtList.add(line.split(",")[0]);
                logger.info("DownTimer ComAaLdap Stopped at: {}", line.split(",")[0]);
                dtStarted = false;
            }
            previousLine = line;
        }
        if (!list.isEmpty() && dtStarted) {    //stop dt window at the end of file
            final String lastLine = list.get(list.size() - 1);
            logger.info("Last line: {}", lastLine);
            final String stopWindow = lastLine.split(",")[0];
            dtList.add(stopWindow);
            logger.info("stopWindow for ComAaLdap stopped at: {}", stopWindow);
        }
        logger.info("App ComAaLdap DT List : {}", dtList);
        return dtList;
    }

    /**
     * cENM comment.
     * Get dependency DT windows for all application dependencies.
     * @param dep
     * @param list
     * @return List<String>
     */
    private static List<String> processCommandOutput(final String dep, final ArrayList<String> list) {
        final List<String> dtList = new ArrayList<>();
        boolean dtStarted = false;
        String previousLine = "";

        for (final String line : list) {
            if (previousLine.isEmpty()) {
                logger.info("First Line for {} is {}", dep, line);
            } else if (line.contains("ONLINE") && previousLine.contains("ONLINE")) {
                //DO nothing
            } else if (line.contains("OFFLINE") && previousLine.contains("OFFLINE")) {
                //DO nothing
            } else if (line.contains("OFFLINE") && previousLine.contains("ONLINE") && !dtStarted) {    //start DT
                dtList.add(previousLine.split(",")[0]);
                logger.info("DownTimer {} Started at: {}", dep, previousLine.split(",")[0]);
                dtStarted = true;
            } else if (line.contains("ONLINE") && previousLine.contains("OFFLINE") && dtStarted) {    //stop DT
                dtList.add(line.split(",")[0]);
                logger.info("DownTimer {} Stopped at: {}", dep, line.split(",")[0]);
                dtStarted = false;
            }
            previousLine = line;
        }
        if (!list.isEmpty() && dtStarted) {    //stop dt window at the end of file
            final String lastLine = list.get(list.size() - 1);
            logger.info("Last line: {}", lastLine);
            final String stopWindow = lastLine.split(",")[0];
            dtList.add(stopWindow);
            logger.info("stopWindow for {} stopped at: {}", dep, stopWindow);
        }
        logger.info("Dependency {} DT List : {}", dep, dtList);
        return dtList;
    }

    private static ArrayList<String> getResponseContent(final HttpResponse response) {
        final ArrayList<String> lines = new ArrayList<>();
        try {
            final InputStreamReader reader = new InputStreamReader(response.getContent());
            final BufferedReader br = new BufferedReader(reader);
            String line = "";
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                lines.add(line);
            }
            br.close();
            reader.close();
        } catch (final Exception e) {
            logger.warn("Content read Message: {}", e.getMessage());
        }
        return lines;
    }

    /**
     * cENM comment.
     * Get DTs for Neo4j Dependency.
     * @param dependency
     * @param podNames
     * @return
     */
    private List<String> getNeo4jDownTimePeriodsFromWatcherFiles(final String dependency, final List<String> podNames) {
        List<String> dtList;
        Map<String, HttpResponse> csvFiles = new TreeMap<>();
        if (dependency.equalsIgnoreCase(HAPropertiesReader.NEO4J_LEADER)) {
            for (String podName : podNames) {
                if (podName.contains("writeonly")) {
                    final String uri = "watcher/adu/endpoints/" + podName;
                    final HttpResponse podCsv = generateHttpGetResponse(uri);
                    if (podCsv != null && HttpStatus.OK.equals(podCsv.getResponseCode())) {
                        CommonUtils.writeFileToSlave(podName + ".csv", podCsv.getContent());
                        try {
                            CommonUtils.txtFiles.put(podName + ".csv", IOUtils.toString(podCsv.getContent(), StandardCharsets.UTF_8.name()));
                        } catch (final IOException e) {
                            logger.info("error in copying {}  file {}",podName,e.getMessage());
                            CommonUtils.isFileCopyFailed=true;
                        }
                        csvFiles.put(podName, podCsv);
                    }
                }
            }
        } else {
            for (String podName : podNames) {
                if (podName.contains("readonly")) {
                    final String uri = "watcher/adu/endpoints/" + podName;
                    final HttpResponse podCsv = generateHttpGetResponse(uri);
                    if (podCsv != null && HttpStatus.OK.equals(podCsv.getResponseCode())) {
                        CommonUtils.writeFileToSlave(podName + ".csv", podCsv.getContent());
                        try {
                            CommonUtils.txtFiles.put(podName + ".csv", IOUtils.toString(podCsv.getContent(), StandardCharsets.UTF_8.name()));
                        } catch (final IOException e) {
                            logger.info("error in copying {}  file {}",podName,e.getMessage());
                            CommonUtils.isFileCopyFailed=true;
                        }
                        csvFiles.put(podName, podCsv);
                    }
                }
            }
        }
        dtList = generateNeoDtList(csvFiles, dependency);
        return dtList;
    }

    private List<String> generateNeoDtList(final Map<String, HttpResponse> csvFiles, final String dependency) {
        final List<String> fileList = new ArrayList<>();
        final Map<String, List<String>> fileWindowList = new TreeMap<>();

        csvFiles.forEach((file, response) -> {
            final List<String> windowList = new ArrayList<>();
            boolean windowStarted = false;
            String previousLine = "";
            String startWindow = "";
            String stopWindow = "";
            final List<String> list = getResponseContent(response);
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

    private List<String> getNeo4jPodNames() {
        final HttpResponse neo4jpods = generateHttpGetResponse("watcher/adu/neo4j" );
        List<String> podNames = new ArrayList<>();
        if (neo4jpods != null && HttpStatus.OK.equals(neo4jpods.getResponseCode())) {
            final Scanner reader = new Scanner(neo4jpods.getContent());
            while (reader.hasNext()) {
                final String line = reader.nextLine();
                logger.info("line {}", line);
                if (line.contains("neo4j")) {
                    podNames.add(line + "_readonly");
                    podNames.add(line + "_writeonly");
                }
            }
        } else {
            logger.warn("unable to fetch neo4j pod names.");
        }
        logger.info("Using neo files : {}", podNames);
        return podNames;
    }

    private void getImagePullData(final String listType, final List<String> imageList) {
        for (String imageName : imageList) {
            final String uri = "watcher/adu/imagepulldata/" + imageName;
            try {
                HttpResponse response = generateHttpGetResponse(uri);
                if (response != null && HttpStatus.OK.equals(response.getResponseCode())) {
                    if (listType.equalsIgnoreCase("infra")) {
                        infraImagePullData.put(imageName, IOUtils.toString(response.getContent(), StandardCharsets.UTF_8.name()));
                    } else {
                        appImagePullData.put(imageName, IOUtils.toString(response.getContent(), StandardCharsets.UTF_8.name()));
                    }
                } else {
                    for (int start = 0; start < 3; start++) {
                        logger.info("failed to get image data for {}: resp code: {} retrying........", imageName, response == null ? "null" : response.getResponseCode());
                        CommonUtils.sleep(1);
                        response = generateHttpGetResponse(uri);
                        if (response != null && HttpStatus.OK.equals(response.getResponseCode())) {
                            if (listType.equalsIgnoreCase("infra")) {
                                infraImagePullData.put(imageName, IOUtils.toString(response.getContent(), StandardCharsets.UTF_8.name()));
                            } else {
                                appImagePullData.put(imageName, IOUtils.toString(response.getContent(), StandardCharsets.UTF_8.name()));
                            }
                            break;
                        }
                    }
                }
            } catch (final Exception e) {
                logger.warn("Image copy : {}", e.getMessage());
            }
        }
        if (listType.equalsIgnoreCase("infra")) {
            logger.info("Infra imagePullData : {}", infraImagePullData);
        } else {
            logger.info("App imagePullData : {}", appImagePullData);
        }
    }
}
