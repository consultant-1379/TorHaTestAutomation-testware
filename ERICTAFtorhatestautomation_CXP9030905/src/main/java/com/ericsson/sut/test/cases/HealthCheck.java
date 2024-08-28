package com.ericsson.sut.test.cases;

import static org.assertj.core.api.Fail.fail;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.testng.annotations.Test;

import com.ericsson.cifwk.taf.annotations.TestId;
import com.ericsson.nms.rv.core.util.CliResult;
import com.ericsson.nms.rv.core.util.CliShell;
import com.ericsson.nms.rv.taf.tools.Constants;
import com.ericsson.oss.testware.hostconfigurator.HostConfigurator;


/**
 * Writes {@code Hello, world} into the log.
 */
public class HealthCheck extends HighAvailabilityPuppetTestCase {

    private static final Logger logger = LogManager.getLogger(HealthCheck.class);
    private static final long timeToWaitfor = Constants.TEN_EXP_9 * Constants.Time.ONE_MINUTE_IN_SECONDS * 30;

    /**
     * HealthCheck checking .
     */
    @TestId(title = "HealthCheck")
    @Test(enabled = true, groups = {"HealthCheck"})
    public final void healthCheck() {
        final String healthCheckCmd = "/opt/ericsson/enminst/bin/enm_healthcheck.sh -v";
        CliShell msShell = new CliShell(HostConfigurator.getMS());
        try {
            CliResult result;
            int counter = 1;
            do {
                logger.info("Going to execute health check .....");
                result = msShell.executeAsRoot(healthCheckCmd);
                logger.info("Health check command output is {}", result.getOutput());
                if (result.getExitCode() == 0 && result.getOutput().contains("Successfully Completed ENM System Healthcheck")) {
                    logger.info("Health check : Success");
                    break;
                } else {
                    if (counter <= 12){
                        logger.info("going to sleep 10 min...");
                        sleep(600);
                    }
                    counter = counter +1;
                }

            }while(counter <= 12);
            if (result.getOutput().contains("ENM System Healthcheck errors!")){
                logger.error("Health Check Failed ");
                fail("Health Check Failed");
            }
        } catch (final Exception e) {
            logger.error("Exception in Health Check {}",e.getMessage());
            fail("Health Check Failed");
        }
    }

}
