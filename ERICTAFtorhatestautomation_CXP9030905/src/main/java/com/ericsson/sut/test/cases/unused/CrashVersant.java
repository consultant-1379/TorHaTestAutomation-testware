package com.ericsson.sut.test.cases.unused;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Fail.fail;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.testng.annotations.Test;

import com.ericsson.cifwk.taf.annotations.TestId;
import com.ericsson.nms.rv.taf.failures.FailureInjectionException;
import com.ericsson.nms.rv.taf.failures.database.Versant;
import com.ericsson.nms.rv.taf.tools.host.Database;
import com.ericsson.oss.testware.availability.common.shell.ShellException;
import com.ericsson.sut.test.cases.HighAvailabilityTestCase;

/**
 * Crashes versant and verifies system availability.
 */
public class CrashVersant extends HighAvailabilityTestCase {

    private static final Logger logger = LogManager.getLogger(CrashVersant.class);

    /**
     * Crashes versant and verifies system availability.
     */
    @TestId(id = "TORRV-4876_High_1", title = "Crash Versant")
    @Test(groups = {"High Availability"})
    public final void crashVersant() {
        final String logFile = "log" + new SimpleDateFormat("_yyyy_MM_dd_HH_mm_ss").format(new Date()) + ".xml";

        final Versant versant = new Versant();
        Database versantDb;

        try {

            versantDb = versant.getDatabase();
            startRegressionVerificationTasks();
            versantDb.startVersantTransactions(logFile);
            logger.info("Inserting the pcrash module into the Linux kernel of {}", versantDb.getHostname());

            versant.insertCrash();

            logger.info("Verifying availability while attempting to crash Versant");
            final int time = new Random(System.currentTimeMillis()).nextInt(HA_VERIFICATION_TIME / 2);
            sleep(time);

            sleep(HA_VERIFICATION_TIME - time);

            logger.info("Removing the pcrash module from the Linux kernel of {}", versantDb.getHostname());
            versant.removeCrash();

            logger.info("Verifying availability after the system has recovered");
            sleep(HA_VERIFICATION_TIME);

        } catch (final Exception t) {
            logger.error(t.getMessage(), t);
            fail(t.getMessage());
        } finally {
            stopRegressionVerificationTasks();

            try {
                versant.removeCrash();
                versant.restart();
                versantDb = versant.getDatabase();
                if (versantDb != null) {
                    versantDb.validateVersantTransactions(logFile);
                    assertThat(versantDb.isTransactionSuccessful(logFile));
                }
            } catch (final FailureInjectionException | ShellException e) {
                logger.error(e.getMessage(), e);
            }
        }
    }

}
