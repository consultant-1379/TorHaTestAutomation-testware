package com.ericsson.nms.rv.core.nbi.component;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.ericsson.nms.rv.core.HAPropertiesReader;
import com.ericsson.nms.rv.core.nbi.AlarmParser;
import com.ericsson.nms.rv.core.nbi.NbiComponent;
import com.ericsson.nms.rv.core.nbi.NbiContext;
import com.ericsson.nms.rv.core.nbi.error.NbiException;
import com.ericsson.nms.rv.core.nbi.verifier.NbiAlarmVerifier;
import com.ericsson.nms.rv.core.nbi.verifier.NbiCreateSubsVerifier;
import com.ericsson.oss.testware.availability.common.exception.EnmException;


public class CreateNbiSubscription implements NbiComponent {

    private static final Logger logger = LogManager.getLogger(CreateNbiSubscription.class);
    private final AlarmParser alarmParser = new AlarmParser();
    private String CREATE_SUBSCRIPTION = "./testclient.sh subscribe category 1f1 filter \"'%s'~\\$f\" nshost %s";
    private boolean isCallFromNbivs = false;

    public CreateNbiSubscription(final long keepAliveInMinutes) {
        if (keepAliveInMinutes != 0) {
            CREATE_SUBSCRIPTION += " subscriptionTime " + keepAliveInMinutes;
        } else {
            logger.info("subscription time was 0, default one day");
            CREATE_SUBSCRIPTION += " subscriptionTime " + 1440;
        }
    }

    public CreateNbiSubscription() {
        isCallFromNbivs = true;
    }

    @Override
    public void execute(final NbiContext context) throws EnmException {
        try {

            final String hostIp = (HAPropertiesReader.isEnvCloudNative())? NbiContext.getFmVipAddress() : context.getVisinamingPub();
            final String command = String.format(CREATE_SUBSCRIPTION, context.getNbiNodes().get(0).getNetworkElementId(), hostIp);
            logger.info("COMMAND: {}", command);

            context.getCorbaNbiOperatorImpl().openConnection();
            final String output = context.getCorbaNbiOperatorImpl().executeNbiSubscription(command, 35);
            logger.info("cli's output: {}", output);
            String subscriptionId = StringUtils.EMPTY;
            if (!output.isEmpty()) {
                subscriptionId = alarmParser.getSubscriptionId(output);
                if (!subscriptionId.isEmpty()) {
                    context.getEnmApplication().stopAppDownTime();
                    context.getEnmApplication().stopAppTimeoutDownTime();
                    context.setSubscriptionId(subscriptionId);
                } else {
                    throw new EnmException("Failed to create subscription-failed. Subscription id is Empty.");
                }
            } else {
                throw new EnmException("Failed to create subscription-failed. Command output is Empty");
            }
            logger.info("new implementation subscriptionId: {}", subscriptionId);

        } catch (final NbiException e) {
            logger.error("subscription could not be created, possible cause:  connection problems, {}", e.getMessage());
            if (isCallFromNbivs && NbiCreateSubsVerifier.isInitiated()) {
                context.getEnmApplication().stopAppTimeoutDownTime();
                context.getEnmApplication().startAppDownTime();
            } else if (!isCallFromNbivs && NbiAlarmVerifier.isInitiated()) {
                context.getEnmApplication().stopAppTimeoutDownTime();
                context.getEnmApplication().startAppDownTime();
            }
            throw new EnmException("NBI create subscription-failed: ", e);
        } catch (final InterruptedException ex) {
            logger.error("subscription could not be created, possible cause:  connection Timeout. " + ex.getMessage());
            if (isCallFromNbivs && NbiCreateSubsVerifier.isInitiated()) {
                context.getEnmApplication().stopAppDownTime();
                context.getEnmApplication().startAppTimeoutDowntime();
            } else if (!isCallFromNbivs && NbiAlarmVerifier.isInitiated()) {
                context.getEnmApplication().stopAppDownTime();
                context.getEnmApplication().startAppTimeoutDowntime();
            }
            throw new EnmException("NBI create subscription-failed .. has timeout : ", ex);
        }
    }

    @Override
    public void execute(final NbiContext context, long threadId) throws EnmException {
        try {
            final String hostIp = (HAPropertiesReader.isEnvCloudNative())? NbiContext.getFmVipAddress() : context.getVisinamingPub();
            final String command = String.format(CREATE_SUBSCRIPTION, context.getNbiNodes().get(0).getNetworkElementId(), hostIp);
            logger.info("ThreadId: {} COMMAND: {}", threadId, command);

            context.getCorbaNbiOperatorImpl().openConnection();
            final String output = context.getCorbaNbiOperatorImpl().executeNbiSubscription(command, 35);
            logger.info("ThreadId: {} subscribe command's output: {}", threadId, output);
            String subscriptionId = StringUtils.EMPTY;
            if (!output.isEmpty()) {
                subscriptionId = alarmParser.getSubscriptionId(output);
                if (!subscriptionId.isEmpty()) {
                    context.getEnmApplication().stopAppDownTime();
                    context.getEnmApplication().stopAppTimeoutDownTime();
                    context.setSubscriptionId(subscriptionId);
                } else {
                    throw new EnmException("Failed to create subscription. Subscription id is Empty.");
                }
            } else {
                throw new EnmException("Failed to create subscription. Command output is Empty");
            }
            logger.info("ThreadId: {} new implementation subscriptionId: {}", threadId, subscriptionId);

        } catch (final NbiException e) {
            logger.error("ThreadId: {} subscription could not be created, possible cause:  connection problems, {}", threadId, e.getMessage());
            context.getEnmApplication().stopAppTimeoutDownTime();
            context.getEnmApplication().startAppDownTime();
            throw new EnmException("NBI create subscription has failed: ", e);
        } catch (final InterruptedException ex) {
            logger.error("ThreadId: {} subscription could not be created, possible cause:  connection Timeout. " + ex.getMessage(), threadId);
            context.getEnmApplication().stopAppDownTime();
            context.getEnmApplication().startAppTimeoutDowntime();
        }
    }
}
