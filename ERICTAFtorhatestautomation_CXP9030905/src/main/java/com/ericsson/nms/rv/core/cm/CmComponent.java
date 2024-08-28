package com.ericsson.nms.rv.core.cm;

import static com.ericsson.nms.rv.taf.tools.Constants.EMPTY_STRING;

import com.ericsson.nms.rv.core.util.HaTimeoutException;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.ericsson.nms.rv.core.EnmApplication;
import com.ericsson.oss.testware.availability.common.exception.EnmErrorType;
import com.ericsson.oss.testware.availability.common.exception.EnmException;

@FunctionalInterface
public interface CmComponent {
    Logger logger = LogManager.getLogger(CmComponent.class);

    String GETTING_OF_THE_NODE = "Getting '{}' of the node {}";
    String CMEDIT_GET_S_NETWORKELEMENT = "cmedit get %s networkelement.%s";

    void execute(final CmContext context,final CmThreadExecutor cmThreadExecutor) throws EnmException, HaTimeoutException;

    default void runCommand(final CmContext context, final String property, CmThreadExecutor cmThreadExecutor) throws EnmException, HaTimeoutException {
      logger.info("property {}" ,property);
        final String failedToExecuteCommand = "Failed to execute command %s, %s";
        final String oneInstance = "1 instance(s)";
        final String message = "message '" + oneInstance + "' was not found.";
        final String timeoutExceptionJson = "TimeoutException, json: ";

        final String networkElementId = context.getCmNodes().get(0).getNetworkElementId();
        final String command = String.format(CMEDIT_GET_S_NETWORKELEMENT, networkElementId, property);
        final EnmApplication enmApplication = context.getEnmApplication();

        logger.info(GETTING_OF_THE_NODE, property, networkElementId);

        String json = EMPTY_STRING;
        try {
            json = cmThreadExecutor.executeCmCliCommand(command, property);
        } catch (final HaTimeoutException e) {
            throw new HaTimeoutException(String.format(enmApplication.CLI_COMMAND_FAILED, command), EnmErrorType.APPLICATION);
        }
        logger.info("command : {}, output json : {}", command, json);
        if (json != null && json.contains(oneInstance)) {
            enmApplication.stopAppDownTime();
            enmApplication.stopAppTimeoutDownTime();
            enmApplication.setFirstTimeFail(true);
        } else if (json != null && (json.contains("Error 6013") || json.contains("errorCode:6013"))) {
            logger.warn("Database is unavailable !!");
            logger.warn("json : {}", json);
            enmApplication.stopAppDownTime();
            enmApplication.stopAppTimeoutDownTime();
            enmApplication.setFirstTimeFail(true);
        } else {
            logger.warn(message);
            enmApplication.stopAppTimeoutDownTime();
            if (enmApplication.isFirstTimeFail()) {
                enmApplication.setFirstTimeFail(false);
            } else {
                enmApplication.startAppDownTime();
            }
            throw new EnmException(String.format(failedToExecuteCommand, command, message + ", json: " + json), EnmErrorType.APPLICATION);
        }
    }
}
