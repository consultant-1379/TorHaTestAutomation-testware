package com.ericsson.nms.rv.core.nbi.component;

import java.util.List;

import com.ericsson.nms.rv.core.util.HaTimeoutException;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.ericsson.nms.rv.core.fm.FaultManagement;
import com.ericsson.nms.rv.core.nbi.NbiComponent;
import com.ericsson.nms.rv.core.nbi.NbiContext;
import com.ericsson.nms.rv.core.nbi.verifier.NbiSubsThreadExecutor;
import com.ericsson.nms.rv.core.node.Node;
import com.ericsson.oss.testware.availability.common.exception.EnmException;

public class DeleteSubscription implements NbiComponent {

    private static final Logger logger = LogManager.getLogger(DeleteSubscription.class);
    private final FaultManagement fmEditor;
    public static final String EMPTY_STRING = "";

    public DeleteSubscription() {
        fmEditor = new FaultManagement();
        fmEditor.setStopDowntime(false);
    }

    @Override
    public void execute(final NbiContext context) throws EnmException, HaTimeoutException {
        fmEditor.login();
        logger.info("deleting and clearing alarms");

        for (final Node node : context.getNbiNodes()) {
            final String networkElementId = node.getNetworkElementId();
            try {
                final List<String> alarmIds = fmEditor.getAlarmListID(networkElementId, EMPTY_STRING);
                if (fmEditor.acknowledgeAlarmsIds(alarmIds, true)) {
                    logger.info("{} alarms acknowledged", networkElementId);

                    if (fmEditor.clearAlarmsIds(alarmIds)) {
                        logger.info("{} alarms cleared", networkElementId);
                    }
                }
            } catch (final Exception e) {
                logger.warn(e.getMessage());
            }
        }
        fmEditor.logout();
    }


    @Override
    public void execute(final NbiContext context,long threadId) throws EnmException, HaTimeoutException {
        fmEditor.login();
        logger.info("ThreadId: {} deleting and clearing alarms", threadId);

        for (final Node node : context.getNbiNodes()) {
            final String networkElementId = node.getNetworkElementId();
            try {
                final List<String> alarmIds = fmEditor.getAlarmListID(networkElementId, EMPTY_STRING);
                if (fmEditor.acknowledgeAlarmsIds(alarmIds, true)) {
                    logger.info("ThreadId: {} {} alarms acknowledged", threadId, networkElementId);

                    if (fmEditor.clearAlarmsIds(alarmIds)) {
                        logger.info("ThreadId: {} {} alarms cleared", threadId, networkElementId);
                    }
                }
            } catch (final Exception e) {
                logger.warn(e.getMessage());
            }
        }
        fmEditor.logout();
    }
}

