package com.ericsson.nms.rv.core.upgrade;


import static com.ericsson.nms.rv.taf.tools.Constants.EMPTY_STRING;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Map;
import java.util.List;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.ericsson.cifwk.taf.tools.http.HttpResponse;
import com.ericsson.nms.rv.core.EnmApplication;
import com.ericsson.nms.rv.core.HAPropertiesReader;
import com.ericsson.nms.rv.core.util.CliResult;
import com.ericsson.nms.rv.core.util.CliShell;
import com.ericsson.nms.rv.core.util.CommonUtils;
import com.ericsson.nms.rv.core.util.UpgradeUtils;
import com.ericsson.nms.rv.taf.tools.Constants.RegularExpressions;
import com.ericsson.sut.test.cases.HighAvailabilityTestCase;

/**
 * 0.   Get start time (@timeBeforeUpgrade)
 * 1.   Wait for "log_cmdline_args.+upgrade_enm" in /var/log/enminst.log
 * 2.   Parse command line to check if reboot required (-p or --patch)
 * 3.a  If reboot required, wait for "check_reboot_required.+Reboot.+is" in /var/log/enminst.log
 * 3.b  else skip this step
 * 4.   Wait for "execute_.+Upgrade.+has.+been.+started" in /var/log/enminst.log
 * 5.   Disable of db dependencies(Neo4j,Opendj) DT calculation
 * <p>
 * .... Execute Test Availability during the Upgrade
 * <p>
 * 9.   Wait for "execute_.+System.+upgrade" in /var/log/enminst.log
 */
public class UpgradeVerifier {
    private static final Logger logger = LogManager.getLogger(UpgradeVerifier.class);

    private static final String SERVER_LOG = " /ericsson/3pp/jboss/standalone/log/server.log*";
    private static final String ENMINST_LOG = " /var/log/enminst.log*";
    private static final String TIME_STAMP = "[[:digit:]]+.+[[:upper:]]+.+";
    private static final String TIME_STAMP_CLOUD = "[[:digit:]]+.+[[:upper:]]+.+";
    private static final String AWK = "/bin/awk '/%s%s/{m=$0;l=NR}END{print m}'";
    private static final String EGREP = "/bin/egrep -h '%s%s'";
    private static final String EGREP_CLOUD = "/bin/egrep -h '%s%s'";
    private static final String GREP = " | /bin/grep '[a-z0-9]'";
    private static final String TAIL = " | /bin/cut -f 1,2 -d ' ' | /usr/bin/tr -d '\\-/ :' | awk -F ',.' '{print $1}' | /bin/awk '$0 >=\"%d\"' | /usr/bin/head -1 ";

    private static final int TIME_TO_SLEEP = HAPropertiesReader.getTimeToSleep();
    private static final String LOG_CMDLINE_ARGS_UPGRADE_ENM_TIME = String.format(EGREP, TIME_STAMP, "log_cmdline_args.+upgrade_enm") + ENMINST_LOG + TAIL + GREP;
    private static final String LOG_CMDLINE_ARGS_UPGRADE_ENM_REBOOT = String.format(AWK, "%s.+", "log_cmdline_args.+upgrade_enm") + ENMINST_LOG + GREP;
    private static final String UPGRADE_PHYSICAL_CHECK_REBOOT_REQUIRED = String.format(EGREP, TIME_STAMP, "check_reboot_required.+Reboot.+is") + ENMINST_LOG + TAIL + GREP;
    private static final String UPGRADE_PHYSICAL_HAS_BEEN_STARTED = String.format(EGREP, TIME_STAMP, "execute_.+Upgrade.+has.+been.+started") + ENMINST_LOG + TAIL + GREP;
    private static final String UPGRADE_PHYSICAL_FINISHED = String.format(EGREP, TIME_STAMP, "execute_.+System.+successfully.+upgraded") + ENMINST_LOG + TAIL + GREP;
    private static final String UPGRADE_PHYSICAL_FAILED = String.format(EGREP, TIME_STAMP, "(execute_.+System.+upgrade.+failed|Failed.+to.+execute.+create_run_plan|Do.+not.+proceed.+with.+the.+uplift)") + ENMINST_LOG + TAIL + GREP;
    private static final String UPGRADE_CLOUD_STARTED = String.format(EGREP_CLOUD, TIME_STAMP_CLOUD, "WorkflowInstanceServiceImpl.*Successfully.+started.+workflow.+with.+id.+Upgrade.*and.+business.+key.+ENM.+Upgrade") + SERVER_LOG + TAIL + GREP;
    private static final String UPGRADE_CLOUD_FINISHED = String.format(EGREP_CLOUD, TIME_STAMP_CLOUD, "WorkflowLogger.+(Failed to create volume from snapshot |All snapshots are deleted| post deployment tasks.+executed)") + SERVER_LOG + TAIL + GREP;
    private static final String isRHELCommand = "/bin/egrep -h 'check_reboot_required.*Reboot.is.required.to.update.the.kernel'  /var/log/enminst.log*  " +
            "| /bin/cut -f 1,2 -d ' ' | /usr/bin/tr -d '\\-/ :'  | /bin/awk '$0 >=\"%d\" && $0<=\"%d\"'";
    private static final String RHEL_REBOOT_START_COMMAND = "/bin/egrep -h 'handle_reboot.*Please.shutdown.the.system.manually.*Please.continue.the.upgrade.after.restart.to.propagate.the.updates.to.peer.nodes'  /var/log/enminst.log*  " +
            "| /bin/cut -f 1,2 -d ' ' | /usr/bin/tr -d '\\-/ :'  | /bin/awk '$0 >=\"%d\" && $0<=\"%d\"'";
    private static final String RHEL_REBOOT_FINISH_COMMAND = "/bin/egrep -h 'Process.Upgrade.options'  /var/log/enminst.log*  " +
            "| /bin/cut -f 1,2 -d ' ' | /usr/bin/tr -d '\\-/ :'  | /bin/awk '$0 >=\"%d\" && $0<=\"%d\"'";
    private static final String ESMON_ONLINE_COMMAND = "sudo /bin/zgrep -h 'EventMemberJoin: esmon' $(sudo find /var/log -type f -mtime -%d -exec ls {} + | grep messages) | gawk -F' '  '{ print $6 }' | gawk -F'.'  '{ print $1 }' | /usr/bin/tr -d '\\-/ :T' | /bin/sort | /bin/awk '$0 >=\"%d\" && $0<=\"%d\"' | head -n 1";
    private static final String WAITING_FOR_UPGRADE_TO = "Waiting for UPGRADE to %s....";
    private static final String FAILED_TO_WAIT_FOR_UPGRADE = "Failed to wait for Upgrade.";
    private static final String START = "start";
    private static final String FINISH = "finish";
    private static final String YYYY_MMDD_HHMMSS = "yyyyMMddHHmmss";
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern(YYYY_MMDD_HHMMSS);
    public static String upgradeStartTimeCloud;
    public static String upgradeFinishTimeCopy;
    public static int haproxyInstance = 0;
    public static boolean isUpgradeTimeExceeded = false;
    public static long rhelRebootDuration = 0L;
    static long upgradeStartTime;
    static long upgradeFinishTime;
    static final String ENGINE_ALOG = " /var/VRTSvcs/log/engine_*.log";
    static final String NEO4J_LEADER = HAPropertiesReader.NEO4J_LEADER;
    static final String NEO4J_FOLLOWER = HAPropertiesReader.NEO4J_FOLLOWER;
    static CliShell SHELL_MS;
    static String JMSDATE;

    static {
        if (!HAPropertiesReader.isEnvCloudNative()) {
            SHELL_MS = new CliShell(HAPropertiesReader.getMS());
        }
    }

    static long setRegressionStarttime() {
        upgradeStartTime = Long.parseLong(LocalDateTime.now().format(formatter));
        return upgradeStartTime;
    }

    static long setRegressionFinishtime() {
        upgradeFinishTime = Long.parseLong(LocalDateTime.now().format(formatter));
        return upgradeFinishTime;
    }

    final long waitForUpgradeStart(final long timeBeforeUpgrade) {
        JMSDATE = String.valueOf(timeBeforeUpgrade);
        logger.info("JMS UG DAT is {}", JMSDATE);
        if (HAPropertiesReader.isEnvCloudNative()) {
            if(HAPropertiesReader.isDeploymentCCD()) {
                //cENM comment. Set CCD upgrade type in adu-watcher.
                final HttpResponse httpResponse = CloudNativeDependencyDowntimeHelper.generateHttpPostResponse("watcher/adu/upgrade/type/ccd");
                logger.info("Set CCD Upgrade Type httpResponse : {}", httpResponse.getResponseCode().getCode());
                upgradeStartTime = Long.parseLong(LocalDateTime.now().format(formatter));
                //write ccd_status file. watcher-init will start watchers automatically.
                final HttpResponse writeCCDStatus = CloudNativeDependencyDowntimeHelper.generateHttpPostResponse("watcher/adu/upgrade/ccd/running");
                logger.info("Write CCD Upgrade running state response : {}", writeCCDStatus.getResponseCode().getCode());
            } else {
                //cENM comment. Set ENM upgrade type in adu-watcher.
                final HttpResponse httpResponse = CloudNativeDependencyDowntimeHelper.generateHttpPostResponse("watcher/adu/upgrade/type/enm");
                logger.info("Set ENM Upgrade Type httpResponse : {}", httpResponse.getResponseCode().getCode());
                UpgradeUtils.startStopCloudNativeWatcherScript("upgrade", "start");
                CommonUtils.sleep(2);
                upgradeStartTime = waitForCloudNativeUpgradeStart(timeBeforeUpgrade);
            }
        } else if (HAPropertiesReader.isEnvCloud()) {
            upgradeStartTime = waitForCloudUpgradeStart(timeBeforeUpgrade);
            upgradeStartTimeCloud = String.valueOf(upgradeStartTime);
        } else {
            upgradeStartTime = waitForPhysicalUpgradeStart(timeBeforeUpgrade);
        }
        logger.debug("upgradeStartTime\t=\t{}", upgradeStartTime);
        return upgradeStartTime;
    }

    /**
     * cENM comment.
     * Read Upgrade start status from from adu-watcher pod.
     * @param timeBeforeUpgrade
     * @return
     */
    private long waitForCloudNativeUpgradeStart(final long timeBeforeUpgrade) {
        final LocalDateTime startDate = LocalDateTime.parse(String.valueOf(timeBeforeUpgrade), formatter);
        LocalDateTime currentDate = LocalDateTime.now();
        final String uri = "watcher/adu/upgrade/status";
        boolean isWaitingForUpgrade = false;
        while ((Duration.between(startDate, currentDate).getSeconds()) < HAPropertiesReader.cloudUgStartTimeLimit) {
            try {
                final HttpResponse httpResponse = CloudNativeDependencyDowntimeHelper.generateHttpGetResponse(uri);
                if (!EnmApplication.acceptableResponse.contains(httpResponse.getResponseCode())) {
                    throw new Exception("HttpResponse code : " + httpResponse.getResponseCode().getCode());
                }
                logger.info("httpResponse : {}", httpResponse.getBody());
                final String status = httpResponse.getBody().trim();
                logger.info("cENM Upgrade status: {}", status);
                if (status.contains("running")) {
                    final String upgradeStartDate = status.split(",")[0];
                    logger.info("waitForCloudNativeUpgradeStart -- upgradeStartDate : {}", upgradeStartDate);
                    return Long.parseLong(upgradeStartDate);
                } else if (status.contains("failed")) {
                    logger.warn("Upgrade init failed.");
                    HighAvailabilityTestCase.upgradeFailed = true;
                    UpgradeUtils.startStopCloudNativeWatcherScript("upgrade", "stop");
                    return 0L;
                } else if (status.contains("waiting")) {
                    isWaitingForUpgrade = true;
                }
                CommonUtils.sleep(10);
                currentDate = LocalDateTime.now();
            } catch (final Exception e) {
                currentDate = LocalDateTime.now();
                logger.warn("Message : {}", e.getMessage());
                if (isWaitingForUpgrade) {
                    //UG started if exception occurred in ingress call during wait for upgrade.
                    logger.info("waitForCloudNativeUpgradeStart -- exception after waiting.");
                    final SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
                    final long upgradeStartDate = Long.parseLong(sdf.format(new Date()));
                    logger.info("waitForCloudNativeUpgradeStart -- upgradeStartDate : {}", upgradeStartDate);
                    return upgradeStartDate;
                }
            }
        }
        logger.warn("Failed to wait for cENM upgrade start.");
        UpgradeUtils.startStopCloudNativeWatcherScript("upgrade", "stop");
        return 0L;
    }

    /**
     * cENM comment.
     * Read Upgrade stop status from from adu-watcher pod.
     * @param timeStart
     * @return
     */
    private long waitForCloudNativeUpgradeFinish(final long timeStart) {
        final LocalDateTime startDate = LocalDateTime.parse(String.valueOf(timeStart), formatter);
        LocalDateTime currentDate = LocalDateTime.now();
        final String uri = "watcher/adu/upgrade/status";
        while ((Duration.between(startDate, currentDate).getSeconds()) < HAPropertiesReader.cloudUgFinishTimeLimit) {
            try {
                final HttpResponse httpResponse = CloudNativeDependencyDowntimeHelper.generateHttpGetResponse(uri);
                logger.info("waitForCloudNativeUpgradeFinish -- httpResponse : {}", httpResponse.getBody());
                final String status = httpResponse.getBody().trim();
                logger.info("cENM Upgrade status: {}", status);
                if (status.contains("finished")) {
                    final String finishDate = status.split(",")[0];
                    logger.info("waitForCloudNativeUpgradeFinish -- finishDate : {}", finishDate);
                    upgradeFinishTime = Long.parseLong(finishDate);
                    break;
                } else if (status.contains("failed")) {
                    HighAvailabilityTestCase.upgradeFailed = true;
                    final String failedDate = status.split(",")[0];
                    logger.info("waitForCloudNativeUpgradeFinish -- failedDate : {}", failedDate);
                    upgradeFinishTime = Long.parseLong(failedDate);
                    break;
                }
                CommonUtils.sleep(TIME_TO_SLEEP);
                currentDate = LocalDateTime.now();
            } catch (final Exception e) {
                currentDate = LocalDateTime.now();
                logger.warn("Message : {}", e.getMessage());
                upgradeFinishTime = 0L;
            }
        }
        if ((Duration.between(startDate, currentDate).getSeconds()) >= HAPropertiesReader.cloudUgFinishTimeLimit) {
            logger.warn("Failed to wait for cENM upgrade finish.");
            upgradeFinishTime = Long.parseLong(LocalDateTime.now().format(formatter));
            isUpgradeTimeExceeded = true;
        }
        UpgradeUtils.startStopCloudNativeWatcherScript("upgrade", "stop");
        return upgradeFinishTime;
    }

    /**
     * cENM comment.
     * Wait for CCD Upgrade to finish for specific period of time.
     * @param timeStart
     * @return
     */
    private long waitForCloudNativeUpgradeFinishForCCD(final long timeStart) {
        final LocalDateTime startDate = LocalDateTime.parse(String.valueOf(timeStart), formatter);
        LocalDateTime currentDate = LocalDateTime.now();
        while ((Duration.between(startDate, currentDate).getSeconds()) < HAPropertiesReader.aduDurationOnDemand) {
                CommonUtils.sleep(TIME_TO_SLEEP);
                currentDate = LocalDateTime.now();
        }
        upgradeFinishTime =  Long.parseLong(LocalDateTime.now().format(formatter));
        logger.info("CCD Upgrade Finish Time : {} ", upgradeFinishTime);
        //write ccd_status file to stop watcher scripts in tear down.
        try {
            final HttpResponse writeCCDStatus = CloudNativeDependencyDowntimeHelper.generateHttpPostResponse("watcher/adu/upgrade/ccd/stopped");
            logger.info("Write CCD Upgrade stopped state response : {}", writeCCDStatus.getResponseCode().getCode());
        } catch (Exception e) {
            logger.warn("failed to get CCD Upgrade stopped status, {}", e.getMessage());
        }
        return upgradeFinishTime;
    }

    private long waitForCloudUpgradeStart(final long timeBeforeUpgrade) {
        return executeCommandAndWait(SHELL_MS, String.format(UPGRADE_CLOUD_STARTED, timeBeforeUpgrade), timeBeforeUpgrade, System.nanoTime(), HAPropertiesReader.getWaitForUpgradeToStart(), START, "CloudUpgradeStart" );
    }

    private long waitForPhysicalUpgradeStart(final long timeBeforeUpgrade) {
        if (UpgradeUtils.physicalUpgradeType.equalsIgnoreCase("RH6")) {
            boolean waitForReboot;
            boolean isRebootRequired = false;
            try {
                waitForReboot = waitForCommandLineArgsAndParse(timeBeforeUpgrade);
            } catch (final InterruptedException e) {
                logger.error(FAILED_TO_WAIT_FOR_UPGRADE, e);
                return 0;
            }
            long timeReboot = timeBeforeUpgrade;
            if (waitForReboot) {
                timeReboot = waitForReboot(timeBeforeUpgrade);
            }
            logger.info("PhysicalUpgradeStart info : waitForReboot: {} timeReboot: {} and isRebootNotRequired:{} ", waitForReboot, timeReboot, isRebootRequired);
            if (timeReboot > 0) {
                try {
                    return waitForUpgradeHasBeenStarted(timeBeforeUpgrade);
                } catch (final InterruptedException e) {
                    logger.error(FAILED_TO_WAIT_FOR_UPGRADE, e);
                    return 0;
                }
            } else {
                logger.error(FAILED_TO_WAIT_FOR_UPGRADE);
                return 0;
            }
        } else {
            try {
                return waitForUpgradeHasBeenStarted(timeBeforeUpgrade);
            } catch (final InterruptedException e) {
                logger.error(FAILED_TO_WAIT_FOR_UPGRADE, e);
                return 0;
            }
        }
    }

    final long waitForUpgradeFinish(final long timeStart) {
        if (HAPropertiesReader.isEnvCloudNative()) {
            if(HAPropertiesReader.isDeploymentCCD()) {
                logger.info("Wait for CCD upgrade finish ...");
                upgradeFinishTime = waitForCloudNativeUpgradeFinishForCCD(timeStart);
            } else {
                upgradeFinishTime = waitForCloudNativeUpgradeFinish(timeStart);
            }
        } else {
            while (!isFinished(timeStart)) {
                final String msg = String.format(WAITING_FOR_UPGRADE_TO, FINISH);
                logger.debug(msg);
                CommonUtils.sleep(TIME_TO_SLEEP);
            }
        }
        logger.debug("upgradeFinishTime\t=\t{}", upgradeFinishTime);
        upgradeFinishTimeCopy = String.valueOf(upgradeFinishTime);
        return upgradeFinishTime;
    }

    final Map<String, String> buildKnownDowntimeMap() {
        if (HAPropertiesReader.isEnvCloudNative()) {
            return buildKnownDowntimeMapOnCloudNative();
        } else if (HAPropertiesReader.isEnvCloud()) {
            return buildKnownDowntimeMapOnCloud();
        } else {
            return buildKnownDowntimeMapOnPhysical();
        }
    }

    private Map<String, String> buildKnownDowntimeMapOnCloudNative() {
        CloudNativeDependencyDowntimeHelper cloudNativeDependencyDowntimeHelper = new CloudNativeDependencyDowntimeHelper();
        return cloudNativeDependencyDowntimeHelper.buildKnownDowntimeMapForCloudNative();
    }

    private Map<String, String> buildKnownDowntimeMapOnCloud() {
        CloudDependencyDowntimeHelper cloudDependencyDowntimeHelper = new CloudDependencyDowntimeHelper();
        return cloudDependencyDowntimeHelper.buildKnownDowntimeMapForCloud();
    }

    private Map<String, String> buildKnownDowntimeMapOnPhysical() {
        PhysicalDependencyDowntimeHelper physicalDependencyDowntimeHelper = new PhysicalDependencyDowntimeHelper();
        return physicalDependencyDowntimeHelper.buildKnownDowntimeMapForPhysical();
    }

    private boolean waitForCommandLineArgsAndParse(final long timeBefore) throws InterruptedException {
        long time;
        final long startTime = System.nanoTime();
        time = executeCommandAndWait(SHELL_MS, String.format(LOG_CMDLINE_ARGS_UPGRADE_ENM_TIME, timeBefore), timeBefore, startTime, HAPropertiesReader.getWaitForUpgradeToStart(), START, "log_cmdline");
        logger.info("log_cmdline(time): {} ", time);
        if ((System.nanoTime() - startTime) > HAPropertiesReader.getWaitForUpgradeToStart()) {
            logger.error(FAILED_TO_WAIT_FOR_UPGRADE);
            throw new InterruptedException(FAILED_TO_WAIT_FOR_UPGRADE);
        }
        if (time > 0) {
            final String stdOut = SHELL_MS.executeAsRoot(String.format(LOG_CMDLINE_ARGS_UPGRADE_ENM_REBOOT, convertTime2Pattern(time))).getOutput();
            logger.info("Upgrade enm Reboot output: {} ", stdOut);
            return stdOut.matches(".*\\s-p\\s.*") || stdOut.matches(".*\\s--patch.*");
        } else {
            return false;
        }
    }

    private String convertTime2Pattern(final long time) {
        final DateFormat sdf = new SimpleDateFormat(YYYY_MMDD_HHMMSS);
        final DateFormat sdf1 = new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss");
        try {
            return sdf1.format(sdf.parse(String.valueOf(time)));
        } catch (final ParseException e) {
            logger.warn("Failed to convert Time to Pattern", e);
            return "";
        }
    }

    private long waitForReboot(final long timeBefore) {
        long time;
        final long startTime = System.nanoTime();
        time = executeCommandAndWait(SHELL_MS, String.format(UPGRADE_PHYSICAL_CHECK_REBOOT_REQUIRED, timeBefore), timeBefore, startTime, HAPropertiesReader.getWaitForUpgradeToStart(), START, "waitForReboot");
        if ((System.nanoTime() - startTime) > HAPropertiesReader.getWaitForUpgradeToStart()) {
            logger.error(FAILED_TO_WAIT_FOR_UPGRADE);
            return 0;
        }
        return time;
    }

    private long waitForUpgradeHasBeenStarted(final long timeBefore) throws InterruptedException {
        long time;
        final long startTime = System.nanoTime();
        logger.info("WaitForUpgradeHasBeenStarted args TimeBefore: {}, StartTime: {}, TIME_TO_START: {}", timeBefore, startTime, HAPropertiesReader.getWaitForUpgradeToStart());
        time = executeCommandAndWait(SHELL_MS, String.format(UPGRADE_PHYSICAL_HAS_BEEN_STARTED, timeBefore), timeBefore, startTime, HAPropertiesReader.getWaitForUpgradeToStart(), START, "UpgradeHasbeenStart");
        logger.info("UpgradeHasBeen Started time Info  time: {}", time);
        if ((System.nanoTime() - startTime) > HAPropertiesReader.getWaitForUpgradeToStart()) {
            logger.error(FAILED_TO_WAIT_FOR_UPGRADE);
            throw new InterruptedException(FAILED_TO_WAIT_FOR_UPGRADE);
        }
        return time;
    }

    private long executeCommandAndWait(CliShell shell, final String command, final long timeBefore, final long startTime, final long timeOut, final String message, final String CmdInfo) {
        long time = 0L;
        do {
            try {
                CliResult result = shell.execute(command);
                logger.debug(" {} command is:{}, result output is: {}, result exit code is: {}, timeout information: {}, timeBefore: {} ", CmdInfo, command, result.getOutput(), result.getExitCode(), timeOut, timeBefore);
                logger.debug("Start information: {}, finished-flag: {}", startTime, isFinished(timeBefore));
                final String msg = String.format(WAITING_FOR_UPGRADE_TO, message);
                while (result.getExitCode() != 0 && (System.nanoTime() - startTime) < timeOut && !isFinished(timeBefore)) {
                    logger.debug(msg);
                    CommonUtils.sleep(TIME_TO_SLEEP);
                    result = shell.execute(command);
                }
                time = getTime(result.getOutput());
                if (time < timeBefore && (System.nanoTime() - startTime) < timeOut) {
                    logger.debug(msg);
                    CommonUtils.sleep(TIME_TO_SLEEP);
                }
            } catch (final Exception e) {
                logger.warn("executeCommandAndWait() - Exception : {}", e.getMessage());
                CommonUtils.sleep(TIME_TO_SLEEP);
            }
        } while (time < timeBefore && (System.nanoTime() - startTime) < timeOut && !isFinished(timeBefore));
        return time;
    }


    private boolean isFinished(final long timeStart) {
        try {
            final String command = HAPropertiesReader.isEnvCloud() ?
                    String.format(UPGRADE_CLOUD_FINISHED, timeStart) :
                    String.format(UPGRADE_PHYSICAL_FINISHED, timeStart);
            final CliResult result = SHELL_MS.execute(command);
            final boolean successful = result.isSuccess();
            final long timeLimit = HAPropertiesReader.cloudUgFinishTimeLimit;
            final DateFormat sdf = new SimpleDateFormat(YYYY_MMDD_HHMMSS);
            final LocalDateTime startDate = LocalDateTime.parse(String.valueOf(timeStart), formatter);
            final LocalDateTime currentDate = LocalDateTime.now();
            if (successful) {
                final long time = getTime(result.getOutput());
                final boolean finished = time > timeStart;
                if (finished) {
                    upgradeFinishTime = time;
                    logger.info("Upgrade finished .. time : {}, timeStart : {}", time, timeStart);
                    logger.info("StartUpgradeTime : {}, EndTime : {}, Duration(sec) : {}", formatter.format(startDate), formatter.format(currentDate), Duration.between(startDate, currentDate).getSeconds());
                    return true;
                } else if (!HAPropertiesReader.isEnvCloud()) {
                    if (checkPhysicalUpgradeIsFailed(timeStart)) {
                        logger.error("check -- isFinished() : Upgrade Failed!");
                        UpgradeVerificationTasks.upgradeFailed = true;
                        return true;
                    }
                } else if (HAPropertiesReader.isEnvCloud() && (Duration.between(startDate, currentDate).getSeconds()) > timeLimit) {    //  3.5 hours
                    upgradeFinishTime = Long.parseLong(sdf.format(new Date()));
                    logger.info("StartUpgradeTime : {}, EndTime : {}, Duration(sec) : {}", formatter.format(startDate), formatter.format(currentDate), Duration.between(startDate, currentDate).getSeconds());
                    logger.warn("Failed to wait for Upgrade to Finish !");
                    isUpgradeTimeExceeded = true;
                    return true;
                }
            } else if (HAPropertiesReader.isEnvCloud() && (Duration.between(startDate, currentDate).getSeconds()) > timeLimit) {    //  3.5 hours
                upgradeFinishTime = Long.parseLong(sdf.format(new Date()));
                logger.info("StartUpgradeTime : {}, EndTime : {}, Duration(sec) : {}", formatter.format(startDate), formatter.format(currentDate), Duration.between(startDate, currentDate).getSeconds());
                logger.warn("Failed to wait for Upgrade to Finish !");
                isUpgradeTimeExceeded = true;
                return true;
            } else if (!HAPropertiesReader.isEnvCloud()) {
                if (checkPhysicalUpgradeIsFailed(timeStart)) {
                    logger.error("isFinished() : Upgrade Failed!");
                    UpgradeVerificationTasks.upgradeFailed = true;
                    return true;
                }
            }
        } catch (final Exception e) {
            logger.warn("isFinished() Exception : {}", e.getMessage());
        }
        return false;
    }

    boolean checkPhysicalUpgradeIsFailed(final long timeStart) {
        final String command = String.format(UPGRADE_PHYSICAL_FAILED, timeStart);
        final CliResult result = SHELL_MS.executeAsRoot(command);
        final boolean successful = result.isSuccess();
        if (successful) {
            final long time = getTime(result.getOutput());
            if (time > timeStart) {
                logger.info("UG failed command: {}", command);
                logger.warn("Physical Upgrade failed : {}", result.getOutput());
                return true;
            }
        }
        return false;
    }

    private long getTime(final String result) {
        final String[] splittedResult = result.split(RegularExpressions.SPACE);
        switch (splittedResult.length) {
            case 0:
                logger.warn("splittedResult of getTime is EMPTY.");
                return 0L;
            case 1:
                return getTimeFromSplit(splittedResult[0]);
            default:
                return getTimeFromSplit(splittedResult[0] + splittedResult[1]);
        }
    }

    private long getTimeFromSplit(final String splittedResult) {
        final String time = splittedResult.replaceAll("-", EMPTY_STRING).replaceAll(":", EMPTY_STRING).replaceAll("/", EMPTY_STRING).split("\\.")[0].split(",")[0];
        if (!time.isEmpty() && time.matches("\\d+")) {
            logger.info("getTimeFromSplit is :{}", Long.parseLong(time));
            return Long.parseLong(time);
        } else {
            logger.warn("getTimeFromSplit - split of getTime is EMPTY: {}", splittedResult);
            return 0L;
        }
    }

    public static List<LocalDateTime> getRhelRebootTimings() {
        List<LocalDateTime> rhelRebootTimings = new ArrayList<>();
        try {
            String command = String.format(isRHELCommand, upgradeStartTime, upgradeFinishTime);
            final CliShell shell = new CliShell(HAPropertiesReader.getMS());
            final CliResult cliResult = shell.execute(command);
            if (cliResult.isSuccess()) {
                logger.info("RHEL command output is {}", cliResult.getOutput());
                if (!cliResult.getOutput().isEmpty()) {
                    final CliResult startResult = shell.execute(String.format(RHEL_REBOOT_START_COMMAND, upgradeStartTime, upgradeFinishTime));
                    logger.info("Start result is {}", startResult.getOutput());
                    if (startResult.isSuccess() && !startResult.getOutput().isEmpty()) {
                        long rhelStartDate = Long.parseLong(startResult.getOutput().substring(0,14));
                        final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
                        LocalDateTime ugStartLocalDate = LocalDateTime.parse(String.valueOf(upgradeStartTime), formatter);
                        long days = Duration.between(ugStartLocalDate, LocalDateTime.now()).toDays() + 1;
                        logger.info("ESMON_ONLINE_COMMAND : {}", (String.format(ESMON_ONLINE_COMMAND, days, rhelStartDate, upgradeFinishTime)));
                        final CliResult endDateResult = shell.execute(String.format(ESMON_ONLINE_COMMAND, days, rhelStartDate, upgradeFinishTime));
                        logger.info("End result is {}", endDateResult.getOutput());
                        if (endDateResult.isSuccess() && !endDateResult.getOutput().isEmpty()) {
                            try {
                                final SimpleDateFormat SIMPLE_DATE_FORMAT = new SimpleDateFormat("yyyyMMddHHmmss");
                                rhelRebootTimings.add(SIMPLE_DATE_FORMAT.parse(startResult.getOutput()).toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
                                rhelRebootTimings.add(SIMPLE_DATE_FORMAT.parse(endDateResult.getOutput()).toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
                                rhelRebootDuration = Duration.between(rhelRebootTimings.get(0), rhelRebootTimings.get(1)).getSeconds();
                                return rhelRebootTimings;
                            } catch(ParseException e){
                                logger.info("Unable to parse the rhel date&Time : {}", e.getMessage());
                            }
                        } else {
                            logger.info("Failed to execute command to get rhel reboot finish time : {}", endDateResult.getOutput());
                        }
                    } else {
                        logger.info("Rhel Reboot start command isn't successful {}", startResult.getOutput());
                    }
                } else {
                    logger.info("RHEL patch is not included in this Upgrade.");
                }
            } else {
                logger.info("Failed to execute the RHEL command.");
            }
        } catch (Exception e) {
            logger.info("Failed to run the RHEL command: {}", e.getMessage());
        }
        return Collections.emptyList();
    }
}