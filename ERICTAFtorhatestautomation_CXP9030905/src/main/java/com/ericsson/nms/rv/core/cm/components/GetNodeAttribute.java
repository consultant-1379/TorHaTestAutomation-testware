package com.ericsson.nms.rv.core.cm.components;

import static com.ericsson.nms.rv.taf.tools.Constants.EMPTY_STRING;

import com.ericsson.nms.rv.core.cm.CmThreadExecutor;
import com.ericsson.nms.rv.core.util.HaTimeoutException;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.ericsson.nms.rv.core.EnmApplication;
import com.ericsson.nms.rv.core.cm.CmComponent;
import com.ericsson.nms.rv.core.cm.CmContext;
import com.ericsson.oss.testware.availability.common.exception.EnmErrorType;
import com.ericsson.oss.testware.availability.common.exception.EnmException;

public class GetNodeAttribute implements CmComponent {

    private static final Logger logger = LogManager.getLogger(GetNodeAttribute.class);

    @Override
    public void execute(final CmContext context, CmThreadExecutor cmThreadExecutor) throws EnmException, HaTimeoutException {

        final EnmApplication enmApplication = context.getEnmApplication();
        final String json = getUserLabel(enmApplication, context.getCmNodes().get(0).getNetworkElementId());
        logger.info("getting user label of the node");

        if (json != null && json.contains(context.getUserLabel())) {
            enmApplication.stopAppDownTime();
            enmApplication.stopAppTimeoutDownTime();
        } else {
            logger.error("userLabel does not match expected {} received in json {} ", context.getUserLabel(), json);
            throw new EnmException("userLabel does not match expected " + context.getUserLabel() + " received in json " + json, EnmErrorType.APPLICATION);
        }

    }

    private String getUserLabel(final EnmApplication enmApplication, final String ne) throws EnmException, HaTimeoutException {
        final String getUserLabel = "cmedit  get %s LoadModule.userLabel";
        final String command = String.format(getUserLabel, ne);
        String json = EMPTY_STRING;
        try {
            json = enmApplication.executeCliCommand(command);

        } catch (final HaTimeoutException e) {
            logger.error("failed to execute command {} due a TimeoutException, json {}", command, json, e);
            throw new HaTimeoutException("failed to execute command " + command + " due a TimeoutException, json " + json + ": " + e.getMessage(), EnmErrorType.APPLICATION);
        }

        return json;
    }

}
