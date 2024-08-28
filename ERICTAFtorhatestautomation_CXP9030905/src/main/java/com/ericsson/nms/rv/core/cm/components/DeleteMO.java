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

public class DeleteMO implements CmComponent {


    private static final Logger logger = LogManager.getLogger(DeleteMO.class);

    @Override
    public void execute(final CmContext context, CmThreadExecutor cmThreadExecutor) throws EnmException, HaTimeoutException {
        final String contextFdn = context.getFdn();
        logger.info("executing deletion of the node attribute with fdn: {}", contextFdn);

        final EnmApplication enmApplication = context.getEnmApplication();
        if (executeDeleteMo(enmApplication, contextFdn)) {
            enmApplication.stopAppDownTime();
            enmApplication.stopAppTimeoutDownTime();
        } else {
            logger.error("Failed to DeleteMo, fdn {} ", contextFdn);
            throw new EnmException("failed to delete Mo fdn: " + contextFdn, EnmErrorType.APPLICATION);

        }
    }

    private boolean executeDeleteMo(final EnmApplication enmApplication, final String fdn) throws EnmException, HaTimeoutException {

        final String deleteCommand = "cmedit delete %s --force";
        final String command = String.format(deleteCommand, fdn);
        final String oneInstanceDeleted = "1 instance(s) deleted";
        String json = EMPTY_STRING;
        try {
            json = enmApplication.executeCliCommand(command);
            if (json != null && json.contains(oneInstanceDeleted)) {
                logger.debug("command {DELETE_COMMAND} executed successfully, json {} ", command, json);
                return true;
            }

        } catch (final HaTimeoutException e) {
            logger.error("failed to execute command {} due a TimeoutException, json {}", deleteCommand, json, e);
            throw new HaTimeoutException("failed to execute command " + deleteCommand + " due a TimeoutException, json " + json + ": " + e.getMessage(), EnmErrorType.APPLICATION);
        }
        return false;
    }
}
