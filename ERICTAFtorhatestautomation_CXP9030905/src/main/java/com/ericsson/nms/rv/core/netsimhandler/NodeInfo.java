package com.ericsson.nms.rv.core.netsimhandler;

import com.ericsson.cifwk.taf.handlers.netsim.domain.NetworkElement;
import com.ericsson.nms.rv.core.host.NetSim;

/**
 * Information of the node to be used by test cases
 */
public class NodeInfo {

    private NetworkElement networkElement;
    private NetSim netsim;
    private String networkElementId;
    private String ossModelIdentity;
    private String ossPrefix;
    private String ipAddress;
    private String secureUserName;
    private String secureUserPassword;
    private String flags;
    private String neType;
    private String productVersion;
    private boolean isSync;
    private boolean pmEnabled;
    private boolean fmEnabled;
    private boolean shmEnabled;
    private boolean cmEnabled;

    public NodeInfo() {
    }

    public NodeInfo(final NodeInfo nodeInfo) {
        this();
        if (nodeInfo != null) {
            networkElement = nodeInfo.getNetworkElement();
            netsim = nodeInfo.getNetsim();
            networkElementId = nodeInfo.getNetworkElementId();
            ossModelIdentity = nodeInfo.getOssModelIdentity();
            ossPrefix = nodeInfo.getOssPrefix();
            ipAddress = nodeInfo.getIpAddress();
            secureUserName = nodeInfo.getSecureUserName();
            secureUserPassword = nodeInfo.getSecureUserPassword();
            flags = nodeInfo.getFlags();
            neType = nodeInfo.getNeType();
            pmEnabled = nodeInfo.isPmEnabled();
            productVersion = nodeInfo.getProductVersion();
        }
    }

    public NetworkElement getNetworkElement() {
        return networkElement;
    }

    public void setNetworkElement(final NetworkElement networkElement) {
        this.networkElement = networkElement;
    }

    public String getProductVersion() {
        return productVersion;
    }

    public void setProductVersion(final String productVersion) {
        this.productVersion = productVersion;
    }

    public String getNeType() {
        return neType;
    }

    public void setNeType(final String neType) {
        this.neType = neType;
    }

    public NetSim getNetsim() {
        return netsim;
    }

    public void setNetsim(final NetSim netsim) {
        this.netsim = netsim;
    }

    public String getNetworkElementId() {
        return networkElementId;
    }

    public void setNetworkElementId(final String networkElementId) {
        this.networkElementId = networkElementId;
    }

    public String getOssModelIdentity() {
        return ossModelIdentity;
    }

    public void setOssModelIdentity(final String ossModelIdentity) {
        this.ossModelIdentity = ossModelIdentity;
    }

    public String getOssPrefix() {
        return ossPrefix;
    }

    public void setOssPrefix(final String ossPrefix) {
        this.ossPrefix = ossPrefix;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(final String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getSecureUserName() {
        return secureUserName;
    }

    public void setSecureUserName(final String secureUserName) {
        this.secureUserName = secureUserName;
    }

    public String getSecureUserPassword() {
        return secureUserPassword;
    }

    public void setSecureUserPassword(final String secureUserPassword) {
        this.secureUserPassword = secureUserPassword;
    }

    public String getFlags() {
        return flags;
    }

    public void setFlags(final String flags) {
        this.flags = flags;
    }

    public boolean isSyncNode() {
        return isSync;
    }

    public void setSyncNode(final boolean isSync) {
        this.isSync = isSync;
    }

    public boolean isPmEnabled() { return pmEnabled; }

    public void setPmEnabled(final boolean pmEnabled) { this.pmEnabled = pmEnabled; }

    public boolean isFmEnabled() { return fmEnabled; }

    public void setFmEnabled(final boolean fmEnabled) { this.fmEnabled = fmEnabled; }

    public boolean isShmEnabled() { return shmEnabled; }

    public void setShmEnabled(final boolean shmEnabled) { this.shmEnabled = shmEnabled; }

    public boolean isCmEnabled() { return cmEnabled; }

    public void setCmEnabled(final boolean cmEnabled) { this.cmEnabled = cmEnabled; }
}
