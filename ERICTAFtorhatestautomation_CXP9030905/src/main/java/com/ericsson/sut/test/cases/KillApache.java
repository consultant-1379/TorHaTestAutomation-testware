package com.ericsson.sut.test.cases;

import static org.assertj.core.api.Assertions.fail;

import static com.ericsson.nms.rv.taf.tools.process.Process.SIGKILL;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import com.ericsson.cifwk.taf.annotations.TestId;
import com.ericsson.cifwk.taf.data.Host;
import com.ericsson.nms.rv.core.util.CliResult;
import com.ericsson.nms.rv.core.util.CliShell;
import com.ericsson.nms.rv.taf.tools.Constants;
import com.ericsson.oss.testware.hostconfigurator.HostConfigurator;
import com.ericsson.oss.testware.availability.common.shell.ExecutionResult;
import com.ericsson.oss.testware.availability.common.shell.Shell;

/**
 * Kills apache and verifies system availability.
 */
public class KillApache extends HighAvailabilityTestCase {

    private static final Logger logger = LogManager.getLogger(KillApache.class);
    private static final long timeToWaitforServiceRecover = Constants.TEN_EXP_9 * Constants.Time.ONE_MINUTE_IN_SECONDS * 5;
    private static final int ONE_MINUTES = Constants.Time.ONE_MINUTE_IN_SECONDS * 1;
    private static final int FIVE_MINUTES = Constants.Time.ONE_MINUTE_IN_SECONDS * 5;
    private static final String onLine = "Res_App_svc_cluster_httpd_vm_service_httpd.in.online.state";
    private static final String offLine = "Res_App_svc_cluster_httpd_vm_service_httpd.*OFFLINE";
    private static final String ENGINE_ALOG = " /var/VRTSvcs/log/engine_*.log";
    private static final String egrep = "/bin/egrep -h '%s' %s ";
    private static final String cutAndTr = "| /bin/cut -f 1,2 -d ' ' | /usr/bin/tr -d '\\-/ :' ";
    private static final String healthCheckCommand = "/opt/ericsson/enminst/bin/enm_healthcheck.sh";
    final CliShell SHELL_SVC1 = new CliShell(HostConfigurator.getSVC1());

    /**
     * Supplies data to the test methods. We kill it twice to know if there is any difference killing them on both
     * nodes.
     *
     * @return an array of array of objects
     */
    @DataProvider
    public static Object[][] getData() {
        final List<Object[]> list = new ArrayList<>();
        final List<Host> svcHostList = HostConfigurator.getAllHosts("httpd");
        svcHostList.forEach(host -> list.add(new Object[]{host, SIGKILL}));
        final Object[][] objects = new Object[list.size()][2];
        return list.toArray(objects);
    }

    /**
     * Kills the httpd process in a virtual machine and verifies system availability.
     *
     */
    @TestId(id = "TORRV-1794_High_1", title = "Kill Httpd Process")
    @Test(groups = {"High Availability"})
    public final void killHttpdProcess() {
        final List<Host> svcHostList = HostConfigurator.getAllHosts("httpd");
        final CliResult execResult;

        //health checking
        CliShell shellMS = new CliShell(HostConfigurator.getMS());
        execResult = shellMS.executeAsRoot(healthCheckCommand);
        if (execResult.isSuccess()) {
            logger.info("Health check is success");
            try {
                startRegressionVerificationTasks();
                svcHostList.forEach((httpd) -> {
                    Shell shell = new Shell(httpd);
                    long time = Long.parseLong(new SimpleDateFormat("yyyyMMddHHmmss").format(new Date()));
                    logger.info("Killing Apache and its logger on httpd VM with signal [{}] on {}", SIGKILL, httpd.getHostname());
                    ExecutionResult pidResult = shell.executeCommand("sudo cat /var/run/httpd/httpd.pid");
                    logger.info("Pid result is :{}", pidResult.toString());
                    if (pidResult.isSuccessful() && pidResult.getStdOut() != null && !pidResult.getStdOut().isEmpty()) {
                        ExecutionResult killResult = shell.executeCommand("sudo kill -" + SIGKILL + " " + pidResult.getStdOut());
                        logger.info("Kill result is :{}", killResult.toString());
                        if (!killResult.isSuccessful()) {
                            fail("Failed to kill httpd process");
                        }
                    } else {
                        logger.info("Unable to get httpd process id");
                        fail("Unable to get httpd process id");
                    }
                    sleep(FIVE_MINUTES);

                    logger.info("Checking the status after sleeping 5 mins ");
                    final StringBuilder stringBuilderOffline = new StringBuilder();
                    final StringBuilder stringBuilderOnline = new StringBuilder();
                    stringBuilderOffline
                            .append(" ( ")
                            .append(String.format(egrep, String.format(offLine), ENGINE_ALOG))
                            .append(cutAndTr)
                            .append(String.format("| /bin/awk '$0 >=\"%d\"'", time));
                    stringBuilderOffline
                            .append(" )");
                    if (!checkStatus(stringBuilderOffline)) {
                        logger.error("Failed to kill Apache ");
                        fail("Failed to kill Apache ");
                    }
                    stringBuilderOnline
                            .append(" ( ")
                            .append(String.format(egrep, String.format(onLine), ENGINE_ALOG))
                            .append(cutAndTr)
                            .append(String.format("| /bin/awk '$0 >=\"%d\"'", time));
                    stringBuilderOnline
                            .append(" )");

                    long startTime = System.nanoTime();
                    try {
                        boolean status;
                        do {
                            checkTime(startTime);
                            status = checkStatus(stringBuilderOnline);
                            logger.info("status out put is {}", status);
                            sleep(ONE_MINUTES);
                        } while (!status);
                    } catch (final Exception e) {
                        logger.error("Still Httpd in INVALID STATE ", e);
                        fail("Httpd failed to get in ONLINE STATE");
                    }
                    logger.info("Waiting for the system to recover after killing Apache");
                    sleep(HA_VERIFICATION_TIME);
                });
            } catch (final Exception t) {
                logger.error(t.getMessage(), t);
                fail(t.getMessage());
            } finally {
                stopRegressionVerificationTasks();
            }
        } else {
            logger.info("Health check failed ");
            fail("Health check Failed ");
        }
    }

    private boolean checkStatus(final StringBuilder stringBuilder) {
        final CliResult execResult;
        boolean svcStatus = true ;
        logger.info("Executing httpd info command {}",stringBuilder.toString());
        execResult= SHELL_SVC1.execute(stringBuilder.toString());
        if (execResult.isSuccess()) {
            logger.info("httpd info command Output {}",execResult.getOutput());
            if (execResult.getOutput().isEmpty()){
                svcStatus=false;
            }
        }
        return svcStatus;
    }
    private void checkTime(final long startTime) throws InterruptedException {
        if ((System.nanoTime() - startTime) > timeToWaitforServiceRecover) {
            logger.error("Failed to wait for httpd to Recover");
            throw new InterruptedException("Failed to wait for httpd to Recover");
        }
    }

}