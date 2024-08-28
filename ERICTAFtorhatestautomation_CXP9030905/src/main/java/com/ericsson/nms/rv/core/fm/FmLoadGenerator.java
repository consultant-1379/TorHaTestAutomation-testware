package com.ericsson.nms.rv.core.fm;

import java.util.List;

import com.ericsson.nms.rv.core.HAPropertiesReader;
import org.apache.commons.collections.CollectionUtils;

import com.ericsson.nms.rv.core.LoadGenerator;
import com.ericsson.nms.rv.core.node.Node;
import com.ericsson.sut.test.cases.HighAvailabilityTestCase;

public class FmLoadGenerator {
    /**
     * the nodes used in the FM verification
     */
    private static final List<Node> fmVerificationNodes = LoadGenerator.getAllNodesWithFlag("F");

    public List<Node> getFmVerificationNodes() {
        if(CollectionUtils.isEmpty(fmVerificationNodes))
        {
            HighAvailabilityTestCase.multiMapErrorCollection.put(HAPropertiesReader.appMap.get("fm"), HAPropertiesReader.appMap.get("fm") + ", Insufficient Nodes for FM");
        }
        return fmVerificationNodes;
    }

    public boolean areAllNodesAdded() {
        if(LoadGenerator.verifyAllnodesAreAdded(fmVerificationNodes) == false)
        {
            HighAvailabilityTestCase.multiMapErrorCollection.put(HAPropertiesReader.appMap.get("fm"), HAPropertiesReader.appMap.get("fm") + ", Failed to allocate nodes");
        }
        return LoadGenerator.verifyAllnodesAreAdded(fmVerificationNodes);
    }
}
