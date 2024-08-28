package com.ericsson.nms.rv.core.cm.components;

import static com.ericsson.nms.rv.taf.tools.Constants.EMPTY_STRING;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.ericsson.nms.rv.core.cm.CmThreadExecutor;
import com.ericsson.nms.rv.core.util.HaTimeoutException;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.ericsson.nms.rv.core.EnmApplication;
import com.ericsson.nms.rv.core.cm.CmComponent;
import com.ericsson.nms.rv.core.cm.CmContext;
import com.ericsson.oss.testware.availability.common.exception.EnmErrorType;
import com.ericsson.oss.testware.availability.common.exception.EnmException;

public class CmVerifySync implements CmComponent {
    private static final Logger logger = LogManager.getLogger(CmVerifySync.class);

    @Override
    public void execute(final CmContext context, CmThreadExecutor cmThreadExecutor) throws EnmException, HaTimeoutException {
        logger.info("executing sync node verification");
        final String json = executeGetAllCmFunctions(context.getEnmApplication());
        final String nodesSynsched = getNumberOfNodesOnSync(json);

        final int nodesOnSync = Integer.parseInt(nodesSynsched);

        final int numberOfNodesOnSync = context.getNumberOfNodesOnSync();
        if (nodesOnSync != numberOfNodesOnSync) {
            logger.error("Nodes that are synched before upgrade, " +
                    "do not match during upgrade, it can be an issue on the system, " +
                    "current nodes on the DB {}," +
                    " nodes on sync before upgrade: {}", nodesOnSync, numberOfNodesOnSync);
        }
    }

    // TODO separate CM sync nodes from another (eg. CMCU, CMBE)
    public String executeGetAllCmFunctions(final EnmApplication enmApplication) throws EnmException, HaTimeoutException {
        final String getAllCmFunctionCommand = "cmedit get * CmFunction.syncStatus==SYNCHRONIZED";
        String json = EMPTY_STRING;
        try {
            json = enmApplication.executeCliCommand(getAllCmFunctionCommand);
            enmApplication.stopAppDownTime();
            enmApplication.stopAppTimeoutDownTime();
        } catch (final HaTimeoutException e) {
            logger.error("failed to execute command {} due a TimeoutException, json {}", getAllCmFunctionCommand, json, e);
            throw new HaTimeoutException("failed to execute command " + getAllCmFunctionCommand + "due a TimeoutException, json " + json + ": " + e.getMessage(), EnmErrorType.APPLICATION);
        }
        return json;
    }

    public String getNumberOfNodesOnSync(final String json) {
        if (json == null) {
            throw new IllegalArgumentException("json cannot be null getNumberOfNodesOnSync, " +
                    "ENM can be not responding to verify " +
                    "the amounts of nodes on sync");
        }

        if (json.isEmpty() || !json.contains("instance(s)")) {
            logger.error("no nodes on sync were found on DB json {}", json);
            return "0";
        }

        final int indexOfInstances = json.indexOf("instance(s)");
        final String sub1 = json.substring(0, indexOfInstances);

        final Pattern pattern = Pattern.compile("\\d* $");
        final Matcher m = pattern.matcher(sub1);

        while (m.find()) {
            return m.group().trim();
        }

        logger.error("patter {} was not found on the json {} ", "'instance(s) and d* $'", json);
        return "-1";
    }

}
