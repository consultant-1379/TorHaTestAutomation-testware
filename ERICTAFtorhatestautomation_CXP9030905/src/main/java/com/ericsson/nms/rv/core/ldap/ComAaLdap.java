package com.ericsson.nms.rv.core.ldap;

import com.ericsson.cifwk.taf.data.Host;
import com.ericsson.cifwk.taf.tools.http.HttpResponse;
import com.ericsson.nms.rv.core.EnmApplication;
import com.ericsson.nms.rv.core.HAPropertiesReader;
import com.ericsson.nms.rv.core.upgrade.CloudNativeDependencyDowntimeHelper;
import com.ericsson.nms.rv.core.util.AdminUserUtils;
import com.ericsson.nms.rv.core.util.CliResult;
import com.ericsson.nms.rv.core.util.CliShell;
import com.ericsson.oss.testware.availability.common.exception.EnmException;
import com.ericsson.sut.test.cases.HighAvailabilityTestCase;
import com.ericsson.sut.test.cases.util.FunctionalAreaKey;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import java.util.Arrays;
import java.util.Random;

public class ComAaLdap extends EnmApplication {
    private static final Logger logger = LogManager.getLogger(ComAaLdap.class);
    private static final Host workload = HAPropertiesReader.getWorkload();
    public static boolean isLdapTestIsReady = false;
    private static CliShell wlShell;
    String username = "";

    @Override
    public void prepare() throws EnmException {
        String ldapsPort = "";
        String baseDn = "";
        final String password = HAPropertiesReader.getHaPassword();
        final String ldapIpv4Address = HAPropertiesReader.getSecServLoadBalancerIP();
        final Random random = new Random();
        int num = random.nextInt(900) + 100;
        final String userName = "adu-ldap-user-" + num;
        logger.info("Creating ldap user : {}", userName);
        username = AdminUserUtils.createNewUser(this, userName, true);
        logger.info("LDAP user created successfully: {}", username);

        if (workload == null) {
            logger.error("WorkLoad VM is not available ... LDAP test cannot be executed.");
        } else {
            wlShell = new CliShell(workload);
            try {
                final String command = "cli_app 'secadm ldap configure --manual'";
                final CliResult result = wlShell.execute(command);
                logger.info("command result : {}", result.getOutput());
                if (!result.isSuccess()) {
                    throw new Exception("Execution of ldap configure command is failed");
                }
                final String[] resultList = result.getOutput().split("\n");
                logger.info("resultList : {}", Arrays.toString(resultList));
                for (String line : resultList) {
                    if (line.contains("ldapsPort")) {
                        ldapsPort = line.split("ldapsPort")[1];
                        logger.info("ldapsPort : {}", ldapsPort);
                    } else if (line.contains("baseDn")) {
                        baseDn = line.split("baseDn")[1];
                        logger.info("baseDn : {}", baseDn);
                    }
                }
            } catch (final Exception e) {
                logger.error("Message : {}", e.getMessage());
                HighAvailabilityTestCase.multiMapErrorCollection.put(HAPropertiesReader.COMAALDAP, "ComAaLdap failed in prepare phase:" + e.getMessage());
                HighAvailabilityTestCase.appsFailedToStart.add(new FunctionalAreaKey(ComAaLdap.class.getSimpleName(), true));
            }
        }

        if (!username.isEmpty() && wlShell != null) {
            isLdapTestIsReady = true;
        }
        if (isLdapTestIsReady) {
            final String ldapParams = "password:" + password + "~ipv4:" + ldapIpv4Address + "~port:" + ldapsPort + "~basedn:" + baseDn;
            final String userUri = "watcher/adu/ldap/users/" + username;
            final String paramUri = "watcher/adu/ldap/param/" + ldapParams;
            logger.info("user : {}", username);
            logger.info("ldapParams : {}", ldapParams);
            try {
                HttpResponse userResponse = CloudNativeDependencyDowntimeHelper.generateHttpPostResponse(userUri);
                HttpResponse paramResponse = CloudNativeDependencyDowntimeHelper.generateHttpPostResponse(paramUri);
                if (userResponse == null || paramResponse == null || !(EnmApplication.acceptableResponse.contains(userResponse.getResponseCode())) || !(EnmApplication.acceptableResponse.contains(paramResponse.getResponseCode()))) {
                    throw new Exception("Failed to get httpResponse!");
                }
                logger.info("Successfully set ldap user/params.");
            } catch (final Exception e) {
                logger.error("set ldap user/params error : {}", e.getMessage());
                HighAvailabilityTestCase.multiMapErrorCollection.put(HAPropertiesReader.COMAALDAP, "ComAaLdap failed in prepare phase.");
                HighAvailabilityTestCase.appsFailedToStart.add(new FunctionalAreaKey(ComAaLdap.class.getSimpleName(), true));
                isLdapTestIsReady = false;
            }
        } else {
            logger.warn("username : {}, wlShell : {}", username, wlShell);
            HighAvailabilityTestCase.multiMapErrorCollection.put(HAPropertiesReader.COMAALDAP, "ComAaLdap failed in prepare phase.");
            HighAvailabilityTestCase.appsFailedToStart.add(new FunctionalAreaKey(ComAaLdap.class.getSimpleName(), true));
        }
    }

    @Override
    public void cleanup() throws EnmException {
        if (!username.isEmpty()) {
            AdminUserUtils.deleteUser(this, username, true);
        }
    }

    @Override
    public void verify() {
    }
}
