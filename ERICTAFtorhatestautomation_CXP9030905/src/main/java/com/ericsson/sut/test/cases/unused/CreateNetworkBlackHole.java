package com.ericsson.sut.test.cases.unused;

import static org.assertj.core.api.Fail.fail;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import com.ericsson.cifwk.taf.annotations.TestId;
import com.ericsson.nms.rv.taf.failures.FailureInjectionException;
import com.ericsson.nms.rv.taf.failures.network.BlackHole;
import com.ericsson.nms.rv.taf.tools.host.HaHost;
import com.ericsson.nms.rv.taf.tools.host.Service;
import com.ericsson.sut.test.cases.HighAvailabilityPuppetTestCase;

/**
 * Creates a black hole in the network and verifies system availability.
 */
public class CreateNetworkBlackHole extends HighAvailabilityPuppetTestCase {

    private static final Logger logger = LogManager.getLogger(CreateNetworkBlackHole.class);

    /**
     * Supplies data to the test method.
     *
     * @return an array of array of objects
     */
    @DataProvider
    public static Object[][] getData() {

        final HaHost svc1 = new Service("svc-1");
        final HaHost svc2 = new Service("svc-2");

        return new Object[][]{
                {svc1, HaHost.Protocol.ICMP, true, true, "svc-1-cmserv"},
                {svc1, HaHost.Protocol.ICMP, true, false, "svc-1-cmserv"},
                {svc1, HaHost.Protocol.ICMP, false, true, "svc-1-cmserv"},
                {svc1, HaHost.Protocol.ICMP, false, false, "svc-1-cmserv"},
                {svc1, HaHost.Protocol.TCP, true, true, "svc-1-cmserv"},
                {svc1, HaHost.Protocol.TCP, true, false, "svc-1-cmserv"},
                {svc1, HaHost.Protocol.TCP, false, true, "svc-1-cmserv"},
                {svc1, HaHost.Protocol.TCP, false, false, "svc-1-cmserv"},
                {svc1, HaHost.Protocol.UDP, true, true, "svc-1-cmserv"},
                {svc1, HaHost.Protocol.UDP, true, false, "svc-1-cmserv"},
                {svc1, HaHost.Protocol.UDP, false, true, "svc-1-cmserv"},
                {svc1, HaHost.Protocol.UDP, false, false, "svc-1-cmserv"},

                {svc2, HaHost.Protocol.ICMP, true, true, "svc-2-cmserv"},
                {svc2, HaHost.Protocol.ICMP, true, false, "svc-2-cmserv"},
                {svc2, HaHost.Protocol.ICMP, false, true, "svc-2-cmserv"},
                {svc2, HaHost.Protocol.ICMP, false, false, "svc-2-cmserv"},
                {svc2, HaHost.Protocol.TCP, true, true, "svc-2-cmserv"},
                {svc2, HaHost.Protocol.TCP, true, false, "svc-2-cmserv"},
                {svc2, HaHost.Protocol.TCP, false, true, "svc-2-cmserv"},
                {svc2, HaHost.Protocol.TCP, false, false, "svc-2-cmserv"},
                {svc2, HaHost.Protocol.UDP, true, true, "svc-2-cmserv"},
                {svc2, HaHost.Protocol.UDP, true, false, "svc-2-cmserv"},
                {svc2, HaHost.Protocol.UDP, false, true, "svc-2-cmserv"},
                {svc2, HaHost.Protocol.UDP, false, false, "svc-2-cmserv"},
        };

    }

    /**
     * Creates a black hole in the network and verifies system availability.
     *
     * @param host     the host
     * @param protocol the protocol
     * @param input    {@code true} if it is an {@code INPUT} rule, or {@code false} if it is an OUTPUT rule
     * @param source   {@code true} if {@code address} is a {@code source}, or {@code false} if it is a destination
     * @param address  the network address
     */
    @TestId(id = "TORRV-3426_High_1", title = "Create Network Black Hole")
    @Test(enabled = false, groups = {"High Availability"}, dataProvider = "getData", dataProviderClass = CreateNetworkBlackHole.class)
    public final void createNetworkBlackHole(final HaHost host, final HaHost.Protocol protocol, final boolean input, final boolean source, final String address) {

        final BlackHole blackHole = new BlackHole(host, protocol, input, source, address);
        try {
            startRegressionVerificationTasks();

            logger.info("Creating the network black hole on {}", host.getHostname());
            blackHole.create();

            logger.info("Verifying availability after creating the network black hole");
            sleep(HA_VERIFICATION_TIME);

            logger.info("Removing the network black hole");
            blackHole.remove();

            logger.info("Verifying availability after the system has recovered");
            sleep(HA_VERIFICATION_TIME);
        } catch (final Exception t) {
            logger.error(t.getMessage(), t);
            fail(t.getMessage());
        } finally {
            try {
                blackHole.remove();
            } catch (final FailureInjectionException ignore) {
                logger.warn("Failed to createNetworkBlackHole.", ignore);
            }
            stopRegressionVerificationTasks();
        }

    }
}
