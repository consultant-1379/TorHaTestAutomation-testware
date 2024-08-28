package com.ericsson.sut.test.cases;

import static org.assertj.core.api.Fail.fail;

import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import com.ericsson.cifwk.taf.annotations.TestId;
import com.ericsson.cifwk.taf.data.DataHandler;
import com.ericsson.cifwk.taf.data.Host;
import com.ericsson.nms.rv.core.util.CliResult;
import com.ericsson.nms.rv.core.util.CliShell;
import com.ericsson.nms.rv.core.util.RegressionUtils;
import com.ericsson.nms.rv.taf.failures.FailureInjectionException;
import com.ericsson.nms.rv.taf.failures.network.Interfaces;
import com.ericsson.nms.rv.taf.tools.Constants;
import com.ericsson.nms.rv.taf.tools.network.NetworkInterface;
import com.ericsson.oss.testware.hostconfigurator.HostConfigurator;


/**
 * Deactivates one or more network interfaces and verifies system availability.
 */
public class DeactivateNetworkInterfaces extends HighAvailabilityTestCase {

    private static final Logger logger = LogManager.getLogger(DeactivateNetworkInterfaces.class);
    private final String healthCheckCommand = "/opt/ericsson/enminst/bin/enm_healthcheck.sh";
    private final String interfaceCheckListCommand = "/sbin/ifconfig | /bin/egrep -h \"eth.:\" | /bin/awk '{print $1}'";
    private final String activeDeactivateCommand = "/sbin/ip link set dev";
    private String tempInterface;
    private String tempVar;

    /**
     * Supplies data to the test method.
     *
     * @return an array of array of objects
     */
    @DataProvider
    public static Object[][] getData() {
        final Object host = DataHandler.getAttribute("host.name");
        final List<Object[]> list = new ArrayList<>();
        switch(host.toString()) {
            case "SVC1" :
                list.add(new Object[]{HostConfigurator.getSVC1()}); break;
            case "SVC2" :
                list.add(new Object[]{HostConfigurator.getSVC2()}); break;
            case "SVC3" :
                list.add(new Object[]{HostConfigurator.getSVC3()}); break;
            case "SVC4" :
                list.add(new Object[]{HostConfigurator.getSVC4()}); break;
            default:
                logger.error("Invalid host name : {}", host.toString()); break;
        }
        final Object[][] objects = new Object[list.size()][1];
        return list.toArray(objects);
    }

    /**
     * Deactivates one or more network interfaces and verifies system availability.
     *
     * @param host the network interfaces to be deactivated
     */
    @TestId(id = "TORRV-1642_High_1", title = "Deactivate Network Interfaces")
    @Test(groups = {"High Availability"}, dataProvider = "getData", dataProviderClass = DeactivateNetworkInterfaces.class)
    public final void deactivateNetworkInterfaces(final Host host) {
        final CliShell rebootShellMS = new CliShell(HostConfigurator.getMS());
        final CliShell svcShell = new CliShell(host);
        final Object interfaceObj = DataHandler.getAttribute("host.interface");
        final List<NetworkInterface> interfaceList = new ArrayList<>();
        final List<NetworkInterface> networkInterfaces = RegressionUtils.getNetworkInterfaces(host);
        logger.info("networkInterfaces : {}", networkInterfaces);
        logger.info("interfaceObj : {}", interfaceObj.toString());
        logger.info("interfaceCheckListCommand output : {}", svcShell.executeAsRoot(interfaceCheckListCommand).getOutput());

        if(networkInterfaces.isEmpty()) {
            logger.error("Unable to get Network Interfaces, list is empty");
            fail("Unable to get Network Interfaces");
        }

        final String interfaceInput = interfaceObj.toString();
        logger.info("Host selected for testcase is {} and interfaces are {} ", host.getHostname(), interfaceInput);
        for (final NetworkInterface networkInterface : networkInterfaces) {
            tempInterface = (networkInterface.toString().substring(networkInterface.toString().lastIndexOf("-") + 1));
            if(interfaceInput.contains(tempInterface)) {
                interfaceList.add(networkInterface);
            }
        }
        logger.info("List of interfaces requested for testing : {}", interfaceList);
        if(interfaceList.isEmpty()) {
            logger.error("Nothing to test, Network Interface list is empty");
            fail("Network Interface list is empty");
        }
        final CliResult resultHealthCheckBefore = rebootShellMS.executeAsRoot(healthCheckCommand);
        logger.info("Health check command is {}",healthCheckCommand);
        if (resultHealthCheckBefore.getExitCode() == 0) {
            logger.info("Health check : Success");
            try {
                if (HA_VERIFICATION_TIME < 5 * Constants.Time.ONE_MINUTE_IN_SECONDS) {
                    throw new IllegalArgumentException("HA verification time must be at least 5 minutes");
                }
                startRegressionVerificationTasks();
                for (final NetworkInterface networkInterfaceValue : interfaceList) {
                    final Interfaces interfaces = new Interfaces(networkInterfaceValue);
                    tempVar = networkInterfaceValue.toString().substring(networkInterfaceValue.toString().lastIndexOf("-") + 1);
                    try {
                        logger.info("Deactivate command is {} {} down", activeDeactivateCommand,tempVar);
                        interfaces.deactivate();
                        if(!svcShell.executeAsRoot(interfaceCheckListCommand).getOutput().contains(tempVar)){
                            logger.info("Deactivation of {} is success",tempVar);
                        }else{
                            fail("Failed to Deactivate {}",tempVar);
                        }
                    } catch (final FailureInjectionException e) {
                        logger.warn("Failed to deactivate", e);
                        fail("Failed to Deactivate {} due to {}",tempVar,e.getMessage());
                    }

                    logger.info("Verifying availability after deactivating the network interfaces");
                    sleep(HA_VERIFICATION_TIME);

                    logger.info("Activating the network interfaces");
                    try {
                        logger.info("Activate command is {} {} up", activeDeactivateCommand,tempVar);
                        interfaces.activate();
                        if(svcShell.executeAsRoot(interfaceCheckListCommand).getOutput().contains(tempVar)){
                            logger.info("Activation of {} is success",tempVar);
                        }else{
                            fail("Failed to Activate {}",tempVar);
                        }
                    } catch (final FailureInjectionException e) {
                        logger.warn("Failed to activate", e);
                        fail("Failed to Activate {} due to {}",tempVar,e.getMessage());
                    }
                    logger.info("Verifying availability after the system has recovered");
                    sleep(HA_VERIFICATION_TIME);
                    final CliResult resultHealthCheckAfter = rebootShellMS.executeAsRoot(healthCheckCommand);
                    if (resultHealthCheckAfter.getExitCode() == 0) {
                        logger.info("Health check status after deactivating and activating process for {} :Success", networkInterfaceValue);
                    } else {
                        logger.info("Health check status after deactivating and activating process for {} :Failed", networkInterfaceValue);
                        fail("Health Check failed.Test case cannot be proceeded.");
                    }
                    sleep(10);
                }
            } catch (final Exception t) {
                logger.error(t.getMessage(), t);
                fail(t.getMessage());
            } finally {
                try {
                    final String outputValue = svcShell.executeAsRoot(interfaceCheckListCommand).getOutput();
                    logger.info("interfaceCheckListCommand output : {}", outputValue);
                    for (final NetworkInterface valueHolder : interfaceList) {
                        tempVar = valueHolder.toString().substring(valueHolder.toString().lastIndexOf("-") + 1);
                        if(!outputValue.contains(tempVar)) {
                            logger.info("activating {}...", tempVar);
                            final Interfaces interfaces = new Interfaces(valueHolder);
                            interfaces.activate();
                            logger.info("{} activated", tempVar);
                        }
                    }
                } catch (final FailureInjectionException ignore) {
                    logger.warn("Failed to activate NetworkInterfaces : {}", ignore.getMessage());
                }
                stopRegressionVerificationTasks();
            }
        } else {
            logger.info("Health Check status before starting verifier: Failed");
            fail("Health check is failed");
        }
    }
}
