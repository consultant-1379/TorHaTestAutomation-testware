package com.ericsson.sut.test.cases.unused;

import static org.assertj.core.api.Fail.fail;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.testng.annotations.Test;

import com.ericsson.cifwk.taf.annotations.TestId;
import com.ericsson.cifwk.taf.data.Host;
import com.ericsson.nms.rv.taf.failures.FailureInjectionException;
import com.ericsson.nms.rv.taf.failures.GarbageCollector;
import com.ericsson.nms.rv.taf.tools.host.Service;
import com.ericsson.nms.rv.taf.tools.host.VirtualMachine;
import com.ericsson.nms.rv.taf.tools.host.VmFactory;
import com.ericsson.oss.testware.hostconfigurator.HostConfigurator;
import com.ericsson.sut.test.cases.HighAvailabilityTestCase;
import com.google.common.truth.Truth;

/**
 * Runs the garbage collector and verifies system availability.
 */
public class RunGarbageCollector extends HighAvailabilityTestCase {

    private static final Logger logger = LogManager.getLogger(RunGarbageCollector.class);

    /**
     * Runs the garbage collector and verifies system availability.
     *
     * @param host the virtual machine
     */
    @TestId(id = "TORRV-1738_High_1", title = "Run Garbage Collector")
    @Test(enabled = false, groups = {"High Availability"}, dataProvider = "getData", dataProviderClass = ExhaustPermGen.class)
    public final void runGarbageCollector(final Host host) {

        final GarbageCollector gb = new GarbageCollector(host);
        final VirtualMachine virtualMachine = new VmFactory(new Service(HostConfigurator.getHost(host.getParentName()))).create(VirtualMachine.Type.fromString(host.getGroup()), host);
        try {
            final String[] pids = virtualMachine.getProcess().getPids();
            Truth.assertWithMessage("JBoss is not running").that(pids.length).isNotEqualTo(0);

            logger.info("Running the JVM garbage collector on {}", host.getIp());
            startRegressionVerificationTasks();

            logger.info("Deploying the web tools");
            logger.info("Running the JVM garbage collector");
            gb.run();

            sleep(HA_VERIFICATION_TIME);

            logger.info("Verifying availability after the system has recovered");
            sleep(HA_VERIFICATION_TIME);
        } catch (final Exception t) {
            logger.error(t.getMessage(), t);
            fail(t.getMessage());
        } finally {
            try {
                gb.stop();
            } catch (final FailureInjectionException ignore) {
                logger.error(ignore.getMessage(), ignore);
            }
            stopRegressionVerificationTasks();
        }
    }
}
