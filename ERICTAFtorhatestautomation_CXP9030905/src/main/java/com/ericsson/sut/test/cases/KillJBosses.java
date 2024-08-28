package com.ericsson.sut.test.cases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Fail.fail;

import java.util.ArrayList;
import java.util.List;

import com.ericsson.nms.rv.core.util.RegressionUtils;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import com.ericsson.cifwk.taf.annotations.TestId;
import com.ericsson.cifwk.taf.data.Host;
import com.ericsson.nms.rv.taf.failures.Kill;
import com.ericsson.nms.rv.taf.tools.host.Service;
import com.ericsson.nms.rv.taf.tools.host.VirtualMachine.Type;
import com.ericsson.nms.rv.taf.tools.process.Process;
import com.ericsson.oss.testware.availability.common.shell.ExecutionResult;
import com.ericsson.oss.testware.availability.common.shell.Shell;

/**
 * Kills one or more JBoss instances and verifies system availability.
 */
public class KillJBosses extends HighAvailabilityTestCase {

    private static final Logger logger = LogManager.getLogger(KillJBosses.class);

    /**
     * Supplies data to the test method.
     *
     * @return an array of array of objects
     */
    @DataProvider
    public static Object[][] getData() {
        final List<Object[]> list = new ArrayList<>();
        final List<Host> services = RegressionUtils.getAllSvcHostList();

        if (FUNCTIONAL_AREAS_MAP.get("cm") || FUNCTIONAL_AREAS_MAP.get("fm") || FUNCTIONAL_AREAS_MAP.get("pm")) {
            for (final Host host : services) {
                final Service service = new Service(host);
                final List<Host> virtualMachines = service.getAllVirtualMachines();

                // test CM
                if (FUNCTIONAL_AREAS_MAP.get("cm")) {
                    addCM(list, virtualMachines);
                }

                // test FM
                if (FUNCTIONAL_AREAS_MAP.get("fm")) {
                    addFM(list, virtualMachines);
                }

                // test PM
                if (FUNCTIONAL_AREAS_MAP.get("pm")) {
                    addPM(list, virtualMachines);
                }
            }
        }

        final Object[][] objects = new Object[list.size()][2];
        return list.toArray(objects);
    }

    private static void addPM(final List<Object[]> list, final List<Host> virtualMachines) {
        Host auxVm = RegressionUtils.getVMfromList(virtualMachines, Type.MS_PM.getValue());
        if (auxVm != null) {
            list.add(new Object[]{auxVm, Process.SIGKILL});
        }
        auxVm = RegressionUtils.getVMfromList(virtualMachines, Type.PM_SERV.getValue());
        if (auxVm != null) {
            list.add(new Object[]{auxVm, Process.SIGKILL});
        }
    }

    private static void addFM(final List<Object[]> list, final List<Host> virtualMachines) {
        Host auxVm = RegressionUtils.getVMfromList(virtualMachines, Type.MS_FM.getValue());
        if (auxVm != null) {
            list.add(new Object[]{auxVm, Process.SIGKILL});
        }
        auxVm = RegressionUtils.getVMfromList(virtualMachines, Type.FM_SERV.getValue());
        if (auxVm != null) {
            list.add(new Object[]{auxVm, Process.SIGKILL});
        }
    }

    private static void addCM(final List<Object[]> list, final List<Host> virtualMachines) {
        Host auxVm = RegressionUtils.getVMfromList(virtualMachines, Type.MS_CM.getValue());
        if (auxVm != null) {
            list.add(new Object[]{auxVm, Process.SIGKILL});
        }
        auxVm = RegressionUtils.getVMfromList(virtualMachines, Type.CM_SERV.getValue());
        if (auxVm != null) {
            list.add(new Object[]{auxVm, Process.SIGKILL});
        }
        auxVm = RegressionUtils.getVMfromList(virtualMachines, Type.MED_ROUTER.getValue());
        if (auxVm != null) {
            list.add(new Object[]{auxVm, Process.SIGKILL});
        }
        auxVm = RegressionUtils.getVMfromList(virtualMachines, Type.EVENT_BASED_CLIENT.getValue());
        if (auxVm != null) {
            list.add(new Object[]{auxVm, Process.SIGKILL});
        }
        auxVm = RegressionUtils.getVMfromList(virtualMachines, Type.SUPER_VC.getValue());
        if (auxVm != null) {
            list.add(new Object[]{auxVm, Process.SIGKILL});
        }

    }

    /**
     * Kills the JBoss processes within one or more virtual machines and verifies system availability.
     *
     * @param virtualMachine the virtual machines
     * @param signal         the Unix signal to be sent
     */
    @TestId(id = "TORRV-1804_High_1", title = "Kill JBoss Processes")
    @Test(groups = {"High Availability"}, dataProvider = "getData", dataProviderClass = KillJBosses.class)
    public final void killJBossProcesses(final Host virtualMachine, final int signal) {
        try {
            startRegressionVerificationTasks();

            Shell shell = new Shell(virtualMachine);
            logger.info("Killing the JBoss processes within the virtual machine with signal [{}] on {}", signal, virtualMachine.getHostname());
            String command = "/usr/bin/ps -ef| grep \"Djboss.home.dir=/ericsson/3pp/jboss\" | grep -vw grep | awk '{print $2}'";
            ExecutionResult pidResult = shell.executeCommand(command);
            logger.info("pid result is : {}", pidResult.toString());
            if (pidResult.isSuccessful() && pidResult.getStdOut() != null && !pidResult.getStdOut().isEmpty()) {
                String killCommand = "sudo /usr/bin/kill -" + signal + " " + pidResult.getStdOut();
                ExecutionResult killResult = shell.executeCommand(killCommand);
                logger.info("Kill result is :{}", killResult.toString());
                if (!killResult.isSuccessful()) {
                    fail("Failed to kill JBoss process on " + virtualMachine.getHostname());
                }
            } else {
                logger.info("Unable to get jboss process id on " + virtualMachine.getHostname());
                fail("unable to get jboss process id on " + virtualMachine.getHostname());
            }

            logger.info("Waiting for the system to recover after killing JBoss processes on {}", virtualMachine.getHostname());
            sleep(HA_VERIFICATION_TIME);
        } catch (final Exception t) {
            logger.error(t.getMessage(), t);
            fail(t.getMessage());
        } finally {
            stopRegressionVerificationTasks();
        }

    }

    /**
     * Kills one or more JBoss virtual machines and verifies system availability.
     *
     * @param virtualMachine the virtual machines
     * @param signal         the Unix signal to be sent
     */
    @TestId(id = "TORRV-1804_High_2", title = "Kill JBoss Virtual Machine")
    @Test(groups = {"High Availability"}, dataProvider = "getData", dataProviderClass = KillJBosses.class)
    public final void killJBossVirtualMachine(final Host virtualMachine, final int signal) {
        try {
            final Kill kill = new Kill(virtualMachine);
            startRegressionVerificationTasks();

            logger.info("Killing the JBoss virtual machine {} with signal [{}]", virtualMachine.getHostname(), signal);
            assertThat(kill.virtualMachine(signal)).as("Failed to kill JBoss virtual machine " + virtualMachine.getHostname());

            logger.info("Waiting for the system to recover after killing JBoss virtual machine {}", virtualMachine.getHostname());
            sleep(HA_VERIFICATION_TIME);
        } catch (final Exception t) {
            logger.error(t.getMessage(), t);
            fail(t.getMessage());
        } finally {
            stopRegressionVerificationTasks();
        }
    }

}
