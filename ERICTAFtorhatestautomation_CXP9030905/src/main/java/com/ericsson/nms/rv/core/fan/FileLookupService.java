package com.ericsson.nms.rv.core.fan;

import com.ericsson.nms.rv.core.EnmApplication;
import com.ericsson.nms.rv.core.system.SystemStatus;
import com.ericsson.nms.rv.taf.tools.Constants;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Date;
import java.util.NavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;

public class FileLookupService extends EnmApplication {

    private static final Logger logger = LogManager.getLogger(FileLookupService.class);
    static NavigableMap<Date, Date> flsIgnoreDTMap = new ConcurrentSkipListMap();

    public FileLookupService(final SystemStatus systemStatus) {
        initDownTimerHandler(systemStatus);
        initFirstTimeFail();
    }

    @Override
    public void prepare() {
    }

    @Override
    public void verify() {
        FlsThreadExecutor thread = new FlsThreadExecutor(getHttpRestServiceClient(), this);
        thread.execute();
        long startTime = System.currentTimeMillis();
        logger.info("FLS Thread: {} started.", thread.getID());
        do {
            this.sleep(1L);
        }  while (!thread.isCompleted() && !thread.isErrorOcurred() && !thread.isTimedOut() && !(startTime - System.currentTimeMillis() > 60L * Constants.TEN_EXP_3));

        if (thread.isErrorOcurred()) {
            logger.warn("FLS Thread: {} Error occurred in FLS", thread.getID());
        } else if (thread.isTimedOut()) {
            logger.warn("FLS Thread: {} FLS Timed out.", thread.getID());
        } else if (thread.isCompleted()) {
            logger.info("FLS Thread: {} completed!", thread.getID());
        }
    }

    @Override
    public void cleanup() {
    }
}

