/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2017
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/

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
public final class PmLoadGenerator {
    /**
     * the nodes used in the PM verification
     */
    private static final List<Node> pmVerificationNodes = LoadGenerator.getAllNodesWithFlag("P");

    public List<Node> getPmVerificationNodes() {
        if(CollectionUtils.isEmpty(pmVerificationNodes))
        {
            HighAvailabilityTestCase.multiMapErrorCollection.put(HAPropertiesReader.appMap.get("pm"), HAPropertiesReader.appMap.get("pm") + ", Insufficient Nodes for PM");
        }
        return pmVerificationNodes;
    }

    public boolean areAllNodesAdded() {
        if(LoadGenerator.verifyAllnodesAreAdded(pmVerificationNodes) == false)
        {
            HighAvailabilityTestCase.multiMapErrorCollection.put(HAPropertiesReader.appMap.get("pm"), HAPropertiesReader.appMap.get("pm") + ", Failed to allocate nodes");
        }
        return LoadGenerator.verifyAllnodesAreAdded(pmVerificationNodes);
    }

}
