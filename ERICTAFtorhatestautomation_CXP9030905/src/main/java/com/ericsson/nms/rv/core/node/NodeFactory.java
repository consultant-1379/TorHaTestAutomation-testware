package com.ericsson.nms.rv.core.node;

import com.ericsson.nms.rv.core.netsimhandler.NodeInfo;
import com.ericsson.nms.rv.core.netsimhandler.NodeType;

/**
 * Factory for {@link Node}.
 */

/**
 * Factory for {@link Node}.
 */
public final class NodeFactory {
    private NodeFactory() {
    }

    /**
     * Returns an initialised {@code Node} of the given {@code nodeType}.
     *
     * @param nodeInfo the {@link NodeInfo} on which the given simulation runs
     * @return a {@code Node} of the given {@code nodeType}
     * @throws IllegalArgumentException if {@code nodeType} was not recognised or tokens' length is invalid
     */
    public static Node create(final NodeInfo nodeInfo) {

        if ("ERBS".equals(nodeInfo.getNeType())) {
            return new ErbsNode(nodeInfo);
        } else if ("SpitFire".equals(nodeInfo.getNeType())) {
            return new Router(nodeInfo);
        } else if ("BSC".equals(nodeInfo.getNeType())) {
            return new BSC(nodeInfo);
        } else if (NodeType.RADIO.getType().equals(nodeInfo.getNeType())) {
            return new RadioNode(nodeInfo);
        } else {
            throw new IllegalArgumentException("Unrecognized node type: " + nodeInfo.getNeType());
        }
    }

}