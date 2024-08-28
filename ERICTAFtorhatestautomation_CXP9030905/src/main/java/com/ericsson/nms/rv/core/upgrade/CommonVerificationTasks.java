package com.ericsson.nms.rv.core.upgrade;

import java.util.Map;

import com.ericsson.nms.rv.core.HAPropertiesReader;
import com.ericsson.nms.rv.core.amos.AmosLoadGenerator;
import com.ericsson.nms.rv.core.cm.CmLoadGenerator;
import com.ericsson.nms.rv.core.cm.bulk.CmBulkExportLoader;
import com.ericsson.nms.rv.core.cm.bulk.CmImportToLiveLoader;
import com.ericsson.nms.rv.core.fm.FmBscLoadGenerator;
import com.ericsson.nms.rv.core.fm.FmLoadGenerator;
import com.ericsson.nms.rv.core.fm.FmRouterLoadGenerator;
import com.ericsson.nms.rv.core.nbi.NbiLoadGenerator;
import com.ericsson.nms.rv.core.nbi.verifier.NbiAlarmVerifier;
import com.ericsson.nms.rv.core.nbi.verifier.NbiCreateSubsVerifier;
import com.ericsson.nms.rv.core.pm.PmLoadGenerator;
import com.ericsson.nms.rv.core.pm.PmRouterLoadGenerator;
import com.ericsson.nms.rv.core.shm.ShmLoadGenerator;
import com.ericsson.nms.rv.core.shm.SmrsLoadGenerator;
import com.ericsson.nms.rv.core.util.CommonUtils;
import com.ericsson.nms.rv.core.util.FunctionalArea;
import com.ericsson.nms.rv.core.workload.WorkLoadHandler;
import com.ericsson.sut.test.cases.HighAvailabilityTestCase;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class CommonVerificationTasks extends HighAvailabilityTestCase {
    private static final Logger logger = LogManager.getLogger(CommonVerificationTasks.class);
    private static final WorkLoadHandler wlHandler = new WorkLoadHandler();

    static boolean verifyTestCases(final String className, final Object object, final Map<FunctionalArea, Object> loaderObjectMap, final FunctionalArea key, final boolean isCorbaInstalled) {
        switch (className) {
            case HAPropertiesReader.CONFIGURATIONMANGEMENT :
                CmLoadGenerator cmLoadGenerator = (CmLoadGenerator) loaderObjectMap.get(key);
                if (wlHandler.isClashProfileStopped(FunctionalArea.CM) && cmLoadGenerator.areAllNodesAdded() && !cmLoadGenerator.getCmVerificationNodes().isEmpty()) {
                    return true;
                }
                break;
            case HAPropertiesReader.CMBULKEXPORT :
                CmBulkExportLoader cmBulkExportLoader = (CmBulkExportLoader) loaderObjectMap.get(key);
                if (wlHandler.isClashProfileStopped(FunctionalArea.CMBE) && cmBulkExportLoader.areAllNodesAdded() && !cmBulkExportLoader.getCmBulkExportVerificationNodes().isEmpty()) {
                    return true;
                }
                break;
            case HAPropertiesReader.CMBULKIMPORTTOLIVE :
                CmImportToLiveLoader cmImportToLiveLoader = (CmImportToLiveLoader) loaderObjectMap.get(key);
                if (wlHandler.isClashProfileStopped(FunctionalArea.CMBIL) && cmImportToLiveLoader.areAllNodesAdded() && !cmImportToLiveLoader.getCmImportToLiveVerificationNodes().isEmpty()) {
                    return true;
                }
                break;
            case HAPropertiesReader.PERFORMANCEMANAGEMENT :
                PmLoadGenerator pmLoadGenerator = (PmLoadGenerator) loaderObjectMap.get(key);
                if (pmLoadGenerator.areAllNodesAdded() && !pmLoadGenerator.getPmVerificationNodes().isEmpty()) {
                    return true;
                }
                break;
            case HAPropertiesReader.PERFORMANCEMANAGEMENTROUTER :
                PmRouterLoadGenerator pmRouterLoadGenerator = (PmRouterLoadGenerator) loaderObjectMap.get(key);
                if (pmRouterLoadGenerator.areAllNodesAdded() && !pmRouterLoadGenerator.getPmRouterVerificationNodes().isEmpty()) {
                    return true;
                }
                break;
            case HAPropertiesReader.NBIALARMVERIFIER :
                NbiAlarmVerifier nbiAlarmVerifier = (NbiAlarmVerifier) object;
                NbiLoadGenerator nbiLoadGenerator = (NbiLoadGenerator) loaderObjectMap.get(key);
                if (isCorbaInstalled && nbiAlarmVerifier.isInitiated() && nbiLoadGenerator.areAllNodesAdded() && !nbiLoadGenerator.getNbiNodes().isEmpty() && wlHandler.isClashProfileStopped(key)) {
                    return true;
                }
                break;
            case HAPropertiesReader.NBICREATESUBSVERIFIER :
                NbiCreateSubsVerifier nbiCreateSubsVerifier = (NbiCreateSubsVerifier) object;
                NbiLoadGenerator nbiGenerator = (NbiLoadGenerator) loaderObjectMap.get(key);
                if (isCorbaInstalled && nbiCreateSubsVerifier.isInitiated() && nbiGenerator.areAllNodesAdded() && !nbiGenerator.getNbiNodes().isEmpty()) {
                    return true;
                }
                break;
            case HAPropertiesReader.SOFTWAREHARDWAREMANAGEMENT :
                ShmLoadGenerator shmLoadGenerator = (ShmLoadGenerator) loaderObjectMap.get(key);
                if (shmLoadGenerator.areAllNodesAdded() && !shmLoadGenerator.getShmVerificationNodes().isEmpty()) {
                    return true;
                }
                break;
            case HAPropertiesReader.FAULTMANAGEMENT :
                FmLoadGenerator fmLoadGenerator = (FmLoadGenerator) loaderObjectMap.get(key);
                if (fmLoadGenerator.areAllNodesAdded() && !fmLoadGenerator.getFmVerificationNodes().isEmpty()) {
                    return true;
                }
                break;
            case HAPropertiesReader.FAULTMANAGEMENTROUTER :
                FmRouterLoadGenerator fmRouterLoadGenerator = (FmRouterLoadGenerator) loaderObjectMap.get(key);
                if (fmRouterLoadGenerator.areAllNodesAdded() && !fmRouterLoadGenerator.getFmVerificationNodes().isEmpty()) {
                    return true;
                }
                break;
            case HAPropertiesReader.FAULTMANAGEMENTBSC :
                FmBscLoadGenerator fmBscLoadGenerator = (FmBscLoadGenerator) loaderObjectMap.get(key);
                if (fmBscLoadGenerator.areAllNodesAdded() && !fmBscLoadGenerator.getFmbVerificationNodes().isEmpty()) {
                    return true;
                }
                break;
            case HAPropertiesReader.AMOSAPP :
                if (!CommonUtils.predictFunctionalArea(FunctionalArea.AMOSHOST)) {
                    logger.info("AMOS disabled, no SCP blades on this deployment");
                    isScpBladeAvailable = false;
                    return false;
                }
                AmosLoadGenerator amosLoadGenerator = (AmosLoadGenerator) loaderObjectMap.get(key);
                if (wlHandler.isClashProfileStopped(FunctionalArea.AMOS) && amosLoadGenerator.areAllNodesAdded() && !amosLoadGenerator.getAmosVerificationNodes().isEmpty()) {
                    return true;
                }
                break;
            case HAPropertiesReader.APPLAUNCHER :
                if(!(HighAvailabilityTestCase.multiMapErrorCollection.containsKey("AppLauncher"))) {
                    return true;
                }
                break;
            case HAPropertiesReader.NETWORKEXPLORER :
                if (wlHandler.isClashProfileStopped(FunctionalArea.NETEX)) {
                    return true;
                }
                break;
            case HAPropertiesReader.SHMSMRS :
                SmrsLoadGenerator smrsLoadGenerator = (SmrsLoadGenerator) loaderObjectMap.get(key);
                if (smrsLoadGenerator.areAllNodesAdded() && !smrsLoadGenerator.getShmSmrsVerificationNodes().isEmpty()) {
                    return true;
                }
                break;
            case HAPropertiesReader.USERMANAGEMENT :
            case HAPropertiesReader.ENMSYSTEMMONITOR :
            case HAPropertiesReader.SYSTEMVERIFIER :
            case HAPropertiesReader.COMAALDAP :
            case HAPropertiesReader.CNOMSYSTEMMONITOR :
            case HAPropertiesReader.FILEACCESSNBI :
            case HAPropertiesReader.FILELOOKUPSERVICE:
                return true;
            default:
                logger.error("Invalid Test class : {}", className);
        }
        return false;
    }
}
