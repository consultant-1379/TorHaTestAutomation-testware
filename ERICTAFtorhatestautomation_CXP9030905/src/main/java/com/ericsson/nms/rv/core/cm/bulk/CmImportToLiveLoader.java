package com.ericsson.nms.rv.core.cm.bulk;

import java.util.List;

import com.ericsson.nms.rv.core.HAPropertiesReader;
import org.apache.commons.collections.CollectionUtils;

import com.ericsson.nms.rv.core.LoadGenerator;
import com.ericsson.nms.rv.core.node.Node;
import com.ericsson.sut.test.cases.HighAvailabilityTestCase;

public class CmImportToLiveLoader {
    /**
     * the nodes used in the CM Bulk Import verification
     */
    private static final List<Node> cmImportToLiveVerificationNodes = LoadGenerator.getAllNodesWithFlag("L");

    public List<Node> getCmImportToLiveVerificationNodes() {
        if(CollectionUtils.isEmpty(cmImportToLiveVerificationNodes))
        {
            HighAvailabilityTestCase.multiMapErrorCollection.put(HAPropertiesReader.appMap.get("cmbil"), HAPropertiesReader.appMap.get("cmbil") + ", Insufficient Nodes for CMBIL");
        }
        return cmImportToLiveVerificationNodes;
    }

    public boolean areAllNodesAdded() {
        if(LoadGenerator.verifyAllnodesAreAdded(cmImportToLiveVerificationNodes) == false)
        {
            HighAvailabilityTestCase.multiMapErrorCollection.put(HAPropertiesReader.appMap.get("cmbil"), HAPropertiesReader.appMap.get("cmbil") +", Failed to allocate nodes");
        }
        return LoadGenerator.verifyAllnodesAreAdded(cmImportToLiveVerificationNodes);
    }
}
