package com.ericsson.nms.rv.core.cm;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.NavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;

import com.ericsson.nms.rv.taf.tools.Constants;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.ericsson.nms.rv.core.EnmApplication;
import com.ericsson.nms.rv.core.LoadGenerator;
import com.ericsson.nms.rv.core.node.Node;
import com.ericsson.nms.rv.core.system.SystemStatus;
import com.ericsson.oss.testware.availability.common.exception.EnmException;


/**
 * {@code ConfigurationManagement} allows to run {@code cmedit} commands through ENM's {@code Command Line Interface} application.
 */
public final class ConfigurationManagement extends EnmApplication {

    private static final Logger logger = LogManager.getLogger(ConfigurationManagement.class);
    private final List<Node> cmNodes = new ArrayList<>();
    private final List<Node> allNodes = new ArrayList<>();
    private boolean doLogin;
    private CmComposite cmComposite;
    public static NavigableMap<Date, Date> cmIgnoreDTMap = new ConcurrentSkipListMap();

    public ConfigurationManagement(final boolean doLogin) {
        this.doLogin = doLogin;
    }

    ConfigurationManagement(final CmLoadGenerator cmLoadGenerator) {
        allNodes.addAll(LoadGenerator.getNodes());
        cmNodes.addAll(cmLoadGenerator.getCmVerificationNodes());

        logger.info("ConfigurationManagement Using nodes {}", getUsedNodes(cmNodes));
    }

    public ConfigurationManagement(final CmLoadGenerator cmLoadGenerator, final int amountOfNodesOnSync, final SystemStatus systemStatus) {
        allNodes.addAll(LoadGenerator.getNodes());
        cmNodes.addAll(cmLoadGenerator.getCmVerificationNodes());
        initDownTimerHandler(systemStatus);
        initFirstTimeFail();

        logger.info("ConfigurationManagement Using nodes {}", getUsedNodes(cmNodes));
        doLogin = true;
        cmComposite = CmFlowFactory.createCompleteCmFlow(this, cmNodes, amountOfNodesOnSync);
    }

    @Override
    public void login() throws EnmException {
        if (doLogin) {
            super.login();
        }
    }

    @Override
    public void logout() throws EnmException {
        if (doLogin) {
            super.logout();
        }
    }

    /**
     * the CM load generator
     */
    @Override
    public void verify() {
        CmThreadExecutor thread = new CmThreadExecutor(this.cmComposite, getHttpRestServiceClient(), this);
        thread.execute();
        long startTime = System.nanoTime();
        logger.info("CM Thread: {} started.", thread.getID());
        do {
            this.sleep(1L);
        }  while (!thread.isCompleted() && !thread.isErrorOcurred() && !thread.isTimedOut() && !(startTime - System.nanoTime() > 120L * (long) Constants.TEN_EXP_9));

        if (thread.isErrorOcurred()) {
            logger.warn("CM Thread: {} Error occured in CM", thread.getID());
        } else if (thread.isTimedOut()) {
            logger.warn("CM Thread: {} CM Timed out.", thread.getID());
        } else if (thread.isCompleted()) {
            logger.info("CM Thread: {} completed!", thread.getID());
        }
    }
}
