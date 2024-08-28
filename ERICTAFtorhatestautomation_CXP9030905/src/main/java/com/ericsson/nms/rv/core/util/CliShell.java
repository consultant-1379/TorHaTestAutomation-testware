package com.ericsson.nms.rv.core.util;

import com.ericsson.cifwk.taf.data.Host;
import com.ericsson.cifwk.taf.data.HostType;
import com.ericsson.cifwk.taf.data.Ports;
import com.ericsson.cifwk.taf.data.User;
import com.ericsson.cifwk.taf.data.UserType;
import com.ericsson.cifwk.taf.data.exception.UserNotFoundException;
import com.ericsson.cifwk.taf.tools.TargetHost;
import com.ericsson.de.tools.cli.CliCommandResult;
import com.ericsson.de.tools.cli.CliIntermediateResult;
import com.ericsson.de.tools.cli.CliToolShell;
import com.ericsson.de.tools.cli.CliTools;
import com.ericsson.nms.rv.core.HAPropertiesReader;
import com.ericsson.nms.rv.core.node.Node;
import com.ericsson.oss.testware.hostconfigurator.HostConfigurator;
import com.google.common.base.Preconditions;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static java.lang.String.format;

public class CliShell {

    private static final Logger logger = LogManager.getLogger(CliShell.class);

    private static final List<HostType> dbHosts = new ArrayList<>(5);
    private static final List<HostType> scpHosts = new ArrayList<>(5);
    private static final List<HostType> svcHosts = new ArrayList<>(5);
    private static Host MS = HAPropertiesReader.getMS();
    private final Host host;
    private final HostType hostType;

    static {
        if(!HAPropertiesReader.isEnvCloudNative()) {
            if (HAPropertiesReader.isEnvCloud()) {
                MS.setPass(Users.CLOUD_USER.getPassword());
                final List<User> users = new ArrayList<>();
                users.add(Users.CLOUD_USER);
                MS.setUsers(users);
            } else {
                logger.info("Getting Physical MS_IP");
                MS = HostConfigurator.getMS();
                logger.info("MS User : {}", MS.getUser());
                logger.info("MS Pass : {}", MS.getPass());
                try {
                    CliToolShell cliToolShell = CliTools.sshShell(MS.getIp()).withUsername(MS.getUser()).withPassword(MS.getPass()).withDefaultTimeout(320L).build();
                    cliToolShell.execute("hostname");
                    cliToolShell.close();
                } catch (final Exception e) {
                    logger.info("MS execute failed with DIT users : {} ... using default user/password", MS.getUser());
                    MS.setUser(Users.ROOT.getUsername());
                    MS.setPass(Users.ROOT.getPassword());
                }
            }
        }

        Collections.addAll(dbHosts, HostType.DB, HostType.DB1, HostType.DB2, HostType.DB3, HostType.DB4);
        Collections.addAll(scpHosts, HostType.SCP, HostType.SCP1, HostType.SCP2, HostType.SCP3, HostType.SCP4);
        Collections.addAll(svcHosts, HostType.SVC, HostType.SVC1, HostType.SVC2, HostType.SVC3, HostType.SVC4);
    }

    public CliShell(final Host host) {
        Preconditions.checkNotNull(host, "Host can't be null");
        this.host = host;
        hostType = host.getType();
        if (HAPropertiesReader.isEnvCloud()) {
            MS.setPass(Users.CLOUD_USER.getPassword());
            final List<User> users = new ArrayList<>();
            users.add(Users.CLOUD_USER);
            MS.setUsers(users);
        } else if (hostType.getName().equalsIgnoreCase(HostType.MS.getName())) {
            try {
                CliToolShell cliToolShell = CliTools.sshShell(host.getIp()).withUsername(host.getUser()).withPassword(host.getPass()).withDefaultTimeout(320L).build();
                cliToolShell.execute("hostname");
                cliToolShell.close();
            } catch (final Exception e) {
                logger.info("MS host execute failed with DIT users: {}/{}", host.getUser(), host.getPass());
            }
        }
        logger.info("final Hostname: {}, Host IP: {}, Type: {}, User: {}, Pass: {}", host.getHostname(), host.getIp(), host.getType(), host.getUser(), host.getPass());
    }

    private boolean isCluster() {
        return svcHosts.contains(hostType) || scpHosts.contains(hostType) || dbHosts.contains(hostType);
    }

    public CliResult execute(final String command) {
        final String cmd = format("%s%s%s", "( ", command, " )");
        if (isCluster()) {
            return executeOnSvcDbScp(cmd, false);
        } else {
            switch (hostType) {
                case JBOSS:
                case HTTPD:
                    return executeOnVm(cmd);
                case WORKLOAD:
                    return executeOnWL(cmd);
                case NETSIM:
                    return executeOnNetsim(cmd, host);
                case MS:
                    return executeOnMS(cmd, MS, 320L);
                case GATEWAY:
                    return executeOnGateway(cmd);
                default:
                    return executeOnVm(cmd);
            }
        }
    }

    public CliResult executeOnMSTimeout(final String command, final Long timeout) {
        return executeOnMS(command, MS, timeout);
    }

    public CliResult executeAsRoot(final String command) {
        if (isCluster()) {
            logger.info("executeOnSvcDbScp as root ........");
            return executeOnSvcDbScp(command, true);
        } else {
            String execCommand;
            CliCommandResult result;
            CliToolShell cliToolShell;
            try {
                cliToolShell = CliTools.sshShell(host.getIp()).withUsername(host.getUser()).withPassword(host.getPass()).withDefaultTimeout(600L).build();
                if (!host.getUser().equals(Users.ROOT.getUsername())) {
                    execCommand = "sudo " + command;
                } else {
                    execCommand = command;
                }
                result = cliToolShell.execute(execCommand);
            } catch (final Exception e) {
                logger.warn("Failed to executeAsRoot using : {}/{} on {}, retrying with default root user/password.", host.getUser(), host.getPass(), host.getIp());
                cliToolShell = CliTools.sshShell(host.getIp()).withUsername(Users.ROOT.getUsername()).withPassword(Users.ROOT.getPassword()).withDefaultTimeout(600L).build();
                result = cliToolShell.execute(command);
            }
            final CliResult cliResult = new CliResult(result);
            if (cliResult.getExitCode() != 0) {
                logger.info("cliResult : {}", cliResult.toString());
            }
            cliToolShell.close();
            return cliResult;
        }
    }

    public CliResult executeOnNode(final String command, final String nodeIp, final Node node) {
        String output = "";
        CliToolShell cliToolShell = null;
        try {
            cliToolShell = CliTools.sshShell(host.getIp()).withUsername(host.getUser()).withPassword(host.getPass()).withDefaultTimeout(320L).build();
            cliToolShell.hopper().hop(new TargetHost(node.getSecureUserName(), node.getSecureUserPassword(), nodeIp, host.getPort(Ports.SSH), false));
            CliIntermediateResult result = cliToolShell.writeLine("mml");
            output = result.getOutput();
            result = cliToolShell.writeLine(command);
            output += result.getOutput();
            cliToolShell.writeLine("exit;");
            cliToolShell.writeLine("exit");
        } catch (Exception e) {
            if (cliToolShell != null) {
                cliToolShell.close();
            }
            logger.warn("Failed to login with user: {}, pass: {}, error message: {}", host.getUser(), host.getPass(), e.getMessage());
            throw e;
        }
        final int exitCode = output.contains("EXECUTED") ? 0 : -1;
        final CliResult cliResult = new CliResult(output, exitCode, 0L);
        cliToolShell.close();
        return cliResult;
    }

    private CliResult executeOnMS(final String command, final Host host, final Long timeout) {
        CliToolShell cliToolShell = null;
        CliCommandResult result;
        try {
            if (HAPropertiesReader.isEnvCloud()) {
                try {
                    cliToolShell = CliTools.sshShell(host.getIp()).withUsername(host.getUser()).withPassword(host.getPass()).withKeyFile(CertUtil.getPemFilePath()).withDefaultTimeout(timeout).build();
                    result = cliToolShell.execute(command);
                } catch (Exception e) {
                    if (cliToolShell != null) {
                        cliToolShell.close();
                    }
                    logger.warn("Failed to login with user {} key.. : {}", host.getUser(), e.getMessage());
                    logger.warn("Retrying with user/pwd : {}/{}", host.getUser(), host.getPass());
                    cliToolShell = CliTools.sshShell(host.getIp()).withUsername(host.getUser()).withPassword(host.getPass()).withDefaultTimeout(timeout).build();
                    result = cliToolShell.execute(command);
                }
            } else {
                try {
                    cliToolShell = CliTools.sshShell(host.getIp()).withUsername(host.getUser()).withPassword(host.getPass()).withDefaultTimeout(timeout).build();
                    result = cliToolShell.execute(command);
                } catch (final Exception e) {
                    if (cliToolShell != null) {
                        cliToolShell.close();
                    }
                    logger.warn("Failed to executeOnMS using : {}/{}, retrying with default root user/password.", host.getUser(), host.getPass());
                    cliToolShell = CliTools.sshShell(host.getIp()).withUsername(Users.ROOT.getUsername()).withPassword(Users.ROOT.getPassword()).withDefaultTimeout(timeout).build();
                    result = cliToolShell.execute(command);
                }
            }
            final CliResult cliResult = new CliResult(result);
            cliToolShell.close();
            return cliResult;
        } finally {
            if (cliToolShell != null) {
                cliToolShell.close();
            }
        }
    }

    private CliResult executeOnNetsim(final String command, final Host host) {
        CliToolShell cliToolShell;
        cliToolShell = CliTools.sshShell(host.getIp()).withUsername(host.getUser()).withPassword(host.getPass()).withDefaultTimeout(320L).build();
        final CliCommandResult result = cliToolShell.execute(command);
        final CliResult cliResult = new CliResult(result);
        cliToolShell.close();
        return cliResult;
    }

    private CliResult executeOnSvcDbScp(final String command, final boolean isRoot) {
        CliToolShell cliToolShell = null;
        try {
            cliToolShell = CliTools.sshShell(MS.getIp()).withUsername(MS.getUser()).withPassword(MS.getPass()).withDefaultTimeout(600L).build();
            cliToolShell.hopper().hop(new TargetHost(host.getUser(Users.LITP_ADMIN.getUsername()).getUsername(), host.getUser(Users.LITP_ADMIN.getUsername()).getPassword(), host.getIp(), host.getPort(Ports.SSH), false));
            if (isRoot) {
                final String rootPassword = HAPropertiesReader.getProperty("svcdb.root.password", Users.ROOT.getPassword());
                logger.info("svc-db rootPassword : {}", rootPassword);
                cliToolShell.switchUser(Users.ROOT.getUsername(), rootPassword);
            }
            final CliCommandResult result = cliToolShell.execute(command);
            final CliResult cliResult = new CliResult(result);
            cliToolShell.close();
            return cliResult;
        } catch (Exception e) {
            if(cliToolShell != null) {
                cliToolShell.close();
            }
            logger.info("Failed to executeOnSvcDbScp with DIT user : {}, password : {}", host.getUser(Users.LITP_ADMIN.getUsername()).getUsername(), host.getUser(Users.LITP_ADMIN.getUsername()).getPassword());
            logger.info("Message ... {}", e.getMessage());
            logger.info("Retrying login with default root and litp-admin/password ...");
            try {
                cliToolShell = CliTools.sshShell(MS.getIp()).withUsername(Users.ROOT.getUsername()).withPassword(Users.ROOT.getPassword()).withDefaultTimeout(600L).build();
                cliToolShell.hopper().hop(new TargetHost(Users.LITP_ADMIN.getUsername(), Users.LITP_ADMIN.getPassword(), host.getIp(), host.getPort(Ports.SSH), false));
                if (isRoot) {
                    cliToolShell.switchUser(Users.ROOT.getUsername(), Users.ROOT.getPassword());
                }
                final CliCommandResult result = cliToolShell.execute(command);
                final CliResult cliResult = new CliResult(result);
                cliToolShell.close();
                return cliResult;
            } catch (final Exception ex) {
                if(cliToolShell != null) {
                    cliToolShell.close();
                }
                logger.info("Message ... {}", e.getMessage());
                logger.info("Retrying login with default root and host litp-admin/password ...");
                cliToolShell = CliTools.sshShell(MS.getIp()).withUsername(Users.ROOT.getUsername()).withPassword(Users.ROOT.getPassword()).withDefaultTimeout(600L).build();
                cliToolShell.hopper().hop(new TargetHost(host.getUser(Users.LITP_ADMIN.getUsername()).getUsername(), host.getUser(Users.LITP_ADMIN.getUsername()).getPassword(), host.getIp(), host.getPort(Ports.SSH), false));
                if (isRoot) {
                    cliToolShell.switchUser(Users.ROOT.getUsername(), Users.ROOT.getPassword());
                }
                final CliCommandResult result = cliToolShell.execute(command);
                final CliResult cliResult = new CliResult(result);
                cliToolShell.close();
                return cliResult;
            }
        } finally {
            if(cliToolShell != null) {
                cliToolShell.close();
            }
        }
    }

    private CliResult executeOnVm(final String command) {
        final String KeyFileCloud = "/var/tmp/Bravo/pemKey.pem";
        String keyFile = "";
        CliToolShell cliToolShell = null;
        try {
            CliCommandResult result;
            if(HAPropertiesReader.isEnvCloud()) {
                cliToolShell = CliTools.sshShell(MS.getIp()).withKeyFile(CertUtil.getPemFilePath()).withUsername(MS.getUser()).withPassword(MS.getPass()).withDefaultTimeout(320L).build();
            } else {
                cliToolShell = CliTools.sshShell(MS.getIp()).withUsername(MS.getUser()).withPassword(MS.getPass()).withDefaultTimeout(320L).build();
                if (!MS.getUser().equals(Users.ROOT.getUsername())) {
                    cliToolShell.sudoRootUser();
                }
            }
            keyFile = HAPropertiesReader.isEnvCloud() ? KeyFileCloud : HostConfigurator.getKeyFile();      // VMs required key to connect
            logger.info("User: {}, executeOnVm on {} using Key : {}", host.getUser(), host.getHostname(), keyFile);
            try {
                for (final User usr: host.getUsers()) {
                    logger.info("Available Users on Vm : {}", usr.getUsername());
                }
                cliToolShell.hopper().hop(new TargetHost(host.getUser(Users.CLOUD_USER.getUsername()).getUsername(), host.getUser(Users.CLOUD_USER.getUsername()).getPassword(), host.getIp(), host.getPort(Ports.SSH), keyFile, false));
                result = cliToolShell.execute(command);
            } catch (UserNotFoundException u) {
                logger.warn("Failed to execute on Vm : {}, Error : {}", host.getHostname(), u.getMessage());
                logger.info("Retrying using cloud-user explicitly ..");
                cliToolShell.hopper().hop(new TargetHost(Users.CLOUD_USER.getUsername(), Users.CLOUD_USER.getPassword(), host.getIp(), host.getPort(Ports.SSH), keyFile, false));
                result = cliToolShell.execute(command);
                logger.info("result : {}", result);
            }
            final CliResult cliResult = new CliResult(result);
            cliToolShell.close();
            return cliResult;
        } catch (Exception e) {
            if(cliToolShell != null) {
                cliToolShell.close();
            }
            logger.info("Failed to execute on Vm using key: {} with DIT user : {}, password : {}", keyFile, host.getUser(Users.CLOUD_USER.getUsername()).getUsername(), host.getUser(Users.CLOUD_USER.getUsername()).getPassword());
            logger.info("Retrying login with default password ...");
            if(HAPropertiesReader.isEnvCloud()) {
                cliToolShell = CliTools.sshShell(MS.getIp()).withKeyFile(CertUtil.getPemFilePath()).withUsername(MS.getUser()).withPassword(MS.getPass()).withDefaultTimeout(320L).build();
            } else {
                cliToolShell = CliTools.sshShell(MS.getIp()).withUsername(MS.getUser()).withPassword(MS.getPass()).withDefaultTimeout(320L).build();
                if (!MS.getUser().equals(Users.ROOT.getUsername())) {
                    cliToolShell.sudoRootUser();
                }
            }
            keyFile = HAPropertiesReader.isEnvCloud() ? KeyFileCloud : HostConfigurator.getKeyFile();      // VMs required key to connect
            logger.info("Retrying login using key ... {}", keyFile);
            cliToolShell.hopper().hop(new TargetHost(Users.CLOUD_USER.getUsername(), Users.CLOUD_USER.getPassword(), host.getIp(), host.getPort(Ports.SSH), keyFile, false));
            final CliCommandResult result = cliToolShell.execute(command);
            final CliResult cliResult = new CliResult(result);
            logger.info("CliResult code: {}", cliResult.getExitCode());
            cliToolShell.close();
            return cliResult;
        } finally {
            if(cliToolShell != null) {
                cliToolShell.close();
            }
        }
    }

    private CliResult executeOnWL(final String command) {
        CliToolShell cliToolShell = null;
        try {
            if (HAPropertiesReader.isEnvCloud() || HAPropertiesReader.isEnvCloudNative()) {
                cliToolShell = CliTools.sshShell(host.getIp()).withUsername(host.getUser()).withPassword(host.getPass()).withDefaultTimeout(320L).build();
            } else {
                cliToolShell = CliTools.sshShell(MS.getIp()).withUsername(MS.getUser()).withPassword(MS.getPass()).withDefaultTimeout(320L).build();
                cliToolShell.hopper().hop(new TargetHost(host.getUser(Users.ROOT.getUsername()).getUsername(), host.getUser(Users.ROOT.getUsername()).getPassword(), host.getIp(), host.getPort(Ports.SSH), false));
            }
            final CliCommandResult result = cliToolShell.execute(command);
            final CliResult cliResult = new CliResult(result);
            cliToolShell.close();
            return cliResult;
        } catch (Exception e) {
            if(cliToolShell != null) {
                cliToolShell.close();
            }
            logger.warn("Failed to executeOnWL with DIT user {}, password: {}", host.getUser(Users.ROOT.getUsername()).getUsername(), host.getUser(Users.ROOT.getUsername()).getPassword());
            logger.info("Retrying Using default root password ..");
            if (HAPropertiesReader.isEnvCloud() || HAPropertiesReader.isEnvCloudNative()) {
                cliToolShell = CliTools.sshShell(host.getIp()).withUsername(Users.ROOT.getUsername()).withPassword(Users.ROOT.getPassword()).withDefaultTimeout(320L).build();
            } else {
                try {
                    cliToolShell = CliTools.sshShell(MS.getIp()).withUsername(MS.getUser()).withPassword(MS.getPass()).withDefaultTimeout(320L).build();
                    cliToolShell.hopper().hop(new TargetHost(Users.ROOT.getUsername(), Users.ROOT.getPassword(), host.getIp(), host.getPort(Ports.SSH), false));
                } catch (Exception ex) {
                    if(cliToolShell != null) {
                        cliToolShell.close();
                    }
                    logger.info("Failed to login with taf-user, retrying with Root for both MS and WL");
                    cliToolShell = CliTools.sshShell(MS.getIp()).withUsername(Users.ROOT.getUsername()).withPassword(Users.ROOT.getPassword()).withDefaultTimeout(320L).build();
                    cliToolShell.hopper().hop(new TargetHost(Users.ROOT.getUsername(), Users.ROOT.getPassword(), host.getIp(), host.getPort(Ports.SSH), false));
                }
            }
            final CliCommandResult result = cliToolShell.execute(command);
            final CliResult cliResult = new CliResult(result);
            cliToolShell.close();
            return cliResult;
        } finally {
            if(cliToolShell != null) {
                cliToolShell.close();
            }
        }
    }

    private CliResult executeOnGateway(final String command) {
        CliToolShell cliToolShell = null;
        try {

            cliToolShell = CliTools.sshShell(host.getIp()).withUsername(host.getUser()).withPassword(host.getPass()).withDefaultTimeout(320L).build();
            final CliCommandResult result = cliToolShell.execute(command);
            final CliResult cliResult = new CliResult(result);
            cliToolShell.close();
            return cliResult;
        } catch (Exception e) {
            if(cliToolShell != null) {
                cliToolShell.close();
            }
            logger.warn("Failed to executeOnWL with DIT user {}, password: {}", host.getUser(), host.getPass());
            logger.info("Retrying Using default root password ..");
            cliToolShell = CliTools.sshShell(host.getIp()).withUsername(Users.ROOT_GW.getUsername()).withPassword(Users.ROOT_GW.getPassword()).withDefaultTimeout(320L).build();
            final CliCommandResult result = cliToolShell.execute(command);
            final CliResult cliResult = new CliResult(result);
            cliToolShell.close();
            return cliResult;
        } finally {
            if(cliToolShell != null) {
                cliToolShell.close();
            }
        }
    }

    private static final class Users {
        static final User ROOT;
        static final User ROOT_GW;
        static final User LITP_ADMIN;
        static final User CLOUD_USER;

        private Users() {
        }

        static {
            ROOT = new User("root", "12shroot", UserType.ADMIN);
            ROOT_GW = new User("root", "shroot", UserType.ADMIN);
            LITP_ADMIN = new User("litp-admin", "12shroot", UserType.ADMIN);
            CLOUD_USER = new User("cloud-user", "N3wP@55w0rd", UserType.OPER);
        }
    }
}
