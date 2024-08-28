package com.ericsson.nms.rv.core.fm;

import java.util.List;

import com.ericsson.nms.rv.core.HAPropertiesReader;
import org.apache.commons.collections.CollectionUtils;

import com.ericsson.nms.rv.core.LoadGenerator;
import com.ericsson.nms.rv.core.node.Node;
import com.ericsson.sut.test.cases.HighAvailabilityTestCase;

public class FmRouterLoadGenerator {
    /**
     * the nodes used in the FMR verification
     */
    private static final List<Node> fmVerificationNodes = LoadGenerator.getAllNodesWithFlag("R");

    public List<Node> getFmVerificationNodes() {
        if(CollectionUtils.isEmpty(fmVerificationNodes))
        {
            HighAvailabilityTestCase.multiMapErrorCollection.put(HAPropertiesReader.appMap.get("fmr"), HAPropertiesReader.appMap.get("fmr") + ", Insufficient Nodes for FM Router.");
        }
        return fmVerificationNodes;
    }

    public boolean areAllNodesAdded() {
        if(LoadGenerator.verifyAllnodesAreAdded(fmVerificationNodes) == false)
        {
            HighAvailabilityTestCase.multiMapErrorCollection.put(HAPropertiesReader.appMap.get("fmr"), HAPropertiesReader.appMap.get("fmr") + ", Failed to allocate nodes for FM Router.");
        }
        return LoadGenerator.verifyAllnodesAreAdded(fmVerificationNodes);
    }
}
