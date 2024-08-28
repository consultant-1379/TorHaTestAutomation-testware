/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2015
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/

package com.ericsson.sut.test.cases;

import static org.assertj.core.api.Fail.fail;

import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import com.ericsson.cifwk.taf.annotations.TestId;
import com.ericsson.cifwk.taf.data.Host;
import com.ericsson.nms.rv.taf.failures.FailureInjectionException;
import com.ericsson.nms.rv.taf.failures.filesystem.Nfs;
import com.ericsson.nms.rv.taf.tools.host.VirtualMachine.Type;
import com.ericsson.oss.testware.hostconfigurator.HostConfigurator;

/**
 * Unmounts all file systems of type NFS and verifies system availability.
 */
public class UnmountNfsVm extends HighAvailabilityPuppetTestCase {

    private static final Logger logger = LogManager.getLogger(UnmountNfsVm.class);

    /**
     * Supplies data to the test method.
     *
     * @return an array of array of objects
     */
    @DataProvider
    public static Object[][] getData() {
        final List<Object[]> list = new ArrayList<>();

        // test CM        
        final List<Host> hosts = new ArrayList<>();
        if (FUNCTIONAL_AREAS_MAP.get("cm")) {
            hosts.addAll(HostConfigurator.getAllHosts(Type.MS_CM.getValue()));
            hosts.addAll(HostConfigurator.getAllHosts(Type.CM_SERV.getValue()));
            hosts.addAll(HostConfigurator.getAllHosts(Type.MED_ROUTER.getValue()));
            hosts.addAll(HostConfigurator.getAllHosts(Type.EVENT_BASED_CLIENT.getValue()));
            hosts.addAll(HostConfigurator.getAllHosts(Type.SUPER_VC.getValue()));
        }

        // test FM
        if (FUNCTIONAL_AREAS_MAP.get("fm")) {
            hosts.addAll(HostConfigurator.getAllFmServices());
            hosts.addAll(HostConfigurator.getAllMsfmServices());
        }

        // test PM
        if (FUNCTIONAL_AREAS_MAP.get("pm")) {
            hosts.addAll(HostConfigurator.getAllPmServices());
            hosts.addAll(HostConfigurator.getAllHosts(Type.MS_PM.getValue()));
        }
        // test UM
        if (FUNCTIONAL_AREAS_MAP.get("um")) {
            hosts.addAll(HostConfigurator.getAllHosts(Type.OPEND_IDM.getValue()));
            hosts.addAll(HostConfigurator.getAllHosts(Type.SINGLE_SIGN_ON.getValue()));
        }
        // test NETEX
        if (FUNCTIONAL_AREAS_MAP.get("netex")) {
            hosts.addAll(HostConfigurator.getAllHosts(Type.NETEX.getValue()));
        }
        // test ESM
        if (FUNCTIONAL_AREAS_MAP.get("esm")) {
            hosts.addAll(HostConfigurator.getAllHosts(Type.ESM.getValue()));
        }
        // test SHM
        if (FUNCTIONAL_AREAS_MAP.get("shm")) {
            hosts.addAll(HostConfigurator.getAllHosts(Type.SHM_SERV.getValue()));
            hosts.addAll(HostConfigurator.getAllHosts(Type.SHM_CORE_SERV.getValue()));
        }
        // test CMB
        if (FUNCTIONAL_AREAS_MAP.get("cmbe") || FUNCTIONAL_AREAS_MAP.get("cmbil")) {
            hosts.addAll(HostConfigurator.getAllHosts(Type.CMB.getValue()));
        }
        // test NBI
        if (FUNCTIONAL_AREAS_MAP.get("nbi")) {
            hosts.addAll(HostConfigurator.getAllHosts(Type.NBI.getValue()));
        }

        for (final Host host : hosts) {
            list.add(new Object[]{host});
        }

        final Object[][] objects = new Object[list.size()][1];
        return list.toArray(objects);
    }

    /**
     * Unmounts all file systems of type NFS and verifies system availability.
     *
     * @param host the the host
     */
    @TestId(id = "TORRV-1755_High_1", title = "Unmount all file systems of type NFS on VMs")
    @Test(groups = {"High Availability"}, dataProvider = "getData", dataProviderClass = UnmountNfsVm.class)
    public final void unmountNfs(final Host host) {

        final Nfs nfs = new Nfs(host);
        try {
            startRegressionVerificationTasks();

            logger.info("Unmounting all file systems of type NFS on {}", host.getHostname());
            nfs.unmount();

            logger.info("Verifying availability after unmounting all file systems of type NFS");
            sleep(HA_VERIFICATION_TIME);

            logger.info("Mounting all file systems of type NFS");
            nfs.mount();

            logger.info("Verifying availability after the system has recovered");
            sleep(HA_VERIFICATION_TIME);
        } catch (final Exception t) {
            logger.error(t.getMessage(), t);
            fail(t.getMessage());
        } finally {
            try {
                nfs.mount();
            } catch (final FailureInjectionException ignore) {
                logger.warn("Failed to unmountNfs.", ignore);
            }
            stopRegressionVerificationTasks();
        }
    }

}
