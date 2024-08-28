package com.ericsson.nms.rv.core.launcher;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import com.ericsson.nms.rv.core.HAPropertiesReader;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.ericsson.cifwk.taf.tools.http.HttpResponse;
import com.ericsson.cifwk.taf.tools.http.constants.ContentType;
import com.ericsson.cifwk.taf.tools.http.constants.HttpStatus;
import com.ericsson.nms.rv.core.EnmApplication;
import com.ericsson.nms.rv.core.system.SystemStatus;
import com.ericsson.sut.test.cases.HighAvailabilityTestCase;
import com.ericsson.sut.test.cases.util.FunctionalAreaKey;

import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import net.minidev.json.JSONValue;

public class AppLauncher extends EnmApplication {

    private static final String REST_APP = "/rest/apps/";
    private static final Logger LOGGER = LogManager.getLogger(AppLauncher.class);
    private static final Map<String, String> repeatResponseMap = new TreeMap<>();
    private static final Map<String, Integer> missingLinksMap = new TreeMap<>();
    private static final Map<String, String> allAppFailedMap = new TreeMap<>();
    private final Map<String, String> responseMap = new TreeMap<>();
    private final Map<String, Integer> counterMap = new TreeMap<>();
    private int repeatedCounter = 0;
    private int totalCounter = 0;
    private static int allAppFailedCount = 0;
    private boolean failedRequests = false;

    public AppLauncher(final SystemStatus systemStatus) {
        initDownTimerHandler(systemStatus);
        initFirstTimeFail();
    }

    public AppLauncher() {
    }

    public void prepare() {
        for (int i = 0; i < 3; i++) {
            boolean flag = true;
            final HttpResponse response = getHttpRestServiceClient().sendGetRequest(TIMEOUT_REST, ContentType.APPLICATION_JSON, new String[]{Header.ACCEPT.getValue(), ContentType.APPLICATION_JSON}, null, null, REST_APP);
            LOGGER.info("App Launcher response code for fetching the list of UI links in Prepare phase:{}", response.getResponseCode());
            if (!HttpStatus.OK.equals(response.getResponseCode())) {
                LOGGER.info("App Launcher: {}", response.getBody());
                flag = false;
            } else {
                final Object parse = JSONValue.parse(response.getBody());
                if (parse instanceof JSONArray) {
                    //add the id and uri to the map
                    final Map<String, String> map = ((JSONArray) parse).stream().collect(Collectors.toMap(o -> (String) (((JSONObject) o).get("id")), o -> (String) (((JSONObject) o).get("uri"))));
                    if (map.isEmpty()) {
                        LOGGER.info("App Launcher: {}", response.getBody());
                        flag = false;
                    } else {
                        map.remove("wansdn");
                    }
                    if (responseMap.isEmpty()) {
                        responseMap.putAll(map);
                        LOGGER.info("AppLauncher responseMap : {}", responseMap.toString());
                    }
                }
            }
            if (!flag && i == 2) {
                LOGGER.info("AppLauncher failed in prepare phase as unable to get UI links");
                HighAvailabilityTestCase.multiMapErrorCollection.put(HAPropertiesReader.appMap.get("applaunch"), HAPropertiesReader.appMap.get("applaunch") + ", Failed to get application links in prepare phase");
                HighAvailabilityTestCase.appsFailedToStart.add(new FunctionalAreaKey(AppLauncher.class.getSimpleName(), true));
            } else if (!flag) {
                LOGGER.info("Unable to get links of rest apps in prepare phase, Retrying...");
            } else {
                break;
            }
            sleep(5);
        }
    }

    public boolean getAllRestApps() {
        boolean flag = true;
        final HttpResponse response = getHttpRestServiceClient().sendGetRequest(TIMEOUT_REST, ContentType.APPLICATION_JSON, new String[]{Header.ACCEPT.getValue(), ContentType.APPLICATION_JSON}, null, null, REST_APP);
        LOGGER.info("App Launcher response code for fetching the list of UI links:{}", response.getResponseCode());
        if (!EnmApplication.acceptableResponse.contains(response.getResponseCode())) {
            LOGGER.info("response body is : {}", response.getBody());
            flag = false;
        } else {
            final Object parse = JSONValue.parse(response.getBody());
            if (parse instanceof JSONArray) {
                //add the id and uri to the map
                final Map<String, String> map = ((JSONArray) parse).stream().collect(Collectors.toMap(o -> (String) (((JSONObject) o).get("id")), o -> (String) (((JSONObject) o).get("uri"))));
                if (map.isEmpty()) {
                    LOGGER.info("App Launcher: {}", response.getBody());
                    flag = false;
                    LOGGER.info("UI links map is empty in AppLauncher");
                } else {
                    map.remove("wansdn");
                }
                repeatResponseMap.clear();
                repeatResponseMap.putAll(map);
            }
        }
        return flag;
    }

    private void verifyRepeatedURILinkAvailability() {
        repeatedCounter++;
        responseMap.forEach((id, uri) -> {
            final boolean checkId = repeatResponseMap.containsKey(id);
            final boolean checkUri = repeatResponseMap.containsValue(uri);
            //check id
            if (checkId) {
                final String uriString = repeatResponseMap.get(id);
                if (!uriString.equals(uri)) {
                    LOGGER.warn("The uri has changed for the ID: {} ", id);
                }
                //check if both id and uri are missing then increment
            } else if (!checkId && !checkUri) {
                Integer integer = missingLinksMap.getOrDefault(id, 0);
                missingLinksMap.put(id, ++integer);
                LOGGER.warn("The links are missing for {}  {} times", id, integer);
            }
        });
    }

    private void verifyEachApplication() {
        final int[] failureCounter = {0};
        responseMap.forEach((id, uri) -> {
            try {
                if (uri != null) {
                    final HttpResponse response = getHttpRestServiceClient()
                            .sendGetRequest(TIMEOUT_REST, ContentType.APPLICATION_JSON, new String[]{"ContentType", ContentType.APPLICATION_JSON}, null, null, uri);

                    final HttpStatus responseCode = response.getResponseCode();

                    if (!HttpStatus.OK.equals(responseCode) && isLoginSuccess()) {
                        final int integer = fillCounterMap(failureCounter, id);
                        LOGGER.warn("The number of FAILED requests for {} is: {},  Error code: {}", id, integer, response.getStatusLine());
                        LOGGER.warn("Header of failed request: {}" , response.getHeaders().toString());
                        LOGGER.warn("Location: {}" , response.getHeaders().get("Location"));
                    }
                }
            } catch (final Exception e) {
                fillCounterMap(failureCounter, id);
                LOGGER.warn("Request for {} failed with the following uri: {}", id, uri, e);
            }
        });

        if (responseMap.size() == failureCounter[0] && isLoginSuccess()) {
            failedRequests = true;
            allAppFailedCount = allAppFailedCount + 1;
            allAppFailedMap.put("Failed count - " + allAppFailedCount, LocalDateTime.now().format(HAPropertiesReader.formatter));
            LOGGER.info("allAppFailedMap : {}", allAppFailedMap);
            LOGGER.error("All applications have failed HTTP Requests, Count : {}", allAppFailedCount);
        }
    }

    private int fillCounterMap(final int[] failureCounter, final String id) {
        failureCounter[0]++;
        Integer integer = counterMap.getOrDefault(id, 0);
        counterMap.put(id, ++integer);
        return integer;
    }

    @Override
    public void verify() {
        totalCounter++;
        if (getAllRestApps()) {
            verifyEachApplication();
            verifyRepeatedURILinkAvailability();
        } else {
            LOGGER.info("Failed to get rest apps");
        }
        LOGGER.info("Total counter={}", totalCounter);
        LOGGER.info("counterMap={}", counterMap);
        sleep(30L);
    }

    //===== Getters ==========
    public Map<String, String> getResponseMap() {
        LOGGER.info("responseMap : {}", responseMap.toString());
        LOGGER.info("repeatResponseMap : {}", repeatResponseMap.toString());
        return responseMap;
    }

    public Map<String, Integer> getCounterMap() {
        LOGGER.info("counterMap : {}", counterMap.toString());
        return counterMap;
    }

    public Map<String, Integer> getMissingLinksMap() {
        LOGGER.info("missingLinksMap : {}", missingLinksMap.toString());
        return missingLinksMap;
    }

    public int getRepeatedCounter() {
        return repeatedCounter;
    }

    public int getAllAppFailedCount() {
        return allAppFailedCount;
    }

    public Map<String, String> getAllAppFailedMap() {
        return allAppFailedMap;
    }

    public int getTotalCounter() {
        return totalCounter;
    }

    public boolean isFailedRequests() {
        return failedRequests;
    }
}