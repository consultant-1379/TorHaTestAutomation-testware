package com.ericsson.nms.rv.core.fan;

import com.ericsson.cifwk.taf.tools.http.HttpResponse;
import com.ericsson.cifwk.taf.tools.http.constants.ContentType;
import com.ericsson.nms.rv.core.EnmApplication;
import com.ericsson.nms.rv.core.HAPropertiesReader;
import com.ericsson.nms.rv.core.HAconstants;
import com.ericsson.nms.rv.core.restclient.HttpRestServiceClient;
import com.ericsson.nms.rv.core.util.CommonUtils;
import com.ericsson.nms.rv.taf.tools.Constants;
import com.ericsson.oss.testware.availability.common.exception.EnmException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Date;

public class FanThreadExecutor implements Runnable{

    private static final Logger logger = LogManager.getLogger(FanThreadExecutor.class);
    private static boolean printErrorResponse = true;
    private final String fileName;
    private final EnmApplication enmApplication;
    private final HttpRestServiceClient httpRestServiceClient;
    private long threadId;
    private boolean timedOut;
    private boolean completed;
    private boolean failed;

    public FanThreadExecutor(EnmApplication enmApplication, HttpRestServiceClient httpRestServiceClient, String fileName) {
        this.enmApplication = enmApplication;
        this.httpRestServiceClient = httpRestServiceClient;
        this.fileName = fileName;
    }

    public boolean isCompleted() {
        return completed;
    }

    public boolean isFailed() {
        return failed;
    }

    public boolean isTimedOut() {
        return timedOut;
    }

    public long getThreadId() {
        return threadId;
    }

    public void execute() {
        Thread worker = new Thread(this);
        worker.start();
        threadId = worker.getId();
    }
    @Override
    public void run() {
        try {
            downloadFile();
            this.completed = true;
        } catch (IllegalStateException e) {
            logger.warn("Thread: {}, FAN timed out : {}", threadId, e.getMessage());
            this.timedOut = true;
        } catch (Exception e) {
            logger.warn("Thread: {}, FAN failed : {}", threadId, e.getMessage());
            this.failed = true;
            if(this.enmApplication.isFirstTimeFail()){
                this.enmApplication.setFirstTimeFail(false);
            } else {
                this.enmApplication.stopAppTimeoutDownTime();
                this.enmApplication.startAppDownTime();
            }
        } finally {
            CommonUtils.sleep(1);
            Thread.currentThread().stop();
        }
    }

    private void downloadFile() throws EnmException, IllegalStateException {
        HttpResponse response = null;
        String uri = "/file/v1/files/ericsson/pmic1/" + fileName;
        final long startTime = System.currentTimeMillis();
        final Date startDate = new Date(startTime);
        try {
            long dStart = System.currentTimeMillis();
            response = httpRestServiceClient.sendGetRequest(EnmApplication.TIMEOUT_REST, ContentType.APPLICATION_OCTET_STREAM, new String[]{"ContentType", ContentType.APPLICATION_OCTET_STREAM}, null, null, uri);
            logger.info("download time : {}ms", System.currentTimeMillis() - dStart);
        } catch (IllegalStateException e) {
            logger.warn("Thread: {} .. FAN timed out, failed to download file. message : {}", threadId, e.getMessage());
            enmApplication.setFirstTimeFail(true);
            enmApplication.stopAppDownTime();
            timedOut = true;
            enmApplication.startAppTimeoutDowntime();
            response = httpRestServiceClient.sendGetRequest(100, ContentType.APPLICATION_OCTET_STREAM, new String[]{"ContentType", ContentType.APPLICATION_OCTET_STREAM}, null, null, uri);
            if (response != null && checkResponse(response)) {
                final long respTime = response.getResponseTimeMillis() + (20 * Constants.TEN_EXP_3);
                if (respTime < 120 * Constants.TEN_EXP_3) {
                    logger.info("Thread: {} .. startDate : {}", threadId, startDate);
                    logger.info("Thread: {} .. Delayed Response time: {}", threadId, respTime);
                    final Class<? extends EnmApplication> clazz = enmApplication.getClass();
                    final long startTimeNew = System.currentTimeMillis() - ((System.nanoTime() - EnmApplication.getDownTimeAppHandler().get(clazz).getLastTimeoutSuccess().get()) / HAconstants.TEN_EXP_6);
                    logger.info("Thread .. {} .. checkResponse startTimeNew : {}", threadId, new Date(startTimeNew));
                    final Date endDate = new Date(startTime + respTime);
                    logger.info("Thread: {} .. endDate: {}", threadId, endDate);
                    FileAccessNBI.fanIgnoreDTMap.put(startDate, endDate);
                    logger.info("Thread: {} .. fanIgnoreDTMap : {}", threadId, FileAccessNBI.fanIgnoreDTMap);
                    HAPropertiesReader.ignoreDTMap.put(HAPropertiesReader.FILEACCESSNBI, FileAccessNBI.fanIgnoreDTMap);
                }
            }
        } catch (Exception e) {
            logger.warn("Thread: {}, FAN failed to download file. Error : {}", threadId, e.getMessage());
            throw new EnmException(e.getMessage());
        }
        if(response != null) {
            logger.info("response.getBody().length() : {}", response.getBody().length());
            logger.info("response.getResponseCode() : {}", response.getResponseCode());
            if(response.getBody().length() < 10_000) {
                logger.info("response.getHeaders() : {}", response.getHeaders());
                if(printErrorResponse) {
                    printErrorResponse = false;
                    logger.info("response.getBody() : {}", response.getBody());
                }
            }
            enmApplication.analyzeResponse("FAN failed to download file.", response);
        } else {
            logger.warn("download response is null!");
            throw new EnmException("download response is null");
        }
    }

    private boolean checkResponse(final HttpResponse response) {
        if (EnmApplication.acceptableResponse.contains(response.getResponseCode())) {
            enmApplication.setFirstTimeFail(true);
            enmApplication.stopAppTimeoutDownTime();
            enmApplication.stopAppDownTime();
            return true;
        } else if (response.getResponseCode().getCode() == 504) {
            throw new IllegalStateException(response.getResponseCode().toString());
        } else {
            return false;
        }
    }
}
