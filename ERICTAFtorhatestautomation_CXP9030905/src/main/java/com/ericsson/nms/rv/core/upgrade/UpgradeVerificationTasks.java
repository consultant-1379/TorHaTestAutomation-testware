package com.ericsson.nms.rv.core.upgrade;

import static org.assertj.core.api.Fail.fail;

import static com.ericsson.cifwk.taf.scenario.TestScenarios.annotatedMethod;
import static com.ericsson.cifwk.taf.scenario.TestScenarios.flow;
import static com.ericsson.cifwk.taf.scenario.TestScenarios.scenario;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.ericsson.nms.rv.core.esm.CnomSystemMonitor;
import com.ericsson.nms.rv.core.fan.FileAccessNBI;
import com.ericsson.nms.rv.core.fan.FileLookupService;
import com.ericsson.nms.rv.core.fm.FaultManagementBsc;
import com.ericsson.nms.rv.core.fm.FaultManagementRouter;
import com.ericsson.nms.rv.core.pm.PerformanceManagementRouter;
import com.ericsson.nms.rv.core.shm.ShmSmrs;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.ericsson.cifwk.taf.TafTestContext;
import com.ericsson.cifwk.taf.annotations.TestStep;
import com.ericsson.cifwk.taf.scenario.TestScenario;
import com.ericsson.cifwk.taf.scenario.api.ScenarioListener;
import com.ericsson.cifwk.taf.scenario.api.TestStepFlowBuilder;
import com.ericsson.nms.rv.core.ApplicationClashingNeo4j;
import com.ericsson.nms.rv.core.AvailabilityReportGenerator;
import com.ericsson.nms.rv.core.CalculateHaProxyOffset;
import com.ericsson.nms.rv.core.CommonReportHelper;
import com.ericsson.nms.rv.core.EnmApplication;
import com.ericsson.nms.rv.core.HAPropertiesReader;
import com.ericsson.nms.rv.core.ProcessDelayedResponses;
import com.ericsson.nms.rv.core.ScenarioLogger;
import com.ericsson.nms.rv.core.amos.Amos;
import com.ericsson.nms.rv.core.cm.ConfigurationManagement;
import com.ericsson.nms.rv.core.cm.bulk.CmBulkExport;
import com.ericsson.nms.rv.core.cm.bulk.CmImportToLive;
import com.ericsson.nms.rv.core.downtime.DownTimeHandler;
import com.ericsson.nms.rv.core.downtime.EnmDowntime;
import com.ericsson.nms.rv.core.esm.EnmSystemMonitor;
import com.ericsson.nms.rv.core.fm.FaultManagement;
import com.ericsson.nms.rv.core.launcher.AppLauncher;
import com.ericsson.nms.rv.core.nbi.verifier.NbiAlarmVerifier;
import com.ericsson.nms.rv.core.nbi.verifier.NbiCreateSubsVerifier;
import com.ericsson.nms.rv.core.netex.NetworkExplorer;
import com.ericsson.nms.rv.core.pm.PerformanceManagement;
import com.ericsson.nms.rv.core.shm.SoftwareHardwareManagement;
import com.ericsson.nms.rv.core.system.SystemObserver;
import com.ericsson.nms.rv.core.system.SystemVerifier;
import com.ericsson.nms.rv.core.um.UserManagement;
import com.ericsson.nms.rv.core.util.CommonUtils;
import com.ericsson.nms.rv.core.util.FunctionalArea;
import com.ericsson.nms.rv.core.util.TestCase;
import com.ericsson.nms.rv.core.util.UpgradeUtils;
import com.ericsson.nms.rv.core.workload.WorkLoadHandler;
import com.ericsson.nms.rv.core.CsvFilesReportGenerator;
import com.ericsson.nms.rv.core.ImagePullReportGenerator;
import com.ericsson.sut.test.cases.HighAvailabilityTestCase;
import com.ericsson.sut.test.cases.util.FunctionalAreaKey;

public class UpgradeVerificationTasks extends HighAvailabilityTestCase {
    private static final Logger logger = LogManager.getLogger(UpgradeVerificationTasks.class);
    private static final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final String ENM_DOWNTIME_ASSERTION_THRESHOLD_FAILED = "ENM Downtime Assertion(Threshold) failed";
    private static final Map<String, Long> totalDowntimeValues = new ConcurrentHashMap<>();
    private static final Map<String, Long> totalTimeoutDowntimeValues = new ConcurrentHashMap<>();
    private static final Map<String, String> downtimeMap = new HashMap<>();
    private static final Map<String, Map<Date, Date>> appDateTimeList = new ConcurrentHashMap<>();
    private static final Map<Date, Date> esmDowntimeMap = new ConcurrentHashMap<>();
    private static final Map<String, Map<Date, Date>> appDateTimeTimeoutList = new ConcurrentHashMap<>();
    private static final WorkLoadHandler wlHandler = new WorkLoadHandler();
    private static final List<SystemObserver> observers = new ArrayList<>();
    private static final String EXECUTED_BY_V_USER = "Executed by vUser:{}";
    private final List<TestStepFlowBuilder> list = new ArrayList<>();
    private static final Map<String, List<String>> appDateTimeLoggerList = new ConcurrentHashMap<>();
    private static final Map<String, List<String>> appDateTimeTimeoutLoggerList = new ConcurrentHashMap<>();
    private Map<FunctionalArea, Object> objectMap;
    private Map<FunctionalArea, Object> loaderObjectMap;
    private UpgradeVerifier upgradeVerifier;
    private Long currentTimeMillis;
    private boolean isCorbaInstalled;
    private Long upgradeStartTime;
    private Long upgradeFinishTime;
    private String ugStartTime;
    private String ugEndTime;

    public UpgradeVerificationTasks(final Map<FunctionalArea, Object> objectMap,
                                    final Map<FunctionalArea, Object> loaderObjectMap,
                                    final Long currentTimeMillis,
                                    final boolean isCorbaInstalled) {
        this.objectMap = objectMap;
        this.loaderObjectMap = loaderObjectMap;
        this.currentTimeMillis = currentTimeMillis;
        this.isCorbaInstalled = isCorbaInstalled;
    }

    private boolean addTestCase(final Object object, final FunctionalArea key, final boolean isUpgrade) {
        logger.info("Adding testcase for : {}", key.get().toUpperCase());
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
                list.add(flow("CM Flow").addTestStep(annotatedMethod(this, TestCase.CM)).beforeFlow(getRunnable(isUpgrade)));
                break;
            case HAPropertiesReader.CMBULKEXPORT :
                list.add(flow("CMBE Flow").addTestStep(annotatedMethod(this, TestCase.CMBE)).beforeFlow(getRunnable(isUpgrade)));
                break;
            case HAPropertiesReader.CMBULKIMPORTTOLIVE :
                list.add(flow("CMBIL Flow").addTestStep(annotatedMethod(this, TestCase.CMBIL)).beforeFlow(getRunnable(isUpgrade)));
                break;
            case HAPropertiesReader.PERFORMANCEMANAGEMENT :
                list.add(flow("PM Flow").addTestStep(annotatedMethod(this, TestCase.PM)).beforeFlow(getRunnable(isUpgrade)));
                break;
            case HAPropertiesReader.PERFORMANCEMANAGEMENTROUTER:
                list.add(flow("PMR Flow").addTestStep(annotatedMethod(this, TestCase.PMR)).beforeFlow(getRunnable(isUpgrade)));
                break;
            case HAPropertiesReader.NBIALARMVERIFIER :
                list.add(flow("NBIVA Flow").addTestStep(annotatedMethod(this, TestCase.NBIVA)).beforeFlow(getRunnable(isUpgrade)));
                break;
            case HAPropertiesReader.NBICREATESUBSVERIFIER :
                list.add(flow("NBIVS Flow").addTestStep(annotatedMethod(this, TestCase.NBIVS)).beforeFlow(getRunnable(isUpgrade)));
                break;
            case HAPropertiesReader.SOFTWAREHARDWAREMANAGEMENT :
                list.add(flow("SHM Flow").addTestStep(annotatedMethod(this, TestCase.SHM)).beforeFlow(getRunnable(isUpgrade)));
                break;
            case HAPropertiesReader.FAULTMANAGEMENT :
                list.add(flow("FM Flow").addTestStep(annotatedMethod(this, TestCase.FM)).beforeFlow(getRunnable(isUpgrade)));
                break;
            case HAPropertiesReader.FILELOOKUPSERVICE:
                list.add(flow("FLS Flow").addTestStep(annotatedMethod(this, TestCase.FLS)).beforeFlow(getRunnable(isUpgrade)));
                break;
            case HAPropertiesReader.FAULTMANAGEMENTROUTER:
                list.add(flow("FMR Flow").addTestStep(annotatedMethod(this, TestCase.FMR)).beforeFlow(getRunnable(isUpgrade)));
                break;
            case HAPropertiesReader.FAULTMANAGEMENTBSC :
                list.add(flow("FMB Flow").addTestStep(annotatedMethod(this, TestCase.FMB)).beforeFlow(getRunnable(isUpgrade)));
                break;
            case HAPropertiesReader.AMOSAPP :
                list.add(flow("AMOS Flow").addTestStep(annotatedMethod(this, TestCase.AMOS)).beforeFlow(getRunnable(isUpgrade)));
                break;
            case HAPropertiesReader.APPLAUNCHER :
                list.add(flow("AppLauncher Flow").addTestStep(annotatedMethod(this, TestCase.LAUNCHER)).beforeFlow(getRunnable(isUpgrade)));
                break;
            case HAPropertiesReader.NETWORKEXPLORER :
                list.add(flow("NetEx Flow").addTestStep(annotatedMethod(this, TestCase.NETEX)).beforeFlow(getRunnable(isUpgrade)));
                break;
            case HAPropertiesReader.USERMANAGEMENT :
                list.add(flow("UM Flow").addTestStep(annotatedMethod(this, TestCase.UM)).beforeFlow(getRunnable(isUpgrade)));
                break;
            case HAPropertiesReader.ENMSYSTEMMONITOR :
                list.add(flow("ESM Flow").addTestStep(annotatedMethod(this, TestCase.ESM)).beforeFlow(getRunnable(isUpgrade)));
                break;
            case HAPropertiesReader.CNOMSYSTEMMONITOR:
                list.add(flow("CESM Flow").addTestStep(annotatedMethod(this, TestCase.CESM)).beforeFlow(getRunnable(isUpgrade)));
                break;
            case HAPropertiesReader.SYSTEMVERIFIER :
                list.add(flow("System Verifier Flow").addTestStep(annotatedMethod(this, TestCase.SYSTEM)).beforeFlow(getRunnable(isUpgrade)));
                break;
            case HAPropertiesReader.COMAALDAP :
                //DO nothing!
                list.add(flow("Com AA Ldap Verifier Flow").addTestStep(annotatedMethod(this, TestCase.AALDAP)).beforeFlow(getRunnable(isUpgrade)));
                break;
            case HAPropertiesReader.SHMSMRS :
                list.add(flow("SHM SMRS Flow").addTestStep(annotatedMethod(this, TestCase.SMRS)).beforeFlow(getRunnable(isUpgrade)));
                break;
            case HAPropertiesReader.FILEACCESSNBI:
                list.add(flow("FAN Flow").addTestStep(annotatedMethod(this, TestCase.FAN)).beforeFlow(getRunnable(isUpgrade)));
                break;
            default:
                logger.error("Invalid Test class : {}", className);
        }
        return true;
    }

    /**
     * Start Upgrade verification tasks.
     * @param isUpgrade
     */
    public void startVerificationTasks(final boolean ... isUpgrade) {
        if (isUpgrade != null && isUpgrade.length > 0 && isUpgrade[0]) {
            EnmApplication.setSignal();     //  Stops verifiers.
        } else {
            EnmApplication.clearSignal();   //  Start verifiers.
        }
        logger.info("Measuring Upgrade availability......");
        if (HAPropertiesReader.isNodeOperationRequired()) {
            wlHandler.checkClashProfileFlag(currentTimeMillis);
        }
        HAPropertiesReader.areaMap.forEach((key, value) -> {
            boolean isEnabled = CommonUtils.predictFunctionalArea(key);
            boolean success = false;
            if (isEnabled) {
                if(HAPropertiesReader.isEnvCloudNative()) {
                    if (key.equals(FunctionalArea.ESM)) {
                        HighAvailabilityTestCase.multiMapErrorCollection.put(HAPropertiesReader.appMap.get(key.get()), "Feature not available in cENM");
                    } else {
                        Object obj = objectMap.get(key);
                        success = addTestCase(obj, key, isUpgrade[0]);
                    }
                } else {
                    Object obj = objectMap.get(key);
                    success = addTestCase(obj, key, isUpgrade[0]);
                }
            }
            if (!success) {
                if (key.get().equalsIgnoreCase(FunctionalArea.AMOS.get()) && !isScpBladeAvailable) {
                    appsFailedToStart.add(new FunctionalAreaKey(Amos.class.getSimpleName(), false));
                } else {
                    appsFailedToStart.add(new FunctionalAreaKey(value, isEnabled));
                }
            }
        });
        final ScenarioListener scenarioLogger = new ScenarioLogger();
        if (isUpgrade != null && isUpgrade.length > 0 && isUpgrade[0]) {
            upgradeVerifier = new UpgradeVerifier();
            list.add(flow("StopUpgradeVerifiers").addTestStep(annotatedMethod(this, TestCase.UPGRADE_VERIFIER)));
        } else {
            list.add(flow("StopVerifiers").addTestStep(annotatedMethod(this, TestCase.STOP_VERIFIERS)));
        }
        final TestScenario scenario = scenario("Measuring availability")
                .split(list.toArray(new TestStepFlowBuilder[0])).withExceptionHandler(((ScenarioLogger) scenarioLogger).exceptionHandler())
                .build();
        CommonUtils.runScenario(scenario, scenarioLogger);
    }

    /**
     * Stop Upgrade verification tasks.
     */
    public void stopVerificationTasks() {
        logger.info("stopVerificationTasks called ... !!!");
        try {
            final LocalDateTime localDateTimeStart = LocalDateTime.parse(String.valueOf(upgradeStartTime), dateTimeFormatter);
            final LocalDateTime localDateTimeFinish = LocalDateTime.parse(String.valueOf(upgradeFinishTime), dateTimeFormatter);
            if (isUpgradeFailed()) {
                logger.info("IgnoreDTMap in ug file .... : {}", HAPropertiesReader.ignoreDTMap);
                ugStartTime = localDateTimeStart.format(HAPropertiesReader.formatter);
                ugEndTime = localDateTimeFinish.format(HAPropertiesReader.formatter);
                final AvailabilityReportGenerator availabilityReportGenerator = new AvailabilityReportGenerator(Duration.ofSeconds(0L), ugStartTime, ugEndTime, totalDowntimeValues, totalTimeoutDowntimeValues,
                        downtimeMap, appDateTimeList, appDateTimeTimeoutList, new AppLauncher(), multiMapErrorCollection, appDateTimeLoggerList, appDateTimeTimeoutLoggerList, HAPropertiesReader.ignoreDTMap);
                availabilityReportGenerator.attachTestReport();
                availabilityReportGenerator.attachLauncherReport();
                availabilityReportGenerator.attachDelayedResponseTracker();
                if (HAPropertiesReader.isEnvCloudNative()) {
                    CommonUtils.attachLogFilesToReport();
                    final ImagePullReportGenerator imagePullReportGenerator = new ImagePullReportGenerator(
                            CloudNativeDependencyDowntimeHelper.appImagePullData,
                            CloudNativeDependencyDowntimeHelper.infraImagePullData);
                    imagePullReportGenerator.attachTxt();
                }
                if (!HAPropertiesReader.neoconfig40KFlag) {
                    final CsvFilesReportGenerator csvFilesReportGenerator = new CsvFilesReportGenerator(CommonUtils.txtFiles, CommonUtils.dataFiles);
                    csvFilesReportGenerator.attachTxt();
                }
            } else {
                EnmApplication.setSignal();     //  Stops verifiers.
                final Long highestSystemDowntime = EnmApplication.getDownTimeSysHandler().getDowntime();
                final String systemVerifierSimpleName = SystemVerifier.class.getSimpleName();
                totalDowntimeValues.put(systemVerifierSimpleName, highestSystemDowntime);
                EnmApplication.getDownTimeSysHandler().resetDowntime();

                for (final FunctionalAreaKey fak : appsFailedToStart) {
                    totalDowntimeValues.put(fak.getClassName(), -1L);
                }
                final Duration duration = Duration.between(localDateTimeStart, localDateTimeFinish);
                ugStartTime = localDateTimeStart.format(HAPropertiesReader.formatter);
                ugEndTime = localDateTimeFinish.format(HAPropertiesReader.formatter);
                AppLauncher appLauncher = (AppLauncher) objectMap.get(FunctionalArea.APPLAUNCH);
                if (appLauncher == null) {
                    appLauncher = new AppLauncher();
                }
                logger.info("IgnoreDTMap in ug file....: {}", HAPropertiesReader.ignoreDTMap);
                if (HAPropertiesReader.isEnvCloudNative()) {
                    for (int i = 0; i < 3; i++) {
                        if (!CommonUtils.isIngressWorking()) {
                            if (i < 2) {
                                logger.info("Ingress is not reachable, trying...");
                                sleep(300);
                            } else {
                                fail("Ingress is unreachable after upgrade");
                                throw new Exception("Ingress is not reachable after UG");
                            }
                        } else {
                            break;
                        }
                    }
                }
                final AvailabilityReportGenerator availabilityReportGenerator = new AvailabilityReportGenerator(duration, ugStartTime, ugEndTime, totalDowntimeValues, totalTimeoutDowntimeValues,
                        downtimeMap, appDateTimeList, appDateTimeTimeoutList, appLauncher, multiMapErrorCollection, appDateTimeLoggerList, appDateTimeTimeoutLoggerList, HAPropertiesReader.ignoreDTMap);
                logger.info("deltaDowntimeValues : {}", availabilityReportGenerator.getDeltaDowntimeValues());
                logger.info("appDownTimeMap : {}", appDateTimeList);
                logger.info("depDowntimeMap : {}", downtimeMap);

                for (final FunctionalAreaKey fak : appsFailedToStart) {
                    availabilityReportGenerator.getDeltaDowntimeValues().put(fak.getClassName(), -1l);
                }
                availabilityReportGenerator.attachTestReport();
                availabilityReportGenerator.attachLauncherReport();
                availabilityReportGenerator.attachDelayedResponseTracker();
                if (HAPropertiesReader.isEnvCloudNative()) {
                    CommonUtils.attachLogFilesToReport();
                    final ImagePullReportGenerator imagePullReportGenerator = new ImagePullReportGenerator(
                            CloudNativeDependencyDowntimeHelper.appImagePullData,
                            CloudNativeDependencyDowntimeHelper.infraImagePullData);
                    imagePullReportGenerator.attachTxt();
                }
                if(!HAPropertiesReader.neoconfig40KFlag){
                    final CsvFilesReportGenerator csvFilesReportGenerator = new CsvFilesReportGenerator(CommonUtils.txtFiles, CommonUtils.dataFiles);
                    csvFilesReportGenerator.attachTxt();
                }
                final Long systemDeltaDowntime = availabilityReportGenerator.getDeltaDowntimeValues().get(systemVerifierSimpleName);
                final String sysDowntimeMsg = String.format("System Downtime: Total=%d(s), Delta=%d(s)", highestSystemDowntime, systemDeltaDowntime);
                final Long deltaThreshold = HAPropertiesReader.getFunctionalAreasThresholdMap().get(systemVerifierSimpleName).get(HAPropertiesReader.DELTA);
                logger.info("Assertion Info: systemDeltaDowntime: {}, deltaThreshold: {}, dependencyThresholdErrorList: {}", systemDeltaDowntime, deltaThreshold, AvailabilityReportGenerator.dependencyThresholdErrorList.isEmpty());
                CommonUtils.printDownTime(systemDeltaDowntime, deltaThreshold, sysDowntimeMsg);

                if (systemDeltaDowntime > deltaThreshold || !AvailabilityReportGenerator.dependencyThresholdErrorList.isEmpty()) {
                    logger.info("dependencyThresholdErrorList:{}", AvailabilityReportGenerator.dependencyThresholdErrorList.toString());
                    fail(ENM_DOWNTIME_ASSERTION_THRESHOLD_FAILED);
                }
            }
        } catch (final Exception e) {
            logger.error("Problem during stop verification tasks", e);
        }
    }

    /**
     * Upgrade Verifier.
     */
    public void verifier(final EnmApplication enmApplication) {
        final Class<? extends EnmApplication> clazz = enmApplication.getClass();
        final String appName = clazz.getSimpleName();
        final EnmDowntime enmDowntime = new EnmDowntime(enmApplication);
        final DownTimeHandler downTimeHandler = EnmApplication.getDownTimeAppHandler().get(clazz);

        downTimeHandler.resetDowntime();
        CommonUtils.executeUntilSignal(enmApplication, appName);
        enmApplication.stopAppTimeoutDownTime();
        enmApplication.stopAppDownTime();
        enmApplication.setFirstTimeFail(true);
        enmDowntime.setApplicationDowntime(downTimeHandler.getDowntime());
        enmDowntime.setApplicationTimeoutDowntime(downTimeHandler.getTimeoutDowntime());
        downTimeHandler.resetDowntime();    //reset both timeouts
        if (isUpgradeFailed()) {
            return;
        }
        try {
            if (!(enmApplication instanceof SystemVerifier)) {
                final Long applicationDowntime = enmDowntime.getApplicationDowntime();
                final Long applicationTimeoutDowntime = enmDowntime.getApplicationTimeoutDowntime();
                totalDowntimeValues.put(appName, applicationDowntime);
                totalTimeoutDowntimeValues.put(appName, applicationTimeoutDowntime);
                logger.info("TotalDowntimeValues are : {}, TotalTimeoutDowntimeValues are : {}", totalDowntimeValues, totalTimeoutDowntimeValues);
                if(!enmApplication.getAppDateTimeList.isEmpty()) {
                    appDateTimeList.putAll(enmApplication.getAppDateTimeList);
                    if(appName.equalsIgnoreCase(HAPropertiesReader.ENMSYSTEMMONITOR)){
                        esmDowntimeMap.putAll(appDateTimeList.get(appName));
                    }
                    logger.info("appDateTimeList for {} = {}", appName, appDateTimeList.get(appName));
                }
                if(!enmApplication.getAppDateTimeTimeoutList.isEmpty()) {
                    appDateTimeTimeoutList.putAll(enmApplication.getAppDateTimeTimeoutList);
                    logger.info("appDateTimeTimeoutMapList for {} = {}", appName, appDateTimeTimeoutList.get(appName));
                }
                if(!enmApplication.getAppDateTimeLoggerList.isEmpty()) {
                    appDateTimeLoggerList.putAll(enmApplication.getAppDateTimeLoggerList);
                    logger.info("appDateTimeLoggerList for {} = {}", appName, appDateTimeLoggerList.get(appName));
                }
                if(!enmApplication.getAppDateTimeTimeoutLoggerList.isEmpty()) {
                    appDateTimeTimeoutLoggerList.putAll(enmApplication.getAppDateTimeTimeoutLoggerList);
                    logger.info("appDateTimeTimeoutLoggerList for {} = {}", appName, appDateTimeTimeoutLoggerList.get(appName));
                }

                if(HAPropertiesReader.ignoreDTMap.containsKey(appName) && appDateTimeTimeoutLoggerList.containsKey(appName) ){
                    logger.info("before ignore dt {} total dt {} total time out dt {}",appName,totalDowntimeValues.get(appName), totalTimeoutDowntimeValues.get(appName));
                    ProcessDelayedResponses.updateCommonOffset(appName, EnmApplication.getAppDateTimeTimeoutList, appDateTimeTimeoutLoggerList);
                    final long DowntimeValue = (totalDowntimeValues.get(appName) - (HAPropertiesReader.ignoreDtTotalSecs.get(appName) == null ? 0L : HAPropertiesReader.ignoreDtTotalSecs.get(appName)));
                    totalDowntimeValues.put(appName, DowntimeValue > 0L ? DowntimeValue : 0L);
                    final long TimeoutDowntimeValue = totalTimeoutDowntimeValues.get(appName) - (HAPropertiesReader.ignoreDtTotalSecs.get(appName) == null ? 0L : HAPropertiesReader.ignoreDtTotalSecs.get(appName));
                    totalTimeoutDowntimeValues.put(appName, TimeoutDowntimeValue > 0L ? TimeoutDowntimeValue : 0L);
                    logger.info("{} total dt {} total time out dt {}", appName,totalDowntimeValues.get(appName), totalTimeoutDowntimeValues.get(appName));
                }

                if(appName.equalsIgnoreCase(HAPropertiesReader.NETWORKEXPLORER) || appName.equalsIgnoreCase(HAPropertiesReader.FAULTMANAGEMENT)){
                    logger.info("before ignore dt {} total dt {} total time out dt {}",appName,totalDowntimeValues.get(appName), totalTimeoutDowntimeValues.get(appName));
                    ProcessDelayedResponses.appNeo4jClashCheck(appName, EnmApplication.getAppDateTimeTimeoutList, appDateTimeTimeoutLoggerList,downtimeMap,true);
                    ProcessDelayedResponses.appNeo4jClashCheck(appName, EnmApplication.getAppDateTimeList, appDateTimeLoggerList,downtimeMap,false);
                    final long DowntimeValue = (totalDowntimeValues.get(appName) - (HAPropertiesReader.ignoreTimeoutClashNeo4jTotalSecs.get(appName) == null ? 0L : HAPropertiesReader.ignoreTimeoutClashNeo4jTotalSecs.get(appName)) - (HAPropertiesReader.ignoreErrorClashNeo4jTotalSecs.get(appName) == null ? 0L : HAPropertiesReader.ignoreErrorClashNeo4jTotalSecs.get(appName)));
                    totalDowntimeValues.put(appName, DowntimeValue > 0L ? DowntimeValue : 0L);
                    final long TimeoutDowntimeValue = totalTimeoutDowntimeValues.get(appName) - (HAPropertiesReader.ignoreTimeoutClashNeo4jTotalSecs.get(appName) == null ? 0L : HAPropertiesReader.ignoreTimeoutClashNeo4jTotalSecs.get(appName));
                    totalTimeoutDowntimeValues.put(appName, TimeoutDowntimeValue > 0L ? TimeoutDowntimeValue : 0L);
                    logger.info("{} total dt {} total time out dt {}", appName,totalDowntimeValues.get(appName), totalTimeoutDowntimeValues.get(appName));
                }
                final Map<String, Long> downtimeValues = AvailabilityReportGenerator.updateDowntimeValues(totalDowntimeValues, downtimeMap, appDateTimeList);
                Map<String, Long> deltaDtHaProxy;
                if(appName.equalsIgnoreCase(HAPropertiesReader.NETWORKEXPLORER) || appName.equalsIgnoreCase(HAPropertiesReader.FAULTMANAGEMENT)) {
                    final Map<String, Map<Date, Date>> deltaDtValues = ApplicationClashingNeo4j.updateDowntimeMap(appName, appDateTimeList, downtimeMap);
                    deltaDtHaProxy = CalculateHaProxyOffset.updateDowntimeMap(deltaDtValues, downtimeMap, downtimeValues);
                } else {
                    deltaDtHaProxy = CalculateHaProxyOffset.updateDowntimeMap(appDateTimeList, downtimeMap, downtimeValues);
                }
                if(!(HAPropertiesReader.isEnvCloud() || HAPropertiesReader.isEnvCloudNative())) {
                    deltaDtHaProxy = PhysicalDependencyDowntimeHelper.removePostgresUpliftOffset(appDateTimeList, deltaDtHaProxy);
                }
                final Long deltaDowntime = deltaDtHaProxy.get(appName);
                final String appDowntimeMsg = String.format("Application Downtime, %s: Total=%d(s), Delta=%d(s)", appName, applicationDowntime, deltaDowntime);
                final Long deltaThreshold = HAPropertiesReader.getFunctionalAreasThresholdMap().get(appName).get(HAPropertiesReader.DELTA);
                logger.info("Assertion info: DeltaDowntime: {}, DeltaThreshold: {}", deltaDowntime, deltaThreshold);
                CommonUtils.printDownTime(deltaDowntime, deltaThreshold, appDowntimeMsg);
                if(HighAvailabilityTestCase.multiMapErrorCollection.containsKey(appName) && HighAvailabilityTestCase.multiMapErrorCollection.get(appName).contains("Disabled")) {
                    logger.info("multiMapErrorCollection contains data for {} : {}", appName, HighAvailabilityTestCase.multiMapErrorCollection.get(appName));
                } else if (deltaDowntime > deltaThreshold) {
                    logger.info("dependencyThresholdErrorList:{}", AvailabilityReportGenerator.dependencyThresholdErrorList.toString());
                    fail(ENM_DOWNTIME_ASSERTION_THRESHOLD_FAILED);
                }
                if(enmApplication instanceof AppLauncher) {
                    AppLauncher appLauncher = (AppLauncher) enmApplication;
                    if (appLauncher.isFailedRequests()) {
                        logger.info("verifier: Application Launcher failed: All applications have failed HTTP Requests");
                        fail("Application Launcher failed: All applications have failed HTTP Requests");
                    }
                    if (!appLauncher.getMissingLinksMap().isEmpty()) {
                        logger.info("verifier: Application Launcher failed: UI links for few applications are missing");
                        fail("Application Launcher failed: UI links for few applications are missing");
                    }
                }
                enmApplication.logout();
            } else {
                final long highestSystemDowntime = EnmApplication.getDownTimeSysHandler().getDowntime();
                final long systemTimeoutDowntime = EnmApplication.getDownTimeSysHandler().getTimeoutDowntime();
                totalDowntimeValues.put(appName, highestSystemDowntime);
                totalTimeoutDowntimeValues.put(appName, systemTimeoutDowntime);
                Map<String, Long> downtimeValues = AvailabilityReportGenerator.updateDowntimeValues(totalDowntimeValues, downtimeMap, appDateTimeList);
                if(!(HAPropertiesReader.isEnvCloud() || HAPropertiesReader.isEnvCloudNative())) {
                    downtimeValues = PhysicalDependencyDowntimeHelper.removePostgresUpliftOffset(appDateTimeList, downtimeValues);
                }
                final long deltaDowntime = downtimeValues.get(appName);
                final long deltaThreshold = HAPropertiesReader.getFunctionalAreasThresholdMap().get(appName).get(HAPropertiesReader.DELTA);
                logger.info("HighestSystemDowntime: {}, deltaDowntime: {}, deltaThreshold: {}", highestSystemDowntime, deltaDowntime,deltaThreshold);

                if (deltaDowntime > deltaThreshold) {
                    logger.info("dependencyThresholdErrorList:{}", AvailabilityReportGenerator.dependencyThresholdErrorList.toString());
                    fail(ENM_DOWNTIME_ASSERTION_THRESHOLD_FAILED);
                }
            }
        } catch (final Exception e) {
            logger.warn(e.getMessage(), e);
        }
    }

    public static long calculateRhelEsmOffset(){
        List<LocalDateTime> rhelRebootTimings = UpgradeVerifier.getRhelRebootTimings();
        logger.info("RHEL Reboot timings are : {}", rhelRebootTimings);
        logger.info("esmDowntimeMap is {}", esmDowntimeMap);
        long rhelEsmOffset = 0L;
        if(!esmDowntimeMap.isEmpty() && !rhelRebootTimings.isEmpty()) {
            for (Entry<Date, Date> entry : esmDowntimeMap.entrySet()) {
                final LocalDateTime esmDtStartTime = entry.getKey().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
                final LocalDateTime esmDtStopTime = entry.getValue().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
                logger.info("Esm downtime windows are start : {}, stop : {}", esmDtStartTime, esmDtStopTime);
                Duration esmRhelOffset = CommonReportHelper.getCommonOffsetFromTwoDowntimeWindows(rhelRebootTimings.get(0), rhelRebootTimings.get(1), esmDtStartTime, esmDtStopTime);
                long offsetInSecs = esmRhelOffset.getSeconds();
                rhelEsmOffset += offsetInSecs;
            }
        }
        logger.info("Downtime to be removed from ESM for RHEL reboot is : {}", rhelEsmOffset);
        return rhelEsmOffset;
    }

    @TestStep(id = TestCase.UPGRADE_VERIFIER)
    public void upgradeVerifier() {
        try {
            EnmApplication.setSignal();   //  Stops verifiers.
            upgradeStartTime = upgradeVerifier.waitForUpgradeStart(testSuiteStartTimeForUpgrade);
            if (upgradeStartTime > 0) {
                UpgradeUtils.initWatcherScript();
                if (!HAPropertiesReader.isEnvCloud() && HAPropertiesReader.neoconfig60KFlag) {
                    UpgradeUtils.initPhysicalNeo4j();
                }
                EnmApplication.getDownTimeSysHandler().resetDowntime();
                logger.info("Measuring availability during the upgrade procedure.");
                if (!HAPropertiesReader.isEnvCloud() && !HAPropertiesReader.isEnvCloudNative()) {
                    if (upgradeVerifier.checkPhysicalUpgradeIsFailed(upgradeStartTime)) {
                        upgradeFailed = true;
                        upgradeFailedFlag = true;
                        logger.error("Failed to measureUpgradeAvailability because upgrade failed.");
                    } else {
                        EnmApplication.clearSignal();   //Starts verifiers on Physical.
                    }
                } else {
                    EnmApplication.clearSignal();   //Starts verifiers on cloud.
                }
                if (upgradeFailed) {
                    upgradeFinishTime = Long.parseLong(LocalDateTime.now().format(dateTimeFormatter));
                } else {
                    upgradeFinishTime = upgradeVerifier.waitForUpgradeFinish(upgradeStartTime);
                }

            } else {
                logger.error("Failed to measureUpgradeAvailability");
                upgradeStartTime = upgradeFinishTime = Long.parseLong(LocalDateTime.now().format(dateTimeFormatter));
                failedToWaitForUpgrade = true;
            }
        } catch (final Exception t) {
            logger.error("Failed to measureUpgradeAvailability", t);
        } finally {
            if (isUpgradeFailed()) {
                EnmApplication.clearSignal();   //  Starts verifiers.
                sleep(1);
                EnmApplication.setSignal();     //  Stops verifiers.
            } else {
                downtimeMap.putAll(upgradeVerifier.buildKnownDowntimeMap());
                EnmApplication.clearSignal();   //  Starts verifiers.
                sleep(1);
                logger.info("Stopping verifiers!!");
                EnmApplication.setSignal();     //  Stops verifiers.
            }
            if (HAPropertiesReader.isEnvCloudNative()) {
                //remove data in future!
            } else if (HAPropertiesReader.isEnvCloud()) {
                UpgradeUtils.removeNeoData("cloud", true);
            } else if (HAPropertiesReader.neoconfig60KFlag) {
                UpgradeUtils.removeNeoData("physical", true);
            }
        }
    }

    public static boolean isUpgradeFailed() {
        return (failedToWaitForUpgrade || upgradeFailed || UpgradeVerifier.isUpgradeTimeExceeded);
    }
    /**
     * Verifier Test cases.
     */
    @TestStep(id = TestCase.CM)
    public void executeCM() {
        logger.info("isUpgradeFailed() : {}", isUpgradeFailed());
        if(!isUpgradeFailed()) {
            logger.info(EXECUTED_BY_V_USER, TafTestContext.getContext().getVUser());
            ConfigurationManagement configurationManagement = (ConfigurationManagement) objectMap.get(FunctionalArea.CM);
            observers.add(configurationManagement);
            verifier(configurationManagement);
        }
    }

    @TestStep(id = TestCase.NBIVA)
    public void executeNbiva() {
        if(!isUpgradeFailed()) {
            logger.info(EXECUTED_BY_V_USER, TafTestContext.getContext().getVUser());
            NbiAlarmVerifier nbiAlarmVerifier = (NbiAlarmVerifier) objectMap.get(FunctionalArea.NBIVA);
            observers.add(nbiAlarmVerifier);
            verifier(nbiAlarmVerifier);
        }
    }

    @TestStep(id = TestCase.NBIVS)
    public void executeNbivs() {
        if(!isUpgradeFailed()) {
            logger.info(EXECUTED_BY_V_USER, TafTestContext.getContext().getVUser());
            NbiCreateSubsVerifier nbiCreateSubsVerifier = (NbiCreateSubsVerifier) objectMap.get(FunctionalArea.NBIVS);
            observers.add(nbiCreateSubsVerifier);
            verifier(nbiCreateSubsVerifier);
        }
    }

    @TestStep(id = TestCase.PM)
    public void executePM() {
        if(!isUpgradeFailed()) {
            logger.info(EXECUTED_BY_V_USER, TafTestContext.getContext().getVUser());
            PerformanceManagement performanceManagement = (PerformanceManagement) objectMap.get(FunctionalArea.PM);
            observers.add(performanceManagement);
            verifier(performanceManagement);
        }
    }

    @TestStep(id = TestCase.PMR)
    public void executePMR() {
        if(!isUpgradeFailed()) {
            logger.info(EXECUTED_BY_V_USER, TafTestContext.getContext().getVUser());
            PerformanceManagementRouter performanceManagementRouter = (PerformanceManagementRouter) objectMap.get(FunctionalArea.PMR);
            observers.add(performanceManagementRouter);
            verifier(performanceManagementRouter);
        }
    }

    @TestStep(id = TestCase.AMOS)
    public void executeAmos() {
        if(!isUpgradeFailed()) {
            logger.info(EXECUTED_BY_V_USER, TafTestContext.getContext().getVUser());
            Amos amosApp = (Amos) objectMap.get(FunctionalArea.AMOS);
            observers.add(amosApp);
            verifier(amosApp);
        }
    }

    @TestStep(id = TestCase.FM)
    public void executeFM() {
        if(!isUpgradeFailed()) {
            logger.info(EXECUTED_BY_V_USER, TafTestContext.getContext().getVUser());
            FaultManagement faultManagement = (FaultManagement) objectMap.get(FunctionalArea.FM);
            observers.add(faultManagement);
            verifier(faultManagement);
        }
    }

    @TestStep(id = TestCase.FLS)
    public void executeFLS() {
        if(!isUpgradeFailed()) {
            logger.info(EXECUTED_BY_V_USER, TafTestContext.getContext().getVUser());
            FileLookupService fileLookupService = (FileLookupService) objectMap.get(FunctionalArea.FLS);
            observers.add(fileLookupService);
            verifier(fileLookupService);
        }
    }

    @TestStep(id = TestCase.FMR)
    public void executeFMR() {
        if(!isUpgradeFailed()) {
            logger.info(EXECUTED_BY_V_USER, TafTestContext.getContext().getVUser());
            FaultManagementRouter faultManagementRouter = (FaultManagementRouter) objectMap.get(FunctionalArea.FMR);
            observers.add(faultManagementRouter);
            verifier(faultManagementRouter);
        }
    }

    @TestStep(id = TestCase.FMB)
    public void executeFMB() {
        if(!isUpgradeFailed()) {
            logger.info(EXECUTED_BY_V_USER, TafTestContext.getContext().getVUser());
            FaultManagementBsc faultManagementBsc = (FaultManagementBsc) objectMap.get(FunctionalArea.FMB);
            observers.add(faultManagementBsc);
            verifier(faultManagementBsc);
        }
    }

    @TestStep(id = TestCase.UM)
    public void executeUM() {
        if(!isUpgradeFailed()) {
            logger.info(EXECUTED_BY_V_USER, TafTestContext.getContext().getVUser());
            UserManagement userManagement = (UserManagement) objectMap.get(FunctionalArea.UM);
            observers.add(userManagement);
            verifier(userManagement);
        }
    }

    @TestStep(id = TestCase.NETEX)
    public void executeNETEX() {
        if(!isUpgradeFailed()) {
            logger.info(EXECUTED_BY_V_USER, TafTestContext.getContext().getVUser());
            NetworkExplorer networkExplorer = (NetworkExplorer) objectMap.get(FunctionalArea.NETEX);
            observers.add(networkExplorer);
            verifier(networkExplorer);
        }
    }

    @TestStep(id = TestCase.SHM)
    public void executeSHM() {
        if(!isUpgradeFailed()) {
            logger.info(EXECUTED_BY_V_USER, TafTestContext.getContext().getVUser());
            SoftwareHardwareManagement softwareHardwareManagement = (SoftwareHardwareManagement) objectMap.get(FunctionalArea.SHM);
            observers.add(softwareHardwareManagement);
            verifier(softwareHardwareManagement);
        }
    }

    @TestStep(id = TestCase.SMRS)
    public void executeSMRS() {
        if(!isUpgradeFailed()) {
            logger.info(EXECUTED_BY_V_USER, TafTestContext.getContext().getVUser());
            ShmSmrs shmSmrs = (ShmSmrs) objectMap.get(FunctionalArea.SMRS);
            observers.add(shmSmrs);
            verifier(shmSmrs);
        }
    }

    @TestStep(id = TestCase.ESM)
    public void executeESM() {
        if(!isUpgradeFailed()) {
            logger.info(EXECUTED_BY_V_USER, TafTestContext.getContext().getVUser());
            EnmSystemMonitor enmSystemMonitor = (EnmSystemMonitor) objectMap.get(FunctionalArea.ESM);
            observers.add(enmSystemMonitor);
            verifier(enmSystemMonitor);
        }
    }

    @TestStep(id = TestCase.CMBE)
    public void executeCMBE() {
        if(!isUpgradeFailed()) {
            logger.info(EXECUTED_BY_V_USER, TafTestContext.getContext().getVUser());
            CmBulkExport cmBulkExport = (CmBulkExport) objectMap.get(FunctionalArea.CMBE);
            observers.add(cmBulkExport);
            verifier(cmBulkExport);
        }
    }

    @TestStep(id = TestCase.CMBIL)
    public void executeCMBIL() {
        if(!isUpgradeFailed()) {
            logger.info(EXECUTED_BY_V_USER, TafTestContext.getContext().getVUser());
            CmImportToLive cmImportToLive = (CmImportToLive) objectMap.get(FunctionalArea.CMBIL);
            observers.add(cmImportToLive);
            verifier(cmImportToLive);
        }
    }

    @TestStep(id = TestCase.SYSTEM)
    public void executeSystemLogin() {
        if(!isUpgradeFailed()) {
            logger.info(EXECUTED_BY_V_USER, TafTestContext.getContext().getVUser());
            SystemVerifier systemVerifier = (SystemVerifier) objectMap.get(FunctionalArea.SYSTEM);
            observers.add(systemVerifier);
            verifier(systemVerifier);
        }
    }

    @TestStep(id = TestCase.CESM)
    public void executeCESM() {
        if(!isUpgradeFailed()) {
            logger.info(EXECUTED_BY_V_USER, TafTestContext.getContext().getVUser());
            CnomSystemMonitor cnomSystemMonitor = (CnomSystemMonitor) objectMap.get(FunctionalArea.CESM);
            observers.add(cnomSystemMonitor);
            verifier(cnomSystemMonitor);
        }
    }

    @TestStep(id = TestCase.LAUNCHER)
    public void executeLauncher() {
        if(!isUpgradeFailed()) {
            logger.info(EXECUTED_BY_V_USER, TafTestContext.getContext().getVUser());
            AppLauncher appLauncher = (AppLauncher) objectMap.get(FunctionalArea.APPLAUNCH);
            observers.add(appLauncher);
            verifier(appLauncher);
        }
    }

    @TestStep(id = TestCase.AALDAP)
    public void executeComAaLdap() {
        //Do Nothing!
    }

    @TestStep(id = TestCase.FAN)
    public void executeFAN() {
        if(!isUpgradeFailed()) {
            logger.info(EXECUTED_BY_V_USER, TafTestContext.getContext().getVUser());
            FileAccessNBI fileAccessNBI = (FileAccessNBI) objectMap.get(FunctionalArea.FAN);
            observers.add(fileAccessNBI);
            verifier(fileAccessNBI);
        }
    }

    @TestStep(id = TestCase.STOP_VERIFIERS)
    public void stopVerifiers() {
        upgradeStartTime = Long.parseLong(LocalDateTime.now().format(dateTimeFormatter));
        sleep(HA_VERIFICATION_TIME);
        upgradeFinishTime = Long.parseLong(LocalDateTime.now().format(dateTimeFormatter));
        EnmApplication.setSignal();     //  Stops verifiers.
    }

    private Runnable getRunnable(final boolean... isUpgrade) {
        return () -> {
            if (isUpgrade != null && isUpgrade.length > 0 && isUpgrade[0]) {
                while (EnmApplication.getSignal()) {
                    sleep(1);
                }
            }
        };
    }
}
