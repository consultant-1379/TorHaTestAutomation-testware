package com.ericsson.sut.test.cases;

import static org.assertj.core.api.Fail.fail;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.ericsson.nms.rv.core.util.RegressionUtils;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import com.ericsson.cifwk.taf.annotations.TestId;
import com.ericsson.cifwk.taf.data.Host;
import com.ericsson.cifwk.taf.data.HostType;
import com.ericsson.nms.rv.taf.failures.FailureInjectionException;
import com.ericsson.nms.rv.taf.failures.database.Versant;
import com.ericsson.nms.rv.taf.failures.filesystem.FileDescriptors;
import com.ericsson.nms.rv.taf.tools.host.Database;
import com.ericsson.nms.rv.taf.tools.host.Service;
import com.ericsson.nms.rv.taf.tools.host.VirtualMachine;
import com.ericsson.oss.testware.availability.common.shell.ShellException;
import com.google.common.truth.Truth;


/**
 * Exhausts the file descriptors and verifies system availability.
 */
public class ExhaustFileDescriptors extends HighAvailabilityTestCase {

    private static final Logger logger = LogManager.getLogger(ExhaustFileDescriptors.class);

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
        addSVC(list);

        // disabled because of bug TORF-42885
        // enabled because bug TORF-42885 was fixed and closed
        addDB(list);

        final Object[][] objects = new Object[list.size()][2];
        return list.toArray(objects);
    }

    private static void addDB(final List<Object[]> list) {
        for (final Host host : RegressionUtils.getAllDbHostList()) {
            list.add(new Object[]{host});
        }
    }

    private static void addSVC(final List<Object[]> list) {
        for (final Host host : RegressionUtils.getAllSvcHostList()) {
            list.add(new Object[]{host});
        }
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
     * Exhausts the file descriptors and verifies system availability.
     *
     * @param host the host
     */
    @TestId(id = "TORRV-1708_High_1", title = "Exhaust File Descriptors")
    @Test(groups = {"High Availability"}, dataProvider = "getData", dataProviderClass = ExhaustFileDescriptors.class)
    public final void exhaustFileDescriptors(final Host host) {
        final FileDescriptors fileDescriptors = new FileDescriptors(host);
        final Versant versant = new Versant();
        Database versantDatabase = null;
        final String logFile = "log" + new SimpleDateFormat("_yyyy_MM_dd_HH_mm_ss").format(new Date()) + ".xml";

        final boolean isDB = host.getType().toString().startsWith(HostType.DB.toString());
        try {
            startRegressionVerificationTasks();
            if (isDB) {
                try {
                    versantDatabase = versant.getDatabase();
                } catch (final FailureInjectionException e) {
                    logger.error(e.getMessage(), e);
                }
                if (versantDatabase != null) {
                    versantDatabase.startVersantTransactions(logFile);
                }
            }
            logger.info("Exhausting the file descriptors on " + host.getHostname());

            fileDescriptors.exhaust(HA_VERIFICATION_TIME);

            logger.info("Verifying availability while exhausting the file descriptors");
            sleep(HA_VERIFICATION_TIME);

            logger.info("Verifying availability after the system has recovered");
            // empties the pgstartup.log which can become really huge
            fileDescriptors.restore();
            sleep(HA_VERIFICATION_TIME);
        } catch (final Exception t) {
            logger.error(t.getMessage(), t);
            fail(t.getMessage());
        } finally {
            fileDescriptors.restore();

            stopRegressionVerificationTasks();
            if (isDB) {
                try {
                    versant.restart();
                    versantDatabase = versant.getDatabase();
                    if (versantDatabase != null) {
                        versantDatabase.validateVersantTransactions(logFile);
                        Truth.assertThat(versantDatabase.isTransactionSuccessful(logFile));
                    }

                } catch (final ShellException | FailureInjectionException e) {
                    logger.error(e.getMessage(), e);
                }
            }
        }

    }

}
