/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2012
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/
package com.ericsson.sut.test.cases;

import static org.assertj.core.api.Fail.fail;

import java.util.ArrayList;
import java.util.List;


import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import com.ericsson.cifwk.taf.annotations.TestId;
import com.ericsson.nms.rv.core.HAPropertiesReader;
import com.ericsson.nms.rv.core.upgrade.RegressionVerificationTasks;
import com.ericsson.nms.rv.core.util.CliResult;
import com.ericsson.nms.rv.core.util.CliShell;
import com.ericsson.nms.rv.taf.tools.Constants;
import com.ericsson.oss.testware.availability.common.exception.EnmException;
import com.ericsson.oss.testware.hostconfigurator.HostConfigurator;

public class SfsFailOverTest extends HighAvailabilityTestCase {

    private static final Logger logger = LogManager.getLogger(SfsFailOverTest.class);
    private final String healthCheckCommand = "/opt/ericsson/enminst/bin/enm_healthcheck.sh";
    private static final String SUPPORT_USER_PASSWORD = HAPropertiesReader.getSupportUserPassword();
    /**
     * Supplies data to the test method.
     *
     * @return an array of array of objects
     * @throws EnmException
     */
    @DataProvider
    public static Object[][] getData() throws EnmException {
        final String supportUserCommand = "/usr/bin/litp show -p /infrastructure/storage/storage_providers/sfs | /bin/grep -E '_ipv|_name' | /bin/awk '{print $2}' | /usr/bin/xargs | /bin/sed -e 's/ /,/g' | awk -F \",\" '{print $1}'";
        logger.info("supportUserCommand : {}", supportUserCommand);
        final CliShell shell = new CliShell(HostConfigurator.getMS());
        final CliResult supportUserExecResult = shell.executeAsRoot(supportUserCommand);
        logger.info("supportUserIP : {}", supportUserExecResult.getOutput());
        final String supportUserIP = supportUserExecResult.getOutput();
        final List<Object[]> list = new ArrayList<>();
        list.add(new Object[]{supportUserIP});

        final Object[][] objects = new Object[list.size()][1];
        return list.toArray(objects);

    }

    /**
     * Unmounts all file systems of type NFS and verifies system availability.
     *
     */
    @TestId(id = "TORRV-1736_High_1", title = "Insert SFS Failover")
    @Test(groups = "High Availability", dataProvider = "getData", dataProviderClass = SfsFailOverTest.class)
    public final void insertSfsFailover(final String supportUserIPAddress) {
        final String onCommand = "expect -c 'spawn ssh -o StrictHostKeyChecking=no support@" + supportUserIPAddress + " /sbin/reboot; expect password ; send \""+ SUPPORT_USER_PASSWORD +"\\n\" ; interact'";
        final CliShell ShellMS = new CliShell(HostConfigurator.getMS());
        if (HA_VERIFICATION_TIME < 10 * Constants.Time.ONE_MINUTE_IN_SECONDS) {
            throw new IllegalArgumentException("HA verification time must be at least 10 minutes");
        }
        final CliResult resultHealthCheckBefore = ShellMS.executeAsRoot(healthCheckCommand);
        logger.info("Health check command is {}", healthCheckCommand);
        if (resultHealthCheckBefore.getExitCode() == 0) {
            logger.info("Health check : Success");
            try {
                startRegressionVerificationTasks();
                logger.info("starting SFS Failover.");
                try {
                    final String updateHostFileCommand = String.format("sed '/%s/d' $HOME/.ssh/known_hosts > /tmp/tmp_hosts; cat /tmp/tmp_hosts > $HOME/.ssh/known_hosts; rm -rf /tmp/tmp_hosts", supportUserIPAddress);
                    logger.info("updateHostFile command : {}", updateHostFileCommand);
                    final CliResult updateHostFileResult = ShellMS.execute(updateHostFileCommand);
                    logger.info("updateHostFileResult : {}/{}", updateHostFileResult.getOutput(), ShellMS.execute("cat $HOME/.ssh/known_hosts").getOutput());
                } catch (final Exception e) {
                    logger.warn("Message : {}", e.getMessage());
                }

                CliResult supportUSERRebootResult = ShellMS.executeAsRoot(onCommand);
                logger.info("Reboot Support user command is {}", onCommand);
                logger.info("Reboot command execution result {}",supportUSERRebootResult.getOutput());
                if(supportUSERRebootResult.getOutput().toUpperCase().contains("REMOTE HOST IDENTIFICATION HAS CHANGED")) {
                    logger.warn("Login failed. retrying...");
                    final String removeCommand = "rm -rf /root/.ssh/known_hosts";
                    logger.info("removeCommand : {}", removeCommand);
                    final CliResult removeCommandResult = ShellMS.executeAsRoot(removeCommand);
                    logger.info("removeCommandResult : {}", removeCommandResult.getOutput());

                    supportUSERRebootResult = ShellMS.executeAsRoot(onCommand);
                    logger.info("Reboot Support user command is {}", onCommand);
                    logger.info("Reboot command execution result {}",supportUSERRebootResult.getOutput());
                    if(supportUSERRebootResult.getOutput().toUpperCase().contains("REMOTE HOST IDENTIFICATION HAS CHANGED")) {
                        logger.warn("Login to host failed again.");
                        fail("Login to host is failed");
                    }
                }
                if(supportUSERRebootResult.getExitCode() == 0){
                    logger.info("Verifying availability while rebooting the support user ");
                    sleep(HA_VERIFICATION_TIME + HA_VERIFICATION_TIME );
                    final CliResult resultCheck = ShellMS.executeAsRoot(healthCheckCommand);
                    if (resultCheck.getExitCode() == 0) {
                        logger.info("Health Check after SFS reboot :Success");
                        logger.info("Verifying availability after the system has recovered");
                        sleep(HA_VERIFICATION_TIME);
                    } else {
                        logger.info("Health Check after SFS reboot :Failed");
                        RegressionVerificationTasks.isHcAfterRebootFailed = true;
                    }
                }  else {
                    logger.info("supportUSERRebootResult {}",supportUSERRebootResult.getOutput());
                    fail("Failed in rebooting the support user ");
                }

            } catch( final Exception t){
                logger.error(t.getMessage(), t);
                fail(t.getMessage());
            } finally{
                logger.info("stopRegressionVerificationTasks");
                stopRegressionVerificationTasks();
            }
        } else {
            logger.info("Health Check status before starting verifier: Failed");
            fail("Health check is failed");
        }
    }


}
