package com.ericsson.nms.rv.core.cm.components;

import java.util.Random;

import com.ericsson.nms.rv.core.cm.CmThreadExecutor;
import com.ericsson.nms.rv.core.util.HaTimeoutException;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.ericsson.nms.rv.core.EnmApplication;
import com.ericsson.nms.rv.core.cm.CmComponent;
import com.ericsson.nms.rv.core.cm.CmContext;
import com.ericsson.nms.rv.core.node.Node;
import com.ericsson.oss.testware.availability.common.exception.EnmErrorType;
import com.ericsson.oss.testware.availability.common.exception.EnmException;

import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import net.minidev.json.JSONValue;

public class CreateNodeAttribute implements CmComponent {

    private static final Logger logger = LogManager.getLogger(CreateNodeAttribute.class);

    @Override
    public void execute(final CmContext context, CmThreadExecutor cmThreadExecutor) throws EnmException, HaTimeoutException {
        final Random random = new Random(System.currentTimeMillis());
        final String moduleId = "HAModuleId" + random.nextInt(10000);
        final EnmApplication enmApplication = context.getEnmApplication();
        final String fdn = getAnyLoadModuleMo(enmApplication, context.getCmNodes().get(0));
        logger.info("executing creation of the node attribute with fdn: {}", fdn);

        if (fdn == null || "FDN".equals(fdn)) {
            throw new EnmException("FDN could not be found, Create Node Attr could not be done," +
                    "next components cannot be executed.", EnmErrorType.APPLICATION);
        }

        final String fdnNoLoadModule = fdn.substring(0, fdn.indexOf(",LoadModule="));

        final String newFdn = fdnNoLoadModule + ",LoadModule=" + moduleId;
        final String json = createLoadModuleMo(enmApplication, newFdn, moduleId);

        logger.info("fdn created: {}", newFdn);

        if (json.contains("1 instance(s) updated")) {
            enmApplication.stopAppDownTime();
        } else {
            logger.error("Creation of Attribute has failed, message \"1 instance(s) updated\" was not found as a result of the command, {}", json);
            throw new EnmException("FDN could not be found, Create Node Attr could not be done," +
                    "next components cannot be executed.", EnmErrorType.APPLICATION);
        }
        context.setFdn(newFdn);
    }

    public String createLoadModuleMo(final EnmApplication enmApplication, final String fdn, final String moduleIdUniq) throws EnmException, HaTimeoutException {

        final String createLoadModule = "cmedit create %s " +
                "LoadModuleId=%s,loadModuleFilePath=path,productData=(productRevision=rev,productName=name," +
                "productInfo=info,productNumber=123,productionDate=20150325)";

        final String command = String.format(createLoadModule, fdn, moduleIdUniq);

        try {
            logger.debug("creatLoadModule's command {} ", command);
            return enmApplication.executeCliCommand(command);

        } catch (final HaTimeoutException e) {
            logger.error("failed to execute command {} due a TimeoutException", createLoadModule, e);
            throw new HaTimeoutException("Failed to createLoadModuleMo" + ": " + e.getMessage(), EnmErrorType.APPLICATION);
        }
    }

    private String getAnyLoadModuleMo(final EnmApplication enmApplication, final Node node) throws EnmException, HaTimeoutException {

        final String fdn = "FDN";
        final String getAllLoadModuleMos = "cmedit  get %s LoadModule";

        try {
            final String body = enmApplication.executeCliCommand(String.format(getAllLoadModuleMos, node.getNetworkElementId()));
            final Object parse = JSONValue.parse(body);

            final Object responseDto = ((JSONObject) parse).get("responseDto");
            final Object elements = ((JSONObject) responseDto).get("elements");

            if (elements instanceof JSONArray) {
                final JSONArray jsonArray = (JSONArray) elements;


                for (int index = 0; index < jsonArray.size(); index++) {
                    final JSONObject fdnMO = (JSONObject) jsonArray.get(index);
                    final String value = (String) fdnMO.get("value");

                    if (value != null && value.contains(fdn)) {
                        return value.split(":")[1];
                    }
                }
            }

        } catch (final HaTimeoutException e) {
            logger.error("failed to execute command {} due a TimeoutException", getAllLoadModuleMos, e);
            throw new HaTimeoutException("failed to execute command " + getAllLoadModuleMos + " due a TimeoutException" + ": " + e.getMessage(), EnmErrorType.APPLICATION);

        }
        return fdn;
    }

}