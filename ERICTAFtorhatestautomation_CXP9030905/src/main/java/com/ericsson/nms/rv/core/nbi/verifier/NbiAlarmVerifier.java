package com.ericsson.nms.rv.core.nbi.verifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.ericsson.nms.rv.core.HAPropertiesReader;
import com.ericsson.nms.rv.core.nbi.NbiContext;
import com.ericsson.nms.rv.core.netsimhandler.NodeType;
import com.ericsson.nms.rv.core.util.HaTimeoutException;
import com.ericsson.nms.rv.taf.tools.Constants;
import com.ericsson.sut.test.cases.util.FunctionalAreaKey;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.ericsson.nms.rv.core.EnmApplication;
import com.ericsson.nms.rv.core.LoadGenerator;
import com.ericsson.nms.rv.core.fm.AlarmsUtil;
import com.ericsson.nms.rv.core.nbi.NbiFlow;
import com.ericsson.nms.rv.core.nbi.component.AlarmSupervisionSetup;
import com.ericsson.nms.rv.core.nbi.component.CreateNbiSubscription;
import com.ericsson.nms.rv.core.nbi.component.DeleteNbiSubscription;
import com.ericsson.nms.rv.core.nbi.component.DeleteSubscription;
import com.ericsson.nms.rv.core.nbi.component.NbiVerifyAlarms;
import com.ericsson.nms.rv.core.netsimhandler.HaNetsimService;
import com.ericsson.nms.rv.core.node.Node;
import com.ericsson.nms.rv.core.system.SystemStatus;
import com.ericsson.oss.testware.availability.common.exception.EnmException;
import com.ericsson.sut.test.cases.HighAvailabilityTestCase;

public class NbiAlarmVerifier extends EnmApplication {

    private static final Logger logger = LogManager.getLogger(NbiAlarmVerifier.class);
    final List<Node> nbiNodesList;
    private NbiFlow nbiFlow;
    private static boolean isInitiated;
    private static boolean isFirstExecution = true;
    private final static Map<String, Boolean> supervisionMap = new HashMap<>();
    private static String nodeType = NodeType.LTE.getType();
    private static final int MAX_RETRY_ATTEMPT_COUNT = 3;
    private static boolean subscriptionFailed = false;

    public NbiAlarmVerifier(final List<Node> nbiNodeList, final SystemStatus systemStatus) {
        this.nbiNodesList = new ArrayList<>();
        nbiFlow = new NbiFlow(nbiNodeList, this);

        initDownTimerHandler(systemStatus);
        initFirstTimeFail();

        this.nbiNodesList.addAll(nbiNodeList.parallelStream().sequential().collect(Collectors.toList()));
        if (!nbiNodesList.isEmpty()) {
            nodeType = nbiNodesList.get(0).getNodeType();
            logger.info("NBI Alarm Verifier Using node type : {}", nodeType);
        }
        logger.info("NbiAlarmVerifier Using nodes {}", getUsedNodes(nbiNodesList));
        for(final Node node : nbiNodesList) {
            try {
                supervisionMap.put(node.getNetworkElementId(), node.getFmSupervisionStatus());
                logger.info("NBIVA FmSupervisionStatus of node : {} was : {}", node.getNetworkElementId(), node.getFmSupervisionStatus());
            } catch (final Exception e) {
                logger.warn(e.getMessage());
            }
        }
    }

    public static boolean isInitiated() {
        return isInitiated;
    }

    @Override
    public void prepare() throws EnmException {
        if (!nbiNodesList.isEmpty()) {
            testNodeToBeUsed();
            isInitiated = nbiFlow.isAlarmReceived();
            createVerifyAlarmsFlow();
        }
        if(!isInitiated) {
            HighAvailabilityTestCase.multiMapErrorCollection.put(HAPropertiesReader.appMap.get("nbiva"),HAPropertiesReader.appMap.get("nbiva") + ", Alarms not received from NBI");
            HighAvailabilityTestCase.appsFailedToStart.add(new FunctionalAreaKey(NbiAlarmVerifier.class.getSimpleName(), true));
        }
    }

    private void testNodeToBeUsed() throws EnmException {
        try {
            final AlarmsUtil alarmsUtil = new AlarmsUtil();
            Node node;
            logger.info("Netsim Iterator has nodes : {}", HaNetsimService.getNetsimIterator(nodeType).hasNext());
            while (!nbiFlow.isAlarmReceived() && HaNetsimService.getNetsimIterator(nodeType).hasNext()) {
                if (isFirstExecution) {
                    node = nbiNodesList.get(0);
                    isFirstExecution = false;
                } else {
                    nbiNodesList.clear();
                    supervisionMap.clear();
                    node = HaNetsimService.getNextWorkloadNode(HaNetsimService.getNetsimIterator(nodeType));
                    nbiNodesList.add(node);
                    supervisionMap.put(node.getNetworkElementId(), node.getFmSupervisionStatus());
                }
                if(nbiNodesList.isEmpty() || node == null) {
                    continue;
                }
                logger.info("NbiAlarmVerifier Using nodes {}", getUsedNodes(nbiNodesList));
                alarmsUtil.ceasealarmAll(nbiNodesList, NbiAlarmVerifier.class);
                nbiFlow = new NbiFlow(nbiNodesList, this);
                nbiFlow.add(new AlarmSupervisionSetup());
                nbiFlow.add(new CreateNbiSubscription(1440));
                nbiFlow.add(new NbiVerifyAlarms());
                executeAndCheckIfAlarmReceived(node);
            }
        } catch (final Exception e) {
            logger.warn("Failed in testNodeToBeUsed : ", e);
            throw new EnmException(e.getMessage());
        }
    }

    private void executeAndCheckIfAlarmReceived(final Node node) {
        try {
            nbiFlow.execute();
            if (nbiFlow.isAlarmReceived()) {
                LoadGenerator.getNodes().addAll(nbiNodesList);
            } else {
                logger.error("alarms were not received on node {} ", node.getNetworkElementId());
                cleanup();
            }
        } catch (final EnmException | HaTimeoutException e) {
            logger.error("node {} could not be used by nbi, finding a different node", node.getNetworkElementId(), e);
            if (HAPropertiesReader.isEnvCloudNative() && e.getMessage().contains("subscription-failed")) {
                if (!subscriptionFailed) {
                    subscriptionFailed = true;
                    NbiContext.setFmVipAddress(HAPropertiesReader.fmVipAddressFromValuesDoc);
                }
            }
            try {
                cleanup();
            } catch (final Exception ex) {
                logger.warn("Cleanup failed : {}", ex.getMessage());
            }
        }
    }

    private void createVerifyAlarmsFlow() {
        nbiFlow.clear();
        nbiFlow.add(new NbiVerifyAlarms());
    }

    @Override
    public void verify() {
        if (!nbiNodesList.isEmpty()) {
            NbiAlarmThreadExecutor thread = new NbiAlarmThreadExecutor(nbiNodesList, nbiFlow);
            thread.execute();
            long startTime = System.nanoTime();
            logger.info("NbiAlarm Thread: {} started.", thread.getID());
            do {
                this.sleep(1L);
            }
            while (!EnmApplication.getSignal() && !thread.isCompleted() && !thread.isErrorOcurred() && !thread.isTimedOut() && (System.nanoTime() - startTime < 60L * Constants.TEN_EXP_9));

            if (thread.isErrorOcurred()) {
                logger.warn("ThreadId: {} Error occurred in NbiAlarm", thread.getID());
            } else if (thread.isTimedOut()) {
                logger.warn("ThreadId: {} NbiAlarm Timed out.", thread.getID());
            } else if (thread.isCompleted()) {
                logger.info("NbiAlarm Thread: {} completed!", thread.getID());
            }
        } else {
            logger.warn("Nothing to verify, the list of NBI nodes are empty.");
        }
    }

    @Override
    public void cleanup() {
        nbiFlow.clear();
        nbiFlow.add(new DeleteNbiSubscription());
        nbiFlow.add(new DeleteSubscription());
        int count = 0;
        try {
            nbiFlow.execute();
        } catch (final EnmException | HaTimeoutException e) {
            logger.error("FM fail to delete FM subscriptions", e);
        }
        final AlarmSupervisionSetup setup = new AlarmSupervisionSetup();
        while (count < MAX_RETRY_ATTEMPT_COUNT) {
            if (setup.restoreSupervisionStatus(nbiNodesList, supervisionMap)) {
                logger.info("Supervision state restored successfully!");
                break;
            }
            count++;
            logger.warn("Fail to restore node supervision .. retry attempt : {}", count);
        }
    }

}
