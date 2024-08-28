package com.ericsson.nms.rv.core.nbi;

import java.util.List;

import com.ericsson.nms.rv.core.HAPropertiesReader;
import org.apache.commons.collections.CollectionUtils;

import com.ericsson.nms.rv.core.LoadGenerator;
import com.ericsson.nms.rv.core.node.Node;
import com.ericsson.sut.test.cases.HighAvailabilityTestCase;

public class NbiLoadGenerator {
    /**
     * the nodes used in the FM-NBI verification
     */
    private static final List<Node> nbiVerificationNodes = LoadGenerator.getAllNodesWithFlag("N");

    public List<Node> getNbiNodes() {
        if(CollectionUtils.isEmpty(nbiVerificationNodes))
        {
            HighAvailabilityTestCase.multiMapErrorCollection.put(HAPropertiesReader.appMap.get("nbiva"), HAPropertiesReader.appMap.get("nbiva") +", Insufficient Nodes for NBIVA");
            HighAvailabilityTestCase.multiMapErrorCollection.put(HAPropertiesReader.appMap.get("nbivs"), HAPropertiesReader.appMap.get("nbivs") + ", Insufficient Nodes for NBIVS");
        }
        return nbiVerificationNodes;
    }

    public boolean areAllNodesAdded() {
        if(LoadGenerator.verifyAllnodesAreAdded(nbiVerificationNodes) == false)
        {
            HighAvailabilityTestCase.multiMapErrorCollection.put(HAPropertiesReader.appMap.get("nbiva"), HAPropertiesReader.appMap.get("nbiva") + ", Failed to allocate nodes");
            HighAvailabilityTestCase.multiMapErrorCollection.put(HAPropertiesReader.appMap.get("nbivs"), HAPropertiesReader.appMap.get("nbivs") + ", Failed to allocate nodes");
        }
        return LoadGenerator.verifyAllnodesAreAdded(nbiVerificationNodes);
    }
}
