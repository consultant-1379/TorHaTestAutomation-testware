package com.ericsson.sut.test.cases.unused;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Fail.fail;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.testng.annotations.Test;

import com.ericsson.cifwk.taf.annotations.TestId;
import com.ericsson.cifwk.taf.data.Host;
import com.ericsson.nms.rv.taf.failures.FailureInjectionException;
import com.ericsson.nms.rv.taf.failures.exhaust.JvmHeap;
import com.ericsson.nms.rv.taf.tools.host.Service;
import com.ericsson.nms.rv.taf.tools.host.VirtualMachine;
import com.ericsson.nms.rv.taf.tools.host.VirtualMachine.Type;
import com.ericsson.nms.rv.taf.tools.host.VmFactory;
import com.ericsson.oss.testware.hostconfigurator.HostConfigurator;
import com.ericsson.sut.test.cases.HighAvailabilityTestCase;

/**
 * Exhausts the Java Virtual Machine heap and verifies system availability.
 */
public class ExhaustJvmHeap extends HighAvailabilityTestCase {

    private static final Logger logger = LogManager.getLogger(ExhaustJvmHeap.class);

    /**
     * Exhausts the Java Virtual Machine heap and verifies system availability.
     *
     * @param host the virtual machine
     */
    @TestId(id = "TORRV-1733_High_1", title = "Exhaust JVM Heap")
    @Test(enabled = false, groups = {"High Availability"}, dataProvider = "getData", dataProviderClass = ExhaustPermGen.class)
    public final void exhaustJvmHeap(final Host host) {

        final JvmHeap heap = new JvmHeap(host);
        final VirtualMachine virtualMachine = new VmFactory(new Service(HostConfigurator.getHost(host.getParentName()))).create(Type.fromString(host.getGroup()), host);
        try {
            final String[] oldPids = virtualMachine.getProcess().getPids();
            assertThat(oldPids.length != 0).as("JBoss is not running");

            logger.info("Exhausting the JVM heap on {}", host.getIp());
            startRegressionVerificationTasks();

            logger.info("Deploying the web tools");
            logger.info("Exhausting the JVM heap");
            heap.insert();

            sleep(HA_VERIFICATION_TIME);

            final String[] pids = virtualMachine.getProcess().getPids();
            if (pids.length == 0) {
                fail(String.format("JBoss did not restart within %d seconds", HA_VERIFICATION_TIME));
            } else {
                assertThat(pids[0]).isEqualTo(oldPids[0]).as("JBoss was not killed within %d seconds", HA_VERIFICATION_TIME);
            }

            logger.info("Verifying availability after the system has recovered");
            sleep(HA_VERIFICATION_TIME);
        } catch (final Exception t) {
            logger.error(t.getMessage(), t);
            fail(t.getMessage());
        } finally {
            try {
                heap.remove();
            } catch (final FailureInjectionException ignore) {
                logger.warn("Failed to exhaustJvmHeap.", ignore);
            }
            stopRegressionVerificationTasks();
        }
    }
}
