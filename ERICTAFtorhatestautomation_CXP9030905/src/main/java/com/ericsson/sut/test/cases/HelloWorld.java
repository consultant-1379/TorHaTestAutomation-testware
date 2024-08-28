package com.ericsson.sut.test.cases;

import static org.assertj.core.api.Assertions.fail;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Writes {@code Hello, world} into the log.
 */
public class HelloWorld extends HighAvailabilityTestCase {

    private static final Logger logger = LogManager.getLogger(HelloWorld.class);

    /**
     * Supplies data to the test method.
     *
     * @return an array of array of objects
     */
    @DataProvider
    private static Object[][] getData() {
        return new Object[][]{{"Hello, world"}};
    }

    /**
     * Writes the given {@code hello} into the log.
     *
     * @param hello the message to be written
     */
    @Test(groups = {"High Availability"}, dataProvider = "getData", dataProviderClass = HelloWorld.class)
    public void helloWorld(final String hello) {
        try {
            logger.info(hello);
            startRegressionVerificationTasks();
            sleep(HA_VERIFICATION_TIME);
        } catch (final Exception t) {
            logger.error(t.getMessage(), t);
            fail(t.getMessage());
        } finally {
            stopRegressionVerificationTasks();
        }
    }
}
