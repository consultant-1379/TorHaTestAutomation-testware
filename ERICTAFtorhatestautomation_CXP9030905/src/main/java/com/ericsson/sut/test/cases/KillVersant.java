package com.ericsson.sut.test.cases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Fail.fail;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import com.ericsson.cifwk.taf.annotations.TestId;
import com.ericsson.nms.rv.taf.failures.FailureInjectionException;
import com.ericsson.nms.rv.taf.failures.database.Versant;
import com.ericsson.nms.rv.taf.tools.host.Database;
import com.ericsson.nms.rv.taf.tools.process.Process;
import com.ericsson.oss.testware.availability.common.shell.ShellException;

/**
 * Kills versant and verifies system availability.
 */
public class KillVersant extends HighAvailabilityPuppetTestCase {

    private static final Logger logger = LogManager.getLogger(KillVersant.class);

    /**
     * Supplies data to the test methods. We kill it twice to know if there is any difference killing them on both
     * nodes.
     *
     * @return an array of array of objects
     */
    @DataProvider
    public static Object[][] getData() {

        final List<Object[]> list = new ArrayList<>();

        list.add(new Object[]{Process.SIGKILL});
        list.add(new Object[]{Process.SIGKILL});

        final Object[][] objects = new Object[list.size()][1];
        return list.toArray(objects);
    }

    /**
     * Kills versant and verifies system availability. Disabled because failover does not appear to work (see CrashVersant).
     *
     * @param signal the Unix signal to be sent
     */
    @TestId(id = "TORRV-3324_High_1", title = "Kill Versant Process")
    @Test(groups = {"High Availability"}, dataProvider = "getData", dataProviderClass = KillVersant.class)
    public final void killVersant(final int signal) {
        final String logFile = "log" + new SimpleDateFormat("_yyyy_MM_dd_HH_mm_ss").format(new Date()) + ".xml";
        Database versantHost = null;

        final Versant versant = new Versant();
        try {
            versantHost = versant.getDatabase();
            if (versantHost != null) {
                startRegressionVerificationTasks();

                versantHost.startVersantTransactions(logFile);

                logger.info("Killing Versant on [{}] with signal [{}]", versantHost.getHostname(), signal);
                versant.kill(signal);
                assertThat(versantHost.wasVersantKilled()).as("Failed to kill Versant");

                logger.info("Waiting for the system to recover after killing Versant");
                sleep(HA_VERIFICATION_TIME);

            } else {
                logger.info("Versant is not running before a failure has been injected");
                fail("Versant is not running before a failure has been injected");
            }
        } catch (final Exception t) {
            logger.error(t.getMessage(), t);
            fail(t.getMessage());
        } finally {
            stopRegressionVerificationTasks();

            if (versantHost != null) {
                try {
                    versant.restart();
                    versantHost = versant.getDatabase();
                    if (versantHost != null) {
                        versantHost.validateVersantTransactions(logFile);
                        assertThat(versantHost.isTransactionSuccessful(logFile));
                    }
                } catch (final FailureInjectionException | ShellException e) {
                    logger.error(e.getMessage(), e);
                }
            }
        }

    }

}
