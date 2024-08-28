package com.ericsson.nms.rv.core.util;

import java.util.Set;
import java.util.stream.Collectors;

import com.ericsson.nms.rv.core.cm.CmFullSync;
import com.ericsson.nms.rv.core.cm.ConfigurationManagement;
import com.ericsson.nms.rv.core.node.Node;
import com.ericsson.oss.testware.availability.common.exception.EnmException;
import net.minidev.json.JSONArray;
import net.minidev.json.JSONValue;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class NodeUtils {
    private static final Logger logger = LogManager.getLogger(NodeUtils.class);
    private static final String GET_NODE_MODEL = "cmedit get * networkelement.(networkElementId==%s) networkelement.ossModelIdentity";

    public static void syncNodes(final Set<Node> listNodes) {
        listNodes.parallelStream().forEach(node -> {
            final CmFullSync cmFullSync = new CmFullSync();

            if (!node.isSynchDone()) {
                final String networkElementId = node.getNetworkElementId();
                try {
                    cmFullSync.cmSyncNode(networkElementId);
                    if (!cmFullSync.waitForStatusToComplete(networkElementId)) {
                        logger.warn("NetworkElement: {} could not sync correctly: ", networkElementId);
                    }
                } catch (final Exception e) {
                    logger.warn("Failed cmSyncNodes: {}", networkElementId, e);
                }
            }
        });
    }

    public static void updateAllSyncedNodesWithOssModelIdentity(final Set<Node> ALL_NODES_TO_SYNC) {
        ALL_NODES_TO_SYNC.parallelStream().forEach(node -> {
            final ConfigurationManagement cm = new ConfigurationManagement(true);
            try {
                cm.login();
                final String cliCommand = cm.executeCliCommand(String.format(GET_NODE_MODEL, node.getNetworkElementId()));
                try {
                    final String ossModelIdentity = ((JSONArray) (JSONValue.parse(cliCommand))).stream().filter(o -> o != null && o.toString().contains("ossModelIdentity : ")).collect(Collectors.toList()).get(0).toString().split(" : ")[1];
                    node.setOssModelIdentity(ossModelIdentity);
                    logger.info("Set OssModelIdentity : {}", ossModelIdentity);
                } catch (final Exception e) {
                    logger.warn("Failed to set OssModelIdentity", e);
                }
                logger.info(cliCommand);
            } catch (final Exception e) {
                logger.warn("Failed to updateAllSyncedNodesWithOssModelIdentity", e);
            } finally {
                try {
                    cm.logout();
                } catch (final EnmException e) {
                    logger.warn("Failed to logout in updateAllSyncedNodesWithOssModelIdentity", e);
                }
            }
        });
    }
}
