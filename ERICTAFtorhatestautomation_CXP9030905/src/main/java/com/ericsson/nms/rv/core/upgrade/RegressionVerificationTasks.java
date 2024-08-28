package com.ericsson.nms.rv.core.upgrade;

import static org.assertj.core.api.Fail.fail;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.ericsson.cifwk.taf.data.DataHandler;
import com.ericsson.nms.rv.core.ApplicationClashingNeo4j;
import com.ericsson.nms.rv.core.AvailabilityReportGenerator;
import com.ericsson.nms.rv.core.ClusterReport;
import com.ericsson.nms.rv.core.ClusterStatusReportGenerator;
import com.ericsson.nms.rv.core.EnmApplication;
import com.ericsson.nms.rv.core.HAPropertiesReader;
import com.ericsson.nms.rv.core.ProcessDelayedResponses;
import com.ericsson.nms.rv.core.amos.Amos;
import com.ericsson.nms.rv.core.cm.ConfigurationManagement;
import com.ericsson.nms.rv.core.cm.bulk.CmBulkExport;
import com.ericsson.nms.rv.core.cm.bulk.CmImportToLive;
import com.ericsson.nms.rv.core.concurrent.Verifier;
import com.ericsson.nms.rv.core.downtime.EnmDowntime;
import com.ericsson.nms.rv.core.esm.CnomSystemMonitor;
import com.ericsson.nms.rv.core.esm.EnmSystemMonitor;
import com.ericsson.nms.rv.core.fan.FileAccessNBI;
import com.ericsson.nms.rv.core.fan.FileLookupService;
import com.ericsson.nms.rv.core.fm.FaultManagement;
import com.ericsson.nms.rv.core.fm.FaultManagementBsc;
import com.ericsson.nms.rv.core.fm.FaultManagementRouter;
import com.ericsson.nms.rv.core.launcher.AppLauncher;
import com.ericsson.nms.rv.core.nbi.verifier.NbiAlarmVerifier;
import com.ericsson.nms.rv.core.nbi.verifier.NbiCreateSubsVerifier;
import com.ericsson.nms.rv.core.netex.NetworkExplorer;
import com.ericsson.nms.rv.core.pm.PerformanceManagement;
import com.ericsson.nms.rv.core.pm.PerformanceManagementRouter;
import com.ericsson.nms.rv.core.shm.ShmSmrs;
import com.ericsson.nms.rv.core.shm.SoftwareHardwareManagement;
import com.ericsson.nms.rv.core.system.SystemVerifier;
import com.ericsson.nms.rv.core.um.UserManagement;
import com.ericsson.nms.rv.core.util.CommonUtils;
import com.ericsson.nms.rv.core.util.FunctionalArea;
import com.ericsson.nms.rv.core.util.UpgradeUtils;
import com.ericsson.nms.rv.core.workload.WorkLoadHandler;
import com.ericsson.nms.rv.taf.tools.Constants.Time;
import com.ericsson.nms.rv.core.CsvFilesReportGenerator;
import com.ericsson.sut.test.cases.HighAvailabilityTestCase;
import com.ericsson.sut.test.cases.util.FunctionalAreaKey;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class RegressionVerificationTasks extends HighAvailabilityTestCase {

    private static final Logger logger = LogManager.getLogger(RegressionVerificationTasks.class);

    private static final int THREAD_POOL_SIZE = 30;
    private static final List<Future<EnmDowntime>> futureTasks = new ArrayList<>(THREAD_POOL_SIZE);
    private static final int HA_VERIFICATION_TIME = Time.ONE_MINUTE_IN_SECONDS * HAPropertiesReader.getRopTime();
    private static final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final WorkLoadHandler wlHandler = new WorkLoadHandler();
    private static final Map<String, Long> totalDowntimeValues = new HashMap<>();
    private static final Map<String, Long> totalTimeoutDowntimeValues = new HashMap<>();
    private static final Map<String, String> downtimeMap = new HashMap<>();
    public static final Map<String, Map<Date, Date>> appDateTimeList = new HashMap<>();
    public static final Map<String, Map<Date, Date>> appDateTimeTimeoutList = new HashMap<>();
    public static final Map<String, List<String>> appDateTimeLoggerList = new HashMap<>();
    public static final Map<String, List<String>> appDateTimeTimeoutLoggerList = new HashMap<>();
    private static final String HIGHEST_DOWNTIMES = "------------------- HIGHEST DOWNTIMES -------------------";

    private ExecutorService verifierExecutor;
    private UpgradeVerifier upgradeVerifier;
    private Map<FunctionalArea, Object> objectMap;
    private Map<FunctionalArea, Object> loaderObjectMap;
    private Long regressionStartTime;
    private Long regressionFinishTime;
    private Long currentTimeMillis;
    private boolean isCorbaInstalled;
    public static boolean isHcAfterRebootFailed = false;

    public RegressionVerificationTasks(Map<FunctionalArea, Object> objectMap,
                                       Map<FunctionalArea, Object> loaderObjectMap,
                                       final Long currentTimeMillis,
                                       final boolean isCorbaInstalled) {
        this.objectMap = objectMap;
        this.loaderObjectMap = loaderObjectMap;
        this.currentTimeMillis = currentTimeMillis;
        this.isCorbaInstalled = isCorbaInstalled;
        if (HAPropertiesReader.isEnvCloud() && !HAPropertiesReader.isEnvCloudNative()) {
            CloudDependencyDowntimeHelper.generatingPemKey();
        }
    }

    private boolean addFutureTask(final Object object, final FunctionalArea key) {
        logger.info("Adding Regression testcase for : {}", key.get().toUpperCase());
        String className = "";
        if (object != null) {
            int count = 0;
            do {
                try {
                    className = object.getClass().getSimpleName();
                    logger.info("Test className : {}", className);
                } catch (final Exception e) {
                    logger.warn("Message: {}", e.getMessage());
                }
            } while (className.isEmpty() && count++ <3 );
        } else {
            logger.warn("{} test object is Null!", key.get().toUpperCase());
            return false;
        }
        if (className.isEmpty()) {
            logger.warn("{} test object is Empty!", key.get().toUpperCase());
            return false;
        }

        if (!CommonVerificationTasks.verifyTestCases(className, object, loaderObjectMap, key, isCorbaInstalled)) {
            return false;
        }

        switch (className) {
            case HAPropertiesReader.CONFIGURATIONMANGEMENT :
                futureTasks.add(verifierExecutor.submit(new Verifier((ConfigurationManagement)object)));
                break;
            case HAPropertiesReader.CMBULKEXPORT :
                futureTasks.add(verifierExecutor.submit(new Verifier((CmBulkExport)object)));
                break;
            case HAPropertiesReader.CMBULKIMPORTTOLIVE :
                futureTasks.add(verifierExecutor.submit(new Verifier((CmImportToLive)object)));
                break;
            case HAPropertiesReader.PERFORMANCEMANAGEMENT :
                futureTasks.add(verifierExecutor.submit(new Verifier((PerformanceManagement)object)));
                break;
            case HAPropertiesReader.PERFORMANCEMANAGEMENTROUTER:
                futureTasks.add(verifierExecutor.submit(new Verifier((PerformanceManagementRouter)object)));
                break;
            case HAPropertiesReader.NBIALARMVERIFIER :
                futureTasks.add(verifierExecutor.submit(new Verifier((NbiAlarmVerifier)object)));
                break;
            case HAPropertiesReader.NBICREATESUBSVERIFIER :
                futureTasks.add(verifierExecutor.submit(new Verifier((NbiCreateSubsVerifier)object)));
                break;
            case HAPropertiesReader.SOFTWAREHARDWAREMANAGEMENT :
                futureTasks.add(verifierExecutor.submit(new Verifier((SoftwareHardwareManagement)object)));
                break;
            case HAPropertiesReader.FAULTMANAGEMENT :
                futureTasks.add(verifierExecutor.submit(new Verifier((FaultManagement)object)));
                break;
            case HAPropertiesReader.FILELOOKUPSERVICE:
                futureTasks.add(verifierExecutor.submit(new Verifier((FileLookupService)object)));
                break;
            case HAPropertiesReader.FAULTMANAGEMENTROUTER:
                futureTasks.add(verifierExecutor.submit(new Verifier((FaultManagementRouter)object)));
                break;
            case HAPropertiesReader.FAULTMANAGEMENTBSC :
                futureTasks.add(verifierExecutor.submit(new Verifier((FaultManagementBsc)object)));
                break;
            case HAPropertiesReader.AMOSAPP :
                futureTasks.add(verifierExecutor.submit(new Verifier((Amos)object)));
                break;
            case HAPropertiesReader.NETWORKEXPLORER :
                futureTasks.add(verifierExecutor.submit(new Verifier((NetworkExplorer)object)));
                break;
            case HAPropertiesReader.USERMANAGEMENT :
                futureTasks.add(verifierExecutor.submit(new Verifier((UserManagement)object)));
                break;
            case HAPropertiesReader.ENMSYSTEMMONITOR :
                futureTasks.add(verifierExecutor.submit(new Verifier((EnmSystemMonitor)object)));
                break;
            case HAPropertiesReader.SYSTEMVERIFIER :
                futureTasks.add(verifierExecutor.submit(new Verifier((SystemVerifier)object)));
                break;
            case HAPropertiesReader.CNOMSYSTEMMONITOR:
                futureTasks.add(verifierExecutor.submit(new Verifier((CnomSystemMonitor)object)));
                break;
            case HAPropertiesReader.APPLAUNCHER :
                futureTasks.add(verifierExecutor.submit(new Verifier((AppLauncher)object)));
                break;
            case HAPropertiesReader.COMAALDAP :
                //Do nothing
                break;
            case HAPropertiesReader.SHMSMRS:
                futureTasks.add(verifierExecutor.submit(new Verifier((ShmSmrs)object)));
                break;
            case HAPropertiesReader.FILEACCESSNBI:
                futureTasks.add(verifierExecutor.submit(new Verifier((FileAccessNBI)object)));
                break;
            default:
                logger.error("Invalid Test class : {}", className);
        }
        return true;
    }

    /**
     * Start the Regression verification tasks.
     */
    public final void startVerificationTasks() {
        EnmApplication.clearSignal();   //  Starts verifiers.
        verifierExecutor = new ForkJoinPool(THREAD_POOL_SIZE);
        futureTasks.clear();
        if (HAPropertiesReader.isNodeOperationRequired()) {
            wlHandler.checkClashProfileFlag(currentTimeMillis);
        }
        try {
            UpgradeUtils.initWatcherScript();
        } catch (final Exception e) {
            logger.error("Exception while starting watcher scripts: {}.", e.getMessage(), e);
        }
        if(!HAPropertiesReader.isEnvCloud() && !HAPropertiesReader.isEnvCloudNative() && HAPropertiesReader.neoconfig60KFlag) {
            try {
                UpgradeUtils.initPhysicalNeo4j();
            } catch (final Exception e) {
                logger.error("Exception while starting physical Neo4J scripts: {}", e.getMessage(), e);
            }
        }
        HAPropertiesReader.areaMap.forEach((key, value) -> {
            boolean isEnabled = CommonUtils.predictFunctionalArea(key);
            boolean success = false;
            if(isEnabled) {
                if(HAPropertiesReader.isEnvCloudNative()) {
                    if (key.equals(FunctionalArea.ESM)) {
                        HighAvailabilityTestCase.multiMapErrorCollection.put(HAPropertiesReader.appMap.get(key.get()), "Feature not available in cENM");
                    } else {
                        Object obj = objectMap.get(key);
                        success = addFutureTask(obj, key);
                    }
                } else {
                    Object obj = objectMap.get(key);
                    success = addFutureTask(obj, key);
                }
            }
            if(!success) {
                logger.info("appsFailedToStart info: app:{}, Enabled:{}", key, isEnabled);
                if(key.get().equalsIgnoreCase(FunctionalArea.AMOS.get()) && !isScpBladeAvailable) {
                    appsFailedToStart.add(new FunctionalAreaKey(Amos.class.getSimpleName(), false));
                } else {
                    appsFailedToStart.add(new FunctionalAreaKey(value, isEnabled));
                }
            }
        });
        upgradeVerifier = new UpgradeVerifier();
        regressionStartTime = UpgradeVerifier.setRegressionStarttime();
        logger.info("regressionStartTime {}", regressionStartTime);
    }

    /**
     * Stops the Regression verification tasks.
     */
    public final void stopVerificationTasks() {
        EnmApplication.setSignal(); //  Stops verifiers.
        if (verifierExecutor != null) {
            try {
                logger.info("stopping all verifiers...");
                verifierExecutor.shutdown();
                boolean done = false;
                try {
                    done = verifierExecutor.awaitTermination(10L, TimeUnit.MINUTES);
                    if (!done) {
                        verifierExecutor.shutdownNow();
                    }
                } catch (final CancellationException | InterruptedException e) {
                    logger.error("Problem during executor service awaitTermination method", e);
                }
                logger.info("All tasks were finished so far? {}", done);

                final Object testSuiteName = DataHandler.getAttribute("regression.testsuite.name");
                regressionFinishTime = UpgradeVerifier.setRegressionFinishtime();
                logger.info("regressionStartTime {}, regressionFinishTime {}", regressionStartTime, regressionFinishTime);
                if (HAPropertiesReader.isEnvCloud() && !HAPropertiesReader.isEnvCloudNative()) {
                    UpgradeUtils.removeNeoData("cloud", true);
                } else if (HAPropertiesReader.neoconfig60KFlag) {
                    UpgradeUtils.removeNeoData("physical", true);
                }
                if (!testSuiteName.toString().equalsIgnoreCase("KillJbosses.xml")) {
                    downtimeMap.putAll(upgradeVerifier.buildKnownDowntimeMap());
                }
                getAppDowntimesAndCheckIfFailed();
                final String systemVerifierSimpleName = SystemVerifier.class.getSimpleName();
                EnmApplication.getDownTimeSysHandler().stop(systemVerifierSimpleName, 0);
                EnmApplication.getDownTimeSysHandler().stopTimeout(systemVerifierSimpleName, 0);
                final Long systemDowntime = EnmApplication.getDownTimeSysHandler().getDowntime();
                totalDowntimeValues.put(systemVerifierSimpleName, systemDowntime);
                EnmApplication.getDownTimeSysHandler().resetDowntime();
                logger.info(HIGHEST_DOWNTIMES);
                for (final FunctionalAreaKey fak : appsFailedToStart) {
                    totalDowntimeValues.put(fak.getClassName(), -1L);
                }

                final String sysDowntime = String.format("System Downtime: Total=%d(s)", systemDowntime);
                final Long deltaThreshold = HAPropertiesReader.getFunctionalAreasThresholdMap().get(systemVerifierSimpleName).get(HAPropertiesReader.DELTA);

                final LocalDateTime localDateTimeStart = LocalDateTime.parse(String.valueOf(regressionStartTime), dateTimeFormatter);
                final LocalDateTime localDateTimeFinish = LocalDateTime.parse(String.valueOf(regressionFinishTime), dateTimeFormatter);
                final Duration duration = Duration.between(localDateTimeStart, localDateTimeFinish);
                String regStartTime = localDateTimeStart.format(HAPropertiesReader.formatter);
                String regEndTime = localDateTimeFinish.format(HAPropertiesReader.formatter);

                AppLauncher appLauncher = (AppLauncher) objectMap.get(FunctionalArea.APPLAUNCH);
                if (appLauncher == null) {
                    appLauncher = new AppLauncher();
                }
                logger.info("IgnoreDTMap in reg file....{}", HAPropertiesReader.ignoreDTMap);
                Map<String, Map<Date, Date>> deltaDtValues = ApplicationClashingNeo4j.updateDowntimeMap(HAPropertiesReader.NETWORKEXPLORER, appDateTimeList, downtimeMap);
                deltaDtValues = ApplicationClashingNeo4j.updateDowntimeMap(HAPropertiesReader.FAULTMANAGEMENT, deltaDtValues, downtimeMap);
                final AvailabilityReportGenerator availabilityReportGenerator = new AvailabilityReportGenerator(duration, regStartTime, regEndTime, totalDowntimeValues, totalTimeoutDowntimeValues, downtimeMap,
                        deltaDtValues, appDateTimeTimeoutList, appLauncher, HighAvailabilityTestCase.multiMapErrorCollection, appDateTimeLoggerList, appDateTimeTimeoutLoggerList, HAPropertiesReader.ignoreDTMap);
                availabilityReportGenerator.attachTestReport();
                availabilityReportGenerator.attachLauncherReport();
                availabilityReportGenerator.attachDelayedResponseTracker();

                if(HAPropertiesReader.isEnvCloudNative()){
                    CommonUtils.attachLogFilesToReport();
                }
                if(!HAPropertiesReader.neoconfig40KFlag){
                    final CsvFilesReportGenerator csvFilesReportGenerator = new CsvFilesReportGenerator(CommonUtils.txtFiles, CommonUtils.dataFiles);
                    csvFilesReportGenerator.attachTxt();
                }
                if (HAPropertiesReader.getTestType().equalsIgnoreCase("Regression") && (HAPropertiesReader.neoconfig40KFlag || HAPropertiesReader.neoconfig60KFlag)) {
                    logger.info("Attaching cluster report ..");
                    final ClusterStatusReportGenerator clusterStatusReportGenerator = new ClusterStatusReportGenerator(ClusterReport.hostMap, ClusterReport.dataMap);
                    clusterStatusReportGenerator.attachTxt();
                }
                logger.info("Assertion Info: systemDowntime:{}, deltaThreshold:{}, dependencyThresholdErrorList:{}", systemDowntime, deltaThreshold, AvailabilityReportGenerator.dependencyThresholdErrorList.isEmpty());

                final Map<String, Long> downtimeValues = availabilityReportGenerator.getDeltaDowntimeValues();
                logger.info("Downtimevalues are {}", downtimeValues);
                downtimeValues.forEach((key, value) -> {
                    final long deltaThresholdValue = HAPropertiesReader.getFunctionalAreasThresholdMap().get(key).get(HAPropertiesReader.DELTA);
                    logger.info("Application Downtime, {}: Total={}(s), Delta={}(s)", key, value, deltaThresholdValue);
                    if (isHcAfterRebootFailed) {
                        fail("Health check failed after reboot. Please check the health of system");
                    } else if (value > deltaThresholdValue || !AvailabilityReportGenerator.dependencyThresholdErrorList.isEmpty()) {
                        logger.warn("ENM Downtime Assertion(Threshold) failed for: {}", key);
                        fail("ENM Downtime Assertion(Threshold) failed");
                    }
                });
                if (appLauncher.isFailedRequests()) {
                    logger.info("stopVerificationTasks: Application Launcher failed: All applications have failed HTTP Requests");
                    fail("Application Launcher failed: All applications have failed HTTP Requests");
                }
                if (!appLauncher.getMissingLinksMap().isEmpty()) {
                    logger.info("stopVerificationTasks: Application Launcher failed: UI links for few applications are missing");
                    fail("Application Launcher failed: UI links for few applications are missing");
                }
                CommonUtils.printDownTime(systemDowntime, deltaThreshold, sysDowntime);
            } catch (final Exception e) {
                logger.error("Problem during stop verification tasks ", e);
            } finally {
                verifierExecutor.shutdown();
                boolean done = false;
                try {
                    done = verifierExecutor.awaitTermination(1L, TimeUnit.MINUTES);
                    if (!done) {
                        verifierExecutor.shutdownNow();
                    }
                } catch (final CancellationException | InterruptedException e) {
                    logger.error("Problem during executor service awaitTermination method", e);
                }
                logger.info("All tasks were finished so far? {}", done);
            }
        }
    }

    private void getAppDowntimesAndCheckIfFailed() {
        HAPropertiesReader.ignoreDTMap.forEach((appName, appTime) -> {
            ProcessDelayedResponses.updateCommonOffset(appName, appDateTimeTimeoutList,appDateTimeTimeoutLoggerList);
        });
        try {
            ProcessDelayedResponses.appNeo4jClashCheck(HAPropertiesReader.NETWORKEXPLORER, EnmApplication.getAppDateTimeTimeoutList, appDateTimeTimeoutLoggerList,downtimeMap,true);
            ProcessDelayedResponses.appNeo4jClashCheck(HAPropertiesReader.NETWORKEXPLORER, EnmApplication.getAppDateTimeList, appDateTimeLoggerList,downtimeMap,false);
        } catch (Exception e) {
            logger.warn("error occurred in ProcessDelayedResponses.appNeo4jClashCheck for NETWORKEXPLORER, error message : {}", e.getMessage());
        }
        try {
            ProcessDelayedResponses.appNeo4jClashCheck(HAPropertiesReader.FAULTMANAGEMENT, EnmApplication.getAppDateTimeTimeoutList, appDateTimeTimeoutLoggerList, downtimeMap, true);
            ProcessDelayedResponses.appNeo4jClashCheck(HAPropertiesReader.FAULTMANAGEMENT, EnmApplication.getAppDateTimeList, appDateTimeLoggerList, downtimeMap, false);
        } catch (Exception e) {
            logger.warn("error occurred in ProcessDelayedResponses.appNeo4jClashCheck for FAULTMANAGEMENT, error message : {}", e.getMessage());
        }
        for (int i = futureTasks.size() - 1; i >= 0; i--) {
            final Future<EnmDowntime> future = futureTasks.get(i);
            EnmDowntime enmDowntime = null;
            try {
                enmDowntime = future.get(HA_VERIFICATION_TIME * 3L, TimeUnit.SECONDS);
            } catch (final InterruptedException | ExecutionException | TimeoutException | CancellationException e) {
                logger.warn("Failed to get enmDowntime", e);
            }
            try {
                if (enmDowntime != null) {
                    final Long applicationDowntime = enmDowntime.getApplicationDowntime();
                    final Long applicationTimeoutDowntime = enmDowntime.getApplicationTimeoutDowntime();
                    final String appName = enmDowntime.getEnmApplication().getClass().getSimpleName();
                    totalDowntimeValues.put(appName, (applicationDowntime-(HAPropertiesReader.ignoreDtTotalSecs.get(appName)== null ? 0L : HAPropertiesReader.ignoreDtTotalSecs.get(appName) )-(HAPropertiesReader.ignoreTimeoutClashNeo4jTotalSecs.get(appName) == null ? 0L : HAPropertiesReader.ignoreTimeoutClashNeo4jTotalSecs.get(appName)) - (HAPropertiesReader.ignoreErrorClashNeo4jTotalSecs.get(appName) == null ? 0L : HAPropertiesReader.ignoreErrorClashNeo4jTotalSecs.get(appName))));
                    totalTimeoutDowntimeValues.put(appName, applicationTimeoutDowntime-(HAPropertiesReader.ignoreDtTotalSecs.get(appName)== null ? 0L : HAPropertiesReader.ignoreDtTotalSecs.get(appName) )-(HAPropertiesReader.ignoreTimeoutClashNeo4jTotalSecs.get(appName) == null ? 0L : HAPropertiesReader.ignoreTimeoutClashNeo4jTotalSecs.get(appName)));
                    logger.info("appName : {}, totalTimeoutDowntimeValues : {}", appName, totalTimeoutDowntimeValues);
                    logger.info("appName : {}, totalDowntimeValues : {}", appName, totalDowntimeValues);
                    logger.info("appName : {}, downtimeMap : {}", appName, downtimeMap);
                    final Map<String, Long> downtimeValues = AvailabilityReportGenerator.updateDowntimeValues(totalDowntimeValues, downtimeMap, appDateTimeList);
                    final Long deltaDowntime = downtimeValues.get(appName);
                    final String appDowntimeMsg = String.format("Application Downtime, %s: Total=%d(s), Delta=%d(s) ", appName, applicationDowntime, deltaDowntime);
                    final Long deltaThreshold = HAPropertiesReader.getFunctionalAreasThresholdMap().get(appName).get(HAPropertiesReader.DELTA);
                    CommonUtils.printDownTime(deltaDowntime, deltaThreshold, appDowntimeMsg);
                    if (deltaDowntime > deltaThreshold) {
                        logger.info("Application {} DT {} is greater than threshold{}", appName, deltaDowntime, deltaThreshold);
                    }
                }
            } catch (final Exception e) {
                logger.warn("Exception due to empty values");
            }
        }
        logger.info("Regression : totalDowntimeValues : {}", totalDowntimeValues.toString());
        logger.info("Regression : totalTimeoutDowntimeValues : {}", totalTimeoutDowntimeValues.toString());
        futureTasks.clear();
    }
}
