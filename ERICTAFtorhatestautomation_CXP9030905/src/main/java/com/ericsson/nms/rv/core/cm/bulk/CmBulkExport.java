/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2018
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/
package com.ericsson.nms.rv.core.cm.bulk;

import java.util.ArrayList;
import java.util.List;
import java.util.Date;
import java.util.NavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.stream.Collectors;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.ericsson.nms.rv.core.EnmApplication;
import com.ericsson.nms.rv.core.node.Node;
import com.ericsson.nms.rv.core.system.SystemStatus;
import com.ericsson.nms.rv.taf.tools.Constants;


public class CmBulkExport extends EnmApplication implements CmBulk {

    private static final Logger logger = LogManager.getLogger(CmBulkExport.class);
    private final List<String> cmBulkExportVerificationNodes = new ArrayList<>();
    private final boolean readyToVerify;
    private final CmBulkExportLoader cmBulkExportLoader = new CmBulkExportLoader();
    public static final String CMBE_BUSY = "8027";
    public static NavigableMap<Date, Date> cmbeIgnoreDTMap = new ConcurrentSkipListMap();

    public CmBulkExport(final SystemStatus systemStatus) {
        initDownTimerHandler(systemStatus);
        initFirstTimeFail();

        final List<Node> exportVerificationNodes = cmBulkExportLoader.getCmBulkExportVerificationNodes();
        if (!exportVerificationNodes.isEmpty()) {
            cmBulkExportVerificationNodes.addAll(exportVerificationNodes.stream().map(Node::getNetworkElementId).collect(Collectors.toList()));
        }

        logger.info("CM Bulk Export Using nodes {}", getUsedNodes(exportVerificationNodes));
        readyToVerify = cmBulkExportVerificationNodes.size() > 2;
    }

    @Override
    public void prepare() {
        //Do nothing!
    }

    @Override
    public void cleanup() {
        //Do nothing!
    }

    @Override
    public void verify() {
        if (readyToVerify) {
            logger.info("CM Bulk Export Verifier Running");
            long startTime = System.nanoTime();
            CmBulkExportThreadExecutor CmbeThread = new CmBulkExportThreadExecutor(this,getHttpRestServiceClient(),cmBulkExportVerificationNodes);
            CmbeThread.execute();
            do {
                this.sleep(1L);
                if (CmbeThread.isCompleted() || CmbeThread.isErrorOcurred() || CmbeThread.isTimedOut() || CmbeThread.isBusy()) {
                    break;
                }
            }  while ((System.nanoTime() - startTime  < 120L * Constants.TEN_EXP_9));

            if (CmbeThread.isErrorOcurred()) {
                logger.warn("Thread id : {} Error occurred in CMBE",CmbeThread.getID());
            } else if (CmbeThread.isTimedOut()) {
                logger.warn("Thread id : {} CMBE Timed out.",CmbeThread.getID());
            } else if (CmbeThread.isCompleted()) {
                logger.info("Thread id : {} CMBE job completed!", CmbeThread.getID());
            } else if (CmbeThread.isBusy()) {
                logger.info("Thread id : {} CMBE Busy!", CmbeThread.getID());
            } else {
                logger.info("Thread id : {} CMBE taking time to job complete!", CmbeThread.getID());
            }
        } else {
            logger.warn("Nothing to verify, the list of CmBulkExport nodes is empty.");
        }
    }
}
