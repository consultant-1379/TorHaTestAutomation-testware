package com.ericsson.nms.rv.core.node;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.ericsson.cifwk.taf.handlers.netsim.domain.NetworkElement;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.ericsson.nms.rv.core.HAPropertiesReader;
import com.ericsson.nms.rv.core.netsimhandler.HaNetsimHandlerException;
import com.ericsson.nms.rv.core.netsimhandler.HaNetsimHandlerFactory;
import com.ericsson.nms.rv.core.netsimhandler.HaNetsimService;
import com.ericsson.nms.rv.core.netsimhandler.NodeInfo;
import com.ericsson.nms.rv.core.netsimhandler.NodeType;


/**
 * Helper class for parsing node from simulation files.
 */
public final class NodeParser {

    private static final int AMOUNT = 0;
    private static final int NODE_TYPE = AMOUNT + 1;
    private static final int OSS_MODEL_IDENTITY = NODE_TYPE + 1;
    private static final int FLAGS = OSS_MODEL_IDENTITY + 1;
    private static final int IS_SYNC_NEEDED = FLAGS + 1;

    private static final Logger logger = LogManager.getLogger(NodeParser.class);
    private static final String WRONG_NODE_CONFIGURATION = "Wrong node configuration: %s: ";

    /**
     * Parse netsim configuration for workload nodes
     * @param nodesFromWorkload
     * @param netSimsFromWorkload
     * @return
     */
    public List<Node> parseNetsimConfiguration(final List<String> nodesFromWorkload, final List<String> netSimsFromWorkload) {
        final List<Node> nodes = new ArrayList<>();
        final List<String> coreNodes = new ArrayList<>();
        final List<String> allNetsimHosts = new ArrayList<>();
        boolean radioNodesAvailable = false;
        boolean erbsNodesAvailable = false;

        if (!netSimsFromWorkload.isEmpty()) {
            for (final String node : nodesFromWorkload) {
                if (!node.contains("ieatnetsim")) {
                    coreNodes.add(node);
                }
            }
            if (!coreNodes.isEmpty()) {
                allNetsimHosts.addAll(getNetsimHosts(coreNodes));
                logger.info("CORE Netsims Hosts : {}", allNetsimHosts);
                allNetsimHosts.addAll(netSimsFromWorkload);
            }
        } else {
            allNetsimHosts.addAll(getNetsimHosts(nodesFromWorkload));
        }
        logger.info("All Workload Hosts : {}", allNetsimHosts);

        if (!allNetsimHosts.isEmpty()) {
            String configuration;
            final HaNetsimService haNetsimService = createHaNetsimService(allNetsimHosts);
            final List<NetworkElement> radioList = HaNetsimHandlerFactory.getNeListCache().get(NodeType.RADIO.getType());
            final List<NetworkElement> erbsList = HaNetsimHandlerFactory.getNeListCache().get(NodeType.LTE.getType());
            if (radioList != null && !radioList.isEmpty()) {
                logger.info("RadioNodes are Available !");
                radioNodesAvailable = true;
            }
            if (erbsList != null && !erbsList.isEmpty()) {
                logger.info("ERBS nodes are Available !");
                erbsNodesAvailable = true;
            }

            if (erbsNodesAvailable) {
                configuration = HAPropertiesReader.getNetsimConfig();
            } else if (radioNodesAvailable) {
                configuration = HAPropertiesReader.getRadioNetsimConfig();
            } else {
                configuration = HAPropertiesReader.getNetsimConfig();
            }
            final List<String> nodesByFlags = Arrays.asList(configuration.split(";"));

            logger.info("nodes to be parsed: {}", nodesByFlags);
            for (final String node : nodesByFlags) {
                try {
                    final String[] parameters = node.split(",(?=([^\"]*\"[^\"]*\")*[^\"]*$)", -1);
                    final NodeInfo nodeInfo = createNodeInfoFromFile(parameters);

                    nodeInfo.setSyncNode(null != parameters[IS_SYNC_NEEDED] && "y".equalsIgnoreCase(parameters[IS_SYNC_NEEDED]));

                    logger.info("creating {} nodes of type: {} for appFlag: {} with syncStatus: '{}'", parameters[AMOUNT], parameters[NODE_TYPE], parameters[FLAGS], parameters[IS_SYNC_NEEDED]);
                    nodes.addAll(haNetsimService.createNnodesByType(
                            getNodeTypeFromString(parameters[NODE_TYPE]),
                            nodesFromWorkload,
                            nodeInfo,
                            Integer.parseInt(parameters[AMOUNT])));
                } catch (final HaNetsimHandlerException e) {
                    logger.warn(String.format(WRONG_NODE_CONFIGURATION, node), e);
                }
            }
        } else {
            final String msg = String.format(WRONG_NODE_CONFIGURATION, nodesFromWorkload);
            logger.warn(msg);
        }
        return nodes;
    }

    /**
     * Returns the NodeType from the configuration
     *
     * @return The NodeType
     */
    private String getNodeTypeFromString(final String flag) {
        if(flag.equalsIgnoreCase("Router")) {
            return NodeType.CORE.getType();
        } else if(flag.equalsIgnoreCase("BSC")) {
            return NodeType.BSC.getType();
        } else if(flag.equalsIgnoreCase("RadioNode")) {
            return NodeType.RADIO.getType();
        } else {
            return NodeType.LTE.getType();
        }
    }

    /**
     * Creates the NodeInfo object from the configuration.
     *
     * @param parameters the parameters of the configuration
     * @return The NodeInfo
     */
    private NodeInfo createNodeInfoFromFile(final String[] parameters) {
        final NodeInfo nodeInfo = new NodeInfo(null);
        nodeInfo.setOssModelIdentity(parameters[OSS_MODEL_IDENTITY]);
        nodeInfo.setFlags(parameters[FLAGS]);
        return nodeInfo;
    }

    private List<String> getNetsimHosts(final List<String> nodes) {
        return HaNetsimHandlerFactory.getNetsimHosts(nodes);
    }

    private HaNetsimService createHaNetsimService(final List<String> netSimsFromWorkload) {
        return HaNetsimHandlerFactory.createDefaultHaNetsimService(netSimsFromWorkload);
    }

}
