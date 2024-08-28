package com.ericsson.nms.rv.core.concurrent;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Callable;

import com.ericsson.nms.rv.core.EnmApplication;
import com.ericsson.nms.rv.core.downtime.DownTimeHandler;
import com.ericsson.nms.rv.core.downtime.EnmDowntime;
import com.ericsson.nms.rv.core.upgrade.RegressionVerificationTasks;
import com.ericsson.nms.rv.core.util.CommonUtils;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/**
 * {@code Verifier} verifies that an ENM enmApplication works correctly.
 */
public class Verifier implements Callable<EnmDowntime> {
    private static final Logger logger = LogManager.getLogger(Verifier.class);
    private final EnmApplication enmApplication;

    /**
     * Constructs a new {@code Verifier} object.
     *
     * @param application the enmApplication to be verified
     * @throws IllegalArgumentException if enmApplication is {@code null}
     */
    public Verifier(final EnmApplication application) {
        if (application == null) {
            throw new IllegalArgumentException("enmApplication cannot be null");
        }
        enmApplication = application;
    }

    /**
     * Verifies that an ENM enmApplication works fine.
     *
     * @return the total service disruption time in seconds
     */
    @Override
    public final EnmDowntime call() {
        final Class<? extends EnmApplication> clazz = enmApplication.getClass();
        final String appName = clazz.getSimpleName();
        Thread.currentThread().setName(appName);

        logger.info("{} verification started.", appName);
        final DownTimeHandler downTimeHandler = EnmApplication.getDownTimeAppHandler().get(clazz);
        downTimeHandler.resetDowntime();

        CommonUtils.executeUntilSignal(enmApplication, appName);

        logger.info("{} verification stopped.", appName);
        enmApplication.stopAppDownTime();
        enmApplication.stopAppTimeoutDownTime();
        enmApplication.setFirstTimeFail(true);

        final EnmDowntime enmDowntime = new EnmDowntime(enmApplication);
        enmDowntime.setApplicationDowntime(downTimeHandler.getDowntime());
        enmDowntime.setApplicationTimeoutDowntime(downTimeHandler.getTimeoutDowntime());
        downTimeHandler.resetDowntime();

        if(!enmApplication.getAppDateTimeList.isEmpty()) {
            for (Entry<String, Map<Date, Date>> entry : enmApplication.getAppDateTimeList.entrySet()) {
                RegressionVerificationTasks.appDateTimeList.put(entry.getKey(), entry.getValue());
            }
            logger.info("appDateTimeList for {} = {}", appName, RegressionVerificationTasks.appDateTimeList.get(appName));
        }
        if(!enmApplication.getAppDateTimeTimeoutList.isEmpty()) {
            for (Entry<String,Map<Date, Date>> entry : enmApplication.getAppDateTimeTimeoutList.entrySet()) {
                RegressionVerificationTasks.appDateTimeTimeoutList.put(entry.getKey(), entry.getValue());
            }
            logger.info("appDateTimeTimeoutMapList for {} = {}", appName, RegressionVerificationTasks.appDateTimeTimeoutList.get(appName));
        }
        if(!enmApplication.getAppDateTimeLoggerList.isEmpty()) {
            for (Entry<String, List<String>> entry : enmApplication.getAppDateTimeLoggerList.entrySet()) {
                RegressionVerificationTasks.appDateTimeLoggerList.put(entry.getKey(), entry.getValue());
            }
            logger.info("appDateTimeLoggerList for {} = {}", appName, RegressionVerificationTasks.appDateTimeLoggerList.get(appName));
        }
        if(!enmApplication.getAppDateTimeTimeoutLoggerList.isEmpty()) {
            for (Entry<String, List<String>> entry : enmApplication.getAppDateTimeTimeoutLoggerList.entrySet()) {
                RegressionVerificationTasks.appDateTimeTimeoutLoggerList.put(entry.getKey(), entry.getValue());
            }
            logger.info("appDateTimeTimeoutLoggerList for {} = {}", appName, RegressionVerificationTasks.appDateTimeTimeoutLoggerList.get(appName));
        }
        logger.info("{} downtime lists generated.", appName);
        return enmDowntime;
    }

}
