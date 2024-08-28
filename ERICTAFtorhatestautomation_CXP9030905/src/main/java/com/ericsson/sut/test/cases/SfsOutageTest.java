package com.ericsson.sut.test.cases;

import static org.assertj.core.api.Fail.fail;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.testng.annotations.Test;

import com.ericsson.cifwk.taf.annotations.TestId;
import com.ericsson.cifwk.taf.data.Host;
import com.ericsson.nms.rv.core.HAPropertiesReader;
import com.ericsson.nms.rv.taf.failures.filesystem.SfsOutage;

public class SfsOutageTest extends HighAvailabilityTestCase {

    private static final Logger logger = LogManager.getLogger(SfsOutageTest.class);

    /**
     * Unmounts all file systems of type NFS and verifies system availability.
     */
    @TestId(id = "TORRV-1734_High_1", title = "insert SFS Outage")
    @Test(groups = {"High Availability"})
    public final void insertSfsOutage() {

        final Host sfs = new Host();
        final Host ilo1 = new Host();
        final Host ilo2 = new Host();

        sfs.setIp(HAPropertiesReader.getSfsClusterIp());
        sfs.setUser(HAPropertiesReader.getSfsClusterUsername());
        sfs.setPass(HAPropertiesReader.getSfsClusterPassword());

        ilo1.setIp(HAPropertiesReader.getSfsIlo1Ip());
        ilo1.setUser(HAPropertiesReader.getSfsIloUsername());
        ilo1.setPass(HAPropertiesReader.getSfsIloPassword());

        ilo2.setIp(HAPropertiesReader.getSfsIlo2Ip());
        ilo2.setUser(HAPropertiesReader.getSfsIloUsername());
        ilo2.setPass(HAPropertiesReader.getSfsIloPassword());

        final SfsOutage sfsOutage = new SfsOutage(sfs, ilo1, ilo2);

        try {
            startRegressionVerificationTasks();

            logger.info("inserting SFS Outage on {}", sfs.getHostname());
            sfsOutage.insert();

            logger.info("Verifying availability after insert SFS Outage ");
            sleep(HA_VERIFICATION_TIME);

            logger.info("removing SFS Outage");
            sfsOutage.remove();

            logger.info(
                    "Verifying availability after the system has recovered");
            sleep(HA_VERIFICATION_TIME);
        } catch (final Exception t) {
            logger.error(t.getMessage(), t);
            fail(t.getMessage());
        } finally {
            stopRegressionVerificationTasks();
        }
    }

}
