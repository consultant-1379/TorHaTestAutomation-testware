package com.ericsson.sut.test.cases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Fail.fail;

import static com.ericsson.nms.rv.taf.tools.process.Process.SIGKILL;

import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import com.ericsson.cifwk.taf.annotations.TestId;
import com.ericsson.cifwk.taf.data.Host;
import com.ericsson.nms.rv.taf.failures.Kill;
import com.ericsson.nms.rv.taf.tools.host.VirtualMachine.ProcessType;
import com.ericsson.oss.testware.hostconfigurator.HostConfigurator;

/**
 * Kills openidm process and verifies system availability.
 */
public class KillOpenIdm extends HighAvailabilityTestCase {

    private static final Logger logger = LogManager.getLogger(KillOpenIdm.class);

    /**
     * Supplies data to the test methods. We kill it twice to know if there is any difference killing them on both
     * nodes.
     *
     * @return an array of array of objects
     */
    @DataProvider
    public static Object[][] getData() {
        final List<Object[]> list = new ArrayList<>();

        final List<Host> openIdms = HostConfigurator.getAllHosts("openidm");
        openIdms.forEach(host -> list.add(new Object[]{host, SIGKILL}));

        final Object[][] objects = new Object[list.size()][2];
        return list.toArray(objects);
    }

    /**
     * Kills the openidm process in a virtual machine and verifies system availability.
     *
     * @param signal the Unix signal to be sent
     */
    @TestId(id = "TORRV-5013_High_1", title = "Kill openidm Process")
    @Test(groups = {"High Availability"}, dataProvider = "getData", dataProviderClass = KillOpenIdm.class)
    public final void killOpenIdmProcess(final Host openIdm, final int signal) {
        final Kill kill = new Kill(openIdm);
        try {
            startRegressionVerificationTasks();

            logger.info("Killing openidm process on openidm VM with signal [{}]", signal);
            final boolean success = kill.processes(ProcessType.OPEN_IDM_PROCESS, signal);
            assertThat(success).as("Failed to kill OpenIdm");

            logger.info("Waiting for the system to recover after killing openidm process");
            sleep(HA_VERIFICATION_TIME);
        } catch (final Exception t) {
            logger.error(t.getMessage(), t);
            fail(t.getMessage());
        } finally {
            stopRegressionVerificationTasks();
        }
    }

    /**
     * Kills the openidm virtual machine and verifies system availability.
     *
     * @param signal the Unix signal to be sent
     */
    @TestId(id = "TORRV-5013_High_2", title = "Kill openidm Virtual Machine")
    @Test(groups = {"High Availability"}, dataProvider = "getData", dataProviderClass = KillOpenIdm.class)
    public final void killOpenIdmVirtualMachine(final Host openIdm, final int signal) {
        final Kill kill = new Kill(openIdm);
        try {
            startRegressionVerificationTasks();

            logger.info("Killing the openidm virtual machine with signal [{}]", signal);
            final boolean success = kill.virtualMachine(signal);
            assertThat(success).as("Failed to kill openidm virtual machine");

            logger.info("Waiting for the system to recover after killing openidm virtual machine");
            sleep(HA_VERIFICATION_TIME);
        } catch (final Exception t) {
            logger.error(t.getMessage(), t);
            fail(t.getMessage());
        } finally {
            stopRegressionVerificationTasks();
        }
    }

}
