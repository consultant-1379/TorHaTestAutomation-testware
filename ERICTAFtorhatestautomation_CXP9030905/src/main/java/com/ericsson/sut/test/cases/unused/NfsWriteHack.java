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
import com.ericsson.nms.rv.taf.failures.filesystem.HackNfsWrite;
import com.ericsson.nms.rv.taf.tools.host.Service;
import com.ericsson.nms.rv.taf.tools.host.VirtualMachine.Type;
import com.ericsson.sut.test.cases.HighAvailabilityTestCase;

/**
 * Intercepts all write operation to filesystems of type NFS and pretends that data are written to disk whilst they
 * are actually dropped.
 */
public class NfsWriteHack extends HighAvailabilityTestCase {

    private static final Logger logger = LogManager.getLogger(NfsWriteHack.class);

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
            addDB(list);
            addSVC(list);
        }

        final Object[][] objects = new Object[list.size()][2];
        return list.toArray(objects);
    }

    private static void addSVC(final List<Object[]> list) {
        for (final Host host : RegressionUtils.getAllSvcHostList()) {
            list.add(new Object[]{host});
        }
    }

    private static void addDB(final List<Object[]> list) {
        for (final Host host : RegressionUtils.getAllDbHostList()) {
            list.add(new Object[]{host});
        }
    }

    private static void addPM(final List<Object[]> list, final List<Host> virtualMachines) {
        Host auxVm = RegressionUtils.getVMfromList(virtualMachines, Type.MS_PM.getValue());
        if (auxVm != null) {
            list.add(new Object[]{auxVm});
        }
        auxVm = RegressionUtils.getVMfromList(virtualMachines, Type.PM_SERV.getValue());
        if (auxVm != null) {
            list.add(new Object[]{auxVm});
        }
    }

    private static void addFM(final List<Object[]> list, final List<Host> virtualMachines) {
        Host auxVm = RegressionUtils.getVMfromList(virtualMachines, Type.MS_FM.getValue());
        if (auxVm != null) {
            list.add(new Object[]{auxVm});
        }
        auxVm = RegressionUtils.getVMfromList(virtualMachines, Type.FM_SERV.getValue());
        if (auxVm != null) {
            list.add(new Object[]{auxVm});
        }
    }

    private static void addCM(final List<Object[]> list, final List<Host> virtualMachines) {
        Host auxVm = RegressionUtils.getVMfromList(virtualMachines, Type.MS_CM.getValue());
        if (auxVm != null) {
            list.add(new Object[]{auxVm});
        }
        auxVm = RegressionUtils.getVMfromList(virtualMachines, Type.CM_SERV.getValue());
        if (auxVm != null) {
            list.add(new Object[]{auxVm});
        }
        auxVm = RegressionUtils.getVMfromList(virtualMachines, Type.MED_ROUTER.getValue());
        if (auxVm != null) {
            list.add(new Object[]{auxVm});
        }
        auxVm = RegressionUtils.getVMfromList(virtualMachines, Type.EVENT_BASED_CLIENT.getValue());
        if (auxVm != null) {
            list.add(new Object[]{auxVm});
        }
        auxVm = RegressionUtils.getVMfromList(virtualMachines, Type.SUPER_VC.getValue());
        if (auxVm != null) {
            list.add(new Object[]{auxVm});
        }

    }

    /**
     * Intercepts all write operation to filesystems of type NFS and pretends that data are written to disk
     * whilst they are actually dropped.
     *
     * @param host the the host
     */
    @TestId(id = "TORRV-3942_High_1", title = "Hack NFS write")
    @Test(enabled = false, groups = {"High Availability"}, dataProvider = "getData", dataProviderClass = NfsWriteHack.class)
    public final void hackNfsWrite(final Host host) {

        final HackNfsWrite hackNfsWrite = new HackNfsWrite(host);
        try {

            startRegressionVerificationTasks();

            logger.info("Inserting the hack-nfs module into the Linux kernel of {}", host.getHostname());
            hackNfsWrite.insert();

            logger.info("Verifying availability after hacking the NFS write operations");
            sleep(HA_VERIFICATION_TIME);

            logger.info("Removing the hack-nfs module from the Linux kernel of {}", host.getHostname());
            hackNfsWrite.remove();

            logger.info("Verifying availability after the system has recovered");
            sleep(HA_VERIFICATION_TIME);
        } catch (final Exception t) {
            logger.error(t.getMessage(), t);
            fail(t.getMessage());
        } finally {
            hackNfsWrite.remove();
            stopRegressionVerificationTasks();
        }

    }

}
