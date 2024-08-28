package com.ericsson.sut.test.cases.unused;

import static org.assertj.core.api.Fail.fail;

import java.util.ArrayList;
import java.util.List;

import com.ericsson.cifwk.taf.data.Host;
import com.ericsson.nms.rv.core.util.RegressionUtils;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import com.ericsson.cifwk.taf.annotations.TestId;
import com.ericsson.nms.rv.taf.failures.LockCpu;
import com.ericsson.nms.rv.taf.tools.host.HaHost;
import com.ericsson.sut.test.cases.HighAvailabilityTestCase;


/**
 * Locks one or more CPUs and verifies system availability.
 */
public class CpuLock extends HighAvailabilityTestCase {

    private static final Logger logger = LogManager.getLogger(CpuLock.class);

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
            list.add(new Object[]{host, 1});
        }

        final Object[][] objects = new Object[list.size()][2];
        return list.toArray(objects);
    }

    /**
     * Locks one or more CPUs and verifies system availability.
     *
     * @param host the host
     * @param cpu  the number of CPUs to be locked
     */
    @TestId(id = "TORRV-4804_High_1", title = "Lock CPU")
    @Test(enabled = false, groups = {"High Availability"}, dataProvider = "getData", dataProviderClass = CpuLock.class)
    public final void lockCpu(final HaHost host, final int cpu) {

        final LockCpu lockCpu = new LockCpu(host);
        try {
            startRegressionVerificationTasks();

            logger.info("Locking {} CPU(s) on {}", cpu, host.getHostname());
            lockCpu.insert(cpu);

            logger.info("Verifying availability while locking the CPU(s)");
            sleep(HA_VERIFICATION_TIME);

            logger.info("Removing the lock CPU module");
            lockCpu.remove();

            logger.info("Verifying availability after the system has recovered");
            sleep(HA_VERIFICATION_TIME);
        } catch (final Exception t) {
            logger.error(t.getMessage(), t);
            fail(t.getMessage());
        } finally {
            lockCpu.remove();
            stopRegressionVerificationTasks();
        }

    }

}
