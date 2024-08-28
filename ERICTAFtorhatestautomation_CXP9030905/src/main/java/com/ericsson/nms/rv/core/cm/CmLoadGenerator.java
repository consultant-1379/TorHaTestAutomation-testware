package com.ericsson.nms.rv.core.cm;

import java.util.List;

import com.ericsson.nms.rv.core.HAPropertiesReader;
import org.apache.commons.collections.CollectionUtils;

import com.ericsson.nms.rv.core.LoadGenerator;
import com.ericsson.nms.rv.core.node.Node;
import com.ericsson.sut.test.cases.HighAvailabilityTestCase;

/**
 * {@code CmLoadGenerator} generates CM load.
 */
public class CmLoadGenerator {
    /**
     * the nodes used in the CM verification
     */
    private static final List<Node> cmVerificationNodes = LoadGenerator.getAllNodesWithFlag("T");

    public List<Node> getCmVerificationNodes() {
        if(CollectionUtils.isEmpty(cmVerificationNodes))
        {
            HighAvailabilityTestCase.multiMapErrorCollection.put(HAPropertiesReader.appMap.get("cm"), HAPropertiesReader.appMap.get("cm") +", Insufficient Nodes for CM");
        }
        return cmVerificationNodes;
    }

    public boolean areAllNodesAdded() {
        if(LoadGenerator.verifyAllnodesAreAdded(cmVerificationNodes) == false)
        {
            HighAvailabilityTestCase.multiMapErrorCollection.put(HAPropertiesReader.appMap.get("cm"), HAPropertiesReader.appMap.get("cm") + ", Failed to allocate nodes");
        }
        return LoadGenerator.verifyAllnodesAreAdded(cmVerificationNodes);
    }
}
