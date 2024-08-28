package com.ericsson.sut.test.cases;

import static org.assertj.core.api.Fail.fail;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.testng.annotations.Test;

import com.ericsson.cifwk.taf.annotations.TestId;

/**
 * Measures availability during upgrade the procedure.
 */
public class UpgradeAvailability extends HighAvailabilityTestCase {

    private static final Logger logger = LogManager.getLogger(UpgradeAvailability.class);

    /**
     * Measures availability during upgrade the procedure.
     */
    @Test(groups = {"High Availability"})
    @TestId(id = "TORRV-5477_High_1", title = "Measure ENM Availability during Upgrade")
    public void measureUpgradeAvailability() {
        try {
            startUpgradeVerificationTasks(true);
        } catch (final Exception t) {
            logger.error(t.getMessage(), t);
            fail(t.getMessage());
        } finally {
            stopUpgradeVerificationTasks();
        }
    }

}
