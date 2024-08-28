package com.ericsson.sut.test.cases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Fail.fail;

import java.util.Optional;

import com.ericsson.nms.rv.core.util.RegressionUtils;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import com.ericsson.cifwk.taf.annotations.TestId;
import com.ericsson.cifwk.taf.data.Host;
import com.ericsson.nms.rv.taf.failures.Kill;
import com.ericsson.nms.rv.taf.tools.host.Service;
import com.ericsson.nms.rv.taf.tools.process.Process;

/**
 * Kills haproxy and verifies system availability.
 */
public class KillHaProxy extends HighAvailabilityTestCase {

    private static final Logger logger = LogManager.getLogger(KillHaProxy.class);

    /**
     * Supplies data to the test methods. We kill it twice to know if there is any difference killing them on both
     * nodes.
     *
     * @return an array of array of objects
     */
    @DataProvider
    public static Object[][] getData() {
        return new Object[][]{
                {Process.SIGKILL},
                {Process.SIGKILL}
        };
    }

    /**
     * Kills the haproxy process and verifies system availability.
     *
     * @param signal the Unix signal to be sent
     */
    @TestId(id = "TORRV-5012_High_1", title = "Kill haproxy Process")
    @Test(groups = {"High Availability"}, dataProvider = "getData", dataProviderClass = KillHaProxy.class)
    public final void killHaProxyProcess(final int signal) {
        try {

            final Optional<Host> any = RegressionUtils.getAllSvcHostList()
                    .parallelStream()
                    .filter(host -> new Service(host).isHaProxyRunning())
                    .findAny();
            if (any.isPresent()) {
                final Host host = any.get();
                final Service service = new Service(host);
                final Kill kill = new Kill(host);
                startRegressionVerificationTasks();

                logger.info("Killing haproxy process on {} with signal [{}]", service.getHostname(), signal);
                kill.haProxyProcess(signal);
                assertThat(service.wasHaProxyKilled()).as("Failed to kill HA proxy");

                logger.info("Waiting for the system to recover after killing haproxy process");
                sleep(HA_VERIFICATION_TIME);
            } else {
                logger.info("haproxy is not running before a failure has been injected");
                fail("haproxy is not running before a failure has been injected");
            }
        } catch (final Exception t) {
            logger.error(t.getMessage(), t);
            fail(t.getMessage());
        } finally {
            stopRegressionVerificationTasks();
        }
    }

}
