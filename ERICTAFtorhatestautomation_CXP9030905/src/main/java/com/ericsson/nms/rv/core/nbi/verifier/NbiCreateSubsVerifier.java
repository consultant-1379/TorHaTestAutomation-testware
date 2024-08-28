package com.ericsson.nms.rv.core.nbi.verifier;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.ericsson.nms.rv.core.util.HaTimeoutException;
import com.ericsson.nms.rv.taf.tools.Constants;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.ericsson.nms.rv.core.EnmApplication;
import com.ericsson.nms.rv.core.fm.AlarmsUtil;
import com.ericsson.nms.rv.core.nbi.NbiFlow;
import com.ericsson.nms.rv.core.nbi.component.AlarmSupervisionSetup;
import com.ericsson.nms.rv.core.nbi.component.CreateNbiSubscription;
import com.ericsson.nms.rv.core.nbi.component.DeleteNbiSubscription;
import com.ericsson.nms.rv.core.nbi.component.DeleteSubscription;
import com.ericsson.nms.rv.core.node.Node;
import com.ericsson.nms.rv.core.system.SystemStatus;
import com.ericsson.oss.testware.availability.common.exception.EnmException;

public class NbiCreateSubsVerifier extends EnmApplication {

    private static final Logger logger = LogManager.getLogger(NbiCreateSubsVerifier.class);
    final List<Node> nbiNodesList;
    private final NbiFlow nbiFlow;
    private static boolean isInitiated;
    private final static Map<String, Boolean> supervisionMap = new HashMap<>();
    private static final int MAX_RETRY_ATTEMPT_COUNT = 3;

    public NbiCreateSubsVerifier(final List<Node> nbiNodesList, final SystemStatus systemStatus) {
        this.nbiNodesList = nbiNodesList;
        final AlarmsUtil alarmsUtil = new AlarmsUtil();
        alarmsUtil.ceasealarmAll(nbiNodesList, NbiCreateSubsVerifier.class);
        nbiFlow = new NbiFlow(nbiNodesList, this);
        initDownTimerHandler(systemStatus);
        initFirstTimeFail();

        logger.info("NbiCreateSubsVerifier Using nodes {}", getUsedNodes(nbiNodesList));
        for(final Node node : nbiNodesList) {
            try {
                supervisionMap.put(node.getNetworkElementId(), node.getFmSupervisionStatus());
                logger.info("NBIVS FmSupervisionStatus of node : {} was : {}", node.getNetworkElementId(), node.getFmSupervisionStatus());
            } catch (final Exception e) {
                logger.warn(e.getMessage());
            }
        }
    }

    public static boolean isInitiated() {
        return isInitiated;
    }

    @Override
    public void prepare() throws EnmException, HaTimeoutException {
        nbiFlow.clear();
        nbiFlow.add(new AlarmSupervisionSetup());
        try {
            nbiFlow.add(new CreateNbiSubscription());
            nbiFlow.add(new DeleteNbiSubscription());
            nbiFlow.execute();
        } catch (Exception e) {
            logger.warn("NBIVS failed at prepare phase : ", e);
            throw e;
        }
        createVerifyConnectionFlow();
        isInitiated = true;
    }

    private void createVerifyConnectionFlow() {
        nbiFlow.clear();
        nbiFlow.add(new CreateNbiSubscription());
        nbiFlow.add(new DeleteNbiSubscription());
    }



    @Override
    public void verify() {
        NbiSubsThreadExecutor thread = new NbiSubsThreadExecutor(nbiNodesList, nbiFlow);
        thread.execute();
        long startTime = System.nanoTime();
        logger.info("NBICreateSubs Thread: {} started.", thread.getID());
        do {
            this.sleep(1L);
        }  while (!EnmApplication.getSignal() && !thread.isCompleted() && !thread.isErrorOcurred() && !thread.isTimedOut() && (System.nanoTime() - startTime < 60L *  Constants.TEN_EXP_9));

        if (thread.isErrorOcurred()) {
            logger.warn("ThreadId: {} Error occured in NBICreateSubs", thread.getID());
        } else if (thread.isTimedOut()) {
            logger.warn("ThreadId: {} NBICreateSubs Timed out.", thread.getID());
        } else if (thread.isCompleted()) {
            logger.info("NBICreateSubs Thread: {} completed!", thread.getID());
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