package com.ericsson.nms.rv.core.downtime;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import com.ericsson.nms.rv.core.HAconstants;

public class DownTimeHandler {

    private final AtomicLong totalDownTime = new AtomicLong();
    private final AtomicLong totalErrorDownTime = new AtomicLong();
    private final AtomicLong totalTimeoutDownTime = new AtomicLong();
    private final AtomicLong lastSuccess = new AtomicLong();
    private final AtomicLong lastTimeoutSuccess = new AtomicLong();
    private DowntimeSM downtimeSM = DowntimeSM.STOPPED;
    private boolean isTimeoutStarted = false;
    private boolean isDowntimeStarted = false;

    public DownTimeHandler() {
        resetDowntime();
    }

    public final AtomicLong getLastSuccess() {
        return lastSuccess;
    }

    public final AtomicLong getLastTimeoutSuccess() {
        return lastTimeoutSuccess;
    }

    final void setLastSuccess() {
        lastSuccess.set(System.nanoTime());
    }

    final void setLastTimeoutSuccess() {
        lastTimeoutSuccess.set(System.nanoTime());
    }

    public final boolean getAppTimeoutStarted() {
        return isTimeoutStarted;
    }

    public final boolean getDowntimeStarted() {
        return isDowntimeStarted;
    }

    public final synchronized void start(final String app) {
        isDowntimeStarted = true;
        downtimeSM.startDowntime(this, app);
    }

    public final synchronized void startTimeout(final String app) {
        isTimeoutStarted = true;
        downtimeSM.startTimeoutDowntime(this, app);
    }

    public final synchronized void stop(final String app, final float adjust) {
        downtimeSM.stopDowntime(this, app, adjust);
        isDowntimeStarted = false;
    }

    public final synchronized void stopWithTime(final String app, final long time, final Date date) {
        downtimeSM.stopDowntimeWithTimeStamp(this, app, time, date);
        isDowntimeStarted = false;
    }

    public final synchronized void stopTimeout(final String app, final float adjust) {
        downtimeSM.stopTimeoutDowntime(this, app, adjust);
        isTimeoutStarted = false;
    }

    public final Long getDowntime() {
        return totalDownTime.get() / HAconstants.TEN_EXP_9;
    }

    public final float getAccurateDowntime() {
        return (float)totalDownTime.get() / HAconstants.TEN_EXP_9;
    }

    public final float getAccurateErrorDowntime() {
        return (float)totalErrorDownTime.get() / HAconstants.TEN_EXP_9;
    }

    public final float getAccurateTimeoutDowntime() {
        return (float)totalTimeoutDownTime.get() / HAconstants.TEN_EXP_9;
    }

    public final Long getTimeoutDowntime() {
        return totalTimeoutDownTime.get() / HAconstants.TEN_EXP_9;
    }

    final synchronized void setState(final DowntimeSM downtime) {
        downtimeSM = downtime;
    }

    final void incrementDowntime(final long delta) {
        totalDownTime.getAndAdd(delta);
        setLastSuccess();
    }

    final void incrementTimeoutDowntime(final long delta) {
        totalTimeoutDownTime.getAndAdd(delta);
        setLastTimeoutSuccess();
    }

    final void incrementErrorDowntime(final long delta) {
        totalErrorDownTime.getAndAdd(delta);
    }

    public final void resetDowntime() {
        downtimeSM = DowntimeSM.STOPPED;
        totalDownTime.set(0);
        totalTimeoutDownTime.set(0);
        totalErrorDownTime.set(0);
        setLastSuccess();
        setLastTimeoutSuccess();
    }
    public final Map<String, Map<Date, Date>> appDateTimeMapList = downtimeSM.appDateTimeMap;
    public final Map<String, Map<Date, Date>> appDateTimeTimeoutMapList = downtimeSM.appDateTimeTimeoutMap;
    public final Map<String, List<String>> appDateTimeLoggerMapList = downtimeSM.appDateTimeLoggerMap;
    public final Map<String, List<String>> appDateTimeTimeoutLoggerMapList = downtimeSM.appDateTimeTimeoutLoggerMap;
}