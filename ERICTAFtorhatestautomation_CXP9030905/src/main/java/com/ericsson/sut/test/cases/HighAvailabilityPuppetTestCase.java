package com.ericsson.sut.test.cases;

import java.util.ArrayList;
import java.util.List;

import com.ericsson.nms.rv.core.util.RegressionUtils;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

import com.ericsson.cifwk.taf.data.Host;
import com.ericsson.nms.rv.core.HAPropertiesReader;
import com.ericsson.nms.rv.taf.tools.process.UnixService;
import com.ericsson.nms.rv.taf.tools.process.UnixServiceException;

/**
 * The base class of all test cases.
 */
@SuppressWarnings("PMD.DoNotUseThreads")
public abstract class HighAvailabilityPuppetTestCase extends HighAvailabilityTestCase {

    private static final Logger logger = LogManager.getLogger(HighAvailabilityPuppetTestCase.class);
    /**
     * the {@code puppet} service
     */
    private static final String PUPPET = "puppet";

    /**
     * Starts puppet for each method.
     */
    @AfterMethod(alwaysRun = true)
    public void afterMethod() {
        startPuppet();
    }

    /**
     * Stops puppet for each method.
     */
    @BeforeMethod(alwaysRun = true)
    public void beforeMethod() {
        stopPuppet();
    }

    /**
     * Starts {@code puppet} and {@code puppetmaster} services on all hosts.
     */
    private void startPuppet() {
        getAllHosts().parallelStream().forEach(host -> {
            try {
                new UnixService(host, PUPPET).start();
            } catch (final UnixServiceException e) {
                logger.warn(e.getMessage(), e);
            }
        });
    }

    /**
     * Stops {@code puppet} and {@code puppetmaster} services on all hosts.
     */
    private void stopPuppet() {
        getAllHosts().parallelStream().forEach(host -> {
            try {
                new UnixService(host, PUPPET).stop();
            } catch (final UnixServiceException e) {
                logger.warn(e.getMessage(), e);
            }
        });
    }

    private List<Host> getAllHosts() {
        final List<Host> hosts = new ArrayList<>();
        hosts.add(HAPropertiesReader.getMS());
        hosts.addAll(RegressionUtils.getAllDbHostList());
        hosts.addAll(RegressionUtils.getAllSvcHostList());
        return hosts;
    }

}
