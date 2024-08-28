package com.ericsson.nms.rv.core.cm;

import com.ericsson.cifwk.taf.tools.http.HttpResponse;
import com.ericsson.cifwk.taf.tools.http.constants.ContentType;
import com.ericsson.nms.rv.core.EnmApplication;
import com.ericsson.nms.rv.core.HAPropertiesReader;
import com.ericsson.nms.rv.core.restclient.HttpRestServiceClient;
import com.ericsson.nms.rv.core.util.HaTimeoutException;
import com.ericsson.nms.rv.taf.tools.Constants;
import com.ericsson.oss.services.scriptengine.spi.dtos.AbstractDto;
import com.ericsson.oss.services.scriptengine.spi.dtos.summary.SummaryDto;
import com.ericsson.oss.testware.availability.common.exception.EnmException;

import com.google.common.collect.Lists;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import java.util.Collection;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static com.ericsson.nms.rv.core.HAPropertiesReader.ignoreDTMap;
import static com.ericsson.nms.rv.core.cm.ConfigurationManagement.cmIgnoreDTMap;

public class CmThreadExecutor implements Runnable {
    private static final Logger logger = LogManager.getLogger(CmThreadExecutor.class);
    private Thread worker;
    private HttpRestServiceClient httpRestClient;
    private CmComposite cmComposite;
    private boolean completed;
    private boolean ErrorOccurance;
    private boolean timedOut;
    private EnmApplication enmApplication;
    private long threadId;

    public boolean isCompleted() {
        return completed;
    }

    public boolean isErrorOcurred() {
        return ErrorOccurance;
    }

    public boolean isTimedOut() {
        return timedOut;
    }

    public static Logger getLogger() {
        return logger;
    }

    public CmThreadExecutor(CmComposite cmComposite, HttpRestServiceClient httpRestServiceClient,EnmApplication application) {
        this.httpRestClient = httpRestServiceClient;
        this.cmComposite = cmComposite;
        this.enmApplication = application;
    }

    public long getID () {
        return worker.getId();
    }

    public void execute () {
        worker = new Thread(this);
        threadId = worker.getId();
        worker.start();
    }
    @Override
    public void run () {
        try {
            cmComposite.execute(this);
            completed=true;
        }catch (final IllegalStateException | HaTimeoutException e) {
            timedOut = true;
            logger.warn("ThreadId : {}, Timeout in CM, error message : {}", threadId, e.getMessage());
        } catch (final EnmException e) {
            //Ignore as DT already started!
            ErrorOccurance =true;
            logger.warn("ThreadId : {}, Error occurred in CM, error message : {}", threadId, e.getMessage());
        } finally {
            Thread.currentThread().stop();
        }
    }
    public String executeCmCliCommand(final String command, final String property) throws EnmException, HaTimeoutException {
        logger.info("executeCmCliCommand ThreadId : {}" , threadId);
        String body = null;
        try {
            final List<String[]> bodies = new ArrayList<>();
            bodies.add(new String[]{"command", command});
            final String[] header = new String[]{"Skip-Cache", "true", "streaming", "true", "confirmation", "true", "tabId", "do_not_use"};
            final long startTime = System.currentTimeMillis();
            Date startDate = new Date(startTime);
                HttpResponse response;
                try {
                    response = httpRestClient.sendPostRequest(30, ContentType.MULTIPART_FORM_DATA, header, bodies, null, enmApplication.CLI_COMMAND_URL);
                } catch (IllegalStateException var14) {
                    logger.warn("ThreadId : {}, CM first Timeout failed to execute {}.........{} ", threadId, property, var14.getMessage());
                    enmApplication.stopAppDownTime();
                    enmApplication.setFirstTimeFail(true);
                    enmApplication.startAppTimeoutDowntime();
                    timedOut = true;
                    response = httpRestClient.sendPostRequest(120, ContentType.MULTIPART_FORM_DATA, header, bodies, null, enmApplication.CLI_COMMAND_URL);
                    long respTime = response.getResponseTimeMillis() + (30 * Constants.TEN_EXP_3);
                    if (response != null && checkResponse(response) && respTime < 150000L) {
                        logger.info("ThreadId : {}, Delayed Response time: {} ", threadId, respTime);
                        Date endDate = new Date(startTime + respTime);
                        cmIgnoreDTMap.put(startDate, endDate);
                        logger.info("ThreadId : {}, cmIgnoreDTMap : {}.........{} ", threadId, property, cmIgnoreDTMap);
                        ignoreDTMap.put(HAPropertiesReader.CONFIGURATIONMANGEMENT, cmIgnoreDTMap);
                    } else if (response != null) {
                        logger.warn("ThreadId : {}, Response Body: {}, ResponseTime: {}", threadId, response.getBody(), respTime);
                    } else {
                        logger.warn("ThreadId : {}, Response : {}, ResponseTime: {}", threadId, response, respTime);
                    }
                } catch (Exception var15) {
                    ErrorOccurance = true;
                    enmApplication.stopAppTimeoutDownTime();
                    if (enmApplication.isFirstTimeFail()) {
                        enmApplication.setFirstTimeFail(false);
                    } else {
                        enmApplication.startAppDownTime();
                    }
                    logger.warn("ThreadId : {}, CM failed: {}.........{} ", threadId, property, var15.getMessage());
                    throw new EnmException("CM failed ... Message: " + var15.getMessage());
                }

                if (response != null) {
                    logResponseCode(response, "(GET) " + command);
                    enmApplication.analyzeResponseWithoutStopAppDownTime(String.format(enmApplication.CLI_COMMAND_FAILED, command), response);
                    final Map<String, String> headers = response.getHeaders();

                    final String requestId = headers.get("request_id");
                    logger.debug("ThreadId : {}, For command [{}] requestId is : '{}' ", threadId, command, requestId);

                    final List<AbstractDto> commandResponse = getCommandResponse(requestId);
                    body = String.valueOf(commandResponse);
                } else {
                    logger.warn("ThreadId : {}, For ActionType (POST) and action {} HttpResponse is NULL!", threadId, enmApplication.CLI_COMMAND_URL);
                }
        } catch (IllegalStateException var16) {
            timedOut =true;
            logger.warn("ThreadId : {}, Message in second TimeOut = {}.........{} ", threadId, property, var16.getMessage());
            throw new HaTimeoutException(var16.getMessage());
        } catch (Exception var17) {
            logger.warn("ThreadId : {}, Message in Error = {}.........{} ", threadId, property, var17.getMessage());
            ErrorOccurance = true;
            enmApplication.stopAppTimeoutDownTime();
            if (enmApplication.isFirstTimeFail()) {
                enmApplication.setFirstTimeFail(false);
            } else {
                enmApplication.startAppDownTime();
            }
            throw new EnmException("CM failed ... Message: " + var17.getMessage());
        }
        return body;
    }

    private boolean checkResponse(HttpResponse response)  {
        if (EnmApplication.acceptableResponse.contains(response.getResponseCode())) {
            enmApplication.stopAppTimeoutDownTime();
            enmApplication.stopAppDownTime();
            enmApplication.setFirstTimeFail(true);
            return true;
        } else if (response.getResponseCode().getCode() == 504) {
            throw new IllegalStateException(response.getResponseCode().toString());
        } else {
            return false;
        }
    }

    private void logResponseCode(final HttpResponse response, final String query) {
        logger.debug("ThreadId : {}, response code for: ({}): {}", threadId, query, response.getResponseCode().getCode());
    }

    private String getStream(final String requestId) throws EnmException {
        final String[] queryParam = new String[]{"max_size", "20000"};
        final String[] header = new String[]{EnmApplication.Header.ACCEPT.getValue(), ContentType.APPLICATION_JSON, "wait_interval", "200", "attempts", "100"};
        final long startTime = System.currentTimeMillis();
        Date startDate = new Date(startTime);
        HttpResponse response;
        try {
            response = httpRestClient.sendGetRequest(30, ContentType.APPLICATION_JSON, header, null, queryParam, String.format(enmApplication.CLI_COMMAND_OUTPUT_URL, requestId));
        } catch (IllegalStateException var14) {
            logger.warn("ThreadId : {}, CM first Timeout failed to execute getStream.........{} ", threadId, var14.getMessage());
            enmApplication.stopAppDownTime();
            enmApplication.setFirstTimeFail(true);
            enmApplication.startAppTimeoutDowntime();
            timedOut = true;
            response = httpRestClient.sendGetRequest(120, ContentType.APPLICATION_JSON, header, null, queryParam, String.format(enmApplication.CLI_COMMAND_OUTPUT_URL, requestId));
            long respTime = response.getResponseTimeMillis() + (30 * Constants.TEN_EXP_3);
            if (response != null && checkResponse(response) && respTime < 150000L) {
                logger.info("ThreadId : {}, Delayed Response time: {}", threadId, respTime);
                Date endDate = new Date(startTime + respTime);
                cmIgnoreDTMap.put(startDate, endDate);
                logger.info("ThreadId : {}, cmIgnoreDTMap : .........{} ", threadId, cmIgnoreDTMap);
                ignoreDTMap.put(HAPropertiesReader.CONFIGURATIONMANGEMENT, cmIgnoreDTMap);
            } else if (response != null) {
                logger.warn("ThreadId : {}, Response Body: {}, ResponseTime: {}", threadId, response.getBody(), respTime);
            } else {
                logger.warn("ThreadId : {}, Response : {}, ResponseTime: {}", threadId, response, respTime);
            }
        } catch (Exception var15) {
            ErrorOccurance = true;
            enmApplication.stopAppTimeoutDownTime();
            if (enmApplication.isFirstTimeFail()) {
                enmApplication.setFirstTimeFail(false);
            } else {
                enmApplication.startAppDownTime();
            }
            logger.warn("ThreadId : {}, CM failed in getStream : {} ", threadId, var15.getMessage());
            throw new EnmException("CM failed ... Message  : " + var15.getMessage());
        }
        if (response != null) {
            logger.info("ThreadId : {}, getStream response.getContentType() : {}", threadId, response.getContentType());
            logger.info("ThreadId : {}, getStream response.getBody() : {}", threadId, response.getBody());
            enmApplication.analyzeResponseWithoutStopAppDownTime(String.format(EnmApplication.CLI_COMMAND_FAILED, String.format(enmApplication.CLI_COMMAND_OUTPUT_URL, requestId)), response);
            enmApplication.stopAppTimeoutDownTime();
            return response.getBody();
        }
        return "";
    }

    public List<AbstractDto> getCommandResponse(final String requestId) throws EnmException {
        final List<AbstractDto> result = Lists.newArrayList();
        final long startTime = System.currentTimeMillis();
        while (!containsSummary(result) && (System.currentTimeMillis() - startTime) < 120L * (long) Constants.TEN_EXP_3) {
            result.addAll(dtosFromJson(getStream(requestId)));
        }
        return result;
    }
    public boolean containsSummary(final List dtos) {
        return !dtos.isEmpty() && dtos.get(dtos.size() - 1) instanceof SummaryDto;
    }

    public Collection<AbstractDto> dtosFromJson(final String json) {
        try {
            final ObjectMapper mapper = new ObjectMapper();
            final List<AbstractDto> result = new ArrayList<>();
            for (final JsonNode node : mapper.readTree(json)) {
                result.add(mapper.readValue(node, AbstractDto.class));
            }
            if (result.isEmpty()) {
                logger.info("ThreadId : {}, result of get stream is empty", threadId);
                throw new EnmException("cmedit command failed with message : Result of get stream command is empty");
            }
            return result;
        } catch (final Exception e) {
            throw new IllegalArgumentException(e);
        }
    }
}

