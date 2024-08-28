package com.ericsson.nms.rv.core.shm;

import java.util.List;

import com.ericsson.nms.rv.core.HAPropertiesReader;
import org.apache.commons.collections.CollectionUtils;

import com.ericsson.nms.rv.core.LoadGenerator;
import com.ericsson.nms.rv.core.node.Node;
import com.ericsson.sut.test.cases.HighAvailabilityTestCase;

/**
 * {@link ShmLoadGenerator} generates SHM load.
 */
public class SmrsLoadGenerator {
    /**
     * the nodes used in the SHM verification
     */
    private static final List<Node> smrsVerificationNodes = LoadGenerator.getAllNodesWithFlag("G");

    public List<Node> getShmSmrsVerificationNodes() {
        if(CollectionUtils.isEmpty(smrsVerificationNodes))
        {
            HighAvailabilityTestCase.multiMapErrorCollection.put(HAPropertiesReader.appMap.get("smrs"),  HAPropertiesReader.appMap.get("smrs") + ", Insufficient Nodes for SMRS");
        }
        return smrsVerificationNodes;
    }

    public boolean areAllNodesAdded() {
        if(!LoadGenerator.verifyAllnodesAreAdded(smrsVerificationNodes))
        {
            HighAvailabilityTestCase.multiMapErrorCollection.put(HAPropertiesReader.appMap.get("smrs"), HAPropertiesReader.appMap.get("smrs") + ", Failed to allocate nodes");
        }
        return LoadGenerator.verifyAllnodesAreAdded(smrsVerificationNodes);
    }
}
