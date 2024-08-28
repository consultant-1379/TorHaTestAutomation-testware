package com.ericsson.nms.rv.core.cm.bulk;

import java.util.List;

import com.ericsson.nms.rv.core.HAPropertiesReader;
import org.apache.commons.collections.CollectionUtils;

import com.ericsson.nms.rv.core.LoadGenerator;
import com.ericsson.nms.rv.core.node.Node;
import com.ericsson.sut.test.cases.HighAvailabilityTestCase;

public class CmBulkExportLoader {
    /**
     * the nodes used in the CM Bulk Export verification
     */
    private static final List<Node> cmBulkExportVerificationNodes = LoadGenerator.getAllNodesWithFlag("E");

    public List<Node> getCmBulkExportVerificationNodes() {

        if(CollectionUtils.isEmpty(cmBulkExportVerificationNodes)) {
            HighAvailabilityTestCase.multiMapErrorCollection.put(HAPropertiesReader.appMap.get("cmbe"), HAPropertiesReader.appMap.get("cmbe") + ", Insufficient Nodes for CMBE");
        }
        return cmBulkExportVerificationNodes;
    }

    public boolean areAllNodesAdded() {
        if(LoadGenerator.verifyAllnodesAreAdded(cmBulkExportVerificationNodes) == false) {
            HighAvailabilityTestCase.multiMapErrorCollection.put(HAPropertiesReader.appMap.get("cmbe"), HAPropertiesReader.appMap.get("cmbe") + ", Failed to allocate nodes");
        }
        return LoadGenerator.verifyAllnodesAreAdded(cmBulkExportVerificationNodes);
    }

}
