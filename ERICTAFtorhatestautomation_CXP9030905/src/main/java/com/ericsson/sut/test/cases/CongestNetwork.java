package com.ericsson.sut.test.cases;

import static org.assertj.core.api.Fail.fail;

import java.util.ArrayList;
import java.util.List;

import com.ericsson.nms.rv.core.util.RegressionUtils;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.testng.annotations.Test;

import com.ericsson.cifwk.taf.annotations.TestId;
import com.ericsson.cifwk.taf.data.Host;
import com.ericsson.nms.rv.taf.failures.network.Congestion;


/**
 * Generates TCP and UDP traffic and verifies system availability.
 */
public class CongestNetwork extends HighAvailabilityPuppetTestCase {

    private static final Logger logger = LogManager.getLogger(CongestNetwork.class);

    /**
     * Generates TCP and UDP traffic and verifies system availability.
     */
    @TestId(id = "TORRV-1864_High_1", title = "Congest Network")
    @Test(enabled = true, groups = {"High Availability"})
    public final void congestNetwork() {

        final List<Host> hosts = new ArrayList<>();
        hosts.addAll(RegressionUtils.getAllSvcHostList());
        hosts.addAll(RegressionUtils.getAllDbHostList());

        final Congestion congestion = new Congestion(hosts);
        try {
            startRegressionVerificationTasks();

            logger.info("Verifying availability whilst congesting the network");
            congestion.start();

            sleep(HA_VERIFICATION_TIME);

            logger.info("Killing the traffic-generator processes");
            congestion.stop();

            logger.info("Verifying availability after the system has recovered");
            sleep(HA_VERIFICATION_TIME);

        } catch (final Exception t) {
            logger.error(t.getMessage(), t);
            fail(t.getMessage());
        } finally {
            congestion.stop();
            stopRegressionVerificationTasks();
        }
    }

}
