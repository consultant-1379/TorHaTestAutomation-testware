package com.ericsson.nms.rv.core.cm.bulk;

import static com.ericsson.nms.rv.core.cm.bulk.CmBulkExport.CMBE_BUSY;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.ericsson.cifwk.taf.tools.http.HttpResponse;
import com.ericsson.cifwk.taf.tools.http.constants.ContentType;
import com.ericsson.nms.rv.core.EnmApplication;
import com.ericsson.nms.rv.core.netsimhandler.NodeType;
import com.ericsson.nms.rv.core.util.HaTimeoutException;
import com.ericsson.oss.testware.availability.common.exception.EnmErrorType;
import com.ericsson.oss.testware.availability.common.exception.EnmException;

import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import net.minidev.json.JSONValue;

public interface CmBulk {
    Logger logger = LogManager.getLogger(CmBulk.class);

    String DELETE_CONFIG = "config delete %s";
    String LIST_ALL_CONFIG_OBJECTS = "config list";
    String CREATE_CONFIG = "config create %s";
    String FAILED_TO_EXECUTE_COMMAND = "Failed to execute command";

    default void prepare(final EnmApplication application, final String config) throws EnmException, HaTimeoutException {
        logger.info("Preparing {}...", application.getClass().getSimpleName());
        final String result = application.executeCliCommand(String.format(CREATE_CONFIG, config));
        logger.info("Preparing {} result... {}", application.getClass().getSimpleName(), result);
    }

    default void cleanup(final EnmApplication application, final String haTestConfig) throws EnmException {
        final List<String> configObjects = new CopyOnWriteArrayList<>();
        int exceptionCheck = 0;
        try {
            configObjects.addAll(getAllconfig(application, haTestConfig));
            logger.info("Config to delete : {}", configObjects);
        } catch (final EnmException | HaTimeoutException e) {
            logger.error("cleaning of copy files cannot be done due the list command has failed", e);
            throw new EnmException("cleaning of copy files cannot be done due the list command has failed");
        }

        for (final String configName : configObjects) {
            try {
                final String configObject = deleteCurrentConfigObject(application, configName);
                logger.info("delete's job Id {}", configObject);
                configObjects.remove(configObject);
            } catch (final EnmException | HaTimeoutException e) {
                exceptionCheck++;
                logger.error("delete {} Bulk Export object has failed {}", configName, e);
            }
        }
        if (exceptionCheck > 0) {
            logger.error("Failed to delete configs : {}", configObjects);
            throw new EnmException("Failed to delete config : {}" + configObjects);
        }
    }

    default String deleteCurrentConfigObject(final EnmApplication application, final String configName) throws EnmException, HaTimeoutException {
        final String body = application.executeCliCommand(String.format(DELETE_CONFIG, configName));
        return getJobIdFromBody(body);
    }

    default String getJobIdFromBody(final String body) {
        String jobIdFromBody = StringUtils.EMPTY;
        if (body != null && body.contains("job ID")) {
            final int jobIdIndex = body.indexOf("ID");
            jobIdFromBody = body.substring(jobIdIndex + 2, body.indexOf(',', jobIdIndex)).trim();
        }
        return jobIdFromBody;
    }

    default List<String> getAllconfig(final EnmApplication application, final String haTestConfig) throws EnmException, HaTimeoutException {
        final String body = application.executeCliCommand(LIST_ALL_CONFIG_OBJECTS);
        final List<String> configNameList = new ArrayList<>();

        if (body != null) {
            try {
                configNameList.addAll(((JSONArray) JSONValue.parse(body)).stream().filter(o -> o != null && o.toString().contains(haTestConfig)).map(Object::toString).collect(Collectors.toList()));
            } catch (final Exception e) {
                logger.warn("Failed to get AllconfigExport", e);
            }
        } else {
            logger.error("there are not config objects in the data base");
        }
        return configNameList;
    }

    default String getJobStatus(final EnmApplication application, final String command, final String jobId) throws EnmException, IllegalStateException {
        final String uri;
        final String internalErrorCode;
        if(StringUtils.equals(command, "importToLive")) {
            uri = String.format("/bulk-configuration/v1/import-jobs/jobs/%s", jobId);
        } else {
            uri = String.format("/bulk/%s/jobs/%s", command, jobId);
        }
        final HttpResponse response = application.getHttpRestServiceClient().sendGetRequest(
                EnmApplication.TIMEOUT_REST, ContentType.APPLICATION_JSON, new String[]{"ContentType", ContentType.APPLICATION_JSON}, null, null, uri);

        if(command.equalsIgnoreCase("export")) {
            try {
                internalErrorCode = ((JSONObject) JSONValue.parse(response.getBody())).getAsString("internalErrorCode");
            } catch (final Exception e) {
                if(response != null) {
                    logger.warn("Response : {}", response.getBody());
                }
                throw new EnmException("Failed to read response : " + e.getMessage(), EnmErrorType.APPLICATION);
            }
            if (internalErrorCode != null && Integer.parseInt(internalErrorCode) == 8027) {
                logger.info("Job status response code: {}", response.getResponseCode().getCode());
                logger.info("internalErrorCode: {}", internalErrorCode);
                return CMBE_BUSY;
            }
        }

        if (response != null) {
            application.analyzeResponse(FAILED_TO_EXECUTE_COMMAND, response);
            return ((JSONObject) JSONValue.parse(response.getBody())).getAsString("status");
        } else {
            logger.warn("For ActionType (POST) and action ({}) HttpResponse is NULL! {} failed", command, application.getClass().getSimpleName());
            return StringUtils.EMPTY;
        }
    }

    default List<String> getNodeSubNetwork(final EnmApplication app, final String node) throws EnmException {
        final String command = "cmedit get --node " + node;
        final List<String> subNetworkList = new ArrayList<>();
        final List<String> subNetworks;
        try {
            final String executionResult = app.executeCliCommand(command);
            logger.info("executionResult : {}", executionResult);
            subNetworks = Arrays.stream(executionResult.split(",")).filter(o -> o != null && o.contains("SubNetwork=")).collect(Collectors.toList());
            for (final String subnet : subNetworks) {
                subNetworkList.add(subnet.split("=")[1]);
            }
            logger.info("subNetworkList : {}", subNetworkList);
        } catch (final Exception e) {
            logger.warn("Error: " + e.getMessage());
            throw new EnmException(e.getMessage(), EnmErrorType.APPLICATION);
        }
        return subNetworkList;
    }

    default String getImportFileWriter(final List<String> subNetworkList, final String nodeId, final String nodeType) throws EnmException {
        final String resourceFile;
        String fileWriter = "";
        String line;
        try {
            if(subNetworkList.isEmpty()) {
                resourceFile = "data/cmb_import_file.vm";
            } else {
                resourceFile = "data/cmb_import_subnetwork" + subNetworkList.size() + "_file.vm";
            }
            logger.info("File used for import : {}", resourceFile);
            final InputStream input = getClass().getClassLoader().getResourceAsStream(resourceFile);
            if (input == null) {
                throw new EnmException("Template file doesn't exist", EnmErrorType.APPLICATION);
            }

            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(input));
            while( (line = bufferedReader.readLine()) != null ) {
                if (line.contains("MeContext") && nodeType.equalsIgnoreCase(NodeType.RADIO.getType())) {
                    continue;
                }
                fileWriter = fileWriter.concat(line).concat("\n");
            }
            if (!subNetworkList.isEmpty()) {
                int count = 0;
                for (final String subNetworkId : subNetworkList) {
                    count++;
                    fileWriter = fileWriter.replace("$subNetworkId" + count, subNetworkId);
                }
            }
            if (nodeType.equalsIgnoreCase(NodeType.RADIO.getType())) {
                fileWriter = fileWriter.replace("$managedElementId", nodeId);
            } else {
                fileWriter = fileWriter.replace("$nodeId", nodeId);
                fileWriter = fileWriter.replace("$managedElementId", "1");
            }
            logger.info("fileWriter : {}", fileWriter);
        } catch (final Exception e) {
            logger.warn("Failed to create import file : {}", e.getMessage());
            throw new EnmException(e.getMessage(), EnmErrorType.APPLICATION, e);
        }
        logger.info("Import fileWriter : {}", fileWriter);
        return fileWriter;
    }
}
