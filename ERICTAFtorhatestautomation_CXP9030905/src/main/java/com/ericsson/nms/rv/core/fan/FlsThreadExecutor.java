package com.ericsson.nms.rv.core.fan;

import com.ericsson.cifwk.taf.tools.http.HttpResponse;
import com.ericsson.cifwk.taf.tools.http.constants.ContentType;
import com.ericsson.nms.rv.core.EnmApplication;
import com.ericsson.nms.rv.core.HAPropertiesReader;
import com.ericsson.nms.rv.core.restclient.HttpRestServiceClient;
import com.ericsson.nms.rv.core.util.HaTimeoutException;
import com.ericsson.nms.rv.taf.tools.Constants;
import com.ericsson.oss.testware.availability.common.exception.EnmException;

import net.minidev.json.JSONObject;
import net.minidev.json.JSONValue;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import java.util.Date;

import static com.ericsson.nms.rv.core.HAPropertiesReader.ignoreDTMap;
import static com.ericsson.nms.rv.core.fan.FileLookupService.flsIgnoreDTMap;


public class FlsThreadExecutor implements Runnable {
    private static final Logger logger = LogManager.getLogger(FlsThreadExecutor.class);
    private Thread worker;
    private HttpRestServiceClient httpRestClient;
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

    FlsThreadExecutor(HttpRestServiceClient httpRestServiceClient, EnmApplication application) {
        this.httpRestClient = httpRestServiceClient;
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
            executeFls();
            completed=true;
        }catch (final IllegalStateException | HaTimeoutException e) {
            timedOut = true;
            logger.warn("ThreadId : {}, Timeout in FLS, error message : {}", threadId, e.getMessage());
        } catch (final EnmException e) {
            //Ignore as DT already started!
            ErrorOccurance =true;
            logger.warn("ThreadId : {}, Error occurred in FLS, error message : {}", threadId, e.getMessage());
        } finally {
            Thread.currentThread().stop();
        }
    }

    private void executeFls() throws EnmException, HaTimeoutException {
        logger.info("executeFLS ThreadId : {}" , threadId);
        final String uri = "/file/v1/files/?filter=dataType==PM_STATISTICAL&limit=1&offset=0&orderBy=id%20desc&select=id";
        try {
            final long startTime = System.currentTimeMillis();
            Date startDate = new Date(startTime);
            HttpResponse response;
            try {
                response = httpRestClient.sendGetRequest(20, ContentType.APPLICATION_JSON, new String[]{"ContentType", ContentType.APPLICATION_JSON}, null, null, uri);
            } catch (IllegalStateException e) {
                logger.warn("FLS ThreadId : {}, FLS first Timeout failed to execute.........{} ", threadId, e.getMessage());
                enmApplication.stopAppDownTime();
                enmApplication.setFirstTimeFail(true);
                enmApplication.startAppTimeoutDowntime();
                timedOut = true;
                response = httpRestClient.sendGetRequest(100, ContentType.APPLICATION_JSON, new String[]{"ContentType", ContentType.APPLICATION_JSON}, null, null, uri);
                long respTime = response.getResponseTimeMillis() + (20 * Constants.TEN_EXP_3);
                if (response != null && checkResponse(response) && respTime < 120000L) {
                    logger.info("FLS ThreadId : {}, Delayed Response time: {} ", threadId, respTime);
                    Date endDate = new Date(startTime + respTime);
                    flsIgnoreDTMap.put(startDate, endDate);
                    logger.info("FLS ThreadId : {}, flsIgnoreDTMap : .........{} ", threadId, flsIgnoreDTMap);
                    ignoreDTMap.put(HAPropertiesReader.FILELOOKUPSERVICE, flsIgnoreDTMap);
                } else if (response != null) {
                    logger.warn("FLS ThreadId : {}, Response Body: {}, ResponseTime: {}", threadId, response.getBody(), respTime);
                } else {
                    logger.warn("FLS ThreadId : {}, Response : {}, ResponseTime: {}", threadId, response, respTime);
                }
            } catch (Exception e) {
                throw new EnmException(e.getMessage());
            }

            if (response != null) {
                String fileId = "";
                logger.info("FLS ThreadId : {} , response code : {}", threadId, response.getResponseCode().getCode());
                enmApplication.analyzeResponseWithoutStopAppDownTime(String.format(EnmApplication.CLI_COMMAND_FAILED, uri), response);

                Object parse = JSONValue.parse(response.getContent());
                if (parse instanceof net.minidev.json.JSONObject) {
                    final JSONObject jsonObject = (net.minidev.json.JSONObject) parse;
                    final Object fileObj = jsonObject.get("files");
                    if (fileObj instanceof net.minidev.json.JSONArray) {
                        for (final Object object : (net.minidev.json.JSONArray) (fileObj)) {
                            final JSONObject idObject = (net.minidev.json.JSONObject) object;
                            fileId = idObject.get("id").toString();
                        }
                    }
                }
                if (fileId.isEmpty()) {
                    throw new EnmException("Failed to get file ID.");
                } else {
                    enmApplication.stopAppTimeoutDownTime();
                    enmApplication.stopAppDownTime();
                    enmApplication.setFirstTimeFail(true);
                }
                logger.debug("FLS ThreadId : {}, file ID is : '{}' ", threadId, fileId);

            } else {
                logger.warn("FLS ThreadId : {}, For ActionType (POST) and HttpResponse is NULL!", threadId);
            }
        } catch (IllegalStateException e) {
            timedOut =true;
            logger.warn("FLS ThreadId : {}, Message in second TimeOut = .........{} ", threadId, e.getMessage());
            throw new HaTimeoutException(e.getMessage());
        } catch (Exception ex) {
            logger.warn("FLS ThreadId : {}, Message in Error = ......... {} ", threadId, ex.getMessage());
            ErrorOccurance = true;
            enmApplication.stopAppTimeoutDownTime();
            if (enmApplication.isFirstTimeFail()) {
                enmApplication.setFirstTimeFail(false);
            } else {
                enmApplication.startAppDownTime();
            }
            throw new EnmException("FLS failed ... Message: " + ex.getMessage());
        }
    }

    private boolean checkResponse(HttpResponse response)  {
        if (EnmApplication.acceptableResponse.contains(response.getResponseCode())) {
            enmApplication.stopAppTimeoutDownTime();
            enmApplication.stopAppDownTime();
            enmApplication.setFirstTimeFail(true);
            return true;
        } else {
            return false;
        }
    }

 }

