package com.ericsson.sut.test.cases.unused;

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
import com.ericsson.nms.rv.taf.failures.network.ArpStorm;
import com.ericsson.nms.rv.taf.tools.network.NetworkInterface;
import com.ericsson.sut.test.cases.HighAvailabilityPuppetTestCase;

/**
 * Generates an ARP storm and verifies system availability.
 */
public class GenerateArpStorm extends HighAvailabilityPuppetTestCase {

    private static final Logger logger = LogManager.getLogger(GenerateArpStorm.class);

    /**
     * the number of processes which will generate the ARP storm
     */
    private static final int PROCESSES = 100;

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
            RegressionUtils.getNetworkInterfaces(host).parallelStream().filter(networkInterface -> !networkInterface.getBroadcastAddress().isEmpty()).sequential().forEach(networkInterface -> list.add(new Object[]{networkInterface}));
        }

        final Object[][] objects = new Object[list.size()][1];
        return list.toArray(objects);
    }

    /**
     * Generates an ARP storm and verifies system availability.
     *
     * @param networkInterface the network interface
     */
    @TestId(id = "TORRV-1662_High_1", title = "Generate ARP Storm")
    @Test(groups = {"High Availability"}, dataProvider = "getData", dataProviderClass = GenerateArpStorm.class)
    public void generateArpStorm(final NetworkInterface networkInterface) {

        final ArpStorm storm = new ArpStorm(networkInterface);
        try {

            startRegressionVerificationTasks();

            logger.info("Generates the ARP storm on {} of {}", networkInterface.getName(), networkInterface.getHost().getHostname());
            storm.generate(PROCESSES, HA_VERIFICATION_TIME);

            logger.info("Verifying availability during the ARP storm");
            sleep(HA_VERIFICATION_TIME);

            logger.info("Verifying availability after the system has recovered");
            sleep(HA_VERIFICATION_TIME);
        } catch (final Exception t) {
            logger.error(t.getMessage(), t);
            fail(t.getMessage());
        } finally {
            storm.remove();
            stopRegressionVerificationTasks();
        }
    }

}
