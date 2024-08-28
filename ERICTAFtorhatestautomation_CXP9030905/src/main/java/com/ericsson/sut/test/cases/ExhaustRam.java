package com.ericsson.sut.test.cases;

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
import com.ericsson.nms.rv.taf.failures.exhaust.Ram;
import com.ericsson.nms.rv.taf.tools.host.Service;
import com.ericsson.nms.rv.taf.tools.host.VirtualMachine.Type;


/**
 * Exhausts the RAM and verifies system availability.
 */
public class ExhaustRam extends HighAvailabilityTestCase {

    private static final Logger logger = LogManager.getLogger(ExhaustRam.class);

    /**
     * Supplies data to the test method.
     *
     * @return an array of array of objects
     */
    @DataProvider
    public static Object[][] getData() {
        final List<Object[]> list = new ArrayList<>();

        for (final Host host : RegressionUtils.getAllSvcHostList()) {
            final Service service = new Service(host);
            final List<Host> virtualMachines = service.getAllVirtualMachines();

            Host auxVm = RegressionUtils.getVMfromList(virtualMachines, Type.HTTPD.getValue());
            if (auxVm != null) {
                list.add(new Object[]{auxVm, 30_000});
            }
            auxVm = RegressionUtils.getVMfromList(virtualMachines, Type.SINGLE_SIGN_ON.getValue());
            if (auxVm != null) {
                list.add(new Object[]{auxVm, 30_000});
            }

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

            // add the svc
            list.add(new Object[]{host, 30_000});
        }

        for (final Host host : RegressionUtils.getAllDbHostList()) {
            list.add(new Object[]{host, 30_000});
        }


        final Object[][] objects = new Object[list.size()][2];
        return list.toArray(objects);
    }

    private static void addPM(final List<Object[]> list, final List<Host> virtualMachines) {
        Host auxVm = RegressionUtils.getVMfromList(virtualMachines, Type.MS_PM.getValue());
        if (auxVm != null) {
            list.add(new Object[]{auxVm, 30_000});
        }
        auxVm = RegressionUtils.getVMfromList(virtualMachines, Type.PM_SERV.getValue());
        if (auxVm != null) {
            list.add(new Object[]{auxVm, 30_000});
        }
    }

    private static void addFM(final List<Object[]> list, final List<Host> virtualMachines) {
        Host auxVm = RegressionUtils.getVMfromList(virtualMachines, Type.MS_FM.getValue());
        if (auxVm != null) {
            list.add(new Object[]{auxVm, 30_000});
        }
        auxVm = RegressionUtils.getVMfromList(virtualMachines, Type.FM_SERV.getValue());
        if (auxVm != null) {
            list.add(new Object[]{auxVm, 30_000});
        }
    }

    private static void addCM(final List<Object[]> list, final List<Host> virtualMachines) {
        Host auxVm = RegressionUtils.getVMfromList(virtualMachines, Type.MS_CM.getValue());
        if (auxVm != null) {
            list.add(new Object[]{auxVm, 30_000});
        }
        auxVm = RegressionUtils.getVMfromList(virtualMachines, Type.CM_SERV.getValue());
        if (auxVm != null) {
            list.add(new Object[]{auxVm, 30_000});
        }
        auxVm = RegressionUtils.getVMfromList(virtualMachines, Type.MED_ROUTER.getValue());
        if (auxVm != null) {
            list.add(new Object[]{auxVm, 30_000});
        }
        auxVm = RegressionUtils.getVMfromList(virtualMachines, Type.EVENT_BASED_CLIENT.getValue());
        if (auxVm != null) {
            list.add(new Object[]{auxVm, 30_000});
        }
        auxVm = RegressionUtils.getVMfromList(virtualMachines, Type.SUPER_VC.getValue());
        if (auxVm != null) {
            list.add(new Object[]{auxVm, 30_000});
        }

    }

    /**
     * Exhausts the RAM and verifies system availability.
     *
     * @param host   the host
     * @param memory the amount of RAM to be allocated
     */
    @TestId(id = "TORRV-1728_High_1", title = "Exhaust RAM")
    @Test(groups = {"High Availability"}, dataProvider = "getData", dataProviderClass = ExhaustRam.class)
    public final void exhaustRam(final Host host, final int memory) {

        final Ram ram = new Ram(host, memory);
        try {
            startRegressionVerificationTasks();

            logger.info("Exhausting the RAM on {}", host.getHostname());
            ram.insert();

            logger.info("Verifying availability while exhausting the RAM");
            sleep(HA_VERIFICATION_TIME);

            logger.info("Freeing the RAM");
            ram.remove();

            logger.info("Verifying availability after the system has recovered");
            sleep(HA_VERIFICATION_TIME);
        } catch (final Exception t) {
            logger.error(t.getMessage(), t);
            fail(t.getMessage());
        } finally {
            ram.remove();
            stopRegressionVerificationTasks();
        }

    }

}
