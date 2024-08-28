package com.ericsson.nms.rv.core.fm;

import com.ericsson.cifwk.taf.handlers.netsim.domain.NetworkElement;
import com.ericsson.cifwk.taf.tools.http.HttpResponse;
import com.ericsson.cifwk.taf.tools.http.constants.ContentType;
import com.ericsson.nms.rv.core.EnmApplication;
import com.ericsson.nms.rv.core.HAPropertiesReader;
import com.ericsson.nms.rv.core.HAconstants;
import com.ericsson.nms.rv.core.host.NetSim;
import com.ericsson.nms.rv.core.host.NetsimException;
import com.ericsson.nms.rv.core.netsimhandler.NodeType;
import com.ericsson.nms.rv.core.restclient.HttpRestServiceClient;
import com.ericsson.nms.rv.core.util.CommonUtils;
import com.ericsson.nms.rv.core.util.HaTimeoutException;
import com.ericsson.nms.rv.taf.tools.Constants;
import com.ericsson.oss.testware.availability.common.exception.EnmException;
import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import net.minidev.json.JSONValue;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.ericsson.nms.rv.core.EnmApplication.TIMEOUT_REST;
import static com.ericsson.nms.rv.taf.tools.Constants.EMPTY_STRING;

public class FmThreadExecutor implements Runnable {
    private static final Logger logger = LogManager.getLogger(FmThreadExecutor.class);
    private static final int ALARMS_TO_SEND = HAPropertiesReader.getNumberOfAlarms();
    private Thread worker;
    private String problem;
    private String networkElementId;
    private NetworkElement networkElement;
    private NetSim netsim;
    private boolean timedOut;
    private boolean completed;
    private boolean failed;
    private boolean netsimError;
    private HttpRestServiceClient httpRestClient;
    private EnmApplication application;
    private String nodeType;
    private List<String> alarmListID = new ArrayList<>();
    private long thredId;

    FmThreadExecutor(final String problem, final String networkElementId, final NetworkElement networkElement,
                     final NetSim netsim, HttpRestServiceClient httpRestClient, EnmApplication application, final String nodeType) {
        this.networkElement = networkElement;
        this.networkElementId = networkElementId;
        this.netsim = netsim;
        this.problem = problem;
        this.httpRestClient = httpRestClient;
        this.application = application;
        this.nodeType = nodeType;
    }

    public long getID() {
        return thredId;
    }

    public void execute() {
        worker = new Thread(this);
        worker.start();
        thredId = worker.getId();
    }

    public boolean isTimedOut() {
        return timedOut;
    }

    public boolean isCompleted() {
        return completed;
    }

    public boolean isFailed() {
        return failed;
    }

    public boolean isNetsimError() {
        return netsimError;
    }

    public void run() {
        try {
            if (nodeType.equalsIgnoreCase(NodeType.LTE.getType())) {
                netsim.sendAlarmBurst(Constants.FREQUENCY,
                        ALARMS_TO_SEND, 2, 2, problem,
                        networkElement);
            } else if (nodeType.equalsIgnoreCase(NodeType.RADIO.getType())) {
                netsim.sendAlarmBurstToRouter(ALARMS_TO_SEND, problem, networkElement);
            }
            logger.info("Thread: {} .. Sent alarm burst to {} with problem {}", thredId, networkElement, problem);
            logger.info("Thread: {} .. networkElementId : {}", thredId, networkElementId);
            if (verifyAlarmBurst(networkElementId, problem, ALARMS_TO_SEND)) {
                logger.info("Thread: {} .. {} alarm burst verified", thredId, networkElementId);
            }
            if (acknowledgeAlarms(true)) {
                logger.info("Thread: {} .. {} alarms acknowledged", thredId, networkElementId);

                if (clearAlarms()) {
                    logger.info("Thread: {} .. {} alarms cleared", thredId, networkElementId);
                }
            }
            completed = true;
        } catch (final NetsimException n) {
            netsimError = true;
            logger.warn("Thread: {} .. Alarm burst to {} with problem '{}' could not be sent. {}", thredId, networkElementId, problem, n.getMessage());
        } catch (final IllegalStateException | HaTimeoutException h) {
            timedOut = true;
            application.stopAppDownTime();
            application.setFirstTimeFail(true);
            application.startAppTimeoutDowntime();
            logger.warn("Thread: {} .. FM failed : {}", thredId, h.getMessage());
        } catch (final Exception e) {
            failed = true;
            application.stopAppTimeoutDownTime();
            if (application.isFirstTimeFail()) {
                application.setFirstTimeFail(false);
            } else {
                application.startAppDownTime();
            }
            logger.warn("Thread: {} .. FM failed : {}", thredId, e.getMessage());
        } finally {
            CommonUtils.sleep(1);
            Thread.currentThread().stop();
        }
    }

    private void acknowledgeAlarm(final String alarmId, final boolean acknowledge) throws  HaTimeoutException, EnmException {
        final Map<String, String> params = new HashMap<>();
        params.put("alarmIdList", String.valueOf(alarmId));
        params.put("value", "true");
        actAlarm(acknowledge ? Action.ACK : Action.UNACK, params);
    }

    private boolean acknowledgeAlarms(final boolean acknowledge) throws  HaTimeoutException, EnmException {
        for(final String alarmId : alarmListID) {
            acknowledgeAlarm(alarmId, acknowledge);
        }
        return true;
    }

    private boolean clearAlarms() throws  HaTimeoutException, EnmException {
        for(final String alarmId : alarmListID) {
            clearAlarm(alarmId);
        }
        return true;
    }

    private void clearAlarm(final String alarmId) throws HaTimeoutException, EnmException {
        final Map<String, String> params = new HashMap<>();
        params.put("alarmIdList", String.valueOf(alarmId));
        params.put("value", "true");
        actAlarm(Action.CLEAR, params);
    }

    private void actAlarm(final Action action, final Map<String, String> params) throws  HaTimeoutException, EnmException {
        final JSONObject jsonObject = new JSONObject();
        jsonObject.put("action", action);
        jsonObject.put("operatorName", "administrator");
        for (final Map.Entry<String, String> entry : params.entrySet()) {
            jsonObject.put(entry.getKey(), entry.getValue());
        }
        final List<String[]> bodies = new ArrayList<>();
        bodies.add(new String[]{jsonObject.toString()});
        final String alarmOperation = "alarmcontroldisplayservice/alarmMonitoring/alarmoperations/nodeactions";
        HttpResponse response;
        final long startTime = System.currentTimeMillis();
        final Date startDate = new Date(startTime);

        try {
            response = httpRestClient.sendPostRequest(TIMEOUT_REST, ContentType.APPLICATION_JSON, new String[]{"Accept", ContentType.APPLICATION_JSON}, bodies, null, alarmOperation);
            if (response.getResponseCode().getCode() == 504) {
                throw new IllegalStateException("Timed Out .. code 504");
            }
        } catch (final IllegalStateException e) {
            logger.warn("Thread: {} .. FM Timeout failed to {} .. {}", thredId, action, e.getMessage());
            application.setFirstTimeFail(true);
            application.stopAppDownTime();
            timedOut = true;
            application.startAppTimeoutDowntime();
            response = httpRestClient.sendPostRequest(100, ContentType.APPLICATION_JSON, new String[]{"Accept", ContentType.APPLICATION_JSON}, bodies, null, alarmOperation);
            if (response != null && response.getResponseCode().getCode() == 504) {
                throw new IllegalStateException("Timed Out .. code 504");
            }
            if (response != null && checkResponse(response)) {
                final long respTime = response.getResponseTimeMillis() + (20 * Constants.TEN_EXP_3);
                if (respTime < 120 * Constants.TEN_EXP_3) {
                    logger.info("Thread: {} .. startDate : {}", thredId, startDate);
                    logger.info("Thread: {} .. Delayed Response time: {}", thredId, respTime);
                    final Class<? extends EnmApplication> clazz = application.getClass();
                    final long startTimeNew = System.currentTimeMillis() - ((System.nanoTime() - EnmApplication.getDownTimeAppHandler().get(clazz).getLastTimeoutSuccess().get()) / HAconstants.TEN_EXP_6);
                    logger.info("Thread .. {} .. checkResponse startTimeNew : {}", thredId, new Date(startTimeNew));
                    final Date endDate = new Date(startTime + respTime);
                    logger.info("Thread: {} .. endDate: {}", thredId, endDate);
                    FaultManagement.fmIgnoreDTMap.put(startDate, endDate);
                    logger.info("Thread: {} .. fmIgnoreDTMap : {}", thredId, FaultManagement.fmIgnoreDTMap);
                    HAPropertiesReader.ignoreDTMap.put(HAPropertiesReader.FAULTMANAGEMENT, FaultManagement.fmIgnoreDTMap);
                }
            }
        } catch (final Exception e) {
            logger.warn("Thread: {} .. FM failed to {} .. Error : {}", thredId, action, e.getMessage());
            throw new EnmException(e.getMessage());
        }

        if (response != null) {
            application.analyzeResponse("FM failed to actAlarm", response);
            logger.info("Thread: {} .. FM response body : {} completed.", thredId, response.getBody());
        } else {
            throw new EnmException("Thread: {} .. Response is NULL.");
        }
    }

    public List<String> getAlarmListID(final String networkElement, final String problem) throws EnmException, HaTimeoutException {
        final JSONObject params = new JSONObject();
        params.put("filters", EMPTY_STRING);  //  TODO - use to filter specific alarms
        params.put("nodes", networkElement);
        params.put("recordLimit", ALARMS_TO_SEND * 10);
        params.put("tableSettings", "specificProblem#true#false");
        params.put("sortMode", "desc");
        params.put("sortAttribute", "eventTime");

        final List<String[]> bodies = new ArrayList<>();
        bodies.add(new String[]{params.toString()});

        final String getAlarmList = "alarmcontroldisplayservice/alarmMonitoring/alarmoperations/getalarmlist/eventPoIdList";

        HttpResponse response;
        final long startTime = System.currentTimeMillis();
        final Date startDate = new Date(startTime);

        try {
            response = httpRestClient.sendPostRequest(TIMEOUT_REST, ContentType.APPLICATION_JSON, new String[]{"Accept", ContentType.APPLICATION_JSON}, bodies, null, getAlarmList);
            if (response != null && response.getResponseCode().getCode() == 504) {
                throw new IllegalStateException("Timed Out .. code 504");
            }
        } catch (final IllegalStateException e) {
            logger.warn("Thread: {} .. FM failed to get alarms for NetworkElement={} .. Timeout {}", thredId, networkElement, e.getMessage());
            application.startAppTimeoutDowntime();
            timedOut = true;
            response = httpRestClient.sendPostRequest(100, ContentType.APPLICATION_JSON, new String[]{"Accept", ContentType.APPLICATION_JSON}, bodies, null, getAlarmList);
            if (response != null && response.getResponseCode().getCode() == 504) {
                throw new IllegalStateException("Timed Out .. code 504");
            }

            if (response != null && checkResponse(response)) {
                final long respTime = response.getResponseTimeMillis() + (20 * Constants.TEN_EXP_3);
                if(respTime < 120 * Constants.TEN_EXP_3) {
                    logger.info("Thread: {} .. startDate : {}", thredId, startDate);
                    logger.info("Thread: {} .. Delayed Response time: {}", thredId, respTime);
                    final Class<? extends EnmApplication> clazz = application.getClass();
                    final long startTimeNew = System.currentTimeMillis() - ((System.nanoTime() - EnmApplication.getDownTimeAppHandler().get(clazz).getLastTimeoutSuccess().get()) / HAconstants.TEN_EXP_6);
                    logger.info("Thread .. {} .. checkResponse startTimeNew : {}", thredId, new Date(startTimeNew));
                    final Date endDate = new Date(startTime + respTime);
                    logger.info("Thread: {} .. endDate: {}", thredId, endDate);
                    FaultManagement.fmIgnoreDTMap.put(startDate, endDate);
                    logger.info("Thread: {} .. fmIgnoreDTMap : {}", thredId, FaultManagement.fmIgnoreDTMap);
                    HAPropertiesReader.ignoreDTMap.put(HAPropertiesReader.FAULTMANAGEMENT, FaultManagement.fmIgnoreDTMap);
                }
            }
        } catch (final Exception e) {
            logger.warn("Thread: {} .. FM failed: {}", thredId, e.getMessage());
            throw new EnmException("FM failed ... Message: " + e.getMessage());
        }

        if (response != null && checkResponse(response)) {
            final Object parse = JSONValue.parse(response.getBody());
            final List<String> poidList = new ArrayList<>();
            if (parse instanceof JSONArray) {
                final Object object = ((JSONArray) parse).get(2);
                if (object instanceof JSONObject) {
                    final Object recordList = ((JSONObject) object).get("recordList");
                    if (recordList instanceof JSONArray) {
                        final JSONArray jsonArray = (JSONArray) recordList;
                        if (!jsonArray.isEmpty()) {
                            poidList.addAll(jsonArray.parallelStream()
                                    .filter(o -> problem.isEmpty() ?
                                            ((JSONObject) (o)).get("eventPoIdAsString") != "0" :
                                            problem.equals(((JSONObject) o).get("specificProblem")))
                                    .sequential()
                                    .map(obj -> (String) ((JSONObject) obj).get("eventPoIdAsString"))
                                    .collect(Collectors.toList()));
                        }
                    }
                }
            }
            return poidList;
        } else if (response != null) {
            application.analyzeResponse("FM failed to getAlarmListID", response);
            logger.warn("Thread: {} .. FM getAlarmListID failed ... response body: {}", thredId, response.getBody());
        } else {
            throw new EnmException("FM response is NULL.");
        }
        return new ArrayList<>();
    }

    private boolean checkResponse(final HttpResponse response) {
        if (EnmApplication.acceptableResponse.contains(response.getResponseCode())) {
            application.setFirstTimeFail(true);
            application.stopAppTimeoutDownTime();
            application.stopAppDownTime();
            return true;
        } else if (response.getResponseCode().getCode() == 504) {
            throw new IllegalStateException(response.getResponseCode().toString());
        } else {
            return false;
        }
    }

    private boolean verifyAlarmBurst(final String networkElement, final String problem, final int number) throws EnmException, HaTimeoutException {
        if (waitForAllAlarm(networkElement, problem, number)) {
            return true;
        }

        logger.warn("Thread: {} .. Alarm for NetworkElement {} could not be found in FM ", thredId, networkElement);
        return false;
    }

    private boolean waitForAllAlarm(final String networkElement, final String problem, final int number) throws EnmException, HaTimeoutException {
        final long start = System.nanoTime();
        final long timeToWait = HAPropertiesReader.getFmWaitingTimePerAlarm() * HAPropertiesReader.getNumberOfAlarms() * Constants.TEN_EXP_9;
        while (!EnmApplication.getSignal() && System.nanoTime() - start < timeToWait) {
            alarmListID = getAlarmListID(networkElement, problem);
            if (alarmListID.size() >= 1) {
                logger.info("alarmListID : {} ", alarmListID);
                return true;
            }
            CommonUtils.sleep(1);
        }
        logger.info("alarmListID : {} ", alarmListID);
        return false;
    }

    private enum Action {
        ACK,
        UNACK,
        CLEAR
    }
}
