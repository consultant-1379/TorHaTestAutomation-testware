package com.ericsson.sut.test.cases;

import static org.assertj.core.api.Fail.fail;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import com.ericsson.cifwk.taf.annotations.TestId;
import com.ericsson.nms.rv.taf.failures.Stress;

/**
 * Stresses ENM and verifies system availability. The stress tool will drop the caches and increase:
 * <ul>
 * <li>CPU usage</li>
 * <li>I/O</li>
 * <li>number of context switches</li>
 * <li>number of switches between user and kernel space</li>
 * </ul>
 * Additionally, it will stress the SAN and the SFS.
 */
public class StressEnm extends HighAvailabilityTestCase {

    private static final Logger logger = LogManager.getLogger(StressEnm.class);

    /**
     * Supplies data to the test method.
     *
     * @return an array of array of objects
     */
    @DataProvider
    public static Object[][] getData() {
        return new Object[][]{{50, 60, 70}};
    }

    /**
     * Stresses ENM and verifies system availability.
     *
     * @param processes    the number of processes injecting load on the hosts
     * @param sanProcesses the number of processes injecting load on the SAN
     * @param sfsProcesses the number of processes injecting load on the SFS
     */
    @TestId(id = "TORRV-1971_High_1", title = "Stress ENM")
    @Test(groups = {"High Availability"}, dataProvider = "getData", dataProviderClass = StressEnm.class)
    public final void stressEnm(final int processes, final int sanProcesses, final int sfsProcesses) {

        Stress stress = null;

        try {
            stress = new Stress();
            startRegressionVerificationTasks();

            logger.info("Stressing ENM");
            logger.info("Stressing SFS");
            stress.stressSFS(sfsProcesses, HA_VERIFICATION_TIME);

            logger.info("Stressing all hosts");
            stress.stressHost(processes, HA_VERIFICATION_TIME);

            logger.info("Stressing SAN");
            stress.stressSAN(sanProcesses, HA_VERIFICATION_TIME);

            logger.info("Verifying availability while stressing ENM");
            sleep(HA_VERIFICATION_TIME);

            logger.info("Verifying availability after the system has recovered");
            sleep(HA_VERIFICATION_TIME);
        } catch (final Exception t) {
            logger.error(t.getMessage(), t);
            fail(t.getMessage());
        } finally {
            if (stress != null) {
                stress.stop();
            }
            stopRegressionVerificationTasks();
        }

    }

}
