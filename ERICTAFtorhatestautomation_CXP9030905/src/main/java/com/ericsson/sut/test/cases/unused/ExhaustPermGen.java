package com.ericsson.sut.test.cases.unused;

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
import com.ericsson.nms.rv.taf.failures.FailureInjectionException;
import com.ericsson.nms.rv.taf.failures.exhaust.PermGen;
import com.ericsson.nms.rv.taf.tools.host.Service;
import com.ericsson.nms.rv.taf.tools.host.VirtualMachine;
import com.ericsson.nms.rv.taf.tools.host.VmFactory;
import com.ericsson.oss.testware.hostconfigurator.HostConfigurator;
import com.ericsson.sut.test.cases.HighAvailabilityTestCase;
import com.google.common.truth.Truth;

/**
 * Exhausts the Java Virtual Machine PermGen and verifies system availability.
 */
public class ExhaustPermGen extends HighAvailabilityTestCase {

    private static final Logger logger = LogManager.getLogger(ExhaustPermGen.class);

    /**
     * Supplies data to the below test cases:
     * <ul>
     * <li>{@link ExhaustJvmHeap#exhaustJvmHeap(com.ericsson.nms.rv.taf.tools.host.VirtualMachine)}</li>
     * <li>{@link ExhaustPermGen#exhaustPermGen(com.ericsson.nms.rv.taf.tools.host.VirtualMachine)}</li>
     * <li>{@link RunGarbageCollector#runGarbageCollector(com.ericsson.nms.rv.taf.tools.host.VirtualMachine)}</li>
     * </ul>
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

        final Object[][] objects = new Object[list.size()][1];
        return list.toArray(objects);
    }

    private static void addPM(final List<Object[]> list, final List<Host> virtualMachines) {
        Host auxVm = RegressionUtils.getVMfromList(virtualMachines, VirtualMachine.Type.MS_PM.getValue());
        if (auxVm != null) {
            list.add(new Object[]{auxVm});
        }
        auxVm = RegressionUtils.getVMfromList(virtualMachines, VirtualMachine.Type.PM_SERV.getValue());
        if (auxVm != null) {
            list.add(new Object[]{auxVm});
        }
    }

    private static void addFM(final List<Object[]> list, final List<Host> virtualMachines) {
        Host auxVm = RegressionUtils.getVMfromList(virtualMachines, VirtualMachine.Type.MS_FM.getValue());
        if (auxVm != null) {
            list.add(new Object[]{auxVm});
        }
        auxVm = RegressionUtils.getVMfromList(virtualMachines, VirtualMachine.Type.FM_SERV.getValue());
        if (auxVm != null) {
            list.add(new Object[]{auxVm});
        }
    }

    private static void addCM(final List<Object[]> list, final List<Host> virtualMachines) {
        Host auxVm = RegressionUtils.getVMfromList(virtualMachines, VirtualMachine.Type.MS_CM.getValue());
        if (auxVm != null) {
            list.add(new Object[]{auxVm});
        }
        auxVm = RegressionUtils.getVMfromList(virtualMachines, VirtualMachine.Type.CM_SERV.getValue());
        if (auxVm != null) {
            list.add(new Object[]{auxVm});
        }
        auxVm = RegressionUtils.getVMfromList(virtualMachines, VirtualMachine.Type.MED_ROUTER.getValue());
        if (auxVm != null) {
            list.add(new Object[]{auxVm});
        }
        auxVm = RegressionUtils.getVMfromList(virtualMachines, VirtualMachine.Type.EVENT_BASED_CLIENT.getValue());
        if (auxVm != null) {
            list.add(new Object[]{auxVm});
        }
        auxVm = RegressionUtils.getVMfromList(virtualMachines, VirtualMachine.Type.SUPER_VC.getValue());
        if (auxVm != null) {
            list.add(new Object[]{auxVm});
        }

    }

    /**
     * Exhausts the Java Virtual Machine PermGen and verifies system availability.
     *
     * @param host the virtual machine
     */
    @TestId(id = "TORRV-1743_High_1", title = "Exhaust JVM PermGen")
    @Test(enabled = false, groups = {"High Availability"}, dataProvider = "getData", dataProviderClass = ExhaustPermGen.class)
    public final void exhaustPermGen(final Host host) {

        final PermGen permGen = new PermGen(host);
        final VirtualMachine virtualMachine = new VmFactory(new Service(HostConfigurator.getHost(host.getParentName()))).create(VirtualMachine.Type.fromString(host.getGroup()), host);
        try {
            final String[] oldPids = virtualMachine.getProcess().getPids();
            Truth.assertWithMessage("JBoss is not running").that(oldPids.length).isNotEqualTo(0);

            logger.info("Exhausting the JVM PermGen on {}", virtualMachine.getIp());
            startRegressionVerificationTasks();

            logger.info("Deploying the web tools");
            logger.info("Exhausting the JVM PermGen");
            permGen.insert();

            sleep(HA_VERIFICATION_TIME);

            final String[] pids = virtualMachine.getProcess().getPids();
            if (pids.length == 0) {
                fail(String.format("JBoss did not restart within %d seconds", HA_VERIFICATION_TIME));
            } else {
                Truth.assertWithMessage(String.format("JBoss was not killed within %d seconds", HA_VERIFICATION_TIME)).that(pids[0]).isEqualTo(oldPids[0]);
            }

            logger.info("Verifying availability after the system has recovered");
            sleep(HA_VERIFICATION_TIME);
        } catch (final Exception t) {
            logger.error(t.getMessage(), t);
            fail(t.getMessage());
        } finally {
            try {
                permGen.remove();
            } catch (final FailureInjectionException t) {
                logger.error(t.getMessage(), t);
            }
            stopRegressionVerificationTasks();
        }

    }

}
