package com.ericsson.sut.test.cases;

import static org.assertj.core.api.Fail.fail;

import com.ericsson.cifwk.taf.annotations.TestId;
import com.ericsson.cifwk.taf.data.DataHandler;
import com.ericsson.nms.rv.core.HAPropertiesReader;
import com.ericsson.nms.rv.core.util.CliResult;
import com.ericsson.nms.rv.core.util.CliShell;
import com.ericsson.nms.rv.taf.tools.Constants;
import com.ericsson.oss.testware.availability.common.exception.EnmException;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

public class FailureWithSamAgentsOnVm extends HighAvailabilityTestCase {

    private static final Logger logger = LogManager.getLogger(FailureWithSamAgentsOnVm.class);
    private static final String serviceRegLeaderIpCommand = "consul operator raft list-peers | grep leader | /bin/tr -s ' '";
    private static final String memberIpCommand = "consul members | grep %s | /bin/tr -s ' '";
    private static final String killCommand = "ssh -q -o StrictHostKeyChecking=no -i /var/tmp/Bravo/pemKey.pem cloud-user@%s 'sudo pkill consul'";
    private static final CliShell vnfLafShell = new CliShell(HAPropertiesReader.getMS());

    @DataProvider
    public static Object[][] getData() {
        final Object host = DataHandler.getAttribute("consul.member.vm");
        final List<Object[]> list = new ArrayList<>();
        logger.info("Using consul member host : {}", host.toString());
        list.add(new Object[]{host.toString()});
        final Object[][] objects = new Object[list.size()][1];
        return list.toArray(objects);
    }

    @TestId(id = "ST_RV_HA_CL_04", title = "Failure with SAM agents on a VM")
    @Test(groups = {"High Availability"}, dataProvider = "getData", dataProviderClass = FailureWithSamAgentsOnVm.class)
    public final void failureWithSamAgentsOnVm(final String memberName) {
        if (!(memberName.startsWith("fmserv") || memberName.startsWith("pmserv") || memberName.startsWith("cmserv"))) {
            logger.warn("Invalid consul member : {}", memberName);
            fail("Invalid consul member : %s", memberName);
            return;
        }
        final String memberCommand = String.format(memberIpCommand, memberName);
        boolean isVerifySuccess = false;
        String leaderIp = "";
        String memberIp = "";
        try {
            final CliResult memberIpResult = vnfLafShell.execute(memberCommand);
            logger.info("Consul member status before test : {}", memberIpResult.getOutput());
            if (memberIpResult.isSuccess() && !memberIpResult.getOutput().isEmpty()) {
                memberIp = memberIpResult.getOutput().split(" ")[1].split(":")[0];
            }
            final CliResult leaderIpResult = vnfLafShell.execute(serviceRegLeaderIpCommand);
            logger.info("Consul leader status before test : {}", leaderIpResult.getOutput());
            if (leaderIpResult.isSuccess() && !leaderIpResult.getOutput().isEmpty()) {
                leaderIp = leaderIpResult.getOutput().split(" ")[2].split(":")[0];
            }
        } catch (final Exception e) {
            fail("Fail to get Leader/Member IPs.");
            return;
        }
        logger.info("Member Ip : {}, leaderIp : {}", memberIp, leaderIp);
        if (leaderIp.isEmpty() || memberIp.isEmpty()) {
            fail("Fail to get Leader/Member IPs.");
            return;
        }

        try {
            if (HA_VERIFICATION_TIME < 10 * Constants.Time.ONE_MINUTE_IN_SECONDS) {
                throw new IllegalArgumentException("HA verification time must be at least 10 minutes");
            }
            startRegressionVerificationTasks();
            sleep(20);  //wait to start verifiers completely!
            final CliResult memberKilledResult = vnfLafShell.execute(String.format(killCommand, memberIp));
            logger.info("memberKilledResult : {}", memberKilledResult);
            if (memberKilledResult.isSuccess()) {
                logger.info("Member {} Killed successfully. : {}", memberName, memberKilledResult.getOutput());
            } else {
                throw new EnmException("Failed to kill member : " + memberName);
            }
            final CliResult leaderKilledResult = vnfLafShell.execute(String.format(killCommand, leaderIp));
            logger.info("leaderKilledResult : {}", leaderKilledResult);
            if (leaderKilledResult.isSuccess()) {
                logger.info("Leader Killed successfully. : {}", leaderKilledResult.getOutput());
            } else {
                throw new EnmException("Failed to kill servicereg-leader.");
            }
            int check = 0;
            do {
                sleep(60);
                logger.info("Consul member status during test : \n{}", vnfLafShell.execute(memberCommand).getOutput());
                logger.info("Servicereg leader status during test : \n{}", vnfLafShell.execute(serviceRegLeaderIpCommand).getOutput());
                check++;
            } while (check < 3);

            sleep(HA_VERIFICATION_TIME);

            int count = 0;
            do {
                final String memberStatus = vnfLafShell.execute(memberCommand).getOutput();
                final String leaderStatus = vnfLafShell.execute(serviceRegLeaderIpCommand).getOutput();
                logger.info("Consul member status after test : \n{}", memberStatus);
                logger.info("Srvicereg leader status after test : \n{}", leaderStatus);
                if (memberStatus.contains("alive") && leaderStatus.contains("leader")) {
                    isVerifySuccess = true;
                }
                logger.info("isVerifySuccess : {}", isVerifySuccess);
                if (!isVerifySuccess) {
                    logger.info("Waiting for {}/servicereg-leader to be online.", memberName);
                    sleep(60);
                }
                count++;
            } while (!isVerifySuccess && count < 5);

        } catch (final Exception e) {
            logger.error(e.getMessage(), e);
            fail(e.getMessage());
        } finally {
            stopRegressionVerificationTasks();
        }

    }
}
