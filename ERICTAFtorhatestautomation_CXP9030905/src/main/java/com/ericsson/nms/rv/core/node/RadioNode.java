package com.ericsson.nms.rv.core.node;

import com.ericsson.nms.rv.core.netsimhandler.NodeInfo;
import com.ericsson.nms.rv.core.netsimhandler.NodeType;

public class RadioNode extends Node {

    public RadioNode(final NodeInfo nodeInfo) {
        super(nodeInfo);
    }

    @Override
    public String getNodeType() {
        return NodeType.RADIO.getType();
    }

}
