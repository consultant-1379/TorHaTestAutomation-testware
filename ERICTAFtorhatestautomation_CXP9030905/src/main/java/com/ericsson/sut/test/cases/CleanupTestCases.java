package com.ericsson.sut.test.cases;

import static com.ericsson.cifwk.taf.scenario.TestScenarios.annotatedMethod;
import static com.ericsson.cifwk.taf.scenario.TestScenarios.flow;
import static com.ericsson.cifwk.taf.scenario.TestScenarios.scenario;
import static com.ericsson.nms.rv.core.util.CommonUtils.tryToLogin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.ericsson.cifwk.taf.annotations.TestStep;
import com.ericsson.cifwk.taf.scenario.TestScenario;
import com.ericsson.cifwk.taf.scenario.api.ScenarioListener;
import com.ericsson.cifwk.taf.scenario.api.TestStepFlowBuilder;
import com.ericsson.nms.rv.core.HAPropertiesReader;
import com.ericsson.nms.rv.core.ScenarioLogger;
import com.ericsson.nms.rv.core.cm.bulk.CmBulkExport;
import com.ericsson.nms.rv.core.cm.bulk.CmImportToLive;
import com.ericsson.nms.rv.core.fan.FileAccessNBI;
import com.ericsson.nms.rv.core.fm.FaultManagement;
import com.ericsson.nms.rv.core.fm.FaultManagementBsc;
import com.ericsson.nms.rv.core.fm.FaultManagementRouter;
import com.ericsson.nms.rv.core.ldap.ComAaLdap;
import com.ericsson.nms.rv.core.nbi.verifier.NbiAlarmVerifier;
import com.ericsson.nms.rv.core.nbi.verifier.NbiCreateSubsVerifier;
import com.ericsson.nms.rv.core.netex.NetworkExplorer;
import com.ericsson.nms.rv.core.pm.PerformanceManagement;
import com.ericsson.nms.rv.core.pm.PerformanceManagementRouter;
import com.ericsson.nms.rv.core.shm.ShmSmrs;
import com.ericsson.nms.rv.core.shm.SoftwareHardwareManagement;
import com.ericsson.nms.rv.core.um.UserManagement;
import com.ericsson.nms.rv.core.util.CommonUtils;
import com.ericsson.nms.rv.core.util.FunctionalArea;
import com.ericsson.nms.rv.core.util.TestCase;
import com.ericsson.oss.testware.availability.common.exception.EnmException;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class CleanupTestCases extends HighAvailabilityTestCase {
    private static final Logger logger = LogManager.getLogger(CleanupTestCases.class);
    private Map<FunctionalArea, Object> objectMap;
    private Map<FunctionalArea, Object> loaderObjectMap;
    private static final int MAX_RETRY_ATTEMPT_COUNT = 5;
    private static final Map<FunctionalArea, Integer> retryCounterMap = new ConcurrentHashMap<>();

    static {
        HAPropertiesReader.initCleanupMap();
        HAPropertiesReader.cleanupMap.forEach((area, testCase) -> retryCounterMap.put(area, 0));
    }

    CleanupTestCases(final Map<FunctionalArea, Object> objectMap,
                     final Map<FunctionalArea, Object> loaderObjectMap) {
        this.objectMap = objectMap;
        this.loaderObjectMap = loaderObjectMap;
    }

    public void clean() {
        final ScenarioListener scenarioLogger = new ScenarioLogger();
        final List<TestStepFlowBuilder> list = new ArrayList<>();

        HAPropertiesReader.cleanupMap.forEach((area, testCase) -> {
            if(FUNCTIONAL_AREAS_MAP.get(area.get()) && objectMap.get(area) != null) {
                if(area.get().equalsIgnoreCase(FunctionalArea.NETEX.get()) || area.get().equalsIgnoreCase(FunctionalArea.UM.get())) {
                    list.add(flow(testCase).addTestStep(annotatedMethod(this, testCase)));
                } else if(loaderObjectMap.get(area) != null) {
                    list.add(flow(testCase).addTestStep(annotatedMethod(this, testCase)));
                } else if ((area.get().equalsIgnoreCase(FunctionalArea.AALDAP.get()) || area.get().equalsIgnoreCase(FunctionalArea.FAN.get())) && HAPropertiesReader.isEnvCloudNative()) {
                    list.add(flow(testCase).addTestStep(annotatedMethod(this, testCase)));
                }
            }
        });
        final TestScenario scenario = scenario("Cleaning up ....")
                .split(list.toArray(new TestStepFlowBuilder[0])).withExceptionHandler(((ScenarioLogger) scenarioLogger).exceptionHandler())
                .build();
        CommonUtils.runScenario(scenario, scenarioLogger);
    }

    /**
     * Cleaner Test cases.
     */
    @TestStep(id = TestCase.SHM_CLEANUP)
    public void cleanupSHM() {
        logger.info("Cleaning up SHM...");
        SoftwareHardwareManagement softwareHardwareManagement = (SoftwareHardwareManagement)objectMap.get(FunctionalArea.SHM);
        if (retryCounterMap.get(FunctionalArea.SHM) < MAX_RETRY_ATTEMPT_COUNT) {
            try {
                tryToLogin(softwareHardwareManagement, "login for SHM has failed");
                softwareHardwareManagement.cleanup();
                softwareHardwareManagement.logout();
            } catch (final Exception e) {
                logger.warn("SoftwareHardwareManagement failed to cleanup ... Retry attempt: {}", retryCounterMap.get(FunctionalArea.SHM) + 1, e);
                retryCounterMap.put(FunctionalArea.SHM, retryCounterMap.get(FunctionalArea.SHM) + 1);
                cleanupSHM();
            }
        } else {
            logger.warn("SoftwareHardwareManagement failed to cleanup!!");
        }
    }

    @TestStep(id = TestCase.SMRS_CLEANUP)
    public void cleanupSMRS() {
        logger.info("Cleaning up SMRS ...");
        ShmSmrs shmSmrs = (ShmSmrs) objectMap.get(FunctionalArea.SMRS);
        if (retryCounterMap.get(FunctionalArea.SMRS) < MAX_RETRY_ATTEMPT_COUNT) {
            try {
                tryToLogin(shmSmrs, "login for SMRS has failed");
                shmSmrs.cleanup();
                shmSmrs.logout();
            } catch (final Exception e) {
                logger.warn("SHM SMRS failed to cleanup ... Retry attempt: {}", retryCounterMap.get(FunctionalArea.SMRS) + 1, e);
                retryCounterMap.put(FunctionalArea.SMRS, retryCounterMap.get(FunctionalArea.SMRS) + 1);
                cleanupSMRS();
            }
        } else {
            logger.warn("SHM SMRS failed to cleanup!!");
        }
    }

    @TestStep(id = TestCase.NETEX_CLEANUP)
    public void cleanupNETEX() {
        logger.info("Cleaning up NETEX...");
        NetworkExplorer networkExplorer = (NetworkExplorer)objectMap.get(FunctionalArea.NETEX);
        if (retryCounterMap.get(FunctionalArea.NETEX) < MAX_RETRY_ATTEMPT_COUNT) {
            try {
                tryToLogin(networkExplorer, "login for NETEX has failed");
                networkExplorer.cleanup();
                networkExplorer.logout();
            } catch (final Exception e) {
                logger.warn("NetworkExplorer failed to cleanup", e);
                retryCounterMap.put(FunctionalArea.NETEX, retryCounterMap.get(FunctionalArea.NETEX) + 1);
                cleanupNETEX();
            }
        } else {
            logger.warn("NetworkExplorer failed to cleanup!!!");
        }
    }

    @TestStep(id = TestCase.CMBIL_CLEANUP)
    public void cleanupCMBIL() {
        logger.info("Cleaning up CM-Bulk Import to Live ...");
        CmImportToLive cmImportToLive = (CmImportToLive)objectMap.get(FunctionalArea.CMBIL);
        if (retryCounterMap.get(FunctionalArea.CMBIL) < MAX_RETRY_ATTEMPT_COUNT) {
            try {
                tryToLogin(cmImportToLive, "login for CMBIL has failed");
                cmImportToLive.cleanup();
                cmImportToLive.logout();
            } catch (final Exception e) {
                logger.warn("CM-Bulk Import to Live failed to cleanup ... Retry attempt: {}", retryCounterMap.get(FunctionalArea.CMBIL) + 1 ,e);
                sleep(2);
                retryCounterMap.put(FunctionalArea.CMBIL, retryCounterMap.get(FunctionalArea.CMBIL) + 1);
                cleanupCMBIL();
            }
        } else {
            logger.warn("CM-Bulk Import to Live failed to cleanup!!!");
        }
    }

    @TestStep(id = TestCase.CMBE_CLEANUP)
    public void cleanupCMBE() {
        logger.info("Cleaning up CMBE...");
        CmBulkExport cmBulkExport = (CmBulkExport)objectMap.get(FunctionalArea.CMBE);
        if (retryCounterMap.get(FunctionalArea.CMBE) < MAX_RETRY_ATTEMPT_COUNT) {
            try {
                tryToLogin(cmBulkExport, "login for CMBE has failed");
                cmBulkExport.cleanup();
                cmBulkExport.logout();
            } catch (final Exception e) {
                logger.warn("CMBE failed to cleanup ... Retry attempt: {}", retryCounterMap.get(FunctionalArea.CMBE) + 1, e);
                retryCounterMap.put(FunctionalArea.CMBE, retryCounterMap.get(FunctionalArea.CMBE) + 1);
                cleanupCMBE();
            }
        } else {
            logger.warn("CMBE failed to cleanup!!!");
        }
    }

    @TestStep(id = TestCase.UM_CLEANUP)
    public void cleanupUM() {
        logger.info("Cleaning up UM...");
        UserManagement userManagement = (UserManagement)objectMap.get(FunctionalArea.UM);
        if (retryCounterMap.get(FunctionalArea.UM) < MAX_RETRY_ATTEMPT_COUNT) {
            try {
                tryToLogin(userManagement, "login for UM has failed");
                userManagement.cleanup();
                userManagement.logout();
            } catch (final Exception e) {
                logger.warn("UserManagement failed to cleanup", e);
                retryCounterMap.put(FunctionalArea.UM, retryCounterMap.get(FunctionalArea.UM) + 1);
                cleanupUM();
            }
        } else {
            logger.warn("UserManagement failed to cleanup !!");
        }
    }

    @TestStep(id = TestCase.PM_CLEANUP)
    public void cleanupPM() {
        logger.info("Cleaning up PMIC...");
        PerformanceManagement performanceManagement = (PerformanceManagement)objectMap.get(FunctionalArea.PM);
        if (retryCounterMap.get(FunctionalArea.PM) < MAX_RETRY_ATTEMPT_COUNT) {
            try {
                tryToLogin(performanceManagement, "login for PMIC has failed");
                performanceManagement.cleanup();
                performanceManagement.logout();
            } catch (final Exception e) {
                logger.warn("PMIC failed to cleanup ... Retry attempt: {}", retryCounterMap.get(FunctionalArea.PM) + 1, e);
                retryCounterMap.put(FunctionalArea.PM, retryCounterMap.get(FunctionalArea.PM) + 1);
                cleanupPM();
            }
        } else {
            logger.warn("PMIC failed to cleanup");
        }
    }

    @TestStep(id = TestCase.PMR_CLEANUP)
    public void cleanupPMR() {
        logger.info("Cleaning up pmr...");
        PerformanceManagementRouter performanceManagementRouter = (PerformanceManagementRouter) objectMap.get(FunctionalArea.PMR);
        if (retryCounterMap.get(FunctionalArea.PMR) < MAX_RETRY_ATTEMPT_COUNT) {
            try {
                tryToLogin(performanceManagementRouter, "login for PMIC-Router has failed");
                performanceManagementRouter.cleanup();
                performanceManagementRouter.logout();
            } catch (final EnmException e) {
                logger.warn("PMR failed to cleanup ... Retry attempt: {}", retryCounterMap.get(FunctionalArea.PMR) + 1, e);
                retryCounterMap.put(FunctionalArea.PMR, retryCounterMap.get(FunctionalArea.PMR) + 1);
                cleanupFMR();
            }
        } else {
            logger.warn("PMR failed to cleanup.");
        }
    }

    @TestStep(id = TestCase.NBIVS_CLEANUP)
    public void cleanupNBIVS() {
        logger.info("Cleaning up nbivs...");
        NbiCreateSubsVerifier nbiCreateSubsVerifier = (NbiCreateSubsVerifier)objectMap.get(FunctionalArea.NBIVS);
        try {
            nbiCreateSubsVerifier.login();
            nbiCreateSubsVerifier.cleanup();
            nbiCreateSubsVerifier.logout();
        } catch (final Exception e) {
            logger.warn("NBIVS failed to cleanup", e);
        }
    }

    @TestStep(id = TestCase.NBIVA_CLEANUP)
    public void cleanupNBIVA() {
        logger.info("Cleaning up nbiva...");
        NbiAlarmVerifier nbiAlarmVerifier = (NbiAlarmVerifier)objectMap.get(FunctionalArea.NBIVA);
        try {
            nbiAlarmVerifier.login();
            nbiAlarmVerifier.cleanup();
            nbiAlarmVerifier.logout();
        } catch (final Exception e) {
            logger.warn("NBIVA failed to cleanup", e);
        }
    }

    @TestStep(id = TestCase.FM_CLEANUP)
    public void cleanupFM() {
        logger.info("Cleaning up fm...");
        FaultManagement faultManagement = (FaultManagement)objectMap.get(FunctionalArea.FM);
        if (retryCounterMap.get(FunctionalArea.FM) < MAX_RETRY_ATTEMPT_COUNT) {
            try {
                faultManagement.login();
                faultManagement.cleanup();
                faultManagement.logout();
            } catch (final EnmException e) {
                logger.warn("FM failed to cleanup ... Retry attempt: {}", retryCounterMap.get(FunctionalArea.FM) + 1, e);
                retryCounterMap.put(FunctionalArea.FM, retryCounterMap.get(FunctionalArea.FM) + 1);
                cleanupFM();
            }
        } else {
            logger.warn("FM failed to cleanup.");
        }
    }

    @TestStep(id = TestCase.FMR_CLEANUP)
    public void cleanupFMR() {
        logger.info("Cleaning up fmr...");
        FaultManagementRouter faultManagementRouter = (FaultManagementRouter) objectMap.get(FunctionalArea.FMR);
        if (retryCounterMap.get(FunctionalArea.FMR) < MAX_RETRY_ATTEMPT_COUNT) {
            try {
                faultManagementRouter.login();
                faultManagementRouter.cleanup();
                faultManagementRouter.logout();
            } catch (final EnmException e) {
                logger.warn("FMR failed to cleanup ... Retry attempt: {}", retryCounterMap.get(FunctionalArea.FMR) + 1, e);
                retryCounterMap.put(FunctionalArea.FMR, retryCounterMap.get(FunctionalArea.FMR) + 1);
                cleanupFMR();
            }
        } else {
            logger.warn("FMR failed to cleanup.");
        }
    }

    @TestStep(id = TestCase.FMB_CLEANUP)
    public void cleanupFMB() {
        logger.info("Cleaning up fmb...");
        FaultManagementBsc faultManagementBsc = (FaultManagementBsc) objectMap.get(FunctionalArea.FMB);
        if (retryCounterMap.get(FunctionalArea.FMB) < MAX_RETRY_ATTEMPT_COUNT) {
            try {
                faultManagementBsc.login();
                faultManagementBsc.cleanup();
                faultManagementBsc.logout();
            } catch (final EnmException e) {
                logger.warn("FMB failed to cleanup ... Retry attempt: {}", retryCounterMap.get(FunctionalArea.FMB) + 1, e);
                retryCounterMap.put(FunctionalArea.FMB, retryCounterMap.get(FunctionalArea.FMB) + 1);
                cleanupFMB();
            }
        } else {
            logger.warn("FMB failed to cleanup.");
        }
    }

    @TestStep(id = TestCase.AALDAP_CLEANUP)
    public void cleanupAALDAP() {
        logger.info("Cleaning up AA LDAP...");
        ComAaLdap comAaLdap = (ComAaLdap)objectMap.get(FunctionalArea.AALDAP);
        if (retryCounterMap.get(FunctionalArea.AALDAP) < MAX_RETRY_ATTEMPT_COUNT) {
            try {
                tryToLogin(comAaLdap, "login for AA Ldap has failed");
                comAaLdap.cleanup();
                comAaLdap.logout();
            } catch (final Exception e) {
                logger.warn("ComAaLdap failed to cleanup", e);
                retryCounterMap.put(FunctionalArea.AALDAP, retryCounterMap.get(FunctionalArea.AALDAP) + 1);
                cleanupAALDAP();
            }
        } else {
            logger.warn("ComAaLdap failed to cleanup !!");
        }
    }

    @TestStep(id = TestCase.FAN_CLEANUP)
    public void cleanupFAN() {
        logger.info("Cleaning up FAN...");
        FileAccessNBI fileAccessNBI = (FileAccessNBI)objectMap.get(FunctionalArea.FAN);
        if (retryCounterMap.get(FunctionalArea.FAN) < MAX_RETRY_ATTEMPT_COUNT) {
            try {
                fileAccessNBI.cleanup();
            } catch (final EnmException e) {
                logger.warn("FAN failed to cleanup ... Retry attempt: {}", retryCounterMap.get(FunctionalArea.FAN) + 1, e);
                retryCounterMap.put(FunctionalArea.FAN, retryCounterMap.get(FunctionalArea.FAN) + 1);
                cleanupFAN();
            }
        } else {
            logger.warn("FAN failed to cleanup.");
        }
    }
}
