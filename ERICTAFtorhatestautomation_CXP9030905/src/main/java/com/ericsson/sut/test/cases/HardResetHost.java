package com.ericsson.sut.test.cases;

import static org.assertj.core.api.Fail.fail;

import com.ericsson.nms.rv.core.HAPropertiesReader;
import com.ericsson.nms.rv.core.util.CliResult;
import com.ericsson.nms.rv.core.util.CliShell;
import com.ericsson.oss.testware.hostconfigurator.HostConfigurator;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.testng.annotations.Test;

import com.ericsson.cifwk.taf.annotations.TestId;
import com.ericsson.cifwk.taf.data.Host;
import com.ericsson.cifwk.taf.data.HostType;
import com.ericsson.nms.rv.taf.tools.Constants;
import com.ericsson.nms.rv.taf.tools.host.Service;

import java.time.LocalDateTime;


/**
 * Powers off a host and verifies system availability.
 */
public class HardResetHost extends HighAvailabilityTestCase {

    private static final Logger logger = LogManager.getLogger(HardResetHost.class);
    private static final String ILO_ROOT_PASSWORD = HAPropertiesReader.getIloRootPassword();
    public static LocalDateTime hardResetTime;

    /**
     * Powers off a host and verifies system availability.
     *
     * @param host the host
     */
    @TestId(id = "TORRV-5895_High_1", title = "Hard Reset Host")
    @Test(groups = {"High Availability"}, dataProvider = "getData", dataProviderClass = HaltHost.class)
    public final void hardResetHost(final Host host) {
        logger.info("Host : {}, IP: {}", host.getHostname(), host.getIp());
        logger.info("MS Host : {}, IP: {}", HostConfigurator.getMS().getHostname(), HostConfigurator.getMS().getIp());

        final String iloIp = HAPropertiesReader.getSvcDbIloIP(host.getHostname());
        final String statusCommand = "expect -c 'spawn ssh root@" + iloIp + " power; expect password ; send \""+ ILO_ROOT_PASSWORD +"\\n\" ; interact'";
        final String resetCommand = "expect -c 'spawn ssh root@" + iloIp + " power reset; expect password ; send \""+ ILO_ROOT_PASSWORD +"\\n\" ; interact'";
        final String onCommand = "expect -c 'spawn ssh root@" + iloIp + " power on; expect password ; send \""+ ILO_ROOT_PASSWORD +"\\n\" ; interact'";
        final String connectCommand = "expect -c 'spawn ssh root@" + iloIp + " ; expect yes ; send \"yes\\n\" ;expect password ; send \"" + ILO_ROOT_PASSWORD + "\\n\" ; expect \">\"; send \"exit\\n\" ; exit 0;'";

        CliShell cliToolShellMS = new CliShell(HostConfigurator.getMS());
        try {
            cliToolShellMS.executeAsRoot(connectCommand);
        } catch (Exception e) {}
        final String checkStatus = cliToolShellMS.executeAsRoot(statusCommand).getOutput();
        logger.info("checkStatus : {}", checkStatus);
        if(!checkStatus.toLowerCase().contains("on")) {
            logger.error("Host power status is not on!!");
            fail("Host : " + host.getHostname() + "Status : " + checkStatus);
            return;
        }

        if (HA_VERIFICATION_TIME < 10 * Constants.Time.ONE_MINUTE_IN_SECONDS) {
            throw new IllegalArgumentException("HA verification time must be at least 10 minutes");
        }

        try {
            if (host.getType().toString().startsWith(HostType.DB.toString())) {
                logger.info("----------  DB Services ----------");
                logger.info("Postgres is running on {}", host.getHostname());
                logger.info("----------  DB Services ----------");
            } else if (host.getType().toString().startsWith(HostType.SVC.toString()) && (new Service(host)).isHaProxyRunning()) {
                logger.info("HAProxy is running on {}", host.getHostname());
            }

            startRegressionVerificationTasks();

            logger.info("Hard resetting {}", host.getHostname());
            try {
                hardResetTime = LocalDateTime.now();
                CliResult resetResult = cliToolShellMS.executeAsRoot(resetCommand);
                logger.info("resetResult : {}", resetResult.getOutput());
            } catch (Exception ex) {
                //Do nothing
            }
            logger.info("Verifying availability while the host is resetting !!");
            sleep(HA_VERIFICATION_TIME);
            try {
                CliResult statusResult = cliToolShellMS.executeAsRoot(statusCommand);
                logger.info("statusResult : {}", statusResult.getOutput());
            } catch (Exception e) {
                logger.error("Error: {}", e.getMessage());
            }
            sleep(HA_VERIFICATION_TIME);
            logger.info("Verifying availability after the host has recovered");

            CliResult verifyStatus = cliToolShellMS.executeAsRoot("/opt/ericsson/enminst/bin/vcs.bsh --groups | /bin/awk '{print $6}' | grep -c INVALID");
            logger.info("verifyStatus : {}", verifyStatus.getOutput());
            if (!"0".equals(verifyStatus.getOutput())) {
                throw new Exception("HardResetHost failed to finish correctly, verification of the cluster has failed");
            }

        } catch (final Exception t) {
            logger.error(t.getMessage(), t);
            fail(t.getMessage());
        } finally {
            try {
                CliResult onResult = cliToolShellMS.executeAsRoot(onCommand);
                logger.info("on Result : {}", onResult.getOutput());
            } catch (final Exception ignore) {
                logger.warn("Failed to On after hardResetHost.", ignore);
            }
            stopRegressionVerificationTasks();
        }
    }

}
