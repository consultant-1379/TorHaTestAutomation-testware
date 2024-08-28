package com.ericsson.sut.test.cases;

import static org.assertj.core.api.Fail.fail;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;


import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import com.ericsson.cifwk.taf.annotations.TestId;
import com.ericsson.cifwk.taf.data.DataHandler;
import com.ericsson.nms.rv.core.upgrade.RegressionVerificationTasks;
import com.ericsson.nms.rv.core.util.CliResult;
import com.ericsson.nms.rv.core.util.CliShell;
import com.ericsson.nms.rv.taf.tools.Constants;
import com.ericsson.oss.testware.hostconfigurator.HostConfigurator;

import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import net.minidev.json.JSONValue;

/**
 * Reboots the storage processors and verifies system availability.
 */
public class RebootStorageProcessor extends HighAvailabilityTestCase {

    private static final Logger logger = LogManager.getLogger(RebootStorageProcessor.class);
    private static final int REBOOT_COMPLETION_TIME = HA_VERIFICATION_TIME + Constants.Time.ONE_MINUTE_IN_SECONDS;

    /**
     * Supplies data to the test method.
     *
     * @return an array of array of objects
     */
    @DataProvider
    public static Object[][] getData() {
        final List<Object[]> list = new ArrayList<>();
        final Object host = DataHandler.getAttribute("host.name");
        logger.info("Storage processor used for regression : {}", host.toString());
        list.add(new Object[]{host.toString()});
        final Object[][] objects = new Object[list.size()][1];
        return list.toArray(objects);
    }


    final Object host = DataHandler.getAttribute("host.name");

    /**
     * Reboots the storage processors and verifies system availability.
     */
    @TestId(id = "TORRV-3951_High_1", title = "Reboot Storage Processor")
    @Test(groups = {"High Availability"}, dataProvider = "getData", dataProviderClass = RebootStorageProcessor.class)
    public final void rebootStorageProcessor(final String storageProcessor) {
        String SPAB_IP = "";
        final String spbCommand = "litp show -p /infrastructure/storage/storage_providers/san1 | grep \"ip_b\"| awk '{print $2}'";
        Object parse = null;
        final Object cluster = DataHandler.getAttribute("taf.clusterId");
        logger.info("cluster id is {}",cluster.toString());
        CliShell rebootShellMS = new CliShell(HostConfigurator.getMS());
        if(storageProcessor.equalsIgnoreCase("SPA")) {
            URL storageUrl = null;
            try {
                storageUrl = new URL("https://ci-portal.seli.wh.rnd.internal.ericsson.com/generateTAFHostPropertiesJSON/?clusterId=" + cluster.toString());
                URLConnection urlConnection = null;
                urlConnection = storageUrl.openConnection();
                BufferedReader inputReader = null;
                inputReader = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));
                String inputLine;
                StringBuilder sb = new StringBuilder();
                while ((inputLine = inputReader.readLine()) != null) {
                    logger.info(inputLine);
                    sb.append(inputLine + "\n");

                }
                inputReader.close();

                logger.info("Parsing the json from DMT");
                parse = JSONValue.parse(String.valueOf(sb));
                final JSONArray jsonarray = (JSONArray) parse;
                final Object o = jsonarray.get(jsonarray.size() - 1);
                final JSONObject object = (JSONObject) o;
                if (object.get("type").toString().contains("san")) {
                    SPAB_IP = object.get("ip").toString();
                    logger.info("SPA_IP is {}", SPAB_IP);
                }
            } catch (IOException e) {
                e.printStackTrace();
                logger.error("Failed to get SPA IP from DMT");
                fail("Failed to get SPA IP from DMT");
                return;
            }
        } else {
            final CliResult result = rebootShellMS.executeAsRoot(spbCommand);
            if (!result.isSuccess()) {
                fail("Failed to get SPB IP from DMT.");
                return;
            }
            SPAB_IP = result.getOutput();
            logger.info("SPB IP Address is {}", SPAB_IP);
        }
        if (SPAB_IP == null || SPAB_IP.isEmpty()) {
            fail("Failed to get SPA IP from DMT");
            return;
        }
        logger.info("Storage processor to reboot is {}",storageProcessor);
        final String healthCheckCommand = "/opt/ericsson/enminst/bin/enm_healthcheck.sh";

        try {
            if (HA_VERIFICATION_TIME < 5 * Constants.Time.ONE_MINUTE_IN_SECONDS) {
                throw new IllegalArgumentException("HA verification time must be at least 5 minutes");
            }
            final CliResult resultCheckBefore = rebootShellMS.executeAsRoot(healthCheckCommand);
            logger.info("Health check command is {}", healthCheckCommand);
            logger.info("Health check exit code is {}", resultCheckBefore.getExitCode());
            if (resultCheckBefore.getExitCode() == 0) {
                logger.info("Health Check's status before starting verifier:Success");
                startRegressionVerificationTasks();

                try {
                    final String userSecurityCommand = "/opt/Navisphere/bin/naviseccli -User admin -Password password -Scope 0 -h " + SPAB_IP + " -AddUserSecurity";
                    final String rebootCommand = "/opt/Navisphere/bin/naviseccli -User admin -Password password -Scope 0 -h " + SPAB_IP + " rebootSP -o";
                    final CliResult rs = rebootShellMS.executeAsRoot(userSecurityCommand);
                    logger.info("User Security command is: {}", userSecurityCommand);
                    logger.info("User Security  exit code is: {}", rs.getExitCode());
                    if (rs.getExitCode() == 0) {
                        final CliResult rsReboot = rebootShellMS.executeAsRoot(rebootCommand);
                        final int statusReboot = rsReboot.getExitCode();
                        logger.info("Reboot command is: {}", rebootCommand);
                        if (statusReboot == 0) {
                            logger.info("Reboot command is executed successfully");
                        } else {
                            logger.info("error in rebooting {}", rsReboot.getOutput());
                        }

                    } else {
                        logger.info("error in adding user security command {}", rs.getOutput());
                        fail("Error in adding user security command.");
                        return;
                    }
                } catch(Exception e) {
                    logger.info("Failed to Reboot : ", e);
                    fail("Failed to Reboot.");
                    return;
                }
                sleep(REBOOT_COMPLETION_TIME);
                final CliResult resultCheck = rebootShellMS.executeAsRoot(healthCheckCommand);
                if(resultCheck.getExitCode() == 0) {
                    logger.info("Health Check after rebooting :Success");
                } else {
                    logger.info("Health Check after rebooting :Failed");
                    RegressionVerificationTasks.isHcAfterRebootFailed = true;
                    return;
                }
                logger.info("Verifying availability after the system has recovered");
                sleep(HA_VERIFICATION_TIME);
            } else {
                fail("Health check failed");
                logger.warn("Health check result: {}", resultCheckBefore.getOutput());
                logger.warn("Health Check status before starting verifier:Failed");
            }
        } catch (final Exception t) {
            logger.error(t.getMessage(), t);
            fail(t.getMessage());
        } finally {
            stopRegressionVerificationTasks();
        }
    }
}
