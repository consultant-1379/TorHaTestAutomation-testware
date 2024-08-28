package com.ericsson.sut.test.cases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Fail.fail;

import java.util.ArrayList;
import java.util.List;

import com.ericsson.nms.rv.core.util.RegressionUtils;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import com.ericsson.cifwk.taf.annotations.TestId;
import com.ericsson.cifwk.taf.data.Host;
import com.ericsson.nms.rv.taf.failures.FailureInjectionException;
import com.ericsson.nms.rv.taf.failures.network.Mtu;
import com.ericsson.nms.rv.taf.tools.network.NetworkInterface;
import com.ericsson.nms.rv.taf.tools.storage.FileSystem;

/**
 * Reduces the MTU and verifies system availability.
 */
public class ReduceMtu extends HighAvailabilityTestCase {

    private static final Logger logger = LogManager.getLogger(ReduceMtu.class);

    /**
     * Supplies data to the test method.
     *
     * @return an array of array of objects
     */
    @DataProvider
    public static Object[][] getData() {

        final List<Object[]> list = new ArrayList<>();

        final List<Host> hosts = new ArrayList<>();
        hosts.addAll(RegressionUtils.getAllDbHostList());
        hosts.addAll(RegressionUtils.getAllSvcHostList());

        for (final Host host : hosts) {
            for (final NetworkInterface networkInterface : RegressionUtils.getNetworkInterfaces(host)) {
                list.add(new Object[]{networkInterface, 576});
            }
        }

        final Object[][] objects = new Object[list.size()][1];
        return list.toArray(objects);
    }

    /**
     * Reduces the MTU and verifies system availability.
     *
     * @param networkInterface the network interface whose MTU size will be reduced
     * @param size             the new MTU size's value
     */
    @TestId(id = "TORRV-1627_High_1", title = "Reduce MTU")
    @Test(groups = {"High Availability"}, dataProvider = "getData", dataProviderClass = ReduceMtu.class)
    public final void reduceMtu(final NetworkInterface networkInterface, final int size) {

        final Mtu mtu = new Mtu(networkInterface);
        try {
            startRegressionVerificationTasks();

            logger.info("Reducing the MTU on {} of {}", networkInterface.getName(), networkInterface.getHost().getHostname());
            new FileSystem(networkInterface.getHost()).removeFile("/tmp/ping.out");
            mtu.reduce(size);

            logger.info("Verifying availability after reducing the MTU");
            // ping will run for a bit more than HA_VERIFICATION_TIME
            networkInterface.ping(HA_VERIFICATION_TIME);
            sleep(HA_VERIFICATION_TIME);

            logger.info("Restoring MTU to its previous value");
            mtu.restore();

            logger.info("Verifying availability after the system has recovered");
            sleep(HA_VERIFICATION_TIME);

            // fail if packet loss is greater than 0. Fail not earlier because it is more important to assess availability
            assertThat(networkInterface.getPingCommandPacketLoss() == 0).as("Some ICMP packets were lost");
        } catch (final Exception t) {
            logger.error(t.getMessage(), t);
            fail(t.getMessage());
        } finally {
            try {
                mtu.restore();
            } catch (final FailureInjectionException ignore) {
                logger.warn("Failed to reduceMtu.", ignore);
            }
            stopRegressionVerificationTasks();
        }

    }

}
