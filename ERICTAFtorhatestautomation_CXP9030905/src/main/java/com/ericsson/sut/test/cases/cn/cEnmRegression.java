package com.ericsson.sut.test.cases.cn;

import com.ericsson.nms.rv.core.HAPropertiesReader;
import com.ericsson.nms.rv.core.util.RegressionUtils;
import com.ericsson.sut.test.cases.HighAvailabilityTestCase;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.fail;

/**
 * Writes {@code Hello, world} into the log.
 */
public class cEnmRegression extends HighAvailabilityTestCase {

    private static final Logger logger = LogManager.getLogger(com.ericsson.sut.test.cases.cn.cEnmRegression.class);

    /**
     * Supplies data to the test method.
     *
     * @return an array of array of objects
     */
    @DataProvider
    private static Object[][] getData() {
        final String testCase = " cENM Regression : " + HAPropertiesReader.cEnmChaosTestType + "-" + HAPropertiesReader.cEnmChaosAppName + " ";
        return new Object[][]{{testCase}};
    }

    /**
     * Executes generic cENM chaos Regression test case.
     */
    @Test(groups = {"High Availability"}, dataProvider = "getData", dataProviderClass = com.ericsson.sut.test.cases.cn.cEnmRegression.class)
    public void executeTestCase(final String testCase) {
        try {
            logger.info("Executing .. {}", testCase);
            RegressionUtils.configChaosMesh("install");
            startRegressionVerificationTasks();
            RegressionUtils.executeCloudNaiveChaosRegressionTest("start");
            sleep(HA_VERIFICATION_TIME);
            RegressionUtils.executeCloudNaiveChaosRegressionTest("stop");
        } catch (final Exception t) {
            logger.error(t.getMessage(), t);
            fail(t.getMessage());
        } finally {
            RegressionUtils.configChaosMesh("remove");
            stopRegressionVerificationTasks();
        }
    }
}
