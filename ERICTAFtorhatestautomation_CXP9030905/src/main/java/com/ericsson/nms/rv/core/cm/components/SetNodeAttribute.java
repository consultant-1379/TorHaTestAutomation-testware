package com.ericsson.nms.rv.core.cm.components;

import static com.ericsson.nms.rv.taf.tools.Constants.EMPTY_STRING;

import java.util.Random;

import com.ericsson.nms.rv.core.cm.CmThreadExecutor;
import com.ericsson.nms.rv.core.util.HaTimeoutException;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.ericsson.nms.rv.core.EnmApplication;
import com.ericsson.nms.rv.core.cm.CmComponent;
import com.ericsson.nms.rv.core.cm.CmContext;
import com.ericsson.oss.testware.availability.common.exception.EnmErrorType;
import com.ericsson.oss.testware.availability.common.exception.EnmException;

public class SetNodeAttribute implements CmComponent {

    private static final Logger logger = LogManager.getLogger(SetNodeAttribute.class);

    @Override
    public void execute(final CmContext context, CmThreadExecutor cmThreadExecutor) throws EnmException {
        final Random random = new Random(System.currentTimeMillis());
        final String haUserLabel = "HaUserLabel" + random.nextInt(10000);

        final String contextFdn = context.getFdn();
        logger.info("setting the userLabel of the node userLabel: {}, fdn: {}", haUserLabel, contextFdn);
        final EnmApplication enmApplication = context.getEnmApplication();
        if (setUserLabel(enmApplication, haUserLabel, contextFdn)) {
            enmApplication.stopAppDownTime();
            enmApplication.stopAppTimeoutDownTime();
        } else {
            throw new EnmException("failed to setUserLabel to fdn: " + contextFdn, EnmErrorType.APPLICATION);
        }
        context.setUserLabel(haUserLabel);
    }

    private boolean setUserLabel(final EnmApplication enmApplication, final String userLabel, final String fdn) throws EnmException {

        final String setAttribute = "cmedit set %s userLabel=%s";
        final String oneInstanceUpdated = "1 instance(s) updated";

        final String command = String.format(setAttribute, fdn, userLabel);
        String json = EMPTY_STRING;

        try {
            json = enmApplication.executeCliCommand(command);
            if (json != null && json.contains(oneInstanceUpdated)) {
                logger.info("Setting new userLabel success. Command {} json {}", command, json);
                return true;
            } else {
                logger.error("Setting new userLabel has failed. Failed to execute command: {}, json: {}", command, json);
                throw new EnmException("Setting new userLabel has failed. Failed to execute command: " + command + ", json: " + json, EnmErrorType.APPLICATION);
            }

        } catch (final HaTimeoutException e) {
            logger.error("failed to execute command {} due a TimeoutException, json {}", command, json, e);
        }
        return false;
    }

}
