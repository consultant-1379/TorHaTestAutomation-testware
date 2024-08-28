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
import com.ericsson.nms.rv.taf.failures.exhaust.Inodes;
import com.ericsson.nms.rv.taf.tools.host.Service;
import com.ericsson.nms.rv.taf.tools.host.VirtualMachine;
import com.google.common.truth.Truth;


/**
 * Exhausts the inodes and verifies system availability.
 */
public class ExhaustInodes extends HighAvailabilityTestCase {

    private static final Logger logger = LogManager.getLogger(ExhaustInodes.class);

    /**
     * Supplies data to the test method.
     *
     * @return an array of array of objects
     */
    @DataProvider
    public static Object[][] getData() {
        final List<Object[]> list = new ArrayList<>();

        if (FUNCTIONAL_AREAS_MAP.get("cm") || FUNCTIONAL_AREAS_MAP.get("fm") || FUNCTIONAL_AREAS_MAP.get("pm")) {
            for (final Host host : RegressionUtils.getAllSvcHostList()) {
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

        addDB(list);
        addSVC(list);

        final Object[][] objects = new Object[list.size()][2];
        return list.toArray(objects);
    }

    private static void addSVC(final List<Object[]> list) {
        boolean first = true;
        for (final Host host : RegressionUtils.getAllSvcHostList()) {
            if (first) {
                list.add(new Object[]{host, "/ericsson/batch"});
                list.add(new Object[]{host, "/ericsson/config_mgt"});
                list.add(new Object[]{host, "/ericsson/enm/dumps"});
                list.add(new Object[]{host, "/ericsson/pmic"});
                list.add(new Object[]{host, "/ericsson/symvol"});
                list.add(new Object[]{host, "/ericsson/tor/data"});
                list.add(new Object[]{host, "/ericsson/tor/logstash"});
                list.add(new Object[]{host, "/ericsson/tor/no_rollback"});
                list.add(new Object[]{host, "/ericsson/tor/smrs"});
                list.add(new Object[]{host, "/etc/opt/ericsson/ERICmodeldeployment"});
                list.add(new Object[]{host, "/opt/ericsson/storage/user/nas/brsadm"});
                list.add(new Object[]{host, "/opt/ericsson/storage/user/nas/storobs"});
                list.add(new Object[]{host, "/var/ericsson/ddc_data"});
                first = false;
            }
            list.add(new Object[]{host, "/"});
            list.add(new Object[]{host, "/boot"});
            list.add(new Object[]{host, "/etc/ericsson"});
            list.add(new Object[]{host, "/opt/ericsson"});
            list.add(new Object[]{host, "/var"});
            list.add(new Object[]{host, "/var/ericsson"});
            list.add(new Object[]{host, "/var/lib/libvirt/images"});
        }
    }

    private static void addDB(final List<Object[]> list) {
        for (final Host host : RegressionUtils.getAllDbHostList()) {
            list.add(new Object[]{host, "/"});
            list.add(new Object[]{host, "/boot"});
            list.add(new Object[]{host, "/ericsson/postgres"});
            list.add(new Object[]{host, "/ericsson/versant_data"});
            list.add(new Object[]{host, "/etc/ericsson"});
            list.add(new Object[]{host, "/opt/ericsson"});
            list.add(new Object[]{host, "/var"});
            list.add(new Object[]{host, "/var/ericsson"});
        }
    }

    private static void addPM(final List<Object[]> list, final List<Host> virtualMachines) {
        Host auxVm = RegressionUtils.getVMfromList(virtualMachines, VirtualMachine.Type.MS_PM.getValue());
        if (auxVm != null) {
            list.add(new Object[]{auxVm, "/"});
        }
        auxVm = RegressionUtils.getVMfromList(virtualMachines, VirtualMachine.Type.PM_SERV.getValue());
        if (auxVm != null) {
            list.add(new Object[]{auxVm, "/"});
        }
    }

    private static void addFM(final List<Object[]> list, final List<Host> virtualMachines) {
        Host auxVm = RegressionUtils.getVMfromList(virtualMachines, VirtualMachine.Type.MS_FM.getValue());
        if (auxVm != null) {
            list.add(new Object[]{auxVm, "/"});
        }
        auxVm = RegressionUtils.getVMfromList(virtualMachines, VirtualMachine.Type.FM_SERV.getValue());
        if (auxVm != null) {
            list.add(new Object[]{auxVm, "/"});
        }
    }

    private static void addCM(final List<Object[]> list, final List<Host> virtualMachines) {
        Host auxVm = RegressionUtils.getVMfromList(virtualMachines, VirtualMachine.Type.MS_CM.getValue());
        if (auxVm != null) {
            list.add(new Object[]{auxVm, "/"});
        }
        auxVm = RegressionUtils.getVMfromList(virtualMachines, VirtualMachine.Type.CM_SERV.getValue());
        if (auxVm != null) {
            list.add(new Object[]{auxVm, "/"});
        }
        auxVm = RegressionUtils.getVMfromList(virtualMachines, VirtualMachine.Type.MED_ROUTER.getValue());
        if (auxVm != null) {
            list.add(new Object[]{auxVm, "/"});
        }
        auxVm = RegressionUtils.getVMfromList(virtualMachines, VirtualMachine.Type.EVENT_BASED_CLIENT.getValue());
        if (auxVm != null) {
            list.add(new Object[]{auxVm, "/"});
        }
        auxVm = RegressionUtils.getVMfromList(virtualMachines, VirtualMachine.Type.SUPER_VC.getValue());
        if (auxVm != null) {
            list.add(new Object[]{auxVm, "/"});
        }

    }

    /**
     * Exhaust the inodes verifies system availability.
     *
     * @param host       the host
     * @param mountPoint the mount point where the temporary files shall be created
     */
    @TestId(id = "TORRV-1713_High_1", title = "Exhaust Inodes")
    @Test(groups = {"High Availability"}, dataProvider = "getData", dataProviderClass = ExhaustInodes.class)
    public final void exhaustInodes(final Host host, final String mountPoint) {
        final Inodes inodes = new Inodes(host, mountPoint);
        try {
            startRegressionVerificationTasks();

            logger.info("Exhausting the inodes on {}{}", host.getHostname(), mountPoint);
            inodes.insert();
            Truth.assertWithMessage("Failed to exhaust the inodes").that(inodes.getFree()).isLessThan(100);

            logger.info("Verifying availability while exhausting the inodes");
            sleep(HA_VERIFICATION_TIME);

            logger.info("Removing the temporary files");
            inodes.remove();
            Truth.assertWithMessage("Failed to remove the temporary files").that(inodes.isExists()).isFalse();

            logger.info("Verifying availability after the system has recovered");
            sleep(HA_VERIFICATION_TIME);
        } catch (final Exception t) {
            logger.error(t.getMessage(), t);
            fail(t.getMessage());
        } finally {
            inodes.remove();
            stopRegressionVerificationTasks();
        }
    }

}
