package com.ericsson.sut.test.cases;

import static org.assertj.core.api.Fail.fail;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.testng.annotations.Test;

import com.ericsson.cifwk.taf.annotations.TestId;
import com.ericsson.cifwk.taf.data.Host;
import com.ericsson.nms.rv.taf.failures.FailureInjectionException;
import com.ericsson.nms.rv.taf.failures.filesystem.Nfs;

/**
 * Unmounts all file systems of type NFS and verifies system availability.
 */
public class UnmountNfs extends HighAvailabilityPuppetTestCase {

    private static final Logger logger = LogManager.getLogger(UnmountNfs.class);

    /**
     * Unmounts all file systems of type NFS and verifies system availability.
     *
     * @param host the the host
     */
    @TestId(id = "TORRV-1754_High_1", title = "Unmount all file systems of type NFS")
    @Test(groups = {"High Availability"}, dataProvider = "getData", dataProviderClass = HaltHost.class)
    public final void unmountNfs(final Host host) {

        final Nfs nfs = new Nfs(host);
        try {
            startRegressionVerificationTasks();

            logger.info("Unmounting all file systems of type NFS on {}", host.getHostname());
            nfs.unmount();

            logger.info("Verifying availability after unmounting all file systems of type NFS");
            sleep(HA_VERIFICATION_TIME);

            logger.info("Mounting all file systems of type NFS");
            nfs.mount();

            logger.info("Verifying availability after the system has recovered");
            sleep(HA_VERIFICATION_TIME);
        } catch (final Exception t) {
            logger.error(t.getMessage(), t);
            fail(t.getMessage());
        } finally {
            try {
                nfs.mount();
            } catch (final FailureInjectionException ignore) {
                logger.warn("Failed to unmountNfs.", ignore);
            }
            stopRegressionVerificationTasks();
        }
    }

}
