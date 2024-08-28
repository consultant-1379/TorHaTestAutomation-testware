package com.ericsson.sut.test.cases;

import static org.assertj.core.api.Fail.fail;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.ericsson.cifwk.taf.data.DataHandler;
import com.ericsson.oss.testware.hostconfigurator.HostConfigurator;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import com.ericsson.cifwk.taf.annotations.TestId;
import com.ericsson.cifwk.taf.data.Host;
import com.ericsson.cifwk.taf.data.HostType;
import com.ericsson.nms.rv.core.HAPropertiesReader;
import com.ericsson.nms.rv.core.util.CliResult;
import com.ericsson.nms.rv.core.util.CliShell;
import com.ericsson.nms.rv.taf.tools.Constants;
import com.ericsson.nms.rv.taf.tools.host.Service;


/**
 * Shutdown a host and verifies system availability.
 */
public class HaltHost extends HighAvailabilityTestCase {

    private static final Logger logger = LogManager.getLogger(HaltHost.class);

    private static final long timeToWaitForServiceRecover = Constants.TEN_EXP_9 * Constants.Time.ONE_MINUTE_IN_SECONDS * HAPropertiesReader.getTimeToWaitForServiceOnline();
    private static final int FIVE_MINUTES_IN_SECONDS = Constants.Time.ONE_MINUTE_IN_SECONDS * 5;
    private static final String ILO_ROOT_PASSWORD = HAPropertiesReader.getIloRootPassword();
    public static LocalDateTime haltHostTime;

    /**
     * Supplies data to the test method.
     *
     * @return an array of array of objects
     */
    @DataProvider
    public static Object[][] getData() {
        final List<Object[]> list = new ArrayList<>();
        final Object host = DataHandler.getAttribute("host.name");
        logger.info("Host used for regression : {}", host.toString());

        switch(host.toString()) {
            case "SVC1" :
                list.add(new Object[]{HostConfigurator.getSVC1()}); break;
            case "SVC2" :
                list.add(new Object[]{HostConfigurator.getSVC2()}); break;
            case "SVC3" :
                list.add(new Object[]{HostConfigurator.getSVC3()}); break;
            case "SVC4" :
                list.add(new Object[]{HostConfigurator.getSVC4()}); break;
            case "DB1" :
                list.add(new Object[]{HostConfigurator.getDb1()}); break;
            case "DB2" :
                list.add(new Object[]{HostConfigurator.getDb2()}); break;
            default:
                logger.error("Invalid host name : {}", host.toString()); break;
        }
        final Object[][] objects = new Object[list.size()][1];
        return list.toArray(objects);
    }

    /**
     * Shutdown a host and verifies system availability.
     *
     * @param host the host
     */
    @TestId(id = "TORRV-5038_High_1", title = "Shutdown HaHost")
    @Test(groups = {"High Availability"}, dataProvider = "getData", dataProviderClass = HaltHost.class)
    public final void haltHost(final Host host) {
        logger.info("Host : {}, IP: {}", host.getHostname(), host.getIp());
        logger.info("MS Host : {}, IP: {}", HostConfigurator.getMS().getHostname(), HostConfigurator.getMS().getIp());

        final String iloIp = HAPropertiesReader.getSvcDbIloIP(host.getHostname());
        final String statusCommand = "expect -c 'spawn ssh root@" + iloIp + " power; expect password ; send \""+ ILO_ROOT_PASSWORD +"\\n\" ; interact'";
        final String onCommand = "expect -c 'spawn ssh root@" + iloIp + " power on; expect password ; send \""+ ILO_ROOT_PASSWORD +"\\n\" ; interact'";
        final String resetCommand = "expect -c 'spawn ssh root@" + iloIp + " power reset; expect password ; send \""+ ILO_ROOT_PASSWORD +"\\n\" ; interact'";
        final String offCommand = "expect -c 'spawn ssh root@" + iloIp + " power off; expect password ; send \""+ ILO_ROOT_PASSWORD +"\\n\" ; interact'";
        final String connectCommand = "expect -c 'spawn ssh root@" + iloIp + " ; expect yes ; send \"yes\\n\" ;expect password ; send \"" + ILO_ROOT_PASSWORD + "\\n\" ; expect \">\"; send \"exit\\n\" ; exit 0;'";

        logger.info("---------------------------All commands---------------------------");
        logger.info("HA_VERIFICATION_TIME : {}s", HA_VERIFICATION_TIME);
        logger.info("timeToWaitForServiceRecover : {}s", timeToWaitForServiceRecover /Constants.TEN_EXP_9);
        logger.info("iloIp : {}", iloIp);
        logger.info("statusCommand : {}", statusCommand);
        logger.info("onCommand : {}", onCommand);
        logger.info("resetCommand : {}", resetCommand);
        logger.info("offCommand : {}", offCommand);
        logger.info("connectCommand : {}", connectCommand);
        logger.info("------------------------------------------------------------------");

        if (HA_VERIFICATION_TIME < 10 * Constants.Time.ONE_MINUTE_IN_SECONDS) {
            throw new IllegalArgumentException("HA verification time must be at least 10 minutes");
        }
        CliShell cliToolShellMS = new CliShell(HostConfigurator.getMS());
        try {
            logger.info("executing connectCommand : {}", connectCommand);
            CliResult result = cliToolShellMS.executeAsRoot(connectCommand);
            logger.info("connectCommand output is : {}", result.getOutput());
        } catch(final Exception e) {
            logger.error("Failed to execute connectCommand, msg: {}", e.getMessage());
        }
        logger.info("executing statusCommand : {}", statusCommand);
        final String checkStatus = cliToolShellMS.executeAsRoot(statusCommand).getOutput();
        logger.info("statusCommand output is : {}", checkStatus);
        if(!checkStatus.toLowerCase().contains("on")) {
            logger.error("Host power status is not on!!");
            fail("Host : " + host.getHostname() + "Status : " + checkStatus);
            return;
        }

        startRegressionVerificationTasks();

        try {
            if (host.getType().toString().startsWith(HostType.DB.toString())) {
                logger.info("----------  DB Services ----------");
                logger.info("Postgres is running on {}", host.getHostname());
                logger.info("----------  DB Services ----------");
            } else if (host.getType().toString().startsWith(HostType.SVC.toString()) && (new Service(host)).isHaProxyRunning()) {
                logger.info("HAProxy is running on {}", host.getHostname());
            }

            logger.info("Halting host : {}", host.getHostname());
            try {
                haltHostTime = LocalDateTime.now();
                logger.info("executing offCommand : {}", offCommand);
                CliResult result = cliToolShellMS.executeAsRoot(offCommand);
                logger.info("offCommand output is : {}", result.getOutput());
            } catch (Exception e) {
                logger.error("Failed to execute offCommand : {}", e.getMessage());
            }
            logger.info("Verifying availability while the host is shutting down...");
            try {
                CliResult offStatus;
                long startTime = System.nanoTime();
                do {
                    checkTime(startTime);
                    sleep(2);
                    logger.info("executing statusCommand : {}", statusCommand);
                    offStatus = cliToolShellMS.executeAsRoot(statusCommand);
                    logger.info("statusCommand output is : {}", offStatus.getOutput());
                } while(!(offStatus.getOutput().toLowerCase().contains("off")));
                logger.info("Off Status is : {}", offStatus.getOutput());
            } catch (Exception e) {
                logger.warn("Failed to check status of host : {}", e.getMessage());
            }

            logger.info("Verifying availability after the host has shut down...");
            logger.info("sleeping for HA_VERIFICATION_TIME : {}s..........", HA_VERIFICATION_TIME);
            sleep(HA_VERIFICATION_TIME);

            try {
                CliResult offStatus;
                long startTime = System.nanoTime();
                do {
                    checkTime(startTime);
                    sleep(2);
                    logger.info("executing statusCommand : {}", statusCommand);
                    offStatus = cliToolShellMS.executeAsRoot(statusCommand);
                    logger.info("statusCommand output is : {}", offStatus.getOutput());
                } while (!(offStatus.getOutput().toLowerCase().contains("off")));
                logger.info("Off Status is : {}", offStatus.getOutput());
            } catch (final Exception e) {
                logger.error("Failed to power off the host ", e);
                throw new Exception("Failed to power off the host time was more than the expected ");
            }

            logger.info("Powering on the host...");
            try {
                logger.info("executing onCommand : {}", onCommand);
                CliResult onResult = cliToolShellMS.executeAsRoot(onCommand);
                logger.info("onCommand command output is : {}", onResult.getOutput());
            } catch (final Exception e) {
                logger.error("Failed to execute onCommand : {}", e.getMessage());
            }

            logger.info("Verifying availability while the host is powering on...");
            try {
                CliResult onStatus;
                long startTime = System.nanoTime();
                do {
                    checkTime(startTime);
                    sleep(2);
                    logger.info("executing statusCommand : {}", statusCommand);
                    onStatus = cliToolShellMS.executeAsRoot(statusCommand);
                    logger.info("statusCommand output is : {}", onStatus.getOutput());
                } while (!(onStatus.getOutput().toLowerCase().contains("on")));
                logger.info("On Status is : {}", onStatus.getOutput());
            } catch (final Exception e) {
                logger.error("Failed to check status of host : {}", e.getMessage());
            }

            logger.info("Verifying availability after the host has recovered");
            logger.info("sleeping for HA_VERIFICATION_TIME : {}s..........", HA_VERIFICATION_TIME);
            sleep(HA_VERIFICATION_TIME);

            try {
                logger.info("executing statusCommand : {}", statusCommand);
                CliResult statusResult = cliToolShellMS.executeAsRoot(statusCommand);
                logger.info("statusCommand output is : {}", statusResult.getOutput());
                if (!statusResult.getOutput().toLowerCase().contains("on")) {
                    throw new Exception("HaltHost failed to finish correctly, system was not recovered correctly");
                }
            } catch (final InterruptedException e) {
                logger.error("Failed to get the system status .. after turning on the server. ", e);
            }
            logger.info("sleeping for {}s..........", FIVE_MINUTES_IN_SECONDS);
            sleep(FIVE_MINUTES_IN_SECONDS);

            String grepCommand = "/opt/ericsson/enminst/bin/vcs.bsh --groups | /bin/awk '{print $7}' | grep -c Invalid";
            logger.info("executing grepCommand : {}", grepCommand);
            CliResult verifyStatus = cliToolShellMS.executeAsRoot(grepCommand);
            logger.info("grepCommand output is : {}", verifyStatus.getOutput());

            if (!"0".equals(verifyStatus.getOutput())) {
                throw new Exception("HaltHost failed to finish correctly, verification of the cluster has failed");
            }

        } catch (final Exception t) {
            logger.error(t.getMessage(), t);
            fail(t.getMessage());
        } finally {
            logger.info("executing onCommand : {}", onCommand);
            CliResult result = cliToolShellMS.executeAsRoot(onCommand);
            logger.info("onCommand output is : {}", result.getOutput());
            stopRegressionVerificationTasks();
        }

    }

    private void checkTime(final long startTime) throws InterruptedException {
        logger.info("time lapse : {}s", (System.nanoTime() - startTime)/ Constants.TEN_EXP_9);
        logger.info("timeToWaitForServiceRecover : {}s", timeToWaitForServiceRecover / Constants.TEN_EXP_9);
        if ((System.nanoTime() - startTime) > timeToWaitForServiceRecover) {
            logger.error("Failed to wait for HaltHost Commands");
            throw new InterruptedException("Failed to wait for HaltHost to Recover");
        }
    }

}
