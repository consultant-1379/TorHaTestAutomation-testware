package com.ericsson.nms.rv.core.fm;

import static com.ericsson.nms.rv.taf.tools.Constants.EMPTY_STRING;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.Random;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.stream.Collectors;

import com.ericsson.nms.rv.core.LoadGenerator;
import com.ericsson.nms.rv.core.netsimhandler.HaNetsimService;
import com.ericsson.nms.rv.core.netsimhandler.NodeType;
import com.ericsson.nms.rv.core.util.HaTimeoutException;
import com.ericsson.sut.test.cases.HighAvailabilityTestCase;
import com.ericsson.sut.test.cases.util.FunctionalAreaKey;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.ericsson.cifwk.taf.tools.http.HttpResponse;
import com.ericsson.cifwk.taf.tools.http.constants.ContentType;
import com.ericsson.nms.rv.core.EnmApplication;
import com.ericsson.nms.rv.core.HAPropertiesReader;
import com.ericsson.nms.rv.core.host.NetSim;
import com.ericsson.nms.rv.core.host.NetsimException;
import com.ericsson.nms.rv.core.node.Node;
import com.ericsson.nms.rv.core.system.SystemStatus;
import com.ericsson.nms.rv.taf.tools.Constants;
import com.ericsson.oss.testware.availability.common.exception.EnmErrorType;
import com.ericsson.oss.testware.availability.common.exception.EnmException;

import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import net.minidev.json.JSONValue;


public final class FaultManagementBsc extends EnmApplication {

    private static final Logger logger = LogManager.getLogger(FaultManagementBsc.class);
    private static final String VALUE = "value";
    private static final String NODES = "nodes";
    private static final String FM_FAILED_TO_GET_ALARMS_FOR_NETWORK_ELEMENT = "FMB failed to get alarms for NetworkElement=";
    private static final String EVENT_PO_ID_AS_STRING = "eventPoIdAsString";
    private static final int ALARMS_TO_SEND = HAPropertiesReader.getNumberOfAlarms();
    private static final int MAX_RETRY_ATTEMPT_COUNT = 3;
    private static long timeToWait = HAPropertiesReader.getFmWaitingTimePerAlarm() * HAPropertiesReader.getNumberOfAlarms() * Constants.TEN_EXP_9;

    private final List<Node> nodeList = new ArrayList<>();
    private List<String> alarmListID = new ArrayList<>();
    private static Boolean supervisionStatus = false;
    private static boolean isAlarmReceived = false;
    private static boolean isFirstExecution = true;
    private static String nodeType = NodeType.BSC.getType();
    private final AlarmsUtil alarmsUtil = new AlarmsUtil();
    private static long timeToWaitsec = HAPropertiesReader.getFmWaitingTimePerAlarm() * HAPropertiesReader.getNumberOfAlarms() ;
    public static NavigableMap<Date, Date> fmbIgnoreDTMap = new ConcurrentSkipListMap();

    public FaultManagementBsc(final FmBscLoadGenerator fmBscLoadGenerator, final SystemStatus systemStatus) {
        if (fmBscLoadGenerator == null) {
            throw new IllegalArgumentException("fmLoadGenerator cannot be null");
        }
        nodeList.addAll(fmBscLoadGenerator.getFmbVerificationNodes().parallelStream().sequential().collect(Collectors.toList()));

        initDownTimerHandler(systemStatus);
        initFirstTimeFail();

        if(!nodeList.isEmpty()) {
            alarmsUtil.ceaseAlarmAllBsc(nodeList);
            try {
                for (final Node node : nodeList) {
                    supervisionStatus = node.getFmSupervisionStatus();
                    logger.info("FMB FmSupervisionStatus of node : {} was : {}", node.getNetworkElementId(), node.getFmSupervisionStatus());
                }
            } catch (final Exception e) {
                logger.warn(e.getMessage());
            }
        }
    }

    @Override
    public void prepare() throws EnmException, HaTimeoutException {
        if(LoadGenerator.isBscNodesAvailable) {
            testNodeToBeUsed();
        } else {
            logger.warn("BSC nodes not available for FaultManagementBsc.");
        }
    }

    private void testNodeToBeUsed() throws EnmException, HaTimeoutException {
        Node node;
        while(!isAlarmReceived &&  HaNetsimService.getNetsimIterator(nodeType).hasNext()) {
            if (isFirstExecution) {
                node = nodeList.get(0);
                isFirstExecution = false;
            } else {
                nodeList.clear();
                supervisionStatus = false;
                node = HaNetsimService.getNextWorkloadNode(HaNetsimService.getNetsimIterator(nodeType));
                if(node != null) {
                    nodeList.add(node);
                    alarmsUtil.ceaseAlarmAllBsc(nodeList);
                    sleep(2);
                    try {
                        executeCliCommand(String.format("alarm ack %s", node.getNetworkElementId()));
                        sleep(2);
                        executeCliCommand(String.format("alarm clear %s", node.getNetworkElementId()));
                    } catch (final Exception e) {
                        logger.warn(e.getMessage());
                    }
                    supervisionStatus = node.getFmSupervisionStatus();
                    logger.info("FM BSC FmSupervisionStatus of node : {} is : {}", node.getNetworkElementId(), node.getFmSupervisionStatus());
                }
            }
            if(nodeList.isEmpty() || node == null) {
                continue;
            }
            try {
                int randomNumber = new Random(System.currentTimeMillis()).nextInt();
                String problem = String.format("HA Testing %d", randomNumber < 0L ? -randomNumber : randomNumber);
                setAlarmSupervision(node.getNetworkElementId(), true);
                logger.info("FMB Alarm Supervision state is set to : {}", true);
                logger.info("Sending Alarms burst to : {}", node.getNetworkElementId());
                try {
                    node.getNetsim().sendAlarmBurstToBsc(ALARMS_TO_SEND, problem, node);
                } catch (NetsimException n) {
                    logger.warn("Failed to send alarms to bsc node: {}", n.getMessage());
                    setAlarmSupervision(node.getNetworkElementId(), supervisionStatus);
                    nodeList.clear();
                    continue;
                }
                if (verifyAlarmBurst(node.getNetworkElementId(), problem, ALARMS_TO_SEND)) {
                    isAlarmReceived = true;
                    logger.info("{} alarm burst verified", node.getNetworkElementId());
                } else {
                    setAlarmSupervision(node.getNetworkElementId(), supervisionStatus);
                    nodeList.clear();
                }
            } catch (final Exception e) {
                logger.warn("Failed to get Alarms for Bsc ... trying another node : {}", e.getMessage());
                nodeList.clear();
            }
        }
        if (nodeList.isEmpty()) {
            HighAvailabilityTestCase.multiMapErrorCollection.put(HAPropertiesReader.appMap.get("fmb"), HAPropertiesReader.appMap.get("fmb") + ", Failed to get FM BSC node which can receive alarms.");
            HighAvailabilityTestCase.appsFailedToStart.add(new FunctionalAreaKey(FaultManagementBsc.class.getSimpleName(), true));
        }
        logger.info("FaultManagementBsc Using nodes {}", getUsedNodes(nodeList));
    }

    @Override
    public void cleanup() throws EnmException {
        if(!nodeList.isEmpty() && LoadGenerator.isBscNodesAvailable) {
            alarmsUtil.ceaseAlarmAllBsc(nodeList);
            final Node node = nodeList.get(0);
            final String networkElementId = node.getNetworkElementId();
            int count = 0;
            ackClearAlarms(networkElementId, "ack");
            ackClearAlarms(networkElementId, "clear");
            while (count < MAX_RETRY_ATTEMPT_COUNT) {
                try {
                    logger.info("Restoring supervision state of node {} ... ", networkElementId);
                    setAlarmSupervision(networkElementId, supervisionStatus);
                    sleep(2);
                    final boolean supervisionState = node.getFmSupervisionStatus();
                    logger.info("FMB FmSupervisionStatus of node : {} is set to : {}", networkElementId, supervisionState);
                    if(supervisionStatus.equals(supervisionState)) {
                        logger.info("Supervision state restored successfully!");
                        break;
                    }
                    count++;
                    logger.warn("Fail to restore node supervision .. retry attempt : {}", count);
                } catch (final Exception e) {
                    logger.warn(e.getMessage());
                    throw new EnmException("FMB cleanup failed!");
                }
            }
            count = 0;
            while (count < MAX_RETRY_ATTEMPT_COUNT) {
                try {
                    executeCliCommand(String.format("alarm ack %s", networkElementId));
                    sleep(5);
                    executeCliCommand(String.format("alarm clear %s", networkElementId));
                    break;
                } catch (final Exception e) {
                    logger.warn(e.getMessage());
                }
                count++;
            }
        }
    }
    private void ackClearAlarms(String networkElementId, String action) {
        final String command = String.format("alarm %s NetworkElement=%s --specificProblem \"HA Testing\"", action, networkElementId);
        logger.info("Alarms {} command is: {}", action, command);
        int count  = 1;
        while(count <= 3) {
            try {
                logger.info("Performing {} for the node {} ... ", action, networkElementId);
                final String result = executeCliCommand(command);
                logger.info("Alarms action = {} for the node {} are successfully executed: {}", action, networkElementId, result);
                break;
            } catch (final Exception e) {
                logger.warn("Failed to execute alarm action = {}, retrying the action, Error Message: {}", action, e.getMessage());
                count++;
            }

        }
    }

    @Override
    public void verify() {
        if (!nodeList.isEmpty() && LoadGenerator.isBscNodesAvailable) {
            final Random random = new Random(System.currentTimeMillis());
            final Node node = nodeList.get(random.nextInt(nodeList.size()));
            final String networkElementId = node.getNetworkElementId();
            final NetSim netsim = node.getNetsim();
            int randomNumber = random.nextInt();

            final String problem = String.format("HA Testing %d", randomNumber < 0L ? -randomNumber : randomNumber);
            final FmbThreadExecutor fmbThreadExecutor = new FmbThreadExecutor(problem, networkElementId, node, netsim, getHttpRestServiceClient(), this);
            fmbThreadExecutor.execute();
            final long start = System.currentTimeMillis();
            final long threadId = fmbThreadExecutor.getID();
            logger.info("FMB Thread: {} started.", threadId);
            final long totalWaitTime = (timeToWaitsec + 120) * Constants.TEN_EXP_3;
            //wait for this thread to return for max wait time (15*5 + no of restCalls * 20) sec.
            do {
                sleep(1);
                if (fmbThreadExecutor.isCompleted() || fmbThreadExecutor.isFailed() || fmbThreadExecutor.isTimedOut() || fmbThreadExecutor.isNetsimError()) {
                    break;
                }
            } while (System.currentTimeMillis() - start < totalWaitTime );

            if(fmbThreadExecutor.isFailed()) {
                logger.warn("Thread : {} FMB failed to receive alarms.", threadId);
            } else if(fmbThreadExecutor.isTimedOut()) {
                logger.warn("Thread : {} FMR Timed out.", threadId);
            } else if(fmbThreadExecutor.isNetsimError()) {
                logger.error("Thread : {} Netsim failed to send alarms.", threadId);
            } else if(fmbThreadExecutor.isCompleted()) {
                logger.info("Thread : {} FMB completed!", threadId);
            }
        }
    }

    private void actAlarm(final Action action, final Map<String, String> params) throws EnmException, HaTimeoutException {
        final JSONObject jsonObject = new JSONObject();
        jsonObject.put("action", action);
        jsonObject.put("operatorName", "administrator");
        for (final Entry<String, String> entry : params.entrySet()) {
            jsonObject.put(entry.getKey(), entry.getValue());
        }

        final List<String[]> bodies = new ArrayList<>();
        bodies.add(new String[]{jsonObject.toString()});

        final String alarmOperation = "alarmcontroldisplayservice/alarmMonitoring/alarmoperations/nodeactions";

        HttpResponse response;
        try {
            response = getHttpRestServiceClient().sendPostRequest(TIMEOUT_REST, ContentType.APPLICATION_JSON, new String[]{Header.ACCEPT.getValue(), ContentType.APPLICATION_JSON}, bodies, null, alarmOperation);
        } catch (final IllegalStateException e) {
            logger.warn("FMB Timeout failed to {}", action);
            throw new HaTimeoutException("FMB Timeout failed to " + action + ": " + e.getMessage(), EnmErrorType.APPLICATION);
        }
        analyzeResponse("FMB failed to " + action, response);
    }

    public boolean setAlarmSupervision(final String networkElement, final boolean enabled) throws EnmException, HaTimeoutException {
        final Map<String, String> params = new HashMap<>();
        params.put(NODES, networkElement);
        params.put(VALUE, enabled ? "ON" : "OFF");
        actAlarm(Action.SUPERVISION, params);
        return true;
    }

    public List<String> getAlarmListID(final String networkElement, final String problem) throws EnmException, HaTimeoutException {
        final JSONObject params = new JSONObject();
        params.put("filters", EMPTY_STRING);
        params.put(NODES, networkElement);
        params.put("recordLimit", ALARMS_TO_SEND * 10);
        params.put("tableSettings", "specificProblem#true#false");
        params.put("sortMode", "desc");
        params.put("sortAttribute", "eventTime");

        final List<String[]> bodies = new ArrayList<>();
        bodies.add(new String[]{params.toString()});

        final String getAlarmList = "alarmcontroldisplayservice/alarmMonitoring/alarmoperations/getalarmlist/eventPoIdList";

        HttpResponse response;
        try {
            response = getHttpRestServiceClient().sendPostRequest(TIMEOUT_REST, ContentType.APPLICATION_JSON, new String[]{Header.ACCEPT.getValue(), ContentType.APPLICATION_JSON}, bodies, null, getAlarmList);
        } catch (final IllegalStateException e) {
            logger.warn("FMB failed to get alarms for NetworkElement={}", networkElement);
            throw new HaTimeoutException("FMB failed to " + FM_FAILED_TO_GET_ALARMS_FOR_NETWORK_ELEMENT + networkElement + ": " + e.getMessage(), EnmErrorType.APPLICATION);
        }

        analyzeResponse(FM_FAILED_TO_GET_ALARMS_FOR_NETWORK_ELEMENT + networkElement, response);

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
                                        ((JSONObject) (o)).get(EVENT_PO_ID_AS_STRING) != "0" :
                                        problem.equals(((JSONObject) o).get("specificProblem")))
                                .sequential()
                                .map(obj -> (String) ((JSONObject) obj).get(EVENT_PO_ID_AS_STRING))
                                .collect(Collectors.toList()));
                    }
                }
            }
        }
        return poidList;
    }

    private boolean verifyAlarmBurst(final String networkElement, final String problem, final int number) throws EnmException, HaTimeoutException {
        if (waitForAllAlarm(networkElement, problem, number)) {
            return true;
        }
        logger.warn("Alarm for NetworkElement {} could not be found in FMB.", networkElement);
        return false;
    }

    private boolean waitForAllAlarm(final String networkElement, final String problem, final int number) throws EnmException, HaTimeoutException {
        final long start = System.nanoTime();

        while (!EnmApplication.getSignal() && System.nanoTime() - start < timeToWait) {
            alarmListID = getAlarmListID(networkElement, problem);
            if (alarmListID.size() >= 1) {
                logger.info("alarmListID : {}", alarmListID);
                return true;
            }
            sleep(1L);
        }
        logger.info("alarmListID : {}", alarmListID);
        return false;
    }

    enum Action {
        SUPERVISION
    }

}
