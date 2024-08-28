package com.ericsson.nms.rv.core.cm;

import java.util.List;

import com.ericsson.nms.rv.core.util.HaTimeoutException;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.ericsson.nms.rv.core.EnmApplication;
import com.ericsson.nms.rv.core.HAPropertiesReader;
import com.ericsson.nms.rv.core.node.Node;
import com.ericsson.nms.rv.taf.tools.Constants;
import com.ericsson.nms.rv.taf.tools.Constants.Time;
import com.ericsson.oss.testware.availability.common.exception.EnmException;

/**
 * Runs the sync for all nodes.
 */
public class CmFullSync {

    private static final Logger logger = LogManager.getLogger(CmFullSync.class);

    /**
     * the command to get that the {@code CmFunction} MO  has been created.
     */
    private static final String GET_ALL_CM_FUNCTION_COMMAND = "cmedit get * CmFunction.syncStatus==SYNCHRONIZED";

    /**
     * the command to set the {@code CmNodeHeartbeatSupervision} attribute
     */
    private static final String SET_CM_NODE_HEARTBEAT_SUPERVISION_COMMAND = "cmedit set NetworkElement=%s CmNodeHeartbeatSupervision active=true";

    /**
     * the command to start the synchronization process for the node
     */
    private static final String START_NODE_SYNC = "cmedit action %s CmFunction sync";

    /**
     * the command to get that the {@code CmFunction} MO  has been created.
     */
    private static final String GET_CM_FUNCTION_COMMAND = "cmedit get NetworkElement=%s CmFunction.syncStatus";

    private static final String SYNCHRONIZED = ": SYNCHRONIZED";
    private static final long TIME_TO_WAIT_FOR_NODES_TO_SYNC = Constants.TEN_EXP_9 * Time.ONE_MINUTE_IN_SECONDS * HAPropertiesReader.getTimeToWaitNodesToSync();
    private final EnmApplication enmApplication;

    public CmFullSync() {
        enmApplication = new ConfigurationManagement(true);
    }

    private String getNodesInSyncStateFromJson(final String json) {

        if (json == null) {
            throw new IllegalArgumentException("json cannot be null");
        }

        final int indexOf = json.indexOf("instance(s)");
        if (indexOf == 0) {
            return "The Json Response could not be parsed";
        }

        return json.substring(indexOf - 2, indexOf + 11);
    }

    public long getTimeToWaitForNodesToSync() {
        return TIME_TO_WAIT_FOR_NODES_TO_SYNC;
    }

    /**
     * Sets the {@code CmNodeHeartbeatSupervision} attribute.
     */
    public final void cmSyncNode(final String networkElementId) throws EnmException, HaTimeoutException {
        try {
            logger.info("Starting the sync for node: {}", networkElementId);
            enmApplication.login();
            enmApplication.executeCliCommand(String.format(SET_CM_NODE_HEARTBEAT_SUPERVISION_COMMAND, networkElementId));
            enmApplication.executeCliCommand(String.format(START_NODE_SYNC, networkElementId));
        } finally {
            enmApplication.logout();
        }
        logger.info("Cm Network Sync has been executed");
    }

    /**
     * Sets the {@code CmNodeHeartbeatSupervision} attribute.
     */
    public final void cmSync(final String networkElementId) throws EnmException, HaTimeoutException {
        try {
            logger.info("Starting the sync for node: {}", networkElementId);
            enmApplication.login();
            enmApplication.executeCliCommand(String.format(START_NODE_SYNC, networkElementId));
        } finally {
            enmApplication.logout();
        }
        logger.info("Cm Network Sync has been executed");
    }

    /**
     * Verifies the cm node sync.
     */
    public final void verifyNodesSync() throws EnmException, HaTimeoutException {
        logger.info("Verify Cm Full Sync");
        final String output;
        try {
            enmApplication.login();
            output = enmApplication.executeCliCommand(GET_ALL_CM_FUNCTION_COMMAND);
        } finally {
            enmApplication.logout();
        }
        logger.info("Total: {} are Synchronized", getNodesInSyncStateFromJson(output));
    }

    private void checkTime(final long startTime) throws InterruptedException {
        if ((System.nanoTime() - startTime) > TIME_TO_WAIT_FOR_NODES_TO_SYNC) {
            logger.error("Failed to wait for sync ");
            throw new InterruptedException("Failed to wait for sync operation to finish");
        }
    }

    public boolean waitForStatusToComplete(final String networkElementId) throws Exception {
        String output;
        try {
            final long startTime = System.nanoTime();
            output = StringUtils.EMPTY;
            enmApplication.login();
            do {
                try {
                    output = getNodeSyncStatus(networkElementId);
                } catch (final Exception e) {
                    logger.warn("Failed to check sync status, checking again...", e);
                }
                checkTime(startTime);
            } while (!output.contains(SYNCHRONIZED));
        } finally {
            enmApplication.logout();
        }

        logger.info("Sync status: {} ", output);
        return output.contains(SYNCHRONIZED);
    }

    private String getNodeSyncStatus(final String networkElementId) throws EnmException, HaTimeoutException {
        return enmApplication.executeCliCommand(String.format(GET_CM_FUNCTION_COMMAND, networkElementId));
    }

    public boolean checkNodesSynchronized(final List<Node> nodes) throws EnmException, HaTimeoutException {
        boolean isNodesSynced = true;
        try {
            enmApplication.login();
            for (final Node node : nodes) {
                final String output = getNodeSyncStatus(node.getNetworkElementId());
                if (!output.contains(SYNCHRONIZED)) {
                    isNodesSynced = false;
                    break;
                }
            }
        } finally {
            enmApplication.logout();
        }
        return isNodesSynced;
    }

    public String getNodeSyncStatusLogin(final String networkElementId) throws EnmException, HaTimeoutException {
        final String json;
        try {
            enmApplication.login();
            json = enmApplication.executeCliCommand(String.format(GET_CM_FUNCTION_COMMAND, networkElementId));
        } finally {
            enmApplication.logout();
        }
        return json;
    }

}
