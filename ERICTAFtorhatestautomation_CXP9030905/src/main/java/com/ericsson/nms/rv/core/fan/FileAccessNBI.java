package com.ericsson.nms.rv.core.fan;

import com.ericsson.cifwk.taf.tools.http.HttpResponse;
import com.ericsson.nms.rv.core.EnmApplication;
import com.ericsson.nms.rv.core.HAPropertiesReader;
import com.ericsson.nms.rv.core.system.SystemVerifier;
import com.ericsson.nms.rv.core.upgrade.CloudNativeDependencyDowntimeHelper;
import com.ericsson.nms.rv.taf.tools.Constants;
import com.ericsson.oss.testware.availability.common.exception.EnmException;
import com.ericsson.sut.test.cases.HighAvailabilityTestCase;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Date;
import java.util.NavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;

public class FileAccessNBI extends EnmApplication {

    private static final Logger logger = LogManager.getLogger(FileAccessNBI.class);
    final String fileName = "fan_test_file.gz";
    private boolean isReadyToVerify = false;
    public static NavigableMap<Date, Date> fanIgnoreDTMap = new ConcurrentSkipListMap();

    public FileAccessNBI(final SystemVerifier systemStatus) {
        initDownTimerHandler(systemStatus);
        initFirstTimeFail();
    }

    public void prepare() throws Exception {
        String createOutput = createDeleteFanFile("create");
        if(createOutput.equals("success")) {
            isReadyToVerify = true;
            logger.info("FAN file created");
        } else if(createOutput.equals("FAN not found")) {
            HighAvailabilityTestCase.multiMapErrorCollection.put(HAPropertiesReader.appMap.get("fan"), HAPropertiesReader.appMap.get("fan") + ", FAN is not available on server, disabling FAN test case");
            logger.info("FAN is not available on server, disabling FAN test case.");
        } else {
            logger.warn("Failed in Prepare ... failed to create FAN dummy file");
            throw new EnmException("Failed in prepare. failed to create FAN dummy file.");
        }
    }

    @Override
    public void verify() {
        if(isReadyToVerify) {
            FanThreadExecutor fanThread = new FanThreadExecutor(this, getHttpRestServiceClient(), fileName);
            fanThread.execute();
            long startTime = System.nanoTime();
            logger.info("FAN Thread: {} started.", fanThread.getThreadId());
            do {
                this.sleep(1L);
                if (fanThread.isCompleted() || fanThread.isFailed() || fanThread.isTimedOut()) {
                    break;
                }
            }  while ((System.nanoTime() - startTime  < 120L * (long) Constants.TEN_EXP_9));
            if (fanThread.isFailed()) {
                logger.warn("Error occurred in FAN Thread: {} ", fanThread.getThreadId());
            } else if (fanThread.isTimedOut()) {
                logger.warn("FAN Thread: {} Timed out.", fanThread.getThreadId());
            } else if (fanThread.isCompleted()) {
                logger.info("FAN Thread: {} completed! ", fanThread.getThreadId());
            }
        }
    }

    public void cleanup() throws EnmException {
        String createOutput = createDeleteFanFile("delete");
        if(createOutput.equals("success")) {
            logger.info("FAN file deleted");
        } else if(createOutput.equals("FAN not found")) {
            throw new EnmException("FAN is not available on server, FAN test case disabled");
        } else {
            throw new EnmException("Failed in cleanup... failed to delete FAN dummy file.");
        }
    }

    private String createDeleteFanFile(String action) {
        final String params = action + ":" + fileName;
        final String uri = "watcher/adu/fan/" + params;
        String output = "";
        try {
            HttpResponse response = CloudNativeDependencyDowntimeHelper.generateHttpPostResponse(uri);
            output = response.getBody().trim();
        } catch (Exception e) {
            logger.info("failed to {} FAN file : {}", action, e.getMessage());
        }
        logger.info("output: {}", output);
        return output;
    }
}
