package com.ericsson.sut.test.cases;

import static org.assertj.core.api.Fail.fail;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.testng.annotations.Test;

import com.ericsson.cifwk.taf.annotations.TestId;
import com.ericsson.cifwk.taf.data.Host;
import com.ericsson.nms.rv.taf.failures.FailureInjectionException;
import com.ericsson.nms.rv.taf.failures.filesystem.BlockSize;

/**
 * Reduce the block size for reads and writes on a file system mounted using NFS.
 */
public class ReduceNfsBlockSize extends HighAvailabilityTestCase {

    private static final Logger logger = LogManager.getLogger(ReduceNfsBlockSize.class);

    /**
     * Reduce the block size for reads and writes on a file system mounted using NFS.
     *
     * @param host the host
     */
    @TestId(id = "TORRV-1769_High_1", title = "Reduce NFS block size")
    @Test(groups = {"High Availability"}, dataProvider = "getData", dataProviderClass = HaltHost.class)
    public final void reduceNfsBlockSize(final Host host) {

        final BlockSize bs = new BlockSize(host);
        try {
            startRegressionVerificationTasks();

            logger.info("Reducing the NFS block size on {}", host.getHostname());
            bs.reduce();

            logger.info("Verifying availability after reducing the NFS block size");
            sleep(HA_VERIFICATION_TIME);

            logger.info("Restoring NFS block size");
            bs.restore();

            logger.info("Verifying availability after the system has recovered");
            sleep(HA_VERIFICATION_TIME);
        } catch (final Exception t) {
            logger.error(t.getMessage(), t);
            fail(t.getMessage());
        } finally {
            try {
                bs.restore();
            } catch (final FailureInjectionException e) {
                logger.error(e.getMessage(), e);
            }
            stopRegressionVerificationTasks();
        }
    }
}
