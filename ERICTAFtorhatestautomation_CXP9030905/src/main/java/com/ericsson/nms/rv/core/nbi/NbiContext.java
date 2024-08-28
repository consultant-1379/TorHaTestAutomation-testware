package com.ericsson.nms.rv.core.nbi;

import java.util.List;

import com.ericsson.de.tools.cli.CliToolShell;
import com.ericsson.nms.rv.core.EnmApplication;
import com.ericsson.nms.rv.core.HAPropertiesReader;
import com.ericsson.nms.rv.core.nbi.component.CorbaNbiOperatorImpl;
import com.ericsson.nms.rv.core.node.Node;
import com.ericsson.nms.rv.taf.tools.Constants;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class NbiContext {

    private static final Logger logger = LogManager.getLogger(NbiContext.class);
    private final String visinamingnbPub = HAPropertiesReader.getVisinamingnb();
    private static String fmVipAddress = HAPropertiesReader.getFmVipAddress();
    private final List<Node> nbiNodesList;
    private final EnmApplication enmApplication;
    private CorbaNbiOperatorImpl corbaNbiOperatorImpl = new CorbaNbiOperatorImpl();
    private String haProblem = "";
    private String subscriptionId = "";
    private long timeToWait;
    private boolean isAlarmReceived;

    NbiContext(final List<Node> nbiNodesList, final EnmApplication enmApplication) {
        this.nbiNodesList = nbiNodesList;
        timeToWait = HAPropertiesReader.getFmWaitingTimePerAlarm() * 4 * Constants.TEN_EXP_9;
        this.enmApplication = enmApplication;
    }

    public CliToolShell getShell() {
        return this.corbaNbiOperatorImpl.getShell();
    }

    public static void setFmVipAddress(final String address) {
        logger.info("Setting fm_vip_address to : {}", address);
        fmVipAddress = address;
    }

    public EnmApplication getEnmApplication() {
        return enmApplication;
    }

    public List<Node> getNbiNodes() {
        return nbiNodesList;
    }

    public void setHaProblem(final String problem) {
        haProblem = problem;
    }

    public String getAlarmParoblem() {
        return haProblem;
    }


    public CorbaNbiOperatorImpl getCorbaNbiOperatorImpl() {
        return corbaNbiOperatorImpl;
    }

    public void setCorbaNbiOperatorImpl(final CorbaNbiOperatorImpl corbaNbiOperatorImpl) {
        this.corbaNbiOperatorImpl = corbaNbiOperatorImpl;
    }

    public String getVisinamingPub() {
        return visinamingnbPub;
    }

    public static String getFmVipAddress() {
        return fmVipAddress;
    }

    public String getSubscriptionId() {
        return subscriptionId;
    }

    public void setSubscriptionId(final String subscriptionId) {
        this.subscriptionId = subscriptionId;
    }

    public long getTimeToWait() {
        return timeToWait;
    }

    public void setIsAlarmReceived(final boolean isAlarmReceived) {
        this.isAlarmReceived = isAlarmReceived;
    }

    public boolean isAlarmReceived() {
        return isAlarmReceived;
    }
}