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

import com.ericsson.nms.rv.core.netsimhandler.HaNetsimService;
import com.ericsson.nms.rv.core.netsimhandler.NodeType;
import com.ericsson.nms.rv.core.util.CliResult;
import com.ericsson.nms.rv.core.util.CliShell;
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

/**
 * {@code FaultManagement} allows to run {@code cmedit} commands through ENM's {@code Command Line Interface} application.
 */
public final class FaultManagement extends EnmApplication {

    private static final Logger logger = LogManager.getLogger(FaultManagement.class);
    private static final String VALUE = "value";
    private static final String NODES = "nodes";
    private static final String EVENT_PO_ID_AS_STRING = "eventPoIdAsString";
    private static final String FM_FAILED_TO_GET_ALARMS_FOR_NETWORK_ELEMENT = "FM failed to get alarms for NetworkElement=";
    private static final int ALARMS_TO_SEND = HAPropertiesReader.getNumberOfAlarms();
    private static long timeToWait = HAPropertiesReader.getFmWaitingTimePerAlarm() * HAPropertiesReader.getNumberOfAlarms() * Constants.TEN_EXP_9;
    private static long timeToWaitSec = HAPropertiesReader.getFmWaitingTimePerAlarm() * HAPropertiesReader.getNumberOfAlarms();
    public static NavigableMap<Date, Date> fmIgnoreDTMap = new ConcurrentSkipListMap();
    private static final int MAX_RETRY_ATTEMPT_COUNT = 3;
    /**
     * the nodeList used to generate alarms
     */
    private final List<Node> nodeList = new ArrayList<>();
    private boolean isStopDowntime = true;
    private List<String> alarmListID = new ArrayList<>();
    private static Boolean supervisionStatus = false;
    private static boolean isAlarmReceived = false;
    private static boolean isFirstExecution = true;
    private static String nodeType = NodeType.LTE.getType();

    /**
     * Creates a new {@code FaultManagement} object.
     *
     * @param fmLoadGenerator the FM load generator which contains the nodeList in the simulation
     * @throws IllegalArgumentException if {@code cmLoadGenerator} is {@code null}
     */
    public FaultManagement(final FmLoadGenerator fmLoadGenerator, final SystemStatus systemStatus) {
        if (fmLoadGenerator == null) {
            throw new IllegalArgumentException("fmLoadGenerator cannot be null");
        }
         nodeList.addAll(fmLoadGenerator.getFmVerificationNodes().parallelStream().sequential().collect(Collectors.toList()));

        final AlarmsUtil alarmsUtil = new AlarmsUtil();

        alarmsUtil.ceasealarmAll(nodeList, FaultManagement.class);
        initDownTimerHandler(systemStatus);
        initFirstTimeFail();
        if (!nodeList.isEmpty()) {
            nodeType = nodeList.get(0).getNodeType();
            logger.info("FaultManagement Using node type : {}", nodeType);
        }
        logger.info("FaultManagement Using nodes {}", getUsedNodes(nodeList));
        try {
            for (final Node node : nodeList) {
                supervisionStatus = node.getFmSupervisionStatus();
                logger.info("FM FmSupervisionStatus of node : {} was : {}", node.getNetworkElementId(), node.getFmSupervisionStatus());
            }
        } catch (final Exception e) {
            logger.warn(e.getMessage());
        }
    }

    public FaultManagement() {
    }

    public void setStopDowntime(final boolean isStopDowntime) {
        this.isStopDowntime = isStopDowntime;
    }

    @Override
    public void prepare() throws EnmException, HaTimeoutException {
        if (!nodeList.isEmpty()) {
            testNodeToBeUsed();
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
                    supervisionStatus = node.getFmSupervisionStatus();
                    logger.info("FM FmSupervisionStatus of node : {} is : {}", node.getNetworkElementId(), node.getFmSupervisionStatus());
                }
            }
            if(nodeList.isEmpty() || node == null) {
                continue;
            }
            logger.info("FaultManagement Using nodes {}", getUsedNodes(nodeList));
            try {
                String problem = String.format("HA Testing %d", new Random(System.currentTimeMillis()).nextInt());
                setAlarmSupervision(node.getNetworkElementId(), true);
                printLoadBase();
                logger.info("FM Alarm Supervision state is set to : {}", node.getFmSupervisionStatus());
                logger.info("Sending Alarms burst to : {}", node.getNetworkElementId());
                try {
                    if (nodeType.equalsIgnoreCase(NodeType.LTE.getType())) {
                        node.getNetsim().sendAlarmBurst(Constants.FREQUENCY,
                                ALARMS_TO_SEND, 2, 2, problem,
                                node.getNetworkElement());
                    } else if (nodeType.equalsIgnoreCase(NodeType.RADIO.getType())) {
                        node.getNetsim().sendAlarmBurstToRouter(ALARMS_TO_SEND, problem, node.getNetworkElement());
                    }
                } catch (final NetsimException n) {
                    logger.warn("Failed to send alarms: {}", n.getMessage());
                }
                if (verifyAlarmBurst(node.getNetworkElementId(), problem, ALARMS_TO_SEND)) {
                    isAlarmReceived = true;
                    logger.info("{} alarm burst verified", node.getNetworkElementId());
                } else {
                    //restore supervision state if node is not used!
                    nodeList.clear();
                    setAlarmSupervision(node.getNetworkElementId(), supervisionStatus);
                }
            } catch (final Exception e) {
                logger.warn("Failed to get Alarms ... trying another node : {}", e.getMessage());
            }
        }
        if (nodeList.isEmpty()) {
            HighAvailabilityTestCase.multiMapErrorCollection.put(HAPropertiesReader.appMap.get("fm"), HAPropertiesReader.appMap.get("fm") + ", Failed to get FM ERBS node which can receive alarms.");
            HighAvailabilityTestCase.appsFailedToStart.add(new FunctionalAreaKey(FaultManagement.class.getSimpleName(), true));
        }
    }

    @Override
    public void cleanup() throws EnmException {
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
                logger.info("FM FmSupervisionStatus of node : {} is set to : {}", networkElementId, supervisionState);
                if(supervisionStatus.equals(supervisionState)) {
                    logger.info("Supervision state restored successfully!");
                    break;
                }
                count++;
                logger.warn("Fail to restore node supervision .. retry attempt : {}", count);
            } catch (final Exception e) {
                logger.warn(e.getMessage());
                throw new EnmException("FM cleanup failed!");
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
        if (!nodeList.isEmpty()) {
            final Random random = new Random(System.currentTimeMillis());
            final Node node = nodeList.get(0);
            final String networkElementId = node.getNetworkElementId();
            final NetSim netsim = node.getNetsim();
            int randomNumber = random.nextInt();
            printLoadBase();

            String problem = String.format("HA Testing %d", randomNumber);
            final FmThreadExecutor fmThreadExecutor = new FmThreadExecutor(problem, networkElementId, node.getNetworkElement(), netsim, getHttpRestServiceClient(), this, nodeType);
            fmThreadExecutor.execute();
            final long start = System.currentTimeMillis();
            final long threadId = fmThreadExecutor.getID();
            logger.info("FM Thread: {} started.", threadId);
            final long totalWaitTime = (timeToWaitSec + 120) * Constants.TEN_EXP_3;
            //wait for this thread to return for max wait time (15*5 + no of restCalls * 20) sec.
            do {
                sleep(1);
                if (fmThreadExecutor.isCompleted() || fmThreadExecutor.isFailed() || fmThreadExecutor.isTimedOut() || fmThreadExecutor.isNetsimError()) {
                    break;
                }
            } while (System.currentTimeMillis() - start < totalWaitTime );

            if(fmThreadExecutor.isFailed()) {
                logger.warn("FM failed to receive alarms.");
            } else if(fmThreadExecutor.isTimedOut()) {
                logger.warn("FM Timed out.");
            } else if(fmThreadExecutor.isNetsimError()) {
                logger.error("Netsim failed to send alarms.");
            } else if(fmThreadExecutor.isCompleted()) {
                logger.info("FM Thread: {} completed!", threadId);
            }
        } else {
            logger.warn("Nothing to verify, the list of FaultManagement nodes is empty.");
        }
    }

    private void printLoadBase() {
        final String command = "/opt/ericsson/enmutils/bin/workload status FM_02,FM_03";
        try {
            final CliShell shell = new CliShell(HAPropertiesReader.getWorkload());
            final CliResult result = shell.execute(command);
            final String[] list = result.getOutput().split("\n");
            for (String line : list) {
                if (line.contains("not running")) {
                    logger.info("Load base profile status: {}", line);
                    break;
                } else if (line.contains("FM_02")) {
                    logger.info("Load base profile status: {}", line);
                } else if (line.contains("FM_03")) {
                    logger.info("Load base profile status: {}", line);
                }
            }
        } catch (final Exception e) {
            logger.warn(e.getMessage());
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
            logger.warn("FM Timeout failed to {}", action);
            throw new HaTimeoutException("FM Timeout failed to " + action + ": " + e.getMessage(), EnmErrorType.APPLICATION);
        }

        if (isStopDowntime) {
            analyzeResponse("FM failed to " + action, response);
        }
    }

    /**
     * Acknowledges/Removes the acknowledgment an open alarm for a given Network Element.
     *
     * @param alarmId     the {@code NetworkElement} which has the open alarm
     * @param acknowledge {@code true} if the alarm shall be acknowledged,
     *                    or {@code false} if the alarm shall be unacknowledged
     * @throws EnmException if failed to execute the CLI command OR if the alarm was not acknowledged
     */
    private void acknowledgeAlarm(final String alarmId, final boolean acknowledge) throws EnmException, HaTimeoutException {
        final Map<String, String> params = new HashMap<>();
        params.put("alarmIdList", String.valueOf(alarmId));
        params.put(VALUE, "true");
        actAlarm(acknowledge ? Action.ACK : Action.UNACK, params);
    }

    public boolean acknowledgeAlarmsIds(final List<String> alarmList, final boolean acknowledge) throws EnmException, HaTimeoutException {
        for(final String alarmId : alarmList) {
            acknowledgeAlarm(alarmId, acknowledge);
        }
        return true;
    }

    public boolean clearAlarmsIds(final List<String> alarmList) throws EnmException, HaTimeoutException {
        for(final String alarmId : alarmList) {
            clearAlarm(alarmId);
        }
        return true;
    }

    /**
     * Clears an open alarm for a given Network Element.
     *
     * @param alarmId the {@code NetworkElement} which has the open alarm
     * @throws EnmException if failed to execute the CLI command OR if the alarm was not cleared
     */
    private void clearAlarm(final String alarmId) throws EnmException, HaTimeoutException {
        final Map<String, String> params = new HashMap<>();
        params.put("alarmIdList", String.valueOf(alarmId));
        params.put(VALUE, "true");
        actAlarm(Action.CLEAR, params);
    }

    /**
     * Sets the supervision state of a Network Element.
     *
     * @param networkElement the {@code NetworkElement} which has the open alarm
     * @param enabled        the supervision status of the {@code NetworkElement}
     * @throws EnmException if failed to execute the CLI command OR if the supervision could not be set
     */
    public boolean setAlarmSupervision(final String networkElement, final boolean enabled) throws EnmException, HaTimeoutException {
        final Map<String, String> params = new HashMap<>();
        params.put(NODES, networkElement);
        params.put(VALUE, enabled ? "ON" : "OFF");
        actAlarm(Action.SUPERVISION, params);
        return true;
    }

    public List<String> getAlarmListID(final String networkElement, final String problem) throws EnmException, HaTimeoutException {
        final JSONObject params = new JSONObject();
        params.put("filters", EMPTY_STRING);  //  TODO - use to filter specific alarms
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
            logger.warn("FM failed to get alarms for NetworkElement={}", networkElement);
            throw new HaTimeoutException("FM failed to " + FM_FAILED_TO_GET_ALARMS_FOR_NETWORK_ELEMENT + networkElement + ": " + e.getMessage(), EnmErrorType.APPLICATION);
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

        logger.warn("Alarm for NetworkElement {} could not be found in FM ", networkElement);
        return false;
    }

    private boolean waitForAllAlarm(final String networkElement, final String problem, final int number) throws EnmException, HaTimeoutException {
        final long start = System.nanoTime();

        while (!EnmApplication.getSignal() && System.nanoTime() - start < timeToWait) {
            alarmListID = getAlarmListID(networkElement, problem);
            if (alarmListID.size() >= 1) {
                logger.info("alarmListID : {} ", alarmListID);
                return true;
            }
            sleep(1L);
        }
        logger.info("alarmListID : {} ", alarmListID);
        return false;
    }

    private enum Action {
        ACK,
        UNACK,
        CLEAR,
        SYNC,
        SUPERVISION
    }

}
