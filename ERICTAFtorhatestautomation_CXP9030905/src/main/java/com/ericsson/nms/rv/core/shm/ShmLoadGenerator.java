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
public class ShmLoadGenerator {
    /**
     * the nodes used in the SHM verification
     */
    private static final List<Node> shmVerificationNodes = LoadGenerator.getAllNodesWithFlag("S");

    public List<Node> getShmVerificationNodes() {
        if(CollectionUtils.isEmpty(shmVerificationNodes))
        {
            HighAvailabilityTestCase.multiMapErrorCollection.put(HAPropertiesReader.appMap.get("shm"),  HAPropertiesReader.appMap.get("shm") + ", Insufficient Nodes for SHM");
        }
        return shmVerificationNodes;
    }

    public boolean areAllNodesAdded() {
        if(LoadGenerator.verifyAllnodesAreAdded(shmVerificationNodes) == false)
        {
            HighAvailabilityTestCase.multiMapErrorCollection.put(HAPropertiesReader.appMap.get("shm"), HAPropertiesReader.appMap.get("shm") + ", Failed to allocate nodes");
        }
        return LoadGenerator.verifyAllnodesAreAdded(shmVerificationNodes);
    }
}
