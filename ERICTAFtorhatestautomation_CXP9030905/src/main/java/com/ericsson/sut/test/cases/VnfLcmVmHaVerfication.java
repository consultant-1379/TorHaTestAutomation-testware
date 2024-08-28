package com.ericsson.sut.test.cases;

import com.ericsson.cifwk.taf.annotations.TestId;
import com.ericsson.cifwk.taf.data.Host;
import com.ericsson.cifwk.taf.data.HostType;
import com.ericsson.nms.rv.core.HAPropertiesReader;
import com.ericsson.nms.rv.core.upgrade.CloudDependencyDowntimeHelper;
import com.ericsson.nms.rv.core.util.CliResult;
import com.ericsson.nms.rv.core.util.CliShell;
import com.ericsson.nms.rv.taf.tools.Constants;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.testng.annotations.Test;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;

import static org.assertj.core.api.Fail.fail;

public class VnfLcmVmHaVerfication extends HighAvailabilityTestCase {

    private static final Logger logger = LogManager.getLogger(VnfLcmVmHaVerfication.class);

    private final String rootlogin = "sudo -i ";
    private final String stopJboss = rootlogin + "service jboss stop";
    private final String jbossStatusCheck = rootlogin + "service jboss status";
    private final String vnfGrep = "consul members | grep -i vnf";
    private final String filesList = rootlogin + "ls -lrt /vnflcm-ext/";
    private final String enmFilesList = filesList + "enm";
    private final String workflowBundleList = rootlogin + "wfmgr bundle list";
    private final long maxTime = 60 * 30 * Constants.TEN_EXP_9;
    private final long workflowMaxTime = 60 * 90 * Constants.TEN_EXP_9;
    private final String workflowStartString = "Starting workflow with id ha-auto-recovery";
    private final String workflowCompletedString = "Rebuild server is successful";
    private final String logFile = "/ericsson/3pp/jboss/standalone/log/server.log";
    private final String KillEmpVm = "ssh -q -o StrictHostKeyChecking=no -i /var/tmp/Bravo/pemKey.pem cloud-user@%s  'sudo pkill consul'";
    private static final String aliveMemberIpCommand = "consul members | grep %s | grep alive | /bin/tr -s ' ' | head -n 1";
    private long workflowCompTime;
    private long workflowStartTime;
    private final String emp = "emp";


    static final CliShell SHELL_Kill = new CliShell(HAPropertiesReader.getMS());

    @TestId(id = "RTD-11130", title = "VNF LCM VM HA Verification")
    @Test(groups = {"High Availability"})
    public final void VnfLcmVmHaVerficationProcess() {
        logger.info("MS type {}", HAPropertiesReader.getMS().getType());
        try {
            if (HA_VERIFICATION_TIME < 30 * Constants.Time.ONE_MINUTE_IN_SECONDS) {
                throw new IllegalArgumentException("HA verification time must be at least 30 minutes.");
            }
            if (HAPropertiesReader.HaValue) {
                if (!restPassWords()) {
                    fail("Fail to reset password.");
                    return;
                }
                final Host localMs = HAPropertiesReader.getMS();
                logger.info("origin type " + localMs.getType());
                localMs.setType(HostType.UNEXPECTED);
                logger.info("origin type " + localMs.getType());
                final CliShell SHELL_MS = new CliShell(localMs);
                String initialJbossStatusCheckOutput = SHELL_MS.execute(jbossStatusCheck).getOutput();
                logger.info("initialJbossStatusCheckOutput {}", initialJbossStatusCheckOutput);
                if (initialJbossStatusCheckOutput.contains("running")) {
                    final CliResult execResult = SHELL_MS.execute(stopJboss);
                    String stopJbossOutput = execResult.getOutput();
                    logger.info("---------------stopJbossOutput ------------" + stopJbossOutput + execResult.isSuccess());
                    if (execResult.isSuccess()) {
                        final long tcStartTime = Long.parseLong(new SimpleDateFormat("yyyyMMddHHmmss").format(new Date()));
                        final long jbossStopTime = System.nanoTime();
                        logger.info("jbossStopTime {}", jbossStopTime);
                        Thread.sleep(600L * (long) Constants.TEN_EXP_3);
                        CliResult jbossExecResult = SHELL_MS.execute(jbossStatusCheck);
                        String jbossStatusCheckOutput = jbossExecResult.getOutput();

                        logger.info("---------------jbossStatusCheckOutput ------------{}", jbossStatusCheckOutput);
                        if (jbossStatusCheckOutput.contains("running")) {
                            try {
                                CloudDependencyDowntimeHelper.generatingPemKey();
                                startRegressionVerificationTasks();
                                boolean workFlowFail;
                                boolean fail;
                                int retry = 0;
                                do {
                                    workFlowFail = false;
                                    workflowStartTime = getCommandOutput(tcStartTime, workflowStartString, SHELL_MS);
                                    if (workflowStartTime != 0L) {
                                        do {
                                            fail = false;
                                            String vnfGrepOutput = SHELL_MS.execute(vnfGrep).getOutput(); // should contain 2 lafs
                                            logger.info("---------------vnfGrepOutput ------------{}", vnfGrepOutput);
                                            if (vnfGrepOutput.contains("vnflaf-services-0") && vnfGrepOutput.contains("vnflaf-services-1")) {
                                                String filesListOutput = SHELL_MS.execute(filesList).getOutput(); // should contains  backups,current,enm,lost+found,vnf-lcm,vnflcm.versions
                                                logger.info("--------------filesListOutput-------------------{}", filesListOutput);
                                                if (filesListOutput.contains("backups") && filesListOutput.contains("current") && filesListOutput.contains("enm") && filesListOutput.contains("lost+found") && filesListOutput.contains("vnf-lcm") && filesListOutput.contains("vnflcm.versions")) {
                                                    logger.info("---------{}", enmFilesList);
                                                    String enmFilesListOutput = SHELL_MS.execute(enmFilesList).getOutput(); //should contains cloudtemplates, deployed
                                                    logger.info("--------------enmFilesListOutput-------------------{}", enmFilesListOutput);
                                                    if (enmFilesListOutput.contains("cloudtemplates") && enmFilesListOutput.contains("deployed")) {
                                                        logger.info("----workflowBundleList-----{}", workflowBundleList);
                                                        String workflowBundleListOutput = SHELL_MS.execute(workflowBundleList).getOutput();
                                                        logger.info("--------------workflowBundleListOutput-------------------{}", workflowBundleListOutput);
                                                        if (workflowBundleListOutput.contains("ha-auto-recovery") && workflowBundleListOutput.contains("enmdeploymentworkflows") && workflowBundleListOutput.contains("enmcloudperformanceworkflows") && workflowBundleListOutput.contains("enmcloudmgmtworkflows")) {
                                                            continue;
                                                        }
                                                    }
                                                }
                                            }
                                            fail = true;
                                            Thread.sleep(180L * (long) Constants.TEN_EXP_3);
                                        } while (fail && retry++ < 3);
                                    } else {
                                        workFlowFail = true;
                                        fail = true;
                                        Thread.sleep(600L * (long) Constants.TEN_EXP_3);
                                    }
                                } while (fail && workFlowFail && (System.nanoTime() - jbossStopTime < maxTime));

                                Thread.sleep(600L * (long) Constants.TEN_EXP_3);
                                getCompletedTime(jbossStopTime, SHELL_MS);
                                logger.info("workflowCompTime {}", workflowCompTime);
                                logger.info("workflowStartTime {}", workflowStartTime);
                                logger.info("fail {}", fail);
                                logger.info("workFlowFail {}", workFlowFail);

                                if (workflowCompTime - workflowStartTime >= maxTime) {
                                    fail(" VNF LCM auto recovery took more than 30 min");
                                } else if (workFlowFail || fail) {
                                    fail(" VNF LCM auto recovery failed");
                                } else {
                                    Thread.sleep(60L * (long) Constants.TEN_EXP_3);
                                    CloudDependencyDowntimeHelper.generatingPemKey();
                                    final String aliveEmpIp = getMemberIpAddress(emp, SHELL_MS);
                                    final boolean isAliveAgain = killMemberAndWait(aliveEmpIp, emp, SHELL_Kill);
                                    if (!isAliveAgain) {
                                        fail(" Emp Vm is not alive after restart");
                                    }
                                }
                            } catch (final Exception t) {
                                logger.error(t.getMessage(), t);
                                fail(t.getMessage());
                            } finally {
                                stopRegressionVerificationTasks();
                            }
                        }
                    } else {
                        logger.info("secondary jboss is not running");
                        fail("secondary jboss is not running");
                    }
                } else {
                    logger.info("jboss is in invalid state");
                    fail("jboss is in invalid state");
                }
            } else {
                logger.info("Environment is not HA enabled");
                fail("Environment is not HA enabled");
            }
        } catch (final Exception e) {
            logger.info("in catch");
            logger.info(e.getMessage());
            fail(e.getMessage());
        }
    }


    private long getCommandOutput(Long startTime, String command, final CliShell SHELL_MS) throws InterruptedException {
        boolean fail = false;
        int count = 0;
        long cmdOutputTime = 0L;
        final String egrep = "/bin/egrep -h '%s' %s ";
        final StringBuilder stringBuilder = new StringBuilder();
        final String awk = String.format(" | /bin/awk '$0 >=\"%d\" '", startTime);
        final String cutAndTr = " | /bin/cut -f 1,2 -d ' ' | /usr/bin/tr -d '\\-/ :' ";
        stringBuilder.append(String.format(egrep, command, logFile));
        stringBuilder.append(cutAndTr)
                .append(awk)
                .append(" | /bin/awk -F ',' '{print $1}' | /bin/sort | /usr/bin/xargs | /bin/sed -e 's/ /,/g'");

        logger.debug("Going to execute: [{}]", stringBuilder);
        CliResult execResult;
        do {
            execResult = SHELL_MS.execute(stringBuilder.toString());
            if (execResult.isSuccess()) {
                final String[] TimeStrings = execResult.getOutput().split(",");
                logger.info("timestrings {}", TimeStrings.length);
                if (TimeStrings.length > 0) {
                    cmdOutputTime = buildCorrectTime(TimeStrings);
                }
            } else {
                fail = true;
                Thread.sleep(120L * (long) Constants.TEN_EXP_3);
            }
            logger.info("count in  time {} & falg {}", count, fail);
        }
        while (fail && count++ < 3);
        return cmdOutputTime;
    }

    private long buildCorrectTime(final String[] strings) {
        Arrays.sort(strings, (o1, o2) -> o1.compareToIgnoreCase(o2));
        logger.info("buildCorrectTime list {}", Arrays.toString(strings), strings[strings.length - 1]);
        if (!"".equalsIgnoreCase(strings[strings.length - 1])) {
            return Long.parseLong(strings[strings.length - 1]);
        } else {
            logger.info("output of command is empty");
            return 0L;
        }
    }

    private void getCompletedTime(final long startTime, final CliShell Shell_Ms) {
        boolean workFlowFail;
        long workflowCompletedtTime = 0L;
        try {
            do {
                workFlowFail = false;
                workflowCompletedtTime = getCommandOutput(workflowStartTime, workflowCompletedString, Shell_Ms);
                logger.info("workflowCompletedtTime " + workflowCompletedtTime);
                if (workflowCompletedtTime == 0L) {
                    workFlowFail = true;
                    Thread.sleep(20L * (long) Constants.TEN_EXP_3);
                }
                logger.info("workFlowFail {} {} ", workFlowFail, (System.nanoTime() - startTime < workflowMaxTime));
            } while (workFlowFail && (System.nanoTime() - startTime < workflowMaxTime));
            this.workflowCompTime = workflowCompletedtTime;
        } catch (final Exception e) {
            e.printStackTrace();
            logger.info("exception in fetching workflow completed String {}", e.getStackTrace());
        }
    }


    private String getMemberIpAddress(final String memberName, final CliShell SHELL_MS) {
        String memberVmIp = "";
        final CliResult memberIpResult = SHELL_MS.execute(String.format(aliveMemberIpCommand, memberName));
        logger.info("Get memberIpResult : {}", memberIpResult.getOutput() + memberIpResult.isSuccess());
        if (!"".equalsIgnoreCase(memberIpResult.getOutput()) && memberIpResult.isSuccess()) {
            memberVmIp = memberIpResult.getOutput().split(" ")[0];
            logger.info("MemberName : {}, memberVmIp : {}", memberName, memberVmIp);
        }
        return memberVmIp;
    }

    private boolean killMemberAndWait(final String killAliveEmpIp, final String memberName, CliShell SHELL_Kill) {
        boolean isAliveAgain = false;
        boolean sshable = false;
        String aliveEmpIp = "";
        int retry = 0;
        try {
            logger.info("kill result {}", String.format(KillEmpVm, killAliveEmpIp));
            CliResult killExecut = SHELL_Kill.execute(String.format(KillEmpVm, killAliveEmpIp));

            if (killExecut.isSuccess()) {
                logger.info("kill result {}", killExecut.getOutput() + killExecut.isSuccess());
                do {
                    aliveEmpIp = getMemberIpAddress(memberName, SHELL_Kill);
                    if (killAliveEmpIp.equalsIgnoreCase(aliveEmpIp)) {
                        isAliveAgain = true;
                    } else {
                        logger.info("emp is not same");
                        Thread.sleep(180L * (long) Constants.TEN_EXP_3);
                    }
                } while (!isAliveAgain && retry++ < 3);
            }

        } catch (final Exception e) {
            e.printStackTrace();
            logger.info("exception in killMemberAndWait " + e.getStackTrace());
        }
        return isAliveAgain;
    }

    private boolean restPassWords() {
        final CliShell MS_SHELL = new CliShell(HAPropertiesReader.getMS());
        final String testMasterCommand = "hostname";
        try {
            CliResult testMasterResult = MS_SHELL.execute(testMasterCommand);
            logger.info("testMasterResult: {}", testMasterResult.toString());
            if (!testMasterResult.isSuccess()) {
                logger.warn("Login to Master Vnflaf with key file must be working before executing test");
                return false;
            }
        } catch (final Exception ex) {
            logger.warn("Login to Master Vnflaf with key file must be working before executing test : {}", ex.getMessage());
            return false;
        }
        final String lafPasswordDefault = HAPropertiesReader.getProperty("cloud.vnflaf.password", "").trim();
        final String masterResetCommand = String.format("echo 'cloud-user:%s' | sudo chpasswd", lafPasswordDefault);
        final String lafSlaveIpCommand = "consul members | grep vnflaf | grep -v `hostname` | awk '{print $2}' | cut -f1 -d ':'";
        final String lafPasswordSlave = HAPropertiesReader.getProperty("cloud.vnflaf.slave.password", "").trim();
        final String lafSlaveIp = MS_SHELL.execute(lafSlaveIpCommand).getOutput().trim();
        logger.info("lafSlaveIp: {}", lafSlaveIp);

        final String slaveResetCommand = String.format("expect -c 'spawn ssh -q -o StrictHostKeyChecking=no -i /var/tmp/Bravo/pemKey.pem cloud-user@%s; expect \"UNIX password:\"; " +
                "send \"%s\\n\"; expect \"New password:\"; send \"%s\\n\"; expect \"new password:\"; send \"%s\\n\"; interact'", lafSlaveIp, lafPasswordSlave, lafPasswordDefault, lafPasswordDefault);
        final String testSlaveCommand = String.format("ssh -q -o StrictHostKeyChecking=no -i /var/tmp/Bravo/pemKey.pem cloud-user@%s 'hostname'", lafSlaveIp);
        final String slaveResetCommandWithKeyLogin = String.format("ssh -q -o StrictHostKeyChecking=no -i /var/tmp/Bravo/pemKey.pem cloud-user@%s 'echo \"cloud-user:%s\" | sudo chpasswd'", lafSlaveIp, lafPasswordDefault);
        logger.info("slaveResetCommandWithKeyLogin: {}", slaveResetCommandWithKeyLogin);

        try {
            CliResult masterResetResult = MS_SHELL.execute(masterResetCommand);
            logger.info("masterResetResult : {}", masterResetResult.toString());
            CliResult testSlaveResult = MS_SHELL.executeOnMSTimeout(testSlaveCommand, 15L);
            logger.info("testSlaveResult : {}", testSlaveResult.toString());
            if (!testSlaveResult.isSuccess()) {
                logger.info("slaveResetCommand : {}", slaveResetCommand);
                CliResult slaveResetResult = MS_SHELL.execute(slaveResetCommand);
                logger.info("slaveResetResult : {}", slaveResetResult.toString());
            } else {
                CliResult slaveResetResultWithKeyLogin = MS_SHELL.execute(slaveResetCommandWithKeyLogin);
                logger.info("slaveResetResultWithKeyLogin : {}", slaveResetResultWithKeyLogin.toString());
            }
        } catch (final Exception e) {
            logger.warn("Fail to reset password : {}", e.getMessage());
            return false;
        }
        return true;
    }
}