package com.ericsson.nms.rv.core.netsimhandler;

import static com.ericsson.nms.rv.core.netsimhandler.DefaultNetsimRespoProvider.netsimWorkloadNodeMap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CopyOnWriteArrayList;

import com.ericsson.nms.rv.core.util.HaTimeoutException;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.ericsson.nms.rv.core.EnmApplication;
import com.ericsson.nms.rv.core.cm.CmFullSync;
import com.ericsson.nms.rv.core.netsimhandler.iterator.NetsimIterator;
import com.ericsson.nms.rv.core.netsimhandler.iterator.NetsimRepository;
import com.ericsson.nms.rv.core.node.Node;
import com.ericsson.nms.rv.core.node.NodeFactory;
import com.ericsson.oss.testware.availability.common.exception.EnmException;

/**
 * Service which creates a list of nodes.
 */
public class HaNetsimService {
    private static final Logger logger = LogManager.getLogger(HaNetsimService.class);
    private static Map<String, NetsimRepository> netsimRepositoryMap = new HashMap<>();
    private final Queue<Node> nodesSynchedQueue = new LinkedList<>();
    private final Queue<Node> nodesAddedQueue = new LinkedList<>();
    private final INetsimRepository netsimRepositoryProvider;

    HaNetsimService(final INetsimRepository netsimRepository) {
        netsimRepositoryProvider = netsimRepository;
    }

    public static NetsimIterator getNetsimIterator(final String nodeType) {
        return netsimRepositoryMap.get(nodeType).getIterator();
    }

    public static Node getNextWorkloadNode(final NetsimIterator netsimIterator) {
        Node node = null;
        try {
            final NodeInfo nodeInfo = netsimIterator.next();
            if (nodeInfo != null) {
                logger.info("netsimWorkloadNodeMap -- nbiva workload node : {}", netsimWorkloadNodeMap.get(nodeInfo.getNetworkElementId()));
                nodeInfo.setNetworkElementId(netsimWorkloadNodeMap.get(nodeInfo.getNetworkElementId()));
                node = NodeFactory.create(nodeInfo);
            }
        } catch (final Exception e) {
            logger.error("Node could not be added trying to add next node.", e);
        }
        return node;
    }

    public static String netsimWorkloadNodeMap(String neName) {
        return netsimWorkloadNodeMap.get(neName);
    }

    /**
     * returns the netsim repository by node type.
     */
    private NetsimRepository getNetsimRepository(final String nodeType, final List<String> list, final NodeInfo nodeInfo) throws HaNetsimHandlerException {
        if (netsimRepositoryMap.get(nodeType) == null) {
            netsimRepositoryMap.put(nodeType, netsimRepositoryProvider.getNetsimRepository(nodeType, list, nodeInfo));
        }
        logger.info("netsimRepositoryMap : {}", netsimRepositoryMap);
        return netsimRepositoryMap.get(nodeType);
    }

    /**
     * Creates a list of nodes by type.
     *
     * @param nodeType node type.
     * @param list     list of Nodes to add
     * @param nodeInfo node info
     * @param amount   amount of nodes to be created
     * @return list of created Nodes
     * @throws HaNetsimHandlerException
     */
    public List<Node> createNnodesByType(final String nodeType, final List<String> list, final NodeInfo nodeInfo, final int amount) throws HaNetsimHandlerException {
        logger.info("amount of {} nodes to be created: {}",nodeType, amount);

        final NetsimIterator netsimIterator = getNetsimRepository(nodeType, list, nodeInfo).getIterator();
        if (nodeInfo.isSyncNode()) {
            fillListWithSynchedNodes(netsimIterator, amount);
        } else {
            fillListWithNodes(netsimIterator, amount);
        }

        final String flags = nodeInfo.getFlags();
        boolean empty = false;
        final List<Node> nodeList = new ArrayList<>();
        while (nodeList.size() < amount && !empty) {
            if (nodeInfo.isSyncNode()) {
                if (!nodesSynchedQueue.isEmpty()) {
                    final Node nodeSynched = nodesSynchedQueue.poll();
                    if(nodeSynched.getNodeType().equalsIgnoreCase(nodeType)) {
                        nodeSynched.setFlags(flags);
                        nodeList.add(nodeSynched);
                    }
                } else {
                    empty = true;
                }
            } else {
                if (!nodesAddedQueue.isEmpty()) {
                    final Node nodeAdded = nodesAddedQueue.poll();
                    if(nodeAdded.getNodeType().equalsIgnoreCase(nodeType)) {
                        nodeAdded.setFlags(flags);
                        nodeList.add(nodeAdded);
                    }
                } else {
                    empty = true;
                }
            }
        }

        logger.info("amount of nodes created: {}, amount asked to create: {}, list of nodes created [{}]", nodeList.size(), amount, EnmApplication.getUsedNodes(nodeList));

        if (nodeList.size() == amount) {
            return nodeList;
        } else {
            throw new HaNetsimHandlerException("There are not enough nodes to execute the test case.");
        }
    }

    private List<Node> createNodesList(final NetsimIterator netsimIterator, final int amount) {
        final List<Node> nodeList = new ArrayList<>();
        if (netsimIterator.hasNext()) {
            do {
                final NodeInfo nodeInfo = netsimIterator.next();
                if (nodeInfo != null) {
                    logger.info("netsimWorkloadNodeMap -- workload node : {}", netsimWorkloadNodeMap.get(nodeInfo.getNetworkElementId()));
                    nodeInfo.setNetworkElementId(netsimWorkloadNodeMap.get(nodeInfo.getNetworkElementId()));
                    nodeList.add(NodeFactory.create(nodeInfo));
                }
            } while (netsimIterator.hasNext() && amount != nodeList.size());
        }
        return nodeList;
    }

    private void fillListWithSynchedNodes(final NetsimIterator netsimIterator, final int amount) {
        final List<Node> nodesSynched = new ArrayList<>();
        while (nodesSynched.size() < amount && netsimIterator.hasNext()) {
            final List<Node> nodesToSync = createNodesList(netsimIterator, amount);
             nodesSynched.addAll(syncWorkloadNodes(nodesToSync));
        }
        nodesSynchedQueue.addAll(nodesSynched);
        if (nodesSynched.size() < amount) {
            logger.error("there are not enough nodes which can be synched, synched nodes {} from nodes needed {} ", nodesSynched.size(), amount);
        }
    }

    private void fillListWithNodes(final NetsimIterator netsimIterator, final int amount) {
        final List<Node> nodesAdded = new ArrayList<>();
        while (nodesAdded.size() < amount && netsimIterator.hasNext()) {
            final List<Node> nodesToNotSync = createNodesList(netsimIterator, amount);
            nodesAdded.addAll(addWorkloadNodes(nodesToNotSync));
        }
        nodesAddedQueue.addAll(nodesAdded);
        if (nodesAdded.size() < amount) {
            logger.error("there are not enough nodes which can be added, added nodes {} from nodes needed {} ", nodesAdded.size(), amount);
        }
    }

    private List<Node> syncWorkloadNodes(final List<Node> nodesToSync) {
        final List<Node> synchedNodes = new CopyOnWriteArrayList<>();
        nodesToSync.parallelStream().forEach(node -> {
            final CmFullSync cmFullSync = new CmFullSync();
            try {
                cmFullSync.cmSyncNode(node.getNetworkElementId());
                if (cmFullSync.waitForStatusToComplete(node.getNetworkElementId())) {
                    logger.info("node {} could be synched, adding to the list", node.getNetworkElementId());
                    node.setIsSynchDone(true);
                    node.setIsNodeAdded(true);
                    node.setVerified();
                    synchedNodes.add(node);
                } else {
                    logger.error("node {} could NOT be synched, trying another one", node.getNetworkElementId());
                }
            } catch (final Exception e) {
                logger.error("error trying to synch the node {} could NOT be synched, trying another one.", node.getNetworkElementId(), e);
            }
        });
        return synchedNodes;
    }

    private List<Node> addWorkloadNodes(final List<Node> nodesToNotSync) {
        final List<Node> addedNodes = new CopyOnWriteArrayList<>();
        nodesToNotSync.parallelStream().forEach(node -> {
            try {
                node.setIsNodeAdded(true);
                node.setIsSynchDone(true);
                node.setVerified();
                logger.info("node {} could be added, adding to the list", node.getNetworkElementId());
                addedNodes.add(node);
            } catch (final Exception e) {
                logger.error("node {} could NOT be added, trying another one.", node.getNetworkElementId(), e);
            }
        });
        return addedNodes;
    }
}
