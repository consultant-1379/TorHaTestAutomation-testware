package com.ericsson.nms.rv.core.netsimhandler;

/**
 * The types of nodes supported
 */
public enum NodeType {

    LTE("LTE", "ERBS"),
    CORE("CORE", "SpitFire"),
    BSC ("BSC", "BSC"),
    RADIO ("RadioNode", "MSRBS-V2");

    private final String name;
    private final String type;

    NodeType(final String name, final String type) {
        this.name = name;
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

}
