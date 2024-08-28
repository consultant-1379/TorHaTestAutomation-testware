package com.ericsson.nms.rv.core.node;

import com.ericsson.nms.rv.core.netsimhandler.NodeInfo;

public class Router extends Node {

    public Router(final NodeInfo nodeInfo) {
        super(nodeInfo);
    }

    @Override
    public String getNodeType() {
        return "SpitFire";
    }
}
