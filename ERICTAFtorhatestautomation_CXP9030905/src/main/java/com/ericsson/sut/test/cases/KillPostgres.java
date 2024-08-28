package com.ericsson.sut.test.cases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Fail.fail;

import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.Date;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.testng.annotations.Test;

import com.ericsson.cifwk.taf.annotations.TestId;
import com.ericsson.nms.rv.taf.failures.FailureInjectionException;
import com.ericsson.nms.rv.taf.failures.database.Postgres;
import com.ericsson.nms.rv.taf.tools.host.Database;
import com.ericsson.oss.testware.availability.common.shell.ShellException;

/**
 * Kills postgres and verifies system availability.
 */
public class KillPostgres extends HighAvailabilityTestCase {

    private static final Logger logger = LogManager.getLogger(KillPostgres.class);
    public static LocalDateTime postgresKillTime;
    public static String postgresDbHost = "";

    /**
     * Kills postgres and verifies system availability.
     */
    @TestId(id = "TORRV-4877_High_1", title = "Kill Postgres Process")
    @Test(groups = {"High Availability"})
    public final void killPostgres() {
        Postgres postgres = null;
        Database postgresHost = null;
        final String logFile = "log" + new SimpleDateFormat("_yyyy_MM_dd_HH_mm_ss").format(new Date()) + ".xml";
        try {
            postgres = new Postgres();
            postgresHost = postgres.getPostgresHost();

            if (postgresHost != null) {
                startRegressionVerificationTasks();

                postgresHost.startPostgresTransactions(logFile);

                logger.info("Killing Postgres on {}", postgresHost.getHostname());
                postgresKillTime = LocalDateTime.now();
                postgresDbHost = postgresHost.getHostname();
                postgres.kill();
                assertThat(postgresHost.wasPostgresKilled()).as("Failed to kill Postgress");

                logger.info("Waiting for the system to recover after killing Postgres");
                sleep(HA_VERIFICATION_TIME);

            } else {
                logger.info("Postgres is not running before a failure has been injected");
                fail("Postgres is not running before a failure has been injected");
            }
        } catch (final FailureInjectionException f) {
            logger.error("Failed to kill Postgres : ", f);
            fail(f.getMessage());
        } catch (final Exception t) {
            logger.error("Failed to kill Postgres", t);
            fail(t.getMessage());
        } finally {
            if (postgresHost != null) {
                stopRegressionVerificationTasks();
                try {
                    postgres.restart();
                    postgresHost = postgres.getDatabase();
                    if (postgresHost != null) {
                        postgresHost.validatePostgresTransactions(logFile);
                        assertThat(postgresHost.isTransactionSuccessful(logFile));
                    }
                } catch (final FailureInjectionException | ShellException e) {
                    logger.error("Failed to restart Postgres", e);
                }
            }
        }

    }

}
