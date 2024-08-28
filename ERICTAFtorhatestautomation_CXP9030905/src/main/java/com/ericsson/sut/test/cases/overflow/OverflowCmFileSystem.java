package com.ericsson.sut.test.cases.overflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Fail.fail;

import java.util.ArrayList;
import java.util.List;

import com.ericsson.nms.rv.core.util.RegressionUtils;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import com.ericsson.cifwk.taf.annotations.TestId;
import com.ericsson.cifwk.taf.data.Host;
import com.ericsson.nms.rv.core.util.FunctionalArea;
import com.ericsson.nms.rv.core.util.VmHandler;
import com.ericsson.nms.rv.taf.failures.filesystem.Overflow;
import com.ericsson.nms.rv.taf.tools.host.Service;
import com.ericsson.sut.test.cases.HighAvailabilityTestCase;
import com.google.common.truth.Truth;


/**
 * Overflows a file system and verifies system availability.
 */
public class OverflowCmFileSystem extends HighAvailabilityTestCase {

    private static final Logger logger = LogManager.getLogger(OverflowCmFileSystem.class);

    /**
     * the temp file.
     */
    private static final String TEMP_FILE = "hatemp-";
    private static final VmHandler vmHandler = new VmHandler();

    /**
     * Supplies data to the test method.
     *
     * @return an array of array of objects
     */
    @DataProvider
    public static Object[][] getData() {


        final List<Object[]> list = new ArrayList<>();

        if (FUNCTIONAL_AREAS_MAP.get("cm")) {
            for (final Host host : RegressionUtils.getAllSvcHostList()) {
                final Service service = new Service(host);
                final List<Host> virtualMachines = service.getAllVirtualMachines();

                final List<Object[]> cmList = vmHandler.addVmsForAFunctionalArea(virtualMachines, FunctionalArea.CM);
                list.addAll(cmList);
            }
        }

        final Object[][] objects = new Object[list.size()][2];
        return list.toArray(objects);
    }

    /**
     * Overflows a file system and verifies system availability.
     *
     * @param host       the host
     * @param mountPoint the file system's mount point
     */
    @TestId(id = "TORRV-1698_High_1", title = "Overflow File System")
    @Test(groups = {"High Availability"}, dataProvider = "getData", dataProviderClass = OverflowCmFileSystem.class)
    public final void overflowFileSystem(final Host host, final String mountPoint) {

        final Overflow overflow = new Overflow(host, mountPoint);
        try {

            startRegressionVerificationTasks();

            logger.info("Overflowing file://{}{}", host.getHostname(), mountPoint);
            // the number of files created
            overflow.insert();
            final long availableSpace = overflow.getAvailableSpace();
            logger.info("available space: {}", availableSpace);
            assertThat(availableSpace < 1024 * 1024).as("Failed to overflow the file system");

            logger.info("Verifying availability while overflowing the file system");
            sleep(HA_VERIFICATION_TIME);

            logger.info("Removing the temporary files");
            final String file = mountPoint + "/" + TEMP_FILE + "*";
            overflow.remove();
            Truth.assertWithMessage("Failed to remove the temporary files").that(overflow.isFileExist(file)).isFalse();

            logger.info("Verifying availability after the system has recovered");
            sleep(HA_VERIFICATION_TIME);
        } catch (final Exception t) {
            logger.error(t.getMessage(), t);
            fail(t.getMessage());
        } finally {
            overflow.remove();
            stopRegressionVerificationTasks();
        }

    }
}
