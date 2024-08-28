package com.ericsson.nms.rv.core.node;

import static com.ericsson.nms.rv.taf.tools.Constants.EMPTY_STRING;

import java.util.concurrent.TimeoutException;

import com.ericsson.nms.rv.core.util.HaTimeoutException;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.ericsson.cifwk.taf.handlers.netsim.domain.NetworkElement;
import com.ericsson.nms.rv.core.EnmApplication;
import com.ericsson.nms.rv.core.HAPropertiesReader;
import com.ericsson.nms.rv.core.cm.ConfigurationManagement;
import com.ericsson.nms.rv.core.host.NetSim;
import com.ericsson.nms.rv.core.netsimhandler.NodeInfo;
import com.ericsson.nms.rv.taf.tools.Constants.RegularExpressions;
import com.ericsson.oss.testware.availability.common.exception.EnmException;

/**
 * {@code Node} represents a network node.
 */
public abstract class Node {
    private static final String CMEDIT_CREATE = "cmedit create";
    /**
     * the command to create the {@code NetworkElement} MO
     */
    static final String CREATE_NETWORK_ELEMENT_COMMAND =
            CMEDIT_CREATE + " NetworkElement=%s networkElementId=%s,neType=\"%s\",platformType=\"%s\",ossModelIdentity=\"%s\", %s ossPrefix=\"%s\" -ns=OSS_NE_DEF -v=2.0.0";
    /**
     * the command to create the {@code ComConnectivityInformation} MO
     */
    static final String CREATE_CONNECTIVITY_INFORMATION_COMMAND =
            CMEDIT_CREATE + " NetworkElement=%s,%sConnectivityInformation=1 %sConnectivityInformationId=1,ipAddress=\"%s\",port=%s%s -ns=%s -v=1.0.0";
    private static final String CMEDIT_SET = "cmedit set ";
    private static final String CMEDIT_GET = "cmedit get ";
    private static final Logger logger = LogManager.getLogger(Node.class);

    private static final String SET_PM_FUNCTION_COMMAND = CMEDIT_SET + " NetworkElement=%s,PmFunction=1 pmEnabled=%b --force";

    /**
     * the format used to build the build the productVersion attribute
     */
    private static final String PRODUCT_VERSION_FORMAT = "[(revision=\"%s\",identity=\"%s\")]";

    private static final String CLEARED = " cleared";
    private final String ossPrefix;
    private final String ipAddress;
    private final String secureUserName;
    private final String secureUserPassword;
    private final boolean synchronize;
    private final NetSim netsim;
    private final EnmApplication enmApplication;
    private final NetworkElement networkElement;
    private String networkElementId;
    private String flags;
    private String ossModelIdentity;
    private String neProductVersion;
    private boolean verified;
    private boolean isNodeAdded;
    private boolean isSynchDone;

    Node(final NodeInfo nodeInfo) {
        netsim = nodeInfo.getNetsim();
        networkElementId = nodeInfo.getNetworkElementId();
        ossModelIdentity = nodeInfo.getOssModelIdentity() == null ? EMPTY_STRING : nodeInfo.getOssModelIdentity();
        setProductVersion(nodeInfo.getProductVersion());
        ossPrefix = nodeInfo.getOssPrefix() == null ? EMPTY_STRING : nodeInfo.getOssPrefix();
        ipAddress = nodeInfo.getIpAddress();
        secureUserName = nodeInfo.getSecureUserName();
        secureUserPassword = nodeInfo.getSecureUserPassword();
        flags = nodeInfo.getFlags();
        synchronize = flags.isEmpty() || flags.toLowerCase().contains("s");
        enmApplication = new ConfigurationManagement(true);
        networkElement = nodeInfo.getNetworkElement();
    }

    public void setIsNodeAdded(final boolean nodeAdded) {
        isNodeAdded = nodeAdded;
    }

    public boolean isNodeAdded() {
        return isNodeAdded;
    }

    public NetworkElement getNetworkElement() {
        return networkElement;
    }

    private void setProductVersion(final String productVersion) {

        String pv = productVersion;
        if (pv == null || pv.isEmpty()) {
            neProductVersion = EMPTY_STRING;
        } else {
            if (pv.startsWith("\"")) {
                pv = pv.substring(1, pv.length() - 1);
            }
            final String[] parts = pv.split(RegularExpressions.COMMA);
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid attribute 'neProductVersion': " + pv);
            }
            neProductVersion = String.format(PRODUCT_VERSION_FORMAT, parts[0], parts[1]);
        }
    }

    final EnmApplication getEnmApplication() {
        return enmApplication;
    }

    String getIpAddress() {
        return ipAddress;
    }

    public final String getNeProductVersion() {
        return neProductVersion;
    }

    public final String getOssPrefix() {
        return ossPrefix;
    }

    public final String getSecureUserName() {
        return secureUserName;
    }

    public final String getSecureUserPassword() {
        return secureUserPassword;
    }

    public final String getNetworkElementId() {
        return networkElementId;
    }

    public final String getFlags() {
        return flags;
    }

    public final void setFlags(final String flags) {
        this.flags = flags;
    }

    public final boolean isVerified() {
        return verified;
    }

    public final void setVerified() {
        verified = true;
    }

    public final NetSim getNetsim() {
        return netsim;
    }

    public final void setIsSynchDone(final boolean isSynchDone) {
        this.isSynchDone = isSynchDone;
    }

    public final boolean isSynchDone() {
        return isSynchDone;
    }

    public boolean getFmSupervisionStatus() throws EnmException, HaTimeoutException {
        boolean isEnabled = false;
        final String command = CMEDIT_GET + networkElementId + " FmAlarmSupervision.active";
        try {
            enmApplication.login();
            final String result = enmApplication.executeCliCommand(command);
            logger.info("result : {}", result);
            if(result.contains("true")) {
                isEnabled = true;
            }
        } finally {
            enmApplication.logout();
        }
        return isEnabled;
    }

    @Override
    public final String toString() {
        return "Node{" +
                "networkElementId='" + networkElementId + '\'' +
                ", verified=" + verified +
                '}';
    }

    public abstract String getNodeType();

    public final String getOssModelIdentity() {
        return ossModelIdentity;
    }

    public final void setOssModelIdentity(final String ossModelIdentity) {
        this.ossModelIdentity = ossModelIdentity;
    }

    /**
     * Sets or clears the {@code PmFunction} attribute.
     *
     * @param set {@code true} if the attribute shall be set
     * @throws EnmException     if failed to clear the {@code PmFunction} attribute
     * @throws TimeoutException if the command could not be executed within {@link HAPropertiesReader#getMaxDisruptionTime()}
     */
    public String setPmFunction(final boolean set) throws EnmException, HaTimeoutException {
        final String command = String.format(SET_PM_FUNCTION_COMMAND, networkElementId, set);
        try {
            enmApplication.login();
            final String result = enmApplication.executeCliCommand(command);
            logger.info("PmFunction MO of {} {}", networkElementId, set ? " set" : CLEARED);
            logger.info("Response of PN enmable command {}", result);
            return result;
        } finally {
            enmApplication.logout();
        }
    }
}
