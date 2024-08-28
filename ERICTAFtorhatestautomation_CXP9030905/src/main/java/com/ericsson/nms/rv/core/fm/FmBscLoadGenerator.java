package com.ericsson.nms.rv.core.fm;

import java.util.List;

import com.ericsson.nms.rv.core.HAPropertiesReader;
import org.apache.commons.collections.CollectionUtils;

import com.ericsson.nms.rv.core.LoadGenerator;
import com.ericsson.nms.rv.core.node.Node;
import com.ericsson.sut.test.cases.HighAvailabilityTestCase;

public class FmBscLoadGenerator {
    /**
     * the nodes used in the FMB verification
     */
    private static final List<Node> fmVerificationNodes = LoadGenerator.getAllNodesWithFlag("B");

    public List<Node> getFmbVerificationNodes() {
        if(CollectionUtils.isEmpty(fmVerificationNodes))
        {
            HighAvailabilityTestCase.multiMapErrorCollection.put(HAPropertiesReader.appMap.get("fmb"), HAPropertiesReader.appMap.get("fmb") + ", Insufficient Nodes for FM BSC.");
        }
        return fmVerificationNodes;
    }

    public boolean areAllNodesAdded() {
        if(LoadGenerator.verifyAllnodesAreAdded(fmVerificationNodes) == false)
        {
            HighAvailabilityTestCase.multiMapErrorCollection.put(HAPropertiesReader.appMap.get("fmb"), HAPropertiesReader.appMap.get("fmb") + ", Failed to allocate nodes for FM BSC.");
        }
        return LoadGenerator.verifyAllnodesAreAdded(fmVerificationNodes);
    }
}
