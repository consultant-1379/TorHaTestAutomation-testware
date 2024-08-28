package com.ericsson.nms.rv.core.amos;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.ericsson.nms.rv.core.util.AdminUserUtils;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.ericsson.cifwk.taf.data.Host;
import com.ericsson.nms.rv.core.EnmApplication;
import com.ericsson.nms.rv.core.EnmSystemException;
import com.ericsson.nms.rv.core.HAPropertiesReader;
import com.ericsson.nms.rv.core.amos.common.AmosUiTask;
import com.ericsson.nms.rv.core.amos.operators.http.CustomHttpClient;
import com.ericsson.nms.rv.core.node.Node;
import com.ericsson.nms.rv.core.system.SystemStatus;
import com.ericsson.oss.testware.availability.common.exception.EnmErrorType;
import com.ericsson.oss.testware.availability.common.exception.EnmException;
import com.ericsson.oss.testware.hostconfigurator.HostConfigurator;

/**
 * @author eamgmuh
 * <p>
 * {@code Amos} allows to test Amos through opening websocket and sending sample commands
 */
@SuppressWarnings("deprecation")
public final class Amos extends EnmApplication {

    private static final Logger logger = LogManager.getLogger(Amos.class);

    /**
     * the nodeList used to be used for testing
     */
    private final List<Node> nodeList = new ArrayList<>();

    /**
     * Creates a new {@code FaultManagement} object.
     *
     * @param amosLoadGenerator the Amos load generator which contains the nodeList in the simulation
     * @throws IllegalArgumentException if {@code cmLoadGenerator} is {@code null}
     */
    public Amos(final AmosLoadGenerator amosLoadGenerator, final SystemStatus systemStatus) {
        if (amosLoadGenerator == null) {
            throw new IllegalArgumentException("amosLoadGenerator cannot be null");
        }
        nodeList.addAll(amosLoadGenerator.getAmosVerificationNodes());

        logger.info("Amos Using nodes {}", getUsedNodes(nodeList));

        initDownTimerHandler(systemStatus);
        initFirstTimeFail();
    }

    /**
     * @see EnmApplication#verify()
     */
    @Override
    public void verify() throws EnmException {
        if (!nodeList.isEmpty()) {
            String enmUser = "";
            String enmPassword = "";
            final Host enmHostObj;
            final String enmHost;
            final Random random = new Random(System.currentTimeMillis());
            final Node node = nodeList.get(random.nextInt(nodeList.size()));

            try {
                // ENM user must be admin or has sufficient privileges to run AMOS
                if(AdminUserUtils.getEnmFlag().equalsIgnoreCase("ha")) {
                    enmUser = AdminUserUtils.getUser();
                    enmPassword = AdminUserUtils.getPassword();
                } else if(AdminUserUtils.getEnmFlag().equalsIgnoreCase("enm")){
                    enmUser = HAPropertiesReader.getEnmUsername();
                    enmPassword = HAPropertiesReader.getEnmPassword();
                }
                if (HAPropertiesReader.isEnvCloudNative()) {
                    enmHostObj = new Host();
                    enmHostObj.setHostname(HAPropertiesReader.cloudIngressHost);
                    enmHost = HAPropertiesReader.cloudIngressHost;
                } else {
                    enmHostObj = HostConfigurator.getApache();
                    enmHost = enmHostObj.getIp();
                }
                logger.info("enmHost : {}", enmHost);
                final String nodeIP = node.getNetworkElement().getIp();

                final CustomHttpClient customHttpClient = CustomHttpClient.getConnectionManager();

                final AmosUiTask amosUitTask = new AmosUiTask(enmUser, enmPassword, customHttpClient.getHttpClient(false), enmHost, nodeIP, this);
                final Boolean callSucceeded = amosUitTask.call();

                if (callSucceeded) {
                    // All tests passed
                    stopAppDownTime();
                    stopAppTimeoutDownTime();
                    setFirstTimeFail(true);
                } else {
                    throw new EnmException("Exception in Amos Verify: ", EnmErrorType.APPLICATION);
                }
            } catch (final EnmSystemException e) {
                logger.error("Exception in Amos Verify: {}, error: {}", e.getMessage(), e);
                logger.error("AMOS login/logout has failed {}", e.getMessage());
            } catch (final Exception e) {
                logger.error("Exception in Amos Verify: {}, error: {}", e.getMessage(), e);
                throw new EnmException("Exception in Amos Verify: " + e.getMessage(), EnmErrorType.APPLICATION);
            }
        } else {
            logger.warn("Nothing to Verify, NODES are empty!");
        }

    }

    /**
     * Override as AMOS verifier handle login operation
     *
     * @see EnmApplication#login()
     */
    @Override
    public void login() {
        // do nothing because AMOS verifier handle login operation
    }

    /**
     * Override as AMOS verifier handle logout operation
     *
     * @see EnmApplication#logout()
     */
    @Override
    public void logout() {
        // do nothing because AMOS verifier handle logout operation
    }

}
