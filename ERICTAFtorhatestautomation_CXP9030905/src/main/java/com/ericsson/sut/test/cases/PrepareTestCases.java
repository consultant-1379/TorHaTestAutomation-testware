package com.ericsson.sut.test.cases;

import static com.ericsson.cifwk.taf.scenario.TestScenarios.annotatedMethod;
import static com.ericsson.cifwk.taf.scenario.TestScenarios.flow;
import static com.ericsson.cifwk.taf.scenario.TestScenarios.scenario;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.ericsson.cifwk.taf.annotations.TestStep;
import com.ericsson.cifwk.taf.scenario.TestScenario;
import com.ericsson.cifwk.taf.scenario.api.ScenarioListener;
import com.ericsson.cifwk.taf.scenario.api.TestStepFlowBuilder;
import com.ericsson.nms.rv.core.HAPropertiesReader;
import com.ericsson.nms.rv.core.ScenarioLogger;
import com.ericsson.nms.rv.core.amos.Amos;
import com.ericsson.nms.rv.core.amos.AmosLoadGenerator;
import com.ericsson.nms.rv.core.cm.CmLoadGenerator;
import com.ericsson.nms.rv.core.cm.ConfigurationManagement;
import com.ericsson.nms.rv.core.cm.bulk.CmBulkExport;
import com.ericsson.nms.rv.core.cm.bulk.CmBulkExportLoader;
import com.ericsson.nms.rv.core.cm.bulk.CmImportToLive;
import com.ericsson.nms.rv.core.cm.bulk.CmImportToLiveLoader;
import com.ericsson.nms.rv.core.esm.CnomSystemMonitor;
import com.ericsson.nms.rv.core.esm.EnmSystemMonitor;
import com.ericsson.nms.rv.core.fan.FileAccessNBI;
import com.ericsson.nms.rv.core.fan.FileLookupService;
import com.ericsson.nms.rv.core.fm.FaultManagement;
import com.ericsson.nms.rv.core.fm.FaultManagementBsc;
import com.ericsson.nms.rv.core.fm.FaultManagementRouter;
import com.ericsson.nms.rv.core.fm.FmBscLoadGenerator;
import com.ericsson.nms.rv.core.fm.FmLoadGenerator;
import com.ericsson.nms.rv.core.fm.FmRouterLoadGenerator;
import com.ericsson.nms.rv.core.launcher.AppLauncher;
import com.ericsson.nms.rv.core.ldap.ComAaLdap;
import com.ericsson.nms.rv.core.nbi.NbiContext;
import com.ericsson.nms.rv.core.nbi.NbiLoadGenerator;
import com.ericsson.nms.rv.core.nbi.component.CorbaNbiOperatorImpl;
import com.ericsson.nms.rv.core.nbi.verifier.NbiAlarmVerifier;
import com.ericsson.nms.rv.core.nbi.verifier.NbiCreateSubsVerifier;
import com.ericsson.nms.rv.core.netex.NetworkExplorer;
import com.ericsson.nms.rv.core.node.Node;
import com.ericsson.nms.rv.core.pm.PerformanceManagement;
import com.ericsson.nms.rv.core.pm.PerformanceManagementRouter;
import com.ericsson.nms.rv.core.pm.PmLoadGenerator;
import com.ericsson.nms.rv.core.pm.PmRouterLoadGenerator;
import com.ericsson.nms.rv.core.shm.ShmLoadGenerator;
import com.ericsson.nms.rv.core.shm.ShmSmrs;
import com.ericsson.nms.rv.core.shm.SmrsLoadGenerator;
import com.ericsson.nms.rv.core.shm.SoftwareHardwareManagement;
import com.ericsson.nms.rv.core.system.SystemVerifier;
import com.ericsson.nms.rv.core.um.UserManagement;
import com.ericsson.nms.rv.core.util.CommonUtils;
import com.ericsson.nms.rv.core.util.FunctionalArea;
import com.ericsson.nms.rv.core.util.HaTimeoutException;
import com.ericsson.nms.rv.core.util.TestCase;
import com.ericsson.oss.testware.availability.common.exception.EnmException;
import com.ericsson.sut.test.cases.util.FunctionalAreaKey;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class PrepareTestCases extends HighAvailabilityTestCase {
    private static final Logger logger = LogManager.getLogger(PrepareTestCases.class);
    private static final Map<FunctionalArea, Object> objectMap = new HashMap<>();
    private static final Map<FunctionalArea, Object> loaderObjectMap = new HashMap<>();
    private static SystemVerifier systemVerifier;
    private NbiLoadGenerator nbiLoadGenerator;
    private boolean isCorbaInstalled = false;

    PrepareTestCases(final SystemVerifier systemVerifier) {
        this.systemVerifier = systemVerifier;
    }

    Map<FunctionalArea, Object> getObjectMap() {
        return objectMap;
    }

    Map<FunctionalArea, Object> getLoaderObjectMap() {
        return loaderObjectMap;
    }

    boolean prepareNBI() {
        nbiLoadGenerator = new NbiLoadGenerator();
        final CorbaNbiOperatorImpl corbaNbiImpl = new CorbaNbiOperatorImpl();
        isCorbaInstalled = corbaNbiImpl.downloadTestClient();
        if(isCorbaInstalled == false) {
            HighAvailabilityTestCase.multiMapErrorCollection.put(HAPropertiesReader.appMap.get(FunctionalArea.NBIVA.get()), HAPropertiesReader.appMap.get(FunctionalArea.NBIVA.get()) +", Error in corba installation phase");
            HighAvailabilityTestCase.multiMapErrorCollection.put(HAPropertiesReader.appMap.get(FunctionalArea.NBIVS.get()), HAPropertiesReader.appMap.get(FunctionalArea.NBIVS.get()) + ", Error in corba installation phase");
        }
        return isCorbaInstalled;
    }

    public void prepare() {
        final ScenarioListener scenarioLogger = new ScenarioLogger();
        final List<TestStepFlowBuilder> list = new ArrayList<>();
        FUNCTIONAL_AREAS_MAP.forEach((area, isEnabled) -> {
            if (HAPropertiesReader.isEnvCloudNative() && (area.equalsIgnoreCase(FunctionalArea.ESM.get()))) {
                logger.info("{} skipped because feature not available in cENM ",area.toUpperCase());
            } else if (HAPropertiesReader.isEnvCloudNative() && (area.equalsIgnoreCase(FunctionalArea.FAN.get()) || area.equalsIgnoreCase(FunctionalArea.AALDAP.get()))) {
                if (isEnabled) {
                    final String testCase = area.toUpperCase() + "_PREPARE";
                    list.add(flow(testCase).addTestStep(annotatedMethod(this, testCase)));
                }
            } else {
                if (isEnabled && !(area.equalsIgnoreCase(FunctionalArea.AALDAP.get()))) {
                    final String testCase = area.toUpperCase() + "_PREPARE";
                    list.add(flow(testCase).addTestStep(annotatedMethod(this, testCase)));
                }
            }
        });
        final TestScenario scenario = scenario("Preparing ....")
                .split(list.toArray(new TestStepFlowBuilder[0])).withExceptionHandler(((ScenarioLogger) scenarioLogger).exceptionHandler())
                .build();
        CommonUtils.runScenario(scenario, scenarioLogger);
    }

    /**
     * Prepare Test cases.
     */
    @TestStep(id = TestCase.NBIVA_PREPARE)
    public void prepareNbiva() {
        try {
            if (HAPropertiesReader.isEnvCloudNative()) {
                String fmVipAddress = NbiContext.getFmVipAddress();
                logger.info("fmVipAddress : {}", fmVipAddress);
                if(fmVipAddress == null || fmVipAddress.trim().isEmpty()) {
                    logger.info("Failed to get FM_VIP_ADDRESS, value is : " + fmVipAddress);
                    HighAvailabilityTestCase.multiMapErrorCollection.put(HAPropertiesReader.appMap.get(FunctionalArea.NBIVA.get()), "Failed to get FM_VIP_ADDRESS");
                    throw new EnmException("Failed to get FM_VIP_ADDRESS, value is : " + fmVipAddress);
                }
            }
            if (nbiLoadGenerator.getNbiNodes().size() >= 2) {
                final List<Node> nodes = new ArrayList<>();
                nodes.add(nbiLoadGenerator.getNbiNodes().get(0));
                NbiAlarmVerifier nbiAlarmVerifier = new NbiAlarmVerifier(nodes, systemVerifier);
                objectMap.put(FunctionalArea.NBIVA, nbiAlarmVerifier);
                loaderObjectMap.put(FunctionalArea.NBIVA, nbiLoadGenerator);
                try {
                    nbiAlarmVerifier.prepare();
                } catch (final EnmException e) {
                    logger.info("nbi alarm verifier fail to prepare : {}", e.getMessage());
                    throw new Exception(e.getMessage());
                }
            } else {
                logger.error("nbi alarm verifier needs at least two nodes to work");
            }
        } catch (Exception e) {
            logger.info("NBIVA failed to prepare : {}", e.getMessage());
            HighAvailabilityTestCase.multiMapErrorCollection.put(HAPropertiesReader.appMap.get(FunctionalArea.NBIVA.get()), "Failed to prepare NBIVA:" + e.getMessage());
            HighAvailabilityTestCase.appsFailedToStart.add(new FunctionalAreaKey(NbiCreateSubsVerifier.class.getSimpleName(), true));
        }
    }

    @TestStep(id = TestCase.NBIVS_PREPARE)
    public void prepareNbivs() {
        try {
            if (HAPropertiesReader.isEnvCloudNative()) {
                String fmVipAddress = NbiContext.getFmVipAddress();
                logger.info("fmVipAddress : {}", fmVipAddress);
                if(fmVipAddress == null || fmVipAddress.trim().isEmpty()) {
                    logger.info("Failed to get FM_VIP_ADDRESS, value is : " + fmVipAddress);
                    HighAvailabilityTestCase.multiMapErrorCollection.put(HAPropertiesReader.appMap.get(FunctionalArea.NBIVS.get()), "Failed to get FM_VIP_ADDRESS");
                    throw new EnmException("Failed to get FM_VIP_ADDRESS, value is : " + fmVipAddress);
                }
            }
            if (nbiLoadGenerator.getNbiNodes().size() >= 2) {
                final List<Node> nodes = new ArrayList<>();
                nodes.add(nbiLoadGenerator.getNbiNodes().get(1));
                NbiCreateSubsVerifier nbiCreateSubsVerifier = new NbiCreateSubsVerifier(nodes, systemVerifier);
                objectMap.put(FunctionalArea.NBIVS, nbiCreateSubsVerifier);
                loaderObjectMap.put(FunctionalArea.NBIVS, nbiLoadGenerator);
                try {
                    nbiCreateSubsVerifier.prepare();
                } catch (final EnmException | HaTimeoutException e) {
                    logger.info("nbi subscription verifier fail to prepare : {}", e.getMessage());
                    if (HAPropertiesReader.isEnvCloudNative()) {
                        logger.info("Retrying prepare with fm_vip_address from integration file.");
                        NbiContext.setFmVipAddress(HAPropertiesReader.fmVipAddressFromValuesDoc);
                        try {
                            nbiCreateSubsVerifier.prepare();
                        } catch (final EnmException | HaTimeoutException ex) {
                            logger.info("nbi subscription verifier fail to prepare : {}", ex.getMessage());
                            throw new EnmException("Failed to prepare NBIVS.");
                        }
                    }
                }
                if(!NbiCreateSubsVerifier.isInitiated()) {
                    logger.warn("Subscription/Un-subscription of NBIVS is failed");
                    throw new Exception("Subscription/Un-subscription of NBIVS is failed");
                }
            } else {
                logger.error("nbi subscription verifier needs at least two nodes to work");
            }
        } catch (Exception e) {
            logger.info("nbi subscription verifier fail to prepare : {}", e.getMessage());
            HighAvailabilityTestCase.multiMapErrorCollection.put(HAPropertiesReader.appMap.get("nbivs"), HAPropertiesReader.appMap.get("nbivs") + ", "+ e.getMessage());
            HighAvailabilityTestCase.appsFailedToStart.add(new FunctionalAreaKey(NbiCreateSubsVerifier.class.getSimpleName(), true));
        }
    }

    @TestStep(id = TestCase.SHM_PREPARE)
    public void prepareSHM() {
        ShmLoadGenerator shmLoadGenerator = new ShmLoadGenerator();
        SoftwareHardwareManagement softwareHardwareManagement = new SoftwareHardwareManagement(shmLoadGenerator, systemVerifier);
        objectMap.put(FunctionalArea.SHM, softwareHardwareManagement);
        loaderObjectMap.put(FunctionalArea.SHM, shmLoadGenerator);
    }

    @TestStep(id = TestCase.SMRS_PREPARE)
    public void prepareSMRS() {
        SmrsLoadGenerator smrsLoadGenerator = new SmrsLoadGenerator();
        ShmSmrs shmSmrs = new ShmSmrs(smrsLoadGenerator, systemVerifier);
        objectMap.put(FunctionalArea.SMRS, shmSmrs);
        loaderObjectMap.put(FunctionalArea.SMRS, smrsLoadGenerator);
    }

    @TestStep(id = TestCase.NETEX_PREPARE)
    public void prepareNetex() {
        NetworkExplorer networkExplorer = new NetworkExplorer(true, systemVerifier);
        objectMap.put(FunctionalArea.NETEX, networkExplorer);
        try {
            networkExplorer.login();
            networkExplorer.prepare();
            networkExplorer.logout();
        } catch (final EnmException e) {
            logger.warn("NetworkExplorer failed to prepare", e);
        }
    }

    @TestStep(id = TestCase.AMOS_PREPARE)
    public void prepareAmos() {
        AmosLoadGenerator amosLoadGenerator = new AmosLoadGenerator();
        Amos amosApp = new Amos(amosLoadGenerator, systemVerifier);
        objectMap.put(FunctionalArea.AMOS, amosApp);
        loaderObjectMap.put(FunctionalArea.AMOS, amosLoadGenerator);
    }

    @TestStep(id = TestCase.FM_PREPARE)
    public void prepareFM() {
        FmLoadGenerator fmLoadGenerator = new FmLoadGenerator();
        FaultManagement faultManagement = new FaultManagement(fmLoadGenerator, systemVerifier);
        objectMap.put(FunctionalArea.FM, faultManagement);
        loaderObjectMap.put(FunctionalArea.FM, fmLoadGenerator);
        try {
            faultManagement.login();
            faultManagement.prepare();
            faultManagement.logout();
        } catch (final EnmException | HaTimeoutException e) {
            logger.warn("Failed to prepare FM", e);
            HighAvailabilityTestCase.multiMapErrorCollection.put(HAPropertiesReader.appMap.get("fm"), HAPropertiesReader.appMap.get("fm") + ", Failed to prepare FM.");
            HighAvailabilityTestCase.appsFailedToStart.add(new FunctionalAreaKey(FaultManagement.class.getSimpleName(), true));
        }
    }

    @TestStep(id = TestCase.FMR_PREPARE)
    public void prepareFMR() {
        FmRouterLoadGenerator fmRouterLoadGenerator = new FmRouterLoadGenerator();
        FaultManagementRouter faultManagementRouter = new FaultManagementRouter(fmRouterLoadGenerator, systemVerifier);
        objectMap.put(FunctionalArea.FMR, faultManagementRouter);
        loaderObjectMap.put(FunctionalArea.FMR, fmRouterLoadGenerator);
        try {
            faultManagementRouter.login();
            faultManagementRouter.prepare();
            faultManagementRouter.logout();
        } catch (final EnmException | HaTimeoutException e) {
            logger.warn("Failed to prepare FMR", e);
            HighAvailabilityTestCase.multiMapErrorCollection.put(HAPropertiesReader.appMap.get("fmr"), HAPropertiesReader.appMap.get("fmr") + ", Failed to prepare FMR.");
            HighAvailabilityTestCase.appsFailedToStart.add(new FunctionalAreaKey(FaultManagementRouter.class.getSimpleName(), true));
        }
    }

    @TestStep(id = TestCase.FLS_PREPARE)
    public void prepareFLS() {
        FileLookupService fileLookupService = new FileLookupService(systemVerifier);
        objectMap.put(FunctionalArea.FLS, fileLookupService);
    }

    @TestStep(id = TestCase.FMB_PREPARE)
    public void prepareFMB() {
        FmBscLoadGenerator fmBscLoadGenerator = new FmBscLoadGenerator();
        FaultManagementBsc faultManagementBsc = new FaultManagementBsc(fmBscLoadGenerator, systemVerifier);
        objectMap.put(FunctionalArea.FMB, faultManagementBsc);
        loaderObjectMap.put(FunctionalArea.FMB, fmBscLoadGenerator);
        try {
            faultManagementBsc.login();
            faultManagementBsc.prepare();
            faultManagementBsc.logout();
        } catch (final EnmException | HaTimeoutException e) {
            logger.warn("Failed to prepare FMB", e);
            HighAvailabilityTestCase.multiMapErrorCollection.put(HAPropertiesReader.appMap.get("fmb"), HAPropertiesReader.appMap.get("fmb") + ", Failed to prepare FMB");
            HighAvailabilityTestCase.appsFailedToStart.add(new FunctionalAreaKey(FaultManagementBsc.class.getSimpleName(), true));
        }
    }

    @TestStep(id = TestCase.ESM_PREPARE)
    public void prepareESM() {
        EnmSystemMonitor enmSystemMonitor = new EnmSystemMonitor(systemVerifier);
        objectMap.put(FunctionalArea.ESM, enmSystemMonitor);
        try {
            enmSystemMonitor.prepare();
        } catch (final Exception e) {
            logger.warn("Failed to prepare ESM", e);
        }
    }

    @TestStep(id = TestCase.CM_PREPARE)
    public void prepareCM() {
        CmLoadGenerator cmLoadGenerator = new CmLoadGenerator();
        ALL_NODES_TO_SYNC.addAll(cmLoadGenerator.getCmVerificationNodes());
        ConfigurationManagement configurationManagement = new ConfigurationManagement(cmLoadGenerator, ALL_NODES_TO_SYNC.size(), systemVerifier);
        objectMap.put(FunctionalArea.CM, configurationManagement);
        loaderObjectMap.put(FunctionalArea.CM, cmLoadGenerator);
    }

    @TestStep(id = TestCase.CMBE_PREPARE)
    public void prepareCMBE() {
        CmBulkExportLoader cmBulkExportLoader = new CmBulkExportLoader();
        ALL_NODES_TO_SYNC.addAll(cmBulkExportLoader.getCmBulkExportVerificationNodes());
        CmBulkExport cmBulkExport = new CmBulkExport(systemVerifier);
        objectMap.put(FunctionalArea.CMBE, cmBulkExport);
        loaderObjectMap.put(FunctionalArea.CMBE, cmBulkExportLoader);
        try {
            cmBulkExport.login();
            cmBulkExport.prepare();
            cmBulkExport.logout();
        } catch (final EnmException e) {
            logger.warn("Failed to prepare CMBE", e);
        }
    }

    @TestStep(id = TestCase.CMBIL_PREPARE)
    public void prepareCMBIL() {
        CmImportToLiveLoader cmImportToLiveLoader = new CmImportToLiveLoader();
        ALL_NODES_TO_SYNC.addAll(cmImportToLiveLoader.getCmImportToLiveVerificationNodes());
        CmImportToLive cmImportToLive = new CmImportToLive(systemVerifier);
        objectMap.put(FunctionalArea.CMBIL, cmImportToLive);
        loaderObjectMap.put(FunctionalArea.CMBIL, cmImportToLiveLoader);
        try {
            cmImportToLive.login();
            cmImportToLive.prepare();
            cmImportToLive.logout();
        } catch (final EnmException e) {
            logger.warn("Failed to prepare CMBIL", e);
            HighAvailabilityTestCase.multiMapErrorCollection.put(HAPropertiesReader.appMap.get("cmbil"), HAPropertiesReader.appMap.get("cmbil") + ", Failed to prepare CMBIL");
            HighAvailabilityTestCase.appsFailedToStart.add(new FunctionalAreaKey(CmImportToLive.class.getSimpleName(), true));
        }
    }

    @TestStep(id = TestCase.PM_PREPARE)
    public void preparePM() {
        PmLoadGenerator pmLoadGenerator = new PmLoadGenerator();
        PerformanceManagement performanceManagement = new PerformanceManagement(pmLoadGenerator, systemVerifier);
        objectMap.put(FunctionalArea.PM, performanceManagement);
        loaderObjectMap.put(FunctionalArea.PM, pmLoadGenerator);
        try {
            performanceManagement.prepare();
            ALL_NODES_TO_SYNC.addAll(pmLoadGenerator.getPmVerificationNodes());
        } catch (EnmException e) {
            logger.warn("Failed to prepare PM", e);
            HighAvailabilityTestCase.multiMapErrorCollection.put(HAPropertiesReader.appMap.get("pm"), HAPropertiesReader.appMap.get("pm") + ", Failed to prepare PM");
            HighAvailabilityTestCase.appsFailedToStart.add(new FunctionalAreaKey(PerformanceManagement.class.getSimpleName(), true));
        }
    }

    @TestStep(id = TestCase.PMR_PREPARE)
    public void preparePMR() {
        PmRouterLoadGenerator pmRouterLoadGenerator = new PmRouterLoadGenerator();
        PerformanceManagementRouter performanceManagementRouter = new PerformanceManagementRouter(pmRouterLoadGenerator, systemVerifier);
        objectMap.put(FunctionalArea.PMR, performanceManagementRouter);
        loaderObjectMap.put(FunctionalArea.PMR, pmRouterLoadGenerator);
        try {
            performanceManagementRouter.prepare();
            ALL_NODES_TO_SYNC.addAll(pmRouterLoadGenerator.getPmRouterVerificationNodes());
        } catch (final EnmException e) {
            logger.warn("Failed to prepare PMR", e);
            HighAvailabilityTestCase.multiMapErrorCollection.put(HAPropertiesReader.appMap.get("pmr"), HAPropertiesReader.appMap.get("pmr") + ", Failed to prepare PMR");
            HighAvailabilityTestCase.appsFailedToStart.add(new FunctionalAreaKey(PerformanceManagementRouter.class.getSimpleName(), true));
        }
    }

    @TestStep(id = TestCase.APPLAUNCH_PREPARE)
    public void prepareLauncher() {
        AppLauncher appLauncher = new AppLauncher(systemVerifier);
        objectMap.put(FunctionalArea.APPLAUNCH, appLauncher);
        try {
            appLauncher.login();
            appLauncher.prepare();
            appLauncher.logout();
        } catch (final EnmException e) {
            logger.warn("Failed to get all rest applications", e);
        }
    }

    @TestStep(id = TestCase.CESM_PREPARE)
    public void prepareCESM() {
        CnomSystemMonitor cnomSystemMonitor = new CnomSystemMonitor(systemVerifier);
        objectMap.put(FunctionalArea.CESM, cnomSystemMonitor);
        try {
            cnomSystemMonitor.prepare();
        } catch (final Exception e) {
            logger.warn("Failed to prepare CESM", e);
        }
    }


    @TestStep(id = TestCase.UM_PREPARE)
    public void prepareUM() {
        UserManagement userManagement = new UserManagement(systemVerifier);
        objectMap.put(FunctionalArea.UM, userManagement);
    }

    @TestStep(id = TestCase.AALDAP_PREPARE)
    public void prepareAALDAP() {
        ComAaLdap comAaLdap = new ComAaLdap();
        objectMap.put(FunctionalArea.AALDAP, comAaLdap);
        try {
            comAaLdap.prepare();
        } catch (final Exception e) {
            logger.warn("Failed to prepare AALDAP:", e);
            HighAvailabilityTestCase.multiMapErrorCollection.put(HAPropertiesReader.appMap.get("aaldap"), HAPropertiesReader.appMap.get("aaldap") + ", Failed to prepare AALDAP.");
            HighAvailabilityTestCase.appsFailedToStart.add(new FunctionalAreaKey(ComAaLdap.class.getSimpleName(), true));
        }
    }

    @TestStep(id = TestCase.SYSTEM_PREPARE)
    public void prepareSystem() {
        objectMap.put(FunctionalArea.SYSTEM, systemVerifier);
    }

    @TestStep(id = TestCase.FAN_PREPARE)
    public void prepareFAN() {
        FileAccessNBI fileAccessNBI = new FileAccessNBI(systemVerifier);
        objectMap.put(FunctionalArea.FAN, fileAccessNBI);
        try {
            fileAccessNBI.prepare();
        } catch (final Exception e) {
            logger.warn("Failed to prepare FAN", e);
            HighAvailabilityTestCase.multiMapErrorCollection.put(HAPropertiesReader.appMap.get("fan"), HAPropertiesReader.appMap.get("fan") + ", " + e.getMessage());
            HighAvailabilityTestCase.appsFailedToStart.add(new FunctionalAreaKey(FileAccessNBI.class.getSimpleName(), true));
        }
    }
}
