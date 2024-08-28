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

public class DescribeManagedElement implements CmComponent {

    private static final Logger logger = LogManager.getLogger(DescribeManagedElement.class);

    @Override
    public void execute(final CmContext context, CmThreadExecutor cmThreadExecutor) throws EnmException, HaTimeoutException {

        final String describemangedelement = "cmedit describe ManagedElement -t";
        final String descriptionExcepted = "This MO is the top MO";
        String json = EMPTY_STRING;
        logger.info("getting the description of the ManagedElement");

        try {
            final EnmApplication enmApplication = context.getEnmApplication();
            json = enmApplication.executeCliCommand(describemangedelement);

            if (json.contains(descriptionExcepted)) {
                enmApplication.stopAppDownTime();
                enmApplication.stopAppTimeoutDownTime();
            } else {
                logger.error("Description does not found on the system, " +
                        "possible error: model cannot be accessed or description was changed, expected: {} " +
                        "json's response: {}", descriptionExcepted, json);
                throw new EnmException("Description does not found on the system, possible error: model cannot be accessed or description was changed, expected: " + descriptionExcepted + "json's response: " + json,
                        EnmErrorType.APPLICATION);
            }
        } catch (final HaTimeoutException e) {
            logger.error("failed to execute command {} due a TimeoutException, json {}", descriptionExcepted, json, e);
            throw new HaTimeoutException("failed to execute command " + descriptionExcepted + " due a TimeoutException, json " + json + ": " + e.getMessage(), EnmErrorType.APPLICATION);
        }
    }
}
