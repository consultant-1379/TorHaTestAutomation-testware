package com.ericsson.nms.rv.core.pm;

import java.util.List;

import com.ericsson.nms.rv.core.HAPropertiesReader;
import org.apache.commons.collections.CollectionUtils;

import com.ericsson.nms.rv.core.LoadGenerator;
import com.ericsson.nms.rv.core.node.Node;
import com.ericsson.sut.test.cases.HighAvailabilityTestCase;

/**
 * {@code PmLoadGenerator} generates PM load.
 */
public final class PmRouterLoadGenerator {
    /**
     * the nodes used in the PM verification
     */
    private static final List<Node> pmRouterVerificationNodes = LoadGenerator.getAllNodesWithFlag("U");

    public List<Node> getPmRouterVerificationNodes() {
        if(CollectionUtils.isEmpty(pmRouterVerificationNodes))
        {
            HighAvailabilityTestCase.multiMapErrorCollection.put(HAPropertiesReader.appMap.get("pmr"), HAPropertiesReader.appMap.get("pmr") + ", Insufficient Nodes for PMROUTER");
        }
        return pmRouterVerificationNodes;
    }

    public boolean areAllNodesAdded() {
        if(LoadGenerator.verifyAllnodesAreAdded(pmRouterVerificationNodes) == false)
        {
            HighAvailabilityTestCase.multiMapErrorCollection.put(HAPropertiesReader.appMap.get("pmr"), HAPropertiesReader.appMap.get("pmr") + ", Failed to allocate nodes for PMROUTER");
        }
        return LoadGenerator.verifyAllnodesAreAdded(pmRouterVerificationNodes);
    }

}
