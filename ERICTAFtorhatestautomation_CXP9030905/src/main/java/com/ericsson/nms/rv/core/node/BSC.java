package com.ericsson.nms.rv.core.node;

import com.ericsson.nms.rv.core.netsimhandler.NodeInfo;

public class BSC extends Node {

    public BSC(final NodeInfo nodeInfo) {
        super(nodeInfo);
    }

    @Override
    public String getNodeType() {
        return "BSC";
    }
}
