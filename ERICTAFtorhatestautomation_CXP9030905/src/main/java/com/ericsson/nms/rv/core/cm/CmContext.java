package com.ericsson.nms.rv.core.cm;

import static com.ericsson.nms.rv.taf.tools.Constants.EMPTY_STRING;

import java.util.List;

import com.ericsson.nms.rv.core.EnmApplication;
import com.ericsson.nms.rv.core.node.Node;

public class CmContext {

    private final EnmApplication enmApplication;
    private final List<Node> cmNodes;
    private final int numberOfNodesOnSync;
    private String fdn = EMPTY_STRING;
    private String userLabel = EMPTY_STRING;

    public CmContext(final EnmApplication enmApplication, final List<Node> cmNodes, final int nodesSynch) {
        this.enmApplication = enmApplication;
        this.cmNodes = cmNodes;
        this.numberOfNodesOnSync = nodesSynch;
    }

    public EnmApplication getEnmApplication() {
        return enmApplication;
    }

    public List<Node> getCmNodes() {
        return cmNodes;
    }

    public int getNumberOfNodesOnSync() {
        return numberOfNodesOnSync;
    }

    public String getFdn() {
        return fdn;
    }

    public void setFdn(final String fdn) {
        this.fdn = fdn;
    }

    public String getUserLabel() {
        return userLabel;
    }

    public void setUserLabel(final String userLabel) {
        this.userLabel = userLabel;
    }
}
