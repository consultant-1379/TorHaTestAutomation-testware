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

public class KillConsulMemberNeo4j extends HighAvailabilityTestCase {

    private static final Logger logger = LogManager.getLogger(KillConsulMemberNeo4j.class);
    private static final String aliveMemberIpCommand = "consul members | grep %s | grep alive | /bin/tr -s ' ' | head -n 1";
    private static final String killCommand = "ssh -q -o StrictHostKeyChecking=no -i /var/tmp/Bravo/pemKey.pem cloud-user@%s 'sudo pkill consul'";
    private static final String getNeo4jIpCommand = "ssh -q -o StrictHostKeyChecking=no -i /var/tmp/Bravo/pemKey.pem cloud-user@%s 'source /ericsson/3pp/neo4j/conf/neo4j_env;/ericsson/3pp/neo4j/bin/cypher-shell -u neo4j -p Neo4jadmin123 -a bolt://neo4j:7687 \"CALL dbms.cluster.overview();\"' | grep %s%s";
    private static final String getNeo4jStatusCommand = "ssh -q -o StrictHostKeyChecking=no -i /var/tmp/Bravo/pemKey.pem cloud-user@%s 'source /ericsson/3pp/neo4j/conf/neo4j_env;/ericsson/3pp/neo4j/bin/cypher-shell -u neo4j -p Neo4jadmin123 -a bolt://neo4j:7687 \"CALL dbms.cluster.overview();\"'";
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

    @TestId(id = "ST_RV_HA_CL_10_12", title = "Kill consul member Neo4j.")
    @Test(groups = {"High Availability"}, dataProvider = "getData", dataProviderClass = KillConsulMemberNeo4j.class)
    public final void KillConsulMemberNeo4j(final String memberName) {
        if (!memberName.startsWith("neo4j")) {
            fail("Unsupported consul member : %s", memberName);
            return;
        }
        boolean isVerifySuccess = false;
        final String neo4jIpAddress;
        String memberIpAddress;
        try {
            memberIpAddress = getAliveMemberIpAddress(memberName);
            logger.info("Consul member : {} , IP Address : {}", memberName, memberIpAddress);
            neo4jIpAddress = getNeo4jIpAddress(memberName, memberIpAddress);
        } catch (final Exception e) {
            logger.warn("Failed to get member {} IP address : ", e.getMessage());
            fail("Failed to get member %s IP address : %s", memberName, e.getMessage());
            return;
        }
        logger.info("Consul member : {} , neo4jIpAddress : {}", memberName, neo4jIpAddress);
        if(neo4jIpAddress.isEmpty()) {
            fail("Failed to get member %s IpAddress.", memberName);
            return;
        }

        try {
            if (HA_VERIFICATION_TIME < 10 * Constants.Time.ONE_MINUTE_IN_SECONDS) {
                throw new IllegalArgumentException("HA verification time must be at least 5 minutes");
            }
            startRegressionVerificationTasks();
            sleep(10);
            //kill member Vm.
            final CliResult killResult = vnfLafShell.execute(String.format(killCommand, neo4jIpAddress));

            if (killResult.isSuccess()) {
                logger.info("Member {} Killed successfully. : {}", memberName, killResult.getOutput());
            } else {
                logger.warn("Failed to kill consul member {}.", memberName);
                fail("Failed to kill consul member %s.", memberName);
                return;
            }
            int check = 0;
            do {
                try {
                    sleep(60);
                    //refresh alive memberIP
                    memberIpAddress = getAliveMemberIpAddress(memberName);
                    logger.info("Consul member {} status during test : \n{}", memberName, vnfLafShell.execute(String.format(getNeo4jStatusCommand, memberIpAddress)).getOutput());
                } catch (final Exception e) {
                    logger.info(e.getMessage());
                }
                check++;
            } while (check < 3);

            sleep(HA_VERIFICATION_TIME);
            logger.info("Consul member {} status after test : \n{}", memberName, vnfLafShell.execute(String.format(getNeo4jStatusCommand, memberIpAddress)).getOutput());

            int count = 0;
            do {
                if (memberName.contains("leader") && Integer.parseInt(getNeo4jResult(memberName, memberIpAddress, true)) == 1) {
                    isVerifySuccess = true;
                } else if (memberName.contains("follower") && Integer.parseInt(getNeo4jResult(memberName, memberIpAddress, true)) == 2) {
                    isVerifySuccess = true;
                }
                logger.info("isVerifySuccess : {}", isVerifySuccess);
                if (!isVerifySuccess) {
                    logger.info("Waiting for Vm : {} to be online.", memberName);
                    logger.info("{} Status: {}", memberName, getNeo4jResult(memberName, memberIpAddress, false));
                    sleep(60);
                }
                count++;
            } while(!isVerifySuccess && count < 5);

        } catch (final Exception e) {
            logger.error(e.getMessage(), e);
            fail(e.getMessage());
        } finally {
            stopRegressionVerificationTasks();
        }
    }

    private String getAliveMemberIpAddress(final String memberName) {
        final CliResult memberIpResult;
        String memberVmIp = "";
        memberIpResult = vnfLafShell.execute(String.format(aliveMemberIpCommand, "neo4j"));
        logger.info("Get memberIpResult : {}", memberIpResult.getOutput());
        if(memberIpResult.isSuccess()) {
            memberVmIp = memberIpResult.getOutput().split(" ")[1].split(":")[0];
            logger.info("MemberName : {}, memberVmIp : {}", memberName, memberVmIp);
        }
        return memberVmIp;
    }

    private String getNeo4jResult(final String memberName, final String neo4jHostIp, final boolean isVerify) {
        final CliResult ipResult;
        final String getCount = isVerify ? " | wc -l" : "";
        if (memberName.contains("leader")) {
            ipResult = vnfLafShell.execute(String.format(getNeo4jIpCommand, neo4jHostIp, "LEADER", getCount));
        } else if (memberName.contains("follower-0") || memberName.contains("follower-1")) {
            ipResult = vnfLafShell.execute(String.format(getNeo4jIpCommand, neo4jHostIp, "FOLLOWER", getCount));
        } else {
            return "";
        }
        logger.info("getNeo4jResult : {}", ipResult.getOutput());
        return ipResult.getOutput();
    }

    private String getNeo4jIpAddress(final String memberName, final String neo4jAliveHostIp) {
        String neo4jIp = "";
        final CliResult ipResult;
        if (neo4jAliveHostIp.isEmpty()) {
            return "";
        } else if (memberName.contains("leader")) {
            logger.info("neo4jAliveHostIp Command : {}", String.format(getNeo4jIpCommand, neo4jAliveHostIp, "'dps: \"LEADER\"'", ""));
            ipResult = vnfLafShell.execute(String.format(getNeo4jIpCommand, neo4jAliveHostIp, "'dps: \"LEADER\"'", ""));
        } else if (memberName.contains("follower-0")) {
            ipResult = vnfLafShell.execute(String.format(getNeo4jIpCommand, neo4jAliveHostIp, "'dps: \"FOLLOWER\"'", " | head -n 1"));
        } else if (memberName.contains("follower-1")) {
            ipResult = vnfLafShell.execute(String.format(getNeo4jIpCommand, neo4jAliveHostIp, "'dps: \"FOLLOWER\"'", " | tail -n 1"));
        } else {
            logger.error("Invalid Vm name : {}", memberName);
            return "";
        }
        logger.info("ipResult : {}", ipResult.getOutput());
        if (ipResult.isSuccess()) {
            neo4jIp = ipResult.getOutput().split("\"")[3].split(":")[1].replace("//", "");
        }
        logger.info("Neo4j VM: {} neo4jIp: {}", memberName, neo4jIp);
        return neo4jIp;
    }

}
