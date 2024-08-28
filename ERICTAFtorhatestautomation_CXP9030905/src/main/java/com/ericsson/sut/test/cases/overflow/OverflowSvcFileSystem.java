package com.ericsson.sut.test.cases.overflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Fail.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.ericsson.nms.rv.core.util.RegressionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import com.ericsson.cifwk.taf.annotations.TestId;
import com.ericsson.cifwk.taf.data.Host;
import com.ericsson.nms.rv.core.util.CliResult;
import com.ericsson.nms.rv.core.util.CliShell;
import com.ericsson.nms.rv.core.util.FunctionalArea;
import com.ericsson.nms.rv.core.util.filesystem.FileSystemHandler;
import com.ericsson.nms.rv.taf.failures.filesystem.Overflow;
import com.ericsson.sut.test.cases.HighAvailabilityTestCase;
import com.google.common.truth.Truth;


/**
 * Overflows a file system and verifies system availability.
 */
public class OverflowSvcFileSystem extends HighAvailabilityTestCase {

    private static final Logger logger = LogManager.getLogger(OverflowSvcFileSystem.class);

    /**
     * the temp file.
     */
    private static final String TEMP_FILE = "hatemp-";

    private static final FileSystemHandler fileSystemHandler = new FileSystemHandler();

    /**
     * Supplies data to the test method.
     *
     * @return an array of array of objects
     */
    @DataProvider
    public static Object[][] getData() {

        final List<Object[]> list = new ArrayList<>();
        boolean first = true;

        final List<String> dirList = fileSystemHandler.getOverflowDirectories(FunctionalArea.SVC);

        for (final Host host : RegressionUtils.getAllSvcHostList()) {
            if (first) {
                for (final String dirName : dirList) {
                    if (dirName.contains("pmic")) {
                        try {
                            final List<String> pmciList = listFilesWithFilter(host, "/ericsson/", "pmic");
                            if (pmciList != null && !pmciList.isEmpty()) {
                                for (final String pmciDir : pmciList) {
                                    list.add(new Object[]{host, "/ericsson/" + pmciDir});
                                }
                            }
                        } catch (final Exception e) {
                            logger.error("no pmic directories were found on host: {}", host, e);
                        }
                    } else {
                        list.add(new Object[]{host, dirName});
                    }
                }
                first = false;
            }
        }

        final Object[][] objects = new Object[list.size()][2];
        return list.toArray(objects);
    }

    private static List<String> listFilesWithFilter(final Host host, final String dir, final String filter) {
        if (host == null) {
            throw new IllegalArgumentException("server cannot be found on the system " + host);
        }

        final String command = "/bin/ls %s | /bin/grep %s";

        final CliShell shell = new CliShell(host);
        final CliResult executionResult = shell.execute(String.format(command, dir, filter));

        final String stdOut = executionResult.getOutput();
        if (!StringUtils.isEmpty(stdOut)) {
            final String[] dirs = stdOut.split("\\n");

            return Arrays.asList(Arrays.copyOf(dirs, dirs.length - 1));
        }
        return Collections.emptyList();
    }

    /**
     * Overflows a file system and verifies system availability.
     *
     * @param host       the host
     * @param mountPoint the file system's mount point
     */
    @TestId(id = "TORRV-1698_High_1", title = "Overflow File System")
    @Test(groups = {"High Availability"}, dataProvider = "getData", dataProviderClass = OverflowSvcFileSystem.class)
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
