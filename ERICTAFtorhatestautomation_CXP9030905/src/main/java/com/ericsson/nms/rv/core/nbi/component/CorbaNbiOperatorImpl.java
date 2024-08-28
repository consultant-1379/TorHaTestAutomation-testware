package com.ericsson.nms.rv.core.nbi.component;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.ericsson.cifwk.taf.configuration.TafConfiguration;
import com.ericsson.cifwk.taf.configuration.TafConfigurationProvider;
import com.ericsson.cifwk.taf.data.DataHandler;
import com.ericsson.cifwk.taf.data.Host;
import com.ericsson.cifwk.taf.data.HostType;
import com.ericsson.cifwk.taf.data.User;
import com.ericsson.de.tools.cli.CliCommandResult;
import com.ericsson.de.tools.cli.CliToolShell;
import com.ericsson.de.tools.cli.CliTools;
import com.ericsson.nms.rv.core.HAPropertiesReader;
import com.ericsson.nms.rv.taf.tools.Constants;

public class CorbaNbiOperatorImpl {
    private static final Logger LOGGER = LogManager.getLogger(CorbaNbiOperatorImpl.class);
    private static final String SEMICOLON = " ; ";
    private static final String MKDIR_TEST;
    private static final String MKDIR_TEST_CMD;
    private static final String RPM_TEST_PATH_CMD;
    private static final String WGET_RPM_CMD;
    private static final String RPM_VERSION;
    private static final String LATEST_RPM_VERSION = HAPropertiesReader.getCorbaServerLatestVersion();
    private static final String EXTRACT_TEST_CLIENT;
    private static final String NBI_TEST_CLIENT;
    private static final TafConfiguration configuration = TafConfigurationProvider.provide();
    private static final User user;
    private static final Host host;

    static {
        host = DataHandler.getHostByType(HostType.GATEWAY);
        createHost();
        user = host.getUser("root");
        MKDIR_TEST = configuration.getString("nbi.mkdir.test");
        RPM_VERSION = (LATEST_RPM_VERSION.isEmpty()) ? configuration.getString("nbi.wget.corba.rpm.version") : LATEST_RPM_VERSION;
        EXTRACT_TEST_CLIENT = String.format(configuration.getString("nbi.extract.test.client"), RPM_VERSION);
        NBI_TEST_CLIENT = configuration.getString("nbi.testclient.path");
        RPM_TEST_PATH_CMD = configuration.getString("nbi.changeDir") + " " + configuration.getString("nbi.rpm.test.path");
        WGET_RPM_CMD = String.format(configuration.getString("nbi.wget.corba.rpm"), HAPropertiesReader.getNexusUrl(), RPM_VERSION, RPM_VERSION);
        MKDIR_TEST_CMD = configuration.getString("nbi.makeDir") + " " + MKDIR_TEST;
        LOGGER.info("WGET_RPM_CMD : {}", WGET_RPM_CMD);
    }

    private CliToolShell shell;

    private static void createHost() {
        if (!runLocally()) {
            host.setIp(getHostIpOfTestExecutor());
        }
    }

    private static String getHostIpOfTestExecutor() {
        String ip = "";

        try {
            ip = InetAddress.getLocalHost().getHostAddress();
        } catch (final UnknownHostException var3) {
            LOGGER.error("Failed to getHostIpOfTestExecutor ", var3);
        }
        return ip;
    }

    private static boolean runLocally() {
        String hostName = null;
        try {
            hostName = InetAddress.getLocalHost().getHostName();
        } catch (final UnknownHostException var3) {
            LOGGER.error("Failed to get hostname from localhost", var3);
        }

        return hostName == null || !hostName.startsWith("testexe");
    }

    public CliToolShell getShell() {
        return shell;
    }

    String executeNbiSubscription(final String command, final long time) throws InterruptedException {
        shell.getBufferedShell().send(RPM_TEST_PATH_CMD + NBI_TEST_CLIENT + SEMICOLON + command);

        final long startTime = System.nanoTime();
        try {
            return waitForNotification("Attached to Notification Service", startTime, time);
        } catch (final InterruptedException e) {
            LOGGER.error("Failed to wait for nbi operation, subscribe : {}", e.getMessage());
            throw new InterruptedException("Failed to wait for NbiSubscription operation : " + e.getMessage());
        }
    }

    private String waitForNotification(final String message, final long startTime, final long timeToWait) throws InterruptedException {
        String output = shell.getBufferedShell().getOutput();
        final long timeToWaitInSeconds = Constants.TEN_EXP_9 * timeToWait;
        while (!output.contains(message)) {
            if ((System.nanoTime() - startTime) > timeToWaitInSeconds) {
                LOGGER.error("Failed to wait for nbi operation");
                LOGGER.info("nbi operation's output is : {}", (output.isEmpty() ? "EMPTY" : output));
                throw new InterruptedException("timed out");
            }
            output = shell.getBufferedShell().getOutput();
        }
        return output;
    }

    String executeNbiUnSubscribe(final String command, final long time) throws InterruptedException {
        shell.getBufferedShell().send(RPM_TEST_PATH_CMD + NBI_TEST_CLIENT + SEMICOLON + command);

        final long startTime = System.nanoTime();
        try {
            return waitForNotification("Disconnected from Notification Service.", startTime, time);
        } catch (final InterruptedException e) {
            LOGGER.error("Failed to wait for nbi operation, unsubscribe : {}", e.getMessage());
            throw new InterruptedException("Failed to wait for NbiUnSubscription operation : " + e.getMessage());
        }
    }

    public boolean downloadTestClient() {
        boolean isClientInstalled = true;
        try {
            openConnection();
            if (shell != null) {
                if (!isNbiClientInstalled()) {
                    final StringBuilder builder = new StringBuilder();
                    builder.append(MKDIR_TEST_CMD).append(SEMICOLON);
                    builder.append(RPM_TEST_PATH_CMD).append(SEMICOLON);
                    builder.append(WGET_RPM_CMD).append(SEMICOLON);
                    builder.append(EXTRACT_TEST_CLIENT);
                    final CliCommandResult result = shell.execute(builder.toString(), 300);
                    isClientInstalled = result.isSuccess();
                    LOGGER.info("simpleExec CorbaNbi: {}", result.getOutput());
                    LOGGER.info("Command finished...");
                }
            } else {
                return false;
            }
        } catch (final Exception e) {
            LOGGER.error("error installing nbi client: ", e);
            isClientInstalled = false;
        } finally {
            if (shell != null) {
                shell.close();
            }
        }
        return isClientInstalled;
    }

    private boolean isNbiClientInstalled() {
        final String isNbiClientInstalled = "/bin/ls " + MKDIR_TEST + "/" + NBI_TEST_CLIENT + "/testclient.sh";
        final int exitCode = shell.execute(isNbiClientInstalled).getExitCode();
        if (exitCode != 0) {
            return false;
        }
        LOGGER.info("Nbi Client is already installed");
        return true;
    }

    public void openConnection() {
        shell = CliTools.sshShell(host.getIp())
                .withUsername(user.getUsername())
                .withPassword(user.getPassword())
                .build();
    }
}
