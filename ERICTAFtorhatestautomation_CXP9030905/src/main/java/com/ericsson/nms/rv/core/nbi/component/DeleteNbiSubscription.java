package com.ericsson.nms.rv.core.nbi.component;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.ericsson.de.tools.cli.CliToolShell;
import com.ericsson.nms.rv.core.HAPropertiesReader;
import com.ericsson.nms.rv.core.nbi.NbiComponent;
import com.ericsson.nms.rv.core.nbi.NbiContext;
import com.ericsson.oss.testware.availability.common.exception.EnmException;

public  class DeleteNbiSubscription implements NbiComponent {

    private static final Logger logger = LogManager.getLogger(DeleteNbiSubscription.class);
    private static final String UNSUBSCRIBE = "./testclient.sh unsubscribe id %s nshost %s";

    private final CorbaNbiOperatorImpl corbaNbiOperatorImpl = new CorbaNbiOperatorImpl();

    @Override
    public void execute(final NbiContext context) throws EnmException {
        logger.info("unsubscribe operation");
        final String hostIp = (HAPropertiesReader.isEnvCloudNative())? NbiContext.getFmVipAddress() : context.getVisinamingPub();
        final String command = String.format(UNSUBSCRIBE, context.getSubscriptionId(), hostIp);
        logger.info("COMMAND: {}", command);

        String output = "";
        try {
            corbaNbiOperatorImpl.openConnection();
            output = corbaNbiOperatorImpl.executeNbiUnSubscribe(command, 10L);
            corbaNbiOperatorImpl.getShell().close();
            logger.info("unsubscribe command's output: {} ", output);
            context.getEnmApplication().stopAppDownTime();
            context.getEnmApplication().stopAppTimeoutDownTime();
        } catch (final InterruptedException e) {
            logger.error("Error in unsubscribe operation: {} , {}", output, e);
            context.getEnmApplication().stopAppDownTime();
            context.getEnmApplication().startAppTimeoutDowntime();
        } finally {
            if(corbaNbiOperatorImpl.getShell() != null) {
                corbaNbiOperatorImpl.getShell().close();
                logger.info("delete subscription shell connection closed");
            }
            context.getEnmApplication().sleep(2L);
            final CliToolShell shell = context.getShell();
            if (shell != null) {
                shell.close();
                logger.info("create subscription shell connection closed");
            }
        }
    }

    @Override
    public void execute(final NbiContext context,long threadId) throws EnmException {
        logger.info("ThreadId: {} unsubscribe operation", threadId);
        final String hostIp = (HAPropertiesReader.isEnvCloudNative())? NbiContext.getFmVipAddress() : context.getVisinamingPub();
        final String command = String.format(UNSUBSCRIBE, context.getSubscriptionId(), hostIp);
        logger.info("ThreadId: {} COMMAND: {}", threadId, command);

        String output = "";
        try {
            corbaNbiOperatorImpl.openConnection();
            output = corbaNbiOperatorImpl.executeNbiUnSubscribe(command, 10L);
            corbaNbiOperatorImpl.getShell().close();
            logger.info("ThreadId: {} unsubscribe command's output: {} ", threadId, output);
            context.getEnmApplication().stopAppDownTime();
            context.getEnmApplication().stopAppTimeoutDownTime();
        } catch (final InterruptedException e) {
            logger.error("ThreadId: {} Error in unsubscribe operation: {}, {}", threadId, e.getMessage(), output);
            context.getEnmApplication().stopAppDownTime();
            context.getEnmApplication().startAppTimeoutDowntime();
        } catch(final Exception e){
            logger.error("ThreadId: {} Error in  unsubscribe operation: {}, {}", threadId, e.getMessage(), output);
            context.getEnmApplication().startAppDownTime();
            context.getEnmApplication().stopAppTimeoutDownTime();
        } finally {
            if(corbaNbiOperatorImpl.getShell() != null) {
                corbaNbiOperatorImpl.getShell().close();
                logger.info("delete subscription shell connection closed");
            }
            context.getEnmApplication().sleep(2L);
            final CliToolShell shell = context.getShell();
            if (shell != null) {
                shell.close();
                logger.info("create subscription shell connection closed");
            }
        }
    }

}
