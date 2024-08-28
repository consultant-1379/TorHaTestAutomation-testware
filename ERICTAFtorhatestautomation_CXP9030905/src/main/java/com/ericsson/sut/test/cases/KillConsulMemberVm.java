package com.ericsson.sut.test.cases;

import static org.assertj.core.api.Fail.fail;

import com.ericsson.cifwk.taf.annotations.TestId;
import com.ericsson.cifwk.taf.data.DataHandler;
import com.ericsson.nms.rv.core.HAPropertiesReader;
import com.ericsson.nms.rv.core.util.CliResult;
import com.ericsson.nms.rv.core.util.CliShell;
import com.ericsson.nms.rv.taf.tools.Constants;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

public class KillConsulMemberVm extends HighAvailabilityTestCase {

    private static final Logger logger = LogManager.getLogger(KillConsulMemberVm.class);
    private static final String aliveMemberIpCommand = "consul members | grep %s | grep alive | /bin/tr -s ' ' | head -n 1";
    private static final String memberStatusCommand = "consul members | grep %s | /bin/tr -s ' ' | head -n 1";
    private static final String serviceRegLeaderCommand = "consul operator raft list-peers | grep %s | /bin/tr -s ' '";
    private static final String killCommand = "ssh -q -o StrictHostKeyChecking=no -i /var/tmp/Bravo/pemKey.pem cloud-user@%s 'sudo pkill consul'";
    private static final CliShell vnfLafShell = new CliShell(HAPropertiesReader.getMS());

    @DataProvider
    public static Object[][] getData() {
        final Object host = DataHandler.getAttribute("consul.member.vm");
        final List<Object[]> list = new ArrayList<>();
        logger.info("Using consul member vm : {}", host.toString());
        list.add(new Object[]{host.toString()});
        final Object[][] objects = new Object[list.size()][1];
        return list.toArray(objects);
    }

    @TestId(id = "ST_RV_HA_CL_XX", title = "Kill consul member VM.")
    @Test(groups = {"High Availability"}, dataProvider = "getData", dataProviderClass = KillConsulMemberVm.class)
    public final void KillConsulMemberVm(final String memberName) {
        String memberIpAddress = "";
        try {
            if (memberName.startsWith("neo4j")) {
                fail("Invalid consul member : %s", memberName);
                return;
            } else if (memberName.startsWith("servicereg")) {
                memberIpAddress = getServiceRegLeaderIpAddress();
            } else {
                memberIpAddress = getMemberIpAddress(memberName);
            }
        } catch (final Exception e) {
            logger.warn("Fail message : {}", e.getMessage(), e);
            fail("Failed to get member %s IP Address.", memberName);
            return;
        }
        logger.info("Consul member : {} , IP Address : {}", memberName, memberIpAddress);
        if (memberIpAddress.isEmpty()) {
            fail("Failed to get member %s IP Address.", memberName);
            return;
        }
        boolean isVerifySuccess = false;
        try {
            if (HA_VERIFICATION_TIME < 10 * Constants.Time.ONE_MINUTE_IN_SECONDS) {
                throw new IllegalArgumentException("HA verification time must be at least 10 minutes.");
            }
            startRegressionVerificationTasks();
            sleep(10);
            //kill member Vm.
            final CliResult killResult = vnfLafShell.execute(String.format(killCommand, memberIpAddress));
            logger.info("KillResult : {}", killResult);
            if (killResult.isSuccess()) {
                logger.info("Member {} Killed successfully. : {}", memberName, killResult.getOutput());
            } else {
                fail("Failed to kill consul member : %s", memberName);
                return;
            }
            int check = 0;
            do {
                sleep(60);
                //Print status for 3 min after killing.
                logger.info("Consul member {} status during test : \n{}", memberName, vnfLafShell.execute(String.format(memberStatusCommand, memberIpAddress)).getOutput());
                check++;
            } while (check < 3);

            sleep(HA_VERIFICATION_TIME);
            if (memberName.startsWith("servicereg")) {
                logger.info("Consul member {} status after test : \n{}", memberName, vnfLafShell.execute(String.format(memberStatusCommand, getServiceRegLeaderIpAddress())).getOutput());
            } else {
                logger.info("Consul member {} status after test : \n{}", memberName, vnfLafShell.execute(String.format(memberStatusCommand, memberIpAddress)).getOutput());
            }
            int count = 0;
            do {
                String status;
                if (memberName.startsWith("servicereg")) {
                    status = vnfLafShell.execute(String.format(memberStatusCommand, getServiceRegLeaderIpAddress())).getOutput();
                } else {
                    status = vnfLafShell.execute(String.format(memberStatusCommand, memberName)).getOutput();
                }
                logger.info("status : {}", status);
                if (status.contains("alive")) {
                    isVerifySuccess = true;
                }
                logger.info("isVerifySuccess : {}", isVerifySuccess);
                if (!isVerifySuccess) {
                    logger.info("Waiting for Vm : {} to be online.", memberName);
                    logger.info("{} Status: {}", memberName, status);
                    sleep(60);
                }
                count++;
            } while(!isVerifySuccess && count < 5);

            if (!isVerifySuccess) {
                fail("Consul member %s fail to come online!", memberName);
            }
        } catch (final Exception e) {
            logger.error(e.getMessage(), e);
            fail(e.getMessage());
        } finally {
            stopRegressionVerificationTasks();
        }
    }

    private String getMemberIpAddress(final String memberName) {
        String memberVmIp = "";
        final CliResult memberIpResult = vnfLafShell.execute(String.format(aliveMemberIpCommand, memberName));
        logger.info("Get memberIpResult : {}", memberIpResult.getOutput());
        if(memberIpResult.isSuccess() && !memberIpResult.getOutput().isEmpty()) {
            memberVmIp = memberIpResult.getOutput().split(" ")[1].split(":")[0];
            logger.info("MemberName : {}, memberVmIp : {}", memberName, memberVmIp);
        } else if (memberIpResult.getOutput().isEmpty()) {
            logger.error("Consul member : {} not found in deployment!", memberName);
        }
        return memberVmIp;
    }

    private String getServiceRegLeaderIpAddress() {
        final CliResult result = vnfLafShell.execute(String.format(serviceRegLeaderCommand, "leader"));
        if(result.isSuccess() && !result.getOutput().isEmpty()) {
            final String serviceRegLeaderHost = result.getOutput().split(" ")[0];
            logger.info("getServiceRegLeaderHost : {}", serviceRegLeaderHost);
            final String serviceRegLeaderIp = getMemberIpAddress(serviceRegLeaderHost);
            logger.info("ServiceReg Leader IP : {}", serviceRegLeaderIp);
            return serviceRegLeaderIp;
        }
        logger.info("Failed to get ServiceRegLeaderIpAddress.");
        return "";
    }

}
