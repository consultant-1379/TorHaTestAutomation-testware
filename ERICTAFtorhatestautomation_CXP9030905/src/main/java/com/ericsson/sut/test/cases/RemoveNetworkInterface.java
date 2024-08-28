package com.ericsson.sut.test.cases;

import static org.assertj.core.api.Fail.fail;

import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.testng.annotations.Test;

import com.ericsson.cifwk.taf.annotations.TestId;
import com.ericsson.cifwk.taf.data.DataHandler;
import com.ericsson.cifwk.taf.data.Host;
import com.ericsson.nms.rv.core.util.CliResult;
import com.ericsson.nms.rv.core.util.CliShell;
import com.ericsson.nms.rv.core.util.RegressionUtils;
import com.ericsson.nms.rv.taf.tools.Constants;
import com.ericsson.nms.rv.taf.tools.network.NetworkInterface;
import com.ericsson.oss.testware.hostconfigurator.HostConfigurator;


/**
 * Deactivates one or more network interfaces and verifies system availability.
 */
public class RemoveNetworkInterface extends HighAvailabilityTestCase {

    private static final Logger logger = LogManager.getLogger(RemoveNetworkInterface.class);
    private final String healthCheckCommand = "/opt/ericsson/enminst/bin/enm_healthcheck.sh";
    private final String interfaceCheckListCommand = "/sbin/ifconfig | /bin/egrep -h \"eth.:\" | /bin/awk '{print $1}'";
    private final String removeInterfaceCommand = "/sbin/ifenslave -d bond0 ";
    private final String addInterfaceCommand = "/sbin/ifenslave bond0 ";
    private String removeCommand ;
    private String addCommand;
    private String tempInterface;
    /**
     * Remove one or more network interfaces and verifies system availability.
     *
     * @param host the network interfaces to be deactivated
     */
    @TestId(title = "Remove Network Interfaces")
    @Test(groups = {"High Availability"}, dataProvider = "getData", dataProviderClass = DeactivateNetworkInterfaces.class)
    public final void removeNetworkInterface(final Host host) {
        final CliShell svcShell = new CliShell(host);
        final CliShell rebootShellMS = new CliShell(HostConfigurator.getMS());
        final List<String> interfaceList = new ArrayList<>();
        final Object interfaces = DataHandler.getAttribute("host.interface");
        final String interfaceInput = interfaces.toString();
        logger.info("Host selected for testcase is {} and interfaces are {} ",host.getHostname(),interfaceInput);
        final List<NetworkInterface> networkInterfaces = RegressionUtils.getNetworkInterfaces(host);
        for (final NetworkInterface networkInterface : networkInterfaces) {
            tempInterface = (networkInterface.toString().substring(networkInterface.toString().lastIndexOf("-") + 1));
            if(interfaceInput.contains(tempInterface)) {
                interfaceList.add(tempInterface);
            }
        }
        logger.info("List of interfaces requested for testing : {}",interfaceList);
        final CliResult resultHealthCheckBefore = rebootShellMS.executeAsRoot(healthCheckCommand);
        logger.info("Health check command is {}", healthCheckCommand);
        if (resultHealthCheckBefore.getExitCode() == 0) {
            logger.info("Health check : Success");
            try {
                if (HA_VERIFICATION_TIME < 5 * Constants.Time.ONE_MINUTE_IN_SECONDS) {
                    throw new IllegalArgumentException("HA verification time must be at least 5 minutes");
                }
                startRegressionVerificationTasks();
                for (final String networkInterfaceValue : interfaceList) {
                    removeCommand = removeInterfaceCommand + networkInterfaceValue;
                    addCommand = addInterfaceCommand + networkInterfaceValue;
                    final CliResult removeCommandResult = svcShell.executeAsRoot((removeCommand));
                    logger.info("Removing network interface command is {} and Success status of remove network interface is {} ", removeCommand, removeCommandResult.isSuccess());
                    logger.info("removeCommandResult : {}", removeCommandResult.getOutput());
                    if(!(svcShell.executeAsRoot(interfaceCheckListCommand).getOutput()).contains(networkInterfaceValue)) {
                        logger.info("Verifying availability after removing the network interfaces");
                        sleep(HA_VERIFICATION_TIME);
                        logger.info("Adding the network interfaces");
                        try {
                            final CliResult addCommandResult = svcShell.executeAsRoot((addCommand));
                            logger.info("addCommandResult : {}", addCommandResult.getOutput());
                            logger.info("Adding network interface command is {} and Success status of adding network interface is {} ", addCommand, addCommandResult.isSuccess());
                            if ((svcShell.executeAsRoot(interfaceCheckListCommand).getOutput()).contains(networkInterfaceValue)) {
                                logger.info("Interface is added and verifying availability after the system has recovered");
                                sleep(HA_VERIFICATION_TIME);
                            }else{
                                fail("Failed to add the Interface {}", networkInterfaceValue);
                            }
                        } catch (Exception e) {
                            logger.warn("Failed to add", e);
                        }
                    } else {
                        fail("Failed to remove {}",networkInterfaceValue);
                    }
                    final CliResult resultHealthCheckAfter = rebootShellMS.executeAsRoot(healthCheckCommand);
                    if (resultHealthCheckAfter.getExitCode() == 0) {
                        logger.info("Health check status after removing and adding network interface for {} :Success",networkInterfaceValue);
                    } else {
                        logger.info("Health check status after removing and adding network interface for {} :Failed",networkInterfaceValue);
                        fail("Health Check failed.Test cannot be proceeded");
                    }
                    logger.info("Removing and adding network interface test case for {} is completed", networkInterfaceValue);
                    sleep(10);
                }
            }
            catch (final Exception t) {
                logger.error(t.getMessage(), t);
                fail(t.getMessage());

            } finally {
                try {
                    final String networkInterfaceValue = svcShell.executeAsRoot(interfaceCheckListCommand).getOutput();
                    for(final String valueHolder : interfaceList){
                        if(!networkInterfaceValue.contains(valueHolder)){
                            svcShell.executeAsRoot((addInterfaceCommand + valueHolder));
                        }
                    }

                } catch (final Exception ignore) {
                    logger.warn("Failed to addNetworkInterface.", ignore);
                }
                stopRegressionVerificationTasks();
            }

        }else{
            fail("Health check is failed");
            logger.info("Health Check status before starting verifier: Failed");
        }

    }
}
