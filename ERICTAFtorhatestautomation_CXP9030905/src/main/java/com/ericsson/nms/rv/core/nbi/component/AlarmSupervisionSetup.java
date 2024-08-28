package com.ericsson.nms.rv.core.nbi.component;

import static com.ericsson.nms.rv.core.util.CommonUtils.sleep;

import java.util.List;
import java.util.Map;

import com.ericsson.nms.rv.core.util.HaTimeoutException;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.ericsson.nms.rv.core.fm.FaultManagement;
import com.ericsson.nms.rv.core.nbi.NbiComponent;
import com.ericsson.nms.rv.core.nbi.verifier.NbiSubsThreadExecutor;
import com.ericsson.nms.rv.core.nbi.NbiContext;
import com.ericsson.nms.rv.core.node.Node;
import com.ericsson.oss.testware.availability.common.exception.EnmException;

public class AlarmSupervisionSetup implements NbiComponent {

    private static final Logger logger = LogManager.getLogger(AlarmSupervisionSetup.class);
    private final FaultManagement fmEditor;

    public AlarmSupervisionSetup() {
        fmEditor = new FaultManagement();
        fmEditor.setStopDowntime(false);
    }

    @Override
    public void execute(final NbiContext context) throws EnmException, HaTimeoutException {
        fmEditor.login();
        final boolean enable = true;
        for (final Node node : context.getNbiNodes()) {
            final String networkElementId = node.getNetworkElementId();

            if (fmEditor.setAlarmSupervision(networkElementId, enable)) {
                logger.info("{} alarm supervision enabled", networkElementId);
            }
        }

        fmEditor.logout();
    }


    @Override
    public void execute(final NbiContext context, long threadId) throws EnmException, HaTimeoutException {
        fmEditor.login();
        final boolean enable = true;
        for (final Node node : context.getNbiNodes()) {
            final String networkElementId = node.getNetworkElementId();

            if (fmEditor.setAlarmSupervision(networkElementId, enable)) {
                logger.info("ThreadId: {} {} alarm supervision enabled", threadId, networkElementId);
            }
        }

        fmEditor.logout();
    }

    public boolean restoreSupervisionStatus(final List<Node> nbiNodesList, final Map<String, Boolean> supervisionMap ) {
        for (final Node node: nbiNodesList) {
            try {
                fmEditor.login();
                final String nodeId = node.getNetworkElementId();
                logger.info("Restoring supervision status for node : {} ...", nodeId);
                fmEditor.setAlarmSupervision(nodeId, supervisionMap.get(nodeId));
                sleep(2);
                final boolean supervisionState = node.getFmSupervisionStatus();
                logger.info("NBI FmSupervisionStatus of node : {} is set to : {}", nodeId, supervisionState);
                fmEditor.logout();
                return supervisionMap.get(nodeId).equals(supervisionState);
            } catch (final Exception e) {
                logger.warn(e.getMessage());
            }
        }
        return false;
    }

}