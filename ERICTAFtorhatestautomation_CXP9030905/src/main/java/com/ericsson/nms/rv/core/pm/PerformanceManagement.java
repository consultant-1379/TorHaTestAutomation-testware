/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2017
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/

package com.ericsson.nms.rv.core.pm;

import static com.ericsson.nms.rv.taf.tools.Constants.SUBSCRIPTION_NAME;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Date;
import java.util.NavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.stream.Collectors;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.ericsson.nms.rv.core.EnmApplication;
import com.ericsson.nms.rv.core.HAPropertiesReader;
import com.ericsson.nms.rv.core.cm.CmFullSync;
import com.ericsson.nms.rv.core.cm.ConfigurationManagement;
import com.ericsson.nms.rv.core.netex.NetworkExplorer;
import com.ericsson.nms.rv.core.netex.NetworkExplorerException;
import com.ericsson.nms.rv.core.netsimhandler.NodeType;
import com.ericsson.nms.rv.core.node.Node;
import com.ericsson.nms.rv.core.system.SystemStatus;
import com.ericsson.nms.rv.core.util.HaTimeoutException;
import com.ericsson.nms.rv.taf.tools.Constants;
import com.ericsson.oss.testware.availability.common.exception.EnmException;
import com.ericsson.sut.test.cases.HighAvailabilityTestCase;
import com.ericsson.sut.test.cases.util.FunctionalAreaKey;

import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import net.minidev.json.JSONValue;

/**
 * Allows to create, activate, deactivate, query and delete PM subscriptions.
 */
public final class PerformanceManagement extends EnmApplication {
    private static final Logger logger = LogManager.getLogger(PerformanceManagement.class);
    private static final String LOGIN_FAILED = "NetworkExplorer Login failed";
    private static final String LOGOUT_FAILED = "NetworkExplorer Logout failed";
    private static final List<Node> nodes = new ArrayList<>();
    private final NetworkExplorer networkExplorer;
    public JSONArray syncedManagedObjects;
    public static NavigableMap<Date, Date> pmIgnoreDTMap = new ConcurrentSkipListMap();


    /**
     * Creates a new {@code PerformanceManagement} object.
     *
     * @param pmLoadGenerator the CM load generator which contains the nodes in the simulation
     * @throws IllegalArgumentException if {@code pmLoadGenerator} is {@code null}
     */
    public PerformanceManagement(final PmLoadGenerator pmLoadGenerator, final SystemStatus systemStatus) {
        initDownTimerHandler(systemStatus);
        initFirstTimeFail();
        networkExplorer = new NetworkExplorer(false, systemStatus);
        if (pmLoadGenerator == null) {
            throw new IllegalArgumentException("pmLoadGenerator cannot be null");
        }
        nodes.addAll(pmLoadGenerator.getPmVerificationNodes());

        logger.info("PerformanceManagement Using nodes {}", getUsedNodes(nodes));
    }

    @Override
    public void prepare() throws EnmException {
        syncPmNodes(nodes);
        boolean isMoAttributesEmpty = false;
        nodes.forEach(node -> {
            try {
                node.setPmFunction(true);
            } catch (final EnmException | HaTimeoutException e) {
                logger.warn("Failed to prepare setPmFunction", e);
            }
        });
        for (int i = 0; i < 3; i++) {
            updateSyncedManagedObjects();
            if (!syncedManagedObjects.isEmpty()) {
                final JSONObject object = (JSONObject) syncedManagedObjects.get(0);
                final String ossModelIdentity = object.getAsString("ossModelIdentity");
                final String neType = object.getAsString("neType");
                if (!(ossModelIdentity != null && neType != null && !ossModelIdentity.isEmpty() && !neType.isEmpty())) {
                    logger.info("PM prepare ... ossModelIdentity : {}, neType : {}", ossModelIdentity, neType);
                    isMoAttributesEmpty = true;
                } else {
                    break;
                }
            }
            if ((syncedManagedObjects.isEmpty() || isMoAttributesEmpty)) {
                logger.info("Value of isMoAttributesEmpty in PM is : {}", isMoAttributesEmpty);
                if (i == 2) {
                    logger.info("PM prepare ... empty syncedMangedObjects or empty MO Attributes");
                    HighAvailabilityTestCase.multiMapErrorCollection.put(HAPropertiesReader.appMap.get("pm"), HAPropertiesReader.appMap.get("pm") + ", Error in updation of ManagedObject");
                    HighAvailabilityTestCase.appsFailedToStart.add(new FunctionalAreaKey(PerformanceManagement.class.getSimpleName(), true));
                    throw new EnmException("Failed in PM prepare !!");
                } else {
                    logger.info("Empty syncedManagedObjects or empty MO Attributes!!, Retrying...");
                }
            }
        }
    }

    @Override
    public void cleanup() throws EnmException {
        final List<String> cmeditArguments = new ArrayList<>(2);
        cmeditArguments.add("CelltraceSubscription.EventSubscriptionId");
        cmeditArguments.add("StatisticalSubscription.StatisticalSubscriptionId");
        for (final String argument : cmeditArguments) {
            final String getSubscriptionsCommand = String.format("cmedit get * %s==%s*", argument, SUBSCRIPTION_NAME);
            final String deleteSubscriptionsCommand = String.format("cmedit delete * %s==%s* -ALL --force", argument, SUBSCRIPTION_NAME);
            try {
                execute(getSubscriptionsCommand);
                execute(deleteSubscriptionsCommand);
                execute(getSubscriptionsCommand);
            } catch (final EnmException | HaTimeoutException exception) {
                logger.error("Failed to cleanup", exception);
                throw new EnmException("Failed to cleanup PM");
            }
        }
    }

    private void execute(final String executionCommand) throws EnmException, HaTimeoutException {
        logger.info("Execution command is {}", executionCommand);
        final String executionResult = executeCliCommand(executionCommand);
        logger.info("Execution result is {}", executionResult);
    }

    @Override
    public void verify()  {
        if(syncedManagedObjects.isEmpty()){
            logger.warn("Failed to verify PM, because list of MO is empty!");
        }else if(nodes.isEmpty()){
            logger.warn("Nothing to Verify, NODES are empty!");
        }else {
            PmThreadExecutor pmThread = new PmThreadExecutor(getHttpRestServiceClient(), this, generateUniqueSubscriptionName(), syncedManagedObjects, nodes);
            pmThread.execute();
            long startTime = System.nanoTime();
            logger.info("PM Thread: {} started.", pmThread.getID());
            do {
                this.sleep(1L);
                if (pmThread.isCompleted() || pmThread.isErrorOcurred() || pmThread.isTimedOut()) {
                    break;
                }
            }  while ((System.nanoTime()-startTime  < 120L * (long) Constants.TEN_EXP_9));
            if (pmThread.isErrorOcurred()) {
                logger.warn("Error occurred in PM {} ",pmThread.getID());
            } else if (pmThread.isTimedOut()) {
                logger.warn("PM Timed out. {} ",pmThread.getID());
            } else if (pmThread.isCompleted()) {
                logger.info("PM Thread: {} completed! ", pmThread.getID());
            }
        }
    }

    private String generateUniqueSubscriptionName() {
        final Random random = new Random(System.currentTimeMillis());
        return Constants.SUBSCRIPTION_NAME + String.format(" Testing %d", random.nextInt());
    }
    public void updateSyncedManagedObjects() {
        syncedManagedObjects = getManagedObjects();
        logger.info("PM SyncedManagedObjects : {}", syncedManagedObjects.toJSONString());
    }

    /**
     * Returns the managed objects as a JSON array.
     *
     * @throws EnmException if failed to login
     */
    private JSONArray getManagedObjects() {
        String response = null;
        try {
            networkExplorer.login();
            response = networkExplorer.queryManagedObjects(NetworkExplorer.ManagedObject.NETWORK_ELEMENT);
        } catch (final NetworkExplorerException ne) {
            logger.info("pm could not retrieve the managed objects from network explorer, ", ne);
        } catch (final EnmException e) {
            logger.warn(LOGIN_FAILED, e);
        } catch (final HaTimeoutException t) {
            logger.warn(t.getMessage(), t);
        }
        finally {
            try {
                networkExplorer.logout();
            } catch (final EnmException e) {
                logger.warn(LOGOUT_FAILED, e);
            }
        }
        // create the JSON array containing the MO's which will be in the subscription
        final JSONArray jsonArray = new JSONArray();
        if (response != null && !response.isEmpty()) {
            final Object parse = JSONValue.parse(response);
            if (parse instanceof JSONArray) {
                for (final Object o : (JSONArray) parse) {
                    final JSONObject jsonObject = (JSONObject) o;
                    final String mibRootName = (String) jsonObject.get("mibRootName");
                    PerformanceManagement.nodes.parallelStream().filter(node -> node.isVerified() && node.getNetworkElementId().equals(mibRootName)).sequential()
                            .forEach(node -> {
                                final JSONObject object = new JSONObject();
                                object.put("fdn", "NetworkElement=" + mibRootName);
                                object.put("neType", node.getNodeType().equalsIgnoreCase(NodeType.RADIO.getType()) ? "RadioNode" : node.getNodeType());
                                object.put("id", jsonObject.get("poId"));
                                object.put("ossModelIdentity", node.getOssModelIdentity());
                                jsonArray.add(object);
                            });
                }
            }
        } else {
            logger.warn("Network Explorer returned NULL or empty string, response:{}", response);
        }
        return jsonArray;
    }

    @SuppressWarnings("unused")
    private enum AdministrationState {
        ACTIVE, ACTIVATING, INACTIVE, DEACTIVATING, UPDATING, SCHEDULED
    }

    @SuppressWarnings("unused")
    private enum TaskStatus {
        ERROR, OK, NA
    }

    private void syncPmNodes(final List<Node> listNodes) {
        final String command = "cmedit get * networkelement.(networkElementId==%s) networkelement.ossModelIdentity";
        final ConfigurationManagement cm = new ConfigurationManagement(true);
        listNodes.parallelStream().forEach(node -> {
            final CmFullSync cmFullSync = new CmFullSync();
            if (!node.isSynchDone()) {
                final String networkElementId = node.getNetworkElementId();
                try {
                    cmFullSync.cmSyncNode(networkElementId);
                    if (!cmFullSync.waitForStatusToComplete(networkElementId)) {
                        logger.warn("PM NetworkElement: {} could not sync correctly: ", networkElementId);
                    }
                } catch (final Exception e) {
                    logger.warn("Failed cmSyncNodes for PM: {}", networkElementId, e);
                }
            }
            try {
                cm.login();
                final String cliCommand = cm.executeCliCommand(String.format(command, node.getNetworkElementId()));
                try {
                    final String ossModelIdentity = ((JSONArray) (JSONValue.parse(cliCommand))).stream().filter(o -> o != null && o.toString().contains("ossModelIdentity : ")).collect(Collectors.toList()).get(0).toString().split(" : ")[1];
                    node.setOssModelIdentity(ossModelIdentity);
                    logger.info("Set OssModelIdentity : {}", ossModelIdentity);
                } catch (final Exception e) {
                    logger.warn("Failed to set OssModelIdentity", e);
                }
                logger.info(cliCommand);
            } catch (final Exception e) {
                logger.warn("Failed to update PM nodes with OssModelIdentity", e);
            } finally {
                try {
                    cm.logout();
                } catch (final EnmException e) {
                    logger.warn("Failed to logout in update PM nodes with OssModelIdentity", e);
                }
            }
        });
    }
}
