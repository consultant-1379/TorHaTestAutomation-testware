package com.ericsson.nms.rv.core.util;

import static com.ericsson.nms.rv.taf.tools.Constants.EMPTY_STRING;
import static org.assertj.core.api.Fail.fail;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.ericsson.cifwk.taf.data.Host;
import com.ericsson.cifwk.taf.tools.http.HttpResponse;
import com.ericsson.nms.rv.core.HAPropertiesReader;
import com.ericsson.nms.rv.core.upgrade.CloudNativeDependencyDowntimeHelper;
import com.ericsson.nms.rv.taf.tools.Constants;
import com.ericsson.nms.rv.taf.tools.host.Database;
import com.ericsson.nms.rv.taf.tools.host.Service;
import com.ericsson.nms.rv.taf.tools.network.NetworkException;
import com.ericsson.nms.rv.taf.tools.network.NetworkInterface;
import com.ericsson.oss.testware.availability.common.shell.ShellException;
import com.ericsson.oss.testware.hostconfigurator.HostConfigurator;

public class RegressionUtils {
    private static final Logger logger = LogManager.getLogger(RegressionUtils.class);
    private static final CliShell SHELL = new CliShell(HAPropertiesReader.getMS());
    /**
     * the command which uninstall the HA tools rpm from the Management Server
     */
    private static final String ERIC_TOR_HA_TEST_UTILITIES = "ERICtorhatestutilities_CXP9031104";
    private static final String ERASE_RPM_COMMAND = "/bin/rpm -e " + ERIC_TOR_HA_TEST_UTILITIES;
    private static final String ERASE_UTILITY_RPM_COMMAND = "/bin/rpm -e ERICtorutilitiesinternal_CXP9030579";
    /**
     * the command which queries if the HA tools rpm is already installed on the Management Server
     */
    private static final String QUERY_RPM_COMMAND = "/bin/rpm -qa " + ERIC_TOR_HA_TEST_UTILITIES;
    /**
     * the command which updates the ENM Utilities
     */
    private static final String UPDATE_ENM_UTILS_COMMAND = "/opt/ericsson/enmutils/.deploy/update_enmutils_rpm";
    /**
     * the command which extracts the db-utils tar
     */
    private static final String EXTRACT_DB_UTILS_COMMAND = "/bin/tar -xvf " + HAPropertiesReader.getHatoolsPath() + "db-utils-bin.tar -C " + HAPropertiesReader.getHatoolsPath();
    /**
     * the command which install the HA tools rpm
     */
    private static final String INSTALL_RPM_COMMAND = "/bin/rpm -ivh " + HAPropertiesReader.getHatoolsUrl();
    private static final String ERICSSON_TOR_DATA_UPDATE_ENMUTILS_RPM_SH = HAPropertiesReader.getHatoolsPath() + "update_enmutils_rpm.sh";
    private static final String BIN_GREP = "/bin/grep ";
    private static final String BIN_SED = "/bin/sed ";
    private static final String BIN_AWK = "/bin/awk ";
    private static final String SBIN_IP = "/sbin/ip";
    private static final String SED_ARGS = " -e 's/ /,/g'";
    private static final String ERROR = "Error:";
    private static final String LIST_ETHERNET_NETWORK_INTERFACES_COMMAND = SBIN_IP + " addr show label eth[0-9] | "
            + BIN_GREP + " UP | " + BIN_AWK + " '{print $2}' | " + BIN_SED + " -r 's/://g' | /usr/bin/xargs | "
            + BIN_SED + SED_ARGS;
    private static final String LIST_NETWORK_INTERFACES_COMMAND = SBIN_IP + " addr show | " + BIN_GREP
            + " 'brd [1-9]' | " + BIN_SED + " -r 's/secondary//g' | " + BIN_AWK
            + " '{print $4\"-\"$7}' | /usr/bin/xargs | " + BIN_SED + SED_ARGS;
    private static final List<CliShell> DbList = new ArrayList<>();
    private static List<Host> dbList;
    private static List<Host> svcList;
    private static CliShell DB1, DB2, DB3, DB4;

    public static List<CliShell> getDbList() {
        return DbList;
    }
    public static List<Host> getAllDbHostList() {
        return Collections.unmodifiableList(dbList);
    }
    public static List<Host> getAllSvcHostList() {
        return Collections.unmodifiableList(svcList);
    }


    public static void initDbSvcHosts() {
        if (!HAPropertiesReader.isEnvCloud()) {
            dbList = Database.getAllHosts();
            svcList = Service.getAllHosts();
            DB1 = new CliShell(HostConfigurator.getDb1());
            DB2 = new CliShell(HostConfigurator.getDb2());
            //DbList.add(DB1);
            DbList.add(DB2);
            try {
                if (HostConfigurator.getDb3() != null) {
                    DB3 = new CliShell(HostConfigurator.getDb3());
                    DbList.add(DB3);
                }
            } catch (Exception n) {
                logger.warn("Exception in init DB3.");
            }
            try {
                if (HostConfigurator.getDb4() != null) {
                    DB4 = new CliShell(HostConfigurator.getDb4());
                    DbList.add(DB4);
                }
            } catch (Exception n1) {
                logger.warn("Exception in init DB4.");
            }
        }
    }

    public static Host getVMfromList(final List<Host> hosts, final String type) {
        for (final Host host : hosts) {
            if (host.getHostname().contains(type)) {
                return host;
            }
        }
        return null;
    }

    /**
     * @return the {@code eth} network interfaces and bond and bridges which have a broadcast address
     */
    public static final List<NetworkInterface> getNetworkInterfaces(final Host host) {
        final List<NetworkInterface> networkInterfaces = new ArrayList<>();
        final CliShell shell = new CliShell(host);
        logger.info("Going to execute : {}", LIST_ETHERNET_NETWORK_INTERFACES_COMMAND);
        String output = shell.execute(LIST_ETHERNET_NETWORK_INTERFACES_COMMAND).getOutput();
        logger.info("output is : {}", output);
        for (final String name : output.split(Constants.RegularExpressions.COMMA)) {
            try {
                networkInterfaces.add(new NetworkInterface(name, EMPTY_STRING, host));
            } catch (final NetworkException e) {
                logger.error("fail to create NetworkInterface tool ", e);
            }
        }
        logger.info("Going to execute : {}", LIST_NETWORK_INTERFACES_COMMAND);
        output = shell.execute(LIST_NETWORK_INTERFACES_COMMAND).getOutput();
        logger.info("output is : {}", output);
        // the expected output will be e.g.:
        // -----------------------------NEW FORMAT----------------------------------
        // 141.137.239.255-br0,10.144.21.255-br1,10.144.3.255-br2,10.247.247.255-br3
        // -----------------------------OLD FORMAT----------------------------------
        // bond0 Link encap:Ethernet HWaddr 9C:B6:54:8A:44:38
        // inet addr:10.151.8.68 Bcast:10.151.8.127 Mask:255.255.255.192
        // --
        // bond0.1122 Link encap:Ethernet HWaddr 9C:B6:54:8A:44:38
        // inet addr:10.151.24.24 Bcast:10.151.25.255 Mask:255.255.254.0
        // --
        // bond0.2182 Link encap:Ethernet HWaddr 9C:B6:54:8A:44:38
        // inet addr:10.250.244.2 Bcast:10.250.247.255 Mask:255.255.252.0
        // --
        // bond0.2183 Link encap:Ethernet HWaddr 9C:B6:54:8A:44:38
        // inet addr:10.247.244.2 Bcast:10.247.247.255 Mask:255.255.252.0
        // --
        // bond0.2317 Link encap:Ethernet HWaddr 9C:B6:54:8A:44:38
        // inet addr:10.140.15.5 Bcast:10.140.15.255 Mask:255.255.255.0
        for (final String line : output.split(Constants.RegularExpressions.COMMA)) {
            final String[] split = line.split(Constants.RegularExpressions.DASH);
            if (split.length == 2) {
                try {
                    networkInterfaces.add(new NetworkInterface(split[1], split[0], host));
                } catch (final NetworkException e) {
                    logger.error("fail to create NetworkInterface tool ", e);
                }
            }
        }
        return networkInterfaces;
    }

    /**
     * Installs the HA tools on the DB amd SVC servers.
     *
     * @throws com.ericsson.oss.testware.availability.common.shell.ShellException if the HA tools cannot be downloaded onto the Management Server
     */
    private static void installHaTools() throws ShellException {
        if (SHELL.executeAsRoot(QUERY_RPM_COMMAND).getOutput().contains(ERIC_TOR_HA_TEST_UTILITIES)
                && SHELL.executeAsRoot(ERASE_RPM_COMMAND).getExitCode() != 0) {
            throw new ShellException("failed to erase " + ERIC_TOR_HA_TEST_UTILITIES);
        }
        if (SHELL.executeAsRoot(INSTALL_RPM_COMMAND).getExitCode() != 0) {
            throw new ShellException("failed to install " + ERIC_TOR_HA_TEST_UTILITIES);
        }
        if (SHELL.executeAsRoot(EXTRACT_DB_UTILS_COMMAND).getExitCode() != 0) {
            throw new ShellException("failed to extract db-utils");
        }
    }

    /**
     * Updates the ENM Utilities to the latest version.
     *
     * @throws ShellException if the HA tools cannot be downloaded onto the Management Server
     */
    private static void updateEnmUtils() throws ShellException {
        // update ENM utils so License Keys can be installed without entering username and password
        SHELL.executeAsRoot(ERICSSON_TOR_DATA_UPDATE_ENMUTILS_RPM_SH);
        if (SHELL.executeAsRoot(UPDATE_ENM_UTILS_COMMAND).getOutput().contains(ERROR)) {
            throw new ShellException("Failed to update ENM Utils");
        }
    }

    public static void installUpdateEnmUtils() {
        logger.info("isRunOnce value {}",HAPropertiesReader.isRunOnce());
        if (!HAPropertiesReader.isEnvCloud() && HAPropertiesReader.isRunOnce()) {
            try {
                installHaTools();
            } catch (final ShellException e) {
                logger.warn("Failed to installHaTools: ", e);
            }
            try {
                updateEnmUtils();
            } catch (final ShellException e) {
                logger.warn("Failed to install license key: ", e);
            }
        }
    }

    public static void installHaToolsIfRemoved() {
        if (!HAPropertiesReader.isEnvCloud() && HAPropertiesReader.isNodeOperationRequired()
                && !SHELL.executeAsRoot(QUERY_RPM_COMMAND).getOutput().contains(ERIC_TOR_HA_TEST_UTILITIES)) {
            try {
                logger.info("Installing HA Utilities to clean up the high availability nodes...");
                installHaTools();
            } catch (final ShellException e) {
                logger.warn("Failed to installHaTools", e);
            }
        }
    }

    public static void removeHaTools() {
        try {
            if (!HAPropertiesReader.isEnvCloud() && SHELL.executeAsRoot(QUERY_RPM_COMMAND).getOutput().contains(ERIC_TOR_HA_TEST_UTILITIES)) {
                try {
                    logger.info("Removing HA Utilities ...");
                    final CliResult utilityResult = SHELL.executeAsRoot(ERASE_UTILITY_RPM_COMMAND);
                    final CliResult rpmResult = SHELL.executeAsRoot(ERASE_RPM_COMMAND);
                    if (rpmResult.getExitCode() != 0) {
                        logger.warn("Failed to Remove HA Utilities: {}, {}", utilityResult.getOutput(), rpmResult.getOutput());
                    }
                } catch (final Exception e) {
                    logger.warn("Failed to remove HA Utilities.", e);
                }
            }
        } catch (final Exception ex) {
            logger.warn("Failed to Remove HA Utilities: {}", ex.getMessage());
        }
    }

    public static void configChaosMesh(final String action) {
        try {
            logger.info("Configuring chaos-mesh installation ... ");
            String uri = "/watcher/adu/chaos/" + action;
            final HttpResponse httpResponse = CloudNativeDependencyDowntimeHelper.generateHttpPostResponse(uri, 360);
            logger.info("Chaos-Mesh {} httpResponse : {}", action, httpResponse.getResponseCode().getCode());
            logger.info("Chaos-Mesh {} output : {}", action, httpResponse.getBody());
            CommonUtils.sleep(10);
        } catch (final Exception ex) {
            logger.warn("Failed to {} ChaosMesh: {}", action, ex.getMessage());
        }
    }


    /**
     * cENM comment.
     * Generic function to start/stop all cENM Chaos-Regression test cases.
     * Build Chaos test Data and pass to ADU-watcher application.
     */
    public static void executeCloudNaiveChaosRegressionTest(final String action) {
        final String testType = HAPropertiesReader.cEnmChaosTestType;
        final String appName = HAPropertiesReader.cEnmChaosAppName;
        final String faultDuration = HAPropertiesReader.cEnmChaosFaultDuration;
        final String containerName = HAPropertiesReader.cEnmChaosContainerName;
        final String testMode = HAPropertiesReader.cEnmChaosMode;

        logger.info("Executing Regression test {} with data : {}, {}, {}, {}, {}", action, testType, appName, faultDuration, containerName, testMode);
        if (testType.isEmpty() || appName.isEmpty() || testMode.isEmpty()) {
            logger.error("All Chaos Regression parameters are not defined.");
            fail("Mandatory Chaos Regression parameters are not defined.");
            return;
        }
        if (testType.equalsIgnoreCase("pod-failure")) {
            if (faultDuration.equalsIgnoreCase("NA")) {
                fail("FaultDuration Chaos Regression parameters are not defined.");
                return;
            }
        } else if (testType.equalsIgnoreCase("container-kill")) {
            if (containerName.equalsIgnoreCase("NA") || containerName.isEmpty()) {
                fail("ContainerName Chaos Regression parameters are not defined.");
                return;
            }
        }
        String uri;
        final String testData = testType + ":" + appName + ":" + faultDuration + ":" + containerName + ":" + testMode;
        if (action.equalsIgnoreCase("start")) {
            uri = "watcher/adu/chaos/regression/start/";
        } else {
            uri = "watcher/adu/chaos/regression/stop/";
        }
        CommonUtils.sleep(30);
        final HttpResponse httpResponse = CloudNativeDependencyDowntimeHelper.generateHttpPostResponse(uri + testData);
        logger.info("{} Chaos-Regression test httpResponse : {}", action, httpResponse.getResponseCode().getCode());
    }

}
