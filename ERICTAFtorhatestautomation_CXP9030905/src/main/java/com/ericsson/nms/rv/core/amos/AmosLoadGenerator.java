package com.ericsson.nms.rv.core.amos;

import java.util.List;

import com.ericsson.nms.rv.core.HAPropertiesReader;
import org.apache.commons.collections.CollectionUtils;

import com.ericsson.nms.rv.core.LoadGenerator;
import com.ericsson.nms.rv.core.node.Node;
import com.ericsson.sut.test.cases.HighAvailabilityTestCase;

public class AmosLoadGenerator {

    /**
     * the nodes used in the Amos verification
     */
    private static final List<Node> amosVerificationNodes = LoadGenerator.getAllNodesWithFlag("M");

    public List<Node> getAmosVerificationNodes() {
        if(CollectionUtils.isEmpty(amosVerificationNodes))
        {
            HighAvailabilityTestCase.multiMapErrorCollection.put(HAPropertiesReader.appMap.get("amos"), HAPropertiesReader.appMap.get("amos") + ", Insufficient Nodes for AMOS");
        }
        return amosVerificationNodes;
    }

    public boolean areAllNodesAdded() {
        if(LoadGenerator.verifyAllnodesAreAdded(amosVerificationNodes) == false)
        {
            HighAvailabilityTestCase.multiMapErrorCollection.put(HAPropertiesReader.appMap.get("amos"), HAPropertiesReader.appMap.get("amos") + ", Failed to allocate nodes");
        }
        return LoadGenerator.verifyAllnodesAreAdded(amosVerificationNodes);
    }
}
