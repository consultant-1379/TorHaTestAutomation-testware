package com.ericsson.sut.test.cases;

import static org.assertj.core.api.Fail.fail;

import static com.ericsson.nms.rv.core.HAconstants.HAtime.ROP_TIME_IN_SECONDS;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.ericsson.nms.rv.core.ClusterReport;
import com.ericsson.nms.rv.core.upgrade.RegressionVerificationTasks;
import com.ericsson.nms.rv.core.upgrade.UpgradeVerificationTasks;
import com.ericsson.nms.rv.core.upgrade.UpgradeVerifier;
import com.ericsson.nms.rv.core.util.AdminUserUtils;
import com.ericsson.nms.rv.core.util.CliResult;
import com.ericsson.nms.rv.core.util.CliShell;
import com.ericsson.nms.rv.core.util.CommonUtils;
import com.ericsson.nms.rv.core.util.FunctionalArea;
import com.ericsson.nms.rv.core.util.NodeUtils;
import com.ericsson.nms.rv.core.util.RegressionUtils;
import com.ericsson.nms.rv.core.util.UpgradeUtils;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeSuite;

import com.ericsson.cifwk.taf.TafTestBase;
import com.ericsson.nms.rv.core.HAPropertiesReader;
import com.ericsson.nms.rv.core.LoadGenerator;
import com.ericsson.nms.rv.core.node.Node;
import com.ericsson.nms.rv.core.system.SystemObserver;
import com.ericsson.nms.rv.core.system.SystemVerifier;
import com.ericsson.nms.rv.core.um.UserManagement;
import com.ericsson.nms.rv.core.workload.WorkLoadHandler;
import com.ericsson.sut.test.cases.util.FunctionalAreaKey;

/**
 * The base class of all test cases.
 */
public abstract class HighAvailabilityTestCase extends TafTestBase {
    protected static final Map<String, Boolean> FUNCTIONAL_AREAS_MAP = HAPropertiesReader.getFunctionalAreasMap();
    protected static final int HA_VERIFICATION_TIME = ROP_TIME_IN_SECONDS;

    private static final Logger logger = LogManager.getLogger(HighAvailabilityTestCase.class);
    private static final WorkLoadHandler wlHandler = new WorkLoadHandler();
    private static final List<SystemObserver> observers = new ArrayList<>();
    private static final SystemVerifier systemVerifier = new SystemVerifier(observers);
    private static boolean isCorbaInstalled;
    private static long currentTimeMillis;
    private RegressionVerificationTasks regressionVerificationTasks;
    private UpgradeVerificationTasks upgradeVerificationTasks;
    private PrepareTestCases prepareTestCases;
    private UserManagement userManagement;

    public static final List<FunctionalAreaKey> appsFailedToStart = new ArrayList<>();
    public static final Set<Node> ALL_NODES_TO_SYNC = new HashSet<>();
    public static final Map<String, String> multiMapErrorCollection = new HashMap<>();
    public static boolean failedToWaitForUpgrade = false;
    public static boolean upgradeFailed = false;
    public static long testSuiteStartTimeForUpgrade;
    public static boolean upgradeFailedFlag = false;
    public static boolean isScpBladeAvailable = true;
    public static boolean isLoadInitFailed = false;

    static {
        if(!HAPropertiesReader.isEnvCloudNative()) {
            RegressionUtils.initDbSvcHosts();
        }
        HAPropertiesReader.initFunctionalAreaMap();
    }

    protected HighAvailabilityTestCase() {
        HAPropertiesReader.initAppMap();
    }

    /**
     * @param timeInSeconds the amount of time to sleep in seconds.
     */
    public static void sleep(final int timeInSeconds) {
        CommonUtils.sleep(timeInSeconds);
    }

    /**
     * Initializes the system under test.
     */
    @BeforeSuite(groups={"High Availability"})
    public void beforeSuiteSetup() {
        if (HAPropertiesReader.getTestType().equalsIgnoreCase("Regression") && HAPropertiesReader.getRopTime() == 0) {
            logger.info("Roptime cannot be zero");
            fail("Roptime cannot be zero");
        }
        if (HAPropertiesReader.isEnvCloudNative() && HAPropertiesReader.isWatcherConnectionFailed) {
            fail("Failed to connect to ADU-watcher.");
        }
        prepareTestCases = new PrepareTestCases(systemVerifier);
        if (HAPropertiesReader.isNodeOperationRequired()) {
            if (!wlHandler.checkProfileStatus("HA_01")) {
                fail("HA_01 is disabled or not configured");
            }
            logger.info("Stopping clashing profiles");
            wlHandler.clashingProfileSetFlagExecution(true);
            wlHandler.fmxclashingProfileExecution("stop", "FMX_01,FMX_05");
        }
        testSuiteStartTimeForUpgrade = HAPropertiesReader.getUpgradeStartTime();
        logger.info("Upgrade start time from job {}", HAPropertiesReader.getUpgradeStartTime());
        currentTimeMillis = System.currentTimeMillis();
        if (testSuiteStartTimeForUpgrade == 0) {
            testSuiteStartTimeForUpgrade = Long.parseLong(new SimpleDateFormat("yyyyMMddHHmmss").format(new Date()));
            logger.info("Current time  = Upgrade start time is {}", testSuiteStartTimeForUpgrade);
        }
        if (!HAPropertiesReader.isEnvCloudNative()) {
            HAPropertiesReader.setNeoConfiguration();
            RegressionUtils.installUpdateEnmUtils();
        } else {
            if (!CommonUtils.isIngressWorking()) {
                fail("Ingress not working!");
            }
        }
        if (HAPropertiesReader.getTestType().equalsIgnoreCase("Regression") && (HAPropertiesReader.neoconfig40KFlag || HAPropertiesReader.neoconfig60KFlag)) {
            logger.info("Building cluster report data ...");
            ClusterReport.buildClusterReport();
        }
        HAPropertiesReader.initApplicationDependencyMap();
        HAPropertiesReader.initImageDependencyMap();
        HAPropertiesReader.printTestwareProperties();
        AdminUserUtils.haCreateAdminUser(userManagement = new UserManagement(systemVerifier));

        if (HAPropertiesReader.isNodeOperationRequired()) {
            wlHandler.checkClashProfileFlag(currentTimeMillis);
            try {
                LoadGenerator.initialiseLoad();
                if (LoadGenerator.hasNodes()) {
                    logger.info("Nodes in simulation:\n{}", LoadGenerator.getNodes());
                }
            } catch (final Exception e) {
                logger.error("Failed to initialize load : {}", e.getMessage(), e);
                isLoadInitFailed = true;
            }
            if (isLoadInitFailed) {
                logger.info("Disabling Functional testcases required nodes.");
                HAPropertiesReader.disableFunctionalAreasRequireNodes();
            }
            if (!isLoadInitFailed) {
                if (FUNCTIONAL_AREAS_MAP.get(FunctionalArea.NBIVS.get()) || FUNCTIONAL_AREAS_MAP.get(FunctionalArea.NBIVA.get())) {
                    isCorbaInstalled = prepareTestCases.prepareNBI();
                }
                NodeUtils.syncNodes(ALL_NODES_TO_SYNC);     // Sync all nodes (CMBE, CMBIL, etc)
                NodeUtils.updateAllSyncedNodesWithOssModelIdentity(ALL_NODES_TO_SYNC);
            }
        }
        prepareTestCases.prepare();
    }

    /**
     * Cleans the system under test.
     */
    @AfterSuite(groups={"High Availability"})
    public final void afterSuiteTeardown() {
        logger.info("teardown is executing");
        if (HAPropertiesReader.isNodeOperationRequired()) {
            logger.info("Starting clashing profiles");
            wlHandler.fmxclashingProfileExecution("start", "FMX_01,FMX_05");
            wlHandler.clashingProfileSetFlagExecution(false);
        }
        if (HAPropertiesReader.isEnvCloudNative()) {
            UpgradeUtils.startStopCloudNativeWatcherScript("endpoint", "stop");
        } else {
            RegressionUtils.installHaToolsIfRemoved();
            UpgradeUtils.stopBravoWatcherScript(HAPropertiesReader.isNodeOperationRequired());
        }
        CleanupTestCases cleanupTestCases = new CleanupTestCases(prepareTestCases.getObjectMap(), prepareTestCases.getLoaderObjectMap());
        cleanupTestCases.clean();
        AdminUserUtils.haUserCleanup(userManagement);
        if (!HAPropertiesReader.isEnvCloudNative()) {
            RegressionUtils.removeHaTools();
        } else {
            if (HAPropertiesReader.isExternalVmUsed()) {
                final CliShell extShell = new CliShell(HAPropertiesReader.getExternalHost());
                final CliResult result = extShell.execute("/usr/local/adu/cleanup.sh");
                logger.info("External-watcher Cleanup result {} : {}", result.isSuccess(), result.getOutput());
            }
        }
        if (failedToWaitForUpgrade) {
            fail("Failed to wait for Upgrade to start.");
        }
        if (upgradeFailed) {
            fail("Upgrade failed.");
        }
        if (UpgradeVerifier.isUpgradeTimeExceeded) {
            String msg = String.format("Upgrade could not finished in %.1f Hours.", HAPropertiesReader.cloudUgFinishTimeLimit / 3600f);
            fail(msg);
        }
        if (isLoadInitFailed) {
            fail("Load initialization failed during upgrade. Please check Netsim connectivity. Testcases required nodes will not be executed.");
        }
        int counter = 0;
        for (final FunctionalAreaKey fak : appsFailedToStart) {
            if (fak.isEnable()) {
                if(HAPropertiesReader.isEnvCloudNative() && HAPropertiesReader.isNodeOperationRequired()) {
                    if((fak.getClassName().equalsIgnoreCase(HAPropertiesReader.ENMSYSTEMMONITOR)) ) {
                        //Not available in cENM env.
                    } else if ((((fak.getClassName().equalsIgnoreCase(HAPropertiesReader.PERFORMANCEMANAGEMENTROUTER) ||
                            fak.getClassName().equalsIgnoreCase(HAPropertiesReader.FAULTMANAGEMENTROUTER)) && !LoadGenerator.isRouterNodesAvailable)
                            || (fak.getClassName().equalsIgnoreCase(HAPropertiesReader.FAULTMANAGEMENTBSC) && !LoadGenerator.isBscNodesAvailable))) {
                        logger.warn("application {} was activated and it failed in preparation method, nodes not available.", fak.getClassName());

                    } else {
                        counter++;
                    }
                } else {
                    if (!(((fak.getClassName().equalsIgnoreCase(HAPropertiesReader.PERFORMANCEMANAGEMENTROUTER) ||
                            fak.getClassName().equalsIgnoreCase(HAPropertiesReader.FAULTMANAGEMENTROUTER)) && !LoadGenerator.isRouterNodesAvailable)
                            || (fak.getClassName().equalsIgnoreCase(HAPropertiesReader.FAULTMANAGEMENTBSC) && !LoadGenerator.isBscNodesAvailable))) {
                        logger.error("application {} was activated and it failed in preparation method, failed", fak.getClassName());
                        counter++;
                    }
                }
            }
        }
        if (counter > 0) {
            fail("%d applications were activated and failed in preparation method", counter);
        }
    }

    /**
     * Start-Stop Regression Verification Tasks.
     */
    protected final void startRegressionVerificationTasks() {
        regressionVerificationTasks = new RegressionVerificationTasks(prepareTestCases.getObjectMap(), prepareTestCases.getLoaderObjectMap(), currentTimeMillis, isCorbaInstalled);
        regressionVerificationTasks.startVerificationTasks();
    }

    protected final void stopRegressionVerificationTasks() {
        regressionVerificationTasks.stopVerificationTasks();
    }

    /**
     * Start-Stop Upgrade Verification Tasks.
     */
    void startUpgradeVerificationTasks(final boolean... isUpgrade) {
        upgradeVerificationTasks = new UpgradeVerificationTasks(prepareTestCases.getObjectMap(), prepareTestCases.getLoaderObjectMap(), currentTimeMillis, isCorbaInstalled);
        upgradeVerificationTasks.startVerificationTasks(isUpgrade);
    }

    final void stopUpgradeVerificationTasks() {
        upgradeVerificationTasks.stopVerificationTasks();
    }
}
