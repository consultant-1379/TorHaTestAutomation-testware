package com.ericsson.nms.rv.core.downtime;

import static com.ericsson.nms.rv.taf.tools.Constants.EMPTY_STRING;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.ericsson.nms.rv.core.HAPropertiesReader;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.ericsson.nms.rv.core.HAconstants;

enum DowntimeSM {

    STOPPED {
        @Override
        void startDowntime(final DownTimeHandler handler, final String app) {
            final SimpleDateFormat SIMPLE_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            handler.setState(STARTED);
            final Long startTimeNew = System.currentTimeMillis() - ((System.nanoTime() - handler.getLastSuccess().get()) / HAconstants.TEN_EXP_6);
            final Date instanceTime = new Date(startTimeNew);
            final String msg = String.format(MESSAGE, app, "started", SIMPLE_DATE_FORMAT.format(instanceTime), EMPTY_STRING);
            logger.warn(msg);
        }

        @Override
        void stopDowntime(final DownTimeHandler handler, final String app, final float adjust) {
            handler.setLastSuccess();
        }

        @Override
        void stopDowntimeWithTimeStamp(final DownTimeHandler handler, final String app, final long time, final Date date) {
            handler.setLastSuccess();
        }

        @Override
        void startTimeoutDowntime(final DownTimeHandler handler, final String app) {
            final SimpleDateFormat SIMPLE_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            handler.setState(STARTED);
            final Long startTimeNew = System.currentTimeMillis() - ((System.nanoTime() - handler.getLastTimeoutSuccess().get()) / HAconstants.TEN_EXP_6);
            final Date instanceTime = new Date(startTimeNew);
            final String msg = String.format(MESSAGE_TIMEOUT, app, "started", SIMPLE_DATE_FORMAT.format(instanceTime), EMPTY_STRING);
            logger.warn(msg);
        }

        @Override
        void stopTimeoutDowntime(final DownTimeHandler handler, final String app, final float adjust) {
            handler.setLastTimeoutSuccess();
        }
    },

    STARTED {
        @Override
        void startDowntime(final DownTimeHandler handler, final String app) {
            // needless to start if already started.
        }

        @Override
        void stopDowntime(final DownTimeHandler handler, final String app, final float adjust) {
            if(!handler.getDowntimeStarted()) { //Don't do anything if downtimer is not started!
                return;
            }
            handler.setLastTimeoutSuccess(); // required if immediate timeout after app error.
            final SimpleDateFormat SIMPLE_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            handler.setState(STOPPED);
            final Long startTimeNew = System.currentTimeMillis() - ((System.nanoTime() - handler.getLastSuccess().get()) / HAconstants.TEN_EXP_6);
            final long delta = System.nanoTime() - handler.getLastSuccess().get() - (long) (adjust * HAconstants.TEN_EXP_9);
            handler.incrementDowntime(delta);
            handler.incrementErrorDowntime(delta);
            final Date instanceTime = new Date();
            Map<Date, Date> startStopMap = new HashMap<>();
            List<String> appDateTimeList = new ArrayList<>();

            if (appDateTimeMap.containsKey(app)) {
                startStopMap = appDateTimeMap.get(app);
                startStopMap.put(new Date(startTimeNew), instanceTime); //append to existing List
                appDateTimeMap.put(app, startStopMap);
            } else {
                startStopMap.put(new Date(startTimeNew), instanceTime);
                appDateTimeMap.put(app, startStopMap);
            }

            final String msg = String.format(MESSAGE, app, "stopped", SIMPLE_DATE_FORMAT.format(instanceTime),
                    String.format(", [+%.02fs], [=%.02fs]", (float) delta / HAconstants.TEN_EXP_9, handler.getAccurateDowntime()));

            logger.warn(msg);
            logger.debug("Adjust time is : {}s", adjust);
            logger.info("App Map :" + app + " startTime: " + SIMPLE_DATE_FORMAT.format(new Date(startTimeNew)) + ", stopTime: " + SIMPLE_DATE_FORMAT.format(instanceTime));

            final String logString = "Downtime started at : " + SIMPLE_DATE_FORMAT.format(new Date(startTimeNew)) + ", stopTime: " + SIMPLE_DATE_FORMAT.format(instanceTime) +
                    String.format(", [+%.02fs], [=%.02fs]", (float) delta / HAconstants.TEN_EXP_9, handler.getAccurateErrorDowntime());
            if(appDateTimeLoggerMap.containsKey(app)) {
                appDateTimeList = appDateTimeLoggerMap.get(app);
                appDateTimeList.add(logString);
            } else {
                appDateTimeList.add(logString);
            }
            appDateTimeLoggerMap.put(app, appDateTimeList);
        }

        @Override
        void stopDowntimeWithTimeStamp(final DownTimeHandler handler, final String app, final long time, final Date date) {
            logger.info("stopDowntimeWithTimeStamp() is called for {}.", app);
            if(!handler.getDowntimeStarted()) { //Don't do anything if downtimer is not started!
                return;
            }
            handler.setLastTimeoutSuccess();
            final SimpleDateFormat SIMPLE_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            handler.setState(STOPPED);
            final long startTimeNew = System.currentTimeMillis() - ((System.nanoTime() - handler.getLastSuccess().get()) / HAconstants.TEN_EXP_6);
            final long delta = time - handler.getLastSuccess().get();
            handler.incrementDowntime(delta);
            handler.incrementErrorDowntime(delta);
            Map<Date, Date> startStopMap = new HashMap<>();
            List<String> appDateTimeList = new ArrayList<>();

            if (appDateTimeMap.containsKey(app)) {
                startStopMap = appDateTimeMap.get(app);
                startStopMap.put(new Date(startTimeNew), date);
                appDateTimeMap.put(app, startStopMap);
            } else {
                startStopMap.put(new Date(startTimeNew), date);
                appDateTimeMap.put(app, startStopMap);
            }

            final String msg = String.format(MESSAGE, app, "stopped", SIMPLE_DATE_FORMAT.format(date),
                    String.format(", [+%.02fs], [=%.02fs]", (float) delta / HAconstants.TEN_EXP_9, handler.getAccurateDowntime()));

            logger.warn(msg);
            logger.info("App Map :" + app + " startTime: " + SIMPLE_DATE_FORMAT.format(new Date(startTimeNew)) + ", stopTime: " + SIMPLE_DATE_FORMAT.format(date));

            final String logString = "Downtime started at : " + SIMPLE_DATE_FORMAT.format(new Date(startTimeNew)) + ", stopTime: " + SIMPLE_DATE_FORMAT.format(date) +
                    String.format(", [+%.02fs], [=%.02fs]", (float) delta / HAconstants.TEN_EXP_9, handler.getAccurateErrorDowntime());
            if(appDateTimeLoggerMap.containsKey(app)) {
                appDateTimeList = appDateTimeLoggerMap.get(app);
                appDateTimeList.add(logString);
            } else {
                appDateTimeList.add(logString);
            }
            appDateTimeLoggerMap.put(app, appDateTimeList);
        }

        @Override
        void startTimeoutDowntime(final DownTimeHandler handler, final String app) {}

        @Override
        void stopTimeoutDowntime(final DownTimeHandler handler, final String app, final float adjust) {
            if(!handler.getAppTimeoutStarted()) { //Don't do anything if timeout is not started!
                return;
            }
            handler.setLastSuccess();   // required if immediate app error after timeout.
            final SimpleDateFormat SIMPLE_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            handler.setState(STOPPED);
            final Long startTimeNew = System.currentTimeMillis() - ((System.nanoTime() - handler.getLastTimeoutSuccess().get()) / HAconstants.TEN_EXP_6);
            final long delta = System.nanoTime() - handler.getLastTimeoutSuccess().get() - (long) (adjust * HAconstants.TEN_EXP_9);

            if (!app.equals(HAPropertiesReader.SYSTEMVERIFIER) && (delta / HAconstants.TEN_EXP_9) < 20 ) {
                logger.info("{} return : {}",app, delta / HAconstants.TEN_EXP_9); //ignore if dt less than 20s
                return;
            }
            handler.incrementDowntime(delta); //add timeout to total app DT.
            handler.incrementTimeoutDowntime(delta);
            final Date instanceTime = new Date();
            Map<Date, Date> startStopTimeoutMapforTimeout = new HashMap<>();
            Map<Date, Date> startStopTimeoutMap  = new HashMap<>();
            List<String> appDateTimeTimeoutList = new ArrayList<>();

            if (appDateTimeTimeoutMap.containsKey(app)) {
                startStopTimeoutMapforTimeout = appDateTimeTimeoutMap.get(app);
                startStopTimeoutMapforTimeout.put(new Date(startTimeNew), instanceTime); //append to existing List
                appDateTimeTimeoutMap.put(app, startStopTimeoutMapforTimeout);
            } else {
                startStopTimeoutMapforTimeout.put(new Date(startTimeNew), instanceTime);
                appDateTimeTimeoutMap.put(app, startStopTimeoutMapforTimeout);
            }
            //DateTIme map should also contain timeout -- for delta DT calculation. Timeout map is only for display!
            if (appDateTimeMap.containsKey(app)) {
                startStopTimeoutMap = appDateTimeMap.get(app);
                startStopTimeoutMap.put(new Date(startTimeNew), instanceTime); //append to existing List
                appDateTimeMap.put(app, startStopTimeoutMap);
            } else {
                startStopTimeoutMap.put(new Date(startTimeNew), instanceTime);
                appDateTimeMap.put(app, startStopTimeoutMap);
            }

            final String msg = String.format(MESSAGE_TIMEOUT, app, "stopped", SIMPLE_DATE_FORMAT.format(instanceTime),
                    String.format(", [+%.02fs], [=%.02fs]", (float) delta / HAconstants.TEN_EXP_9, handler.getAccurateDowntime()));

            logger.warn(msg);
            logger.debug("Adjust time is : {}s", adjust);
            logger.info("App Timeout Map :" + app + " startTime: " + SIMPLE_DATE_FORMAT.format(new Date(startTimeNew)) + ", stopTime: " + SIMPLE_DATE_FORMAT.format(instanceTime));

            final String logString = "Downtime started at : " + SIMPLE_DATE_FORMAT.format(new Date(startTimeNew)) + ", stopTime: " + SIMPLE_DATE_FORMAT.format(instanceTime) +
                    String.format(", [+%.02fs], [=%.02fs]", (float) delta / HAconstants.TEN_EXP_9, handler.getAccurateTimeoutDowntime());
            if (appDateTimeTimeoutLoggerMap.containsKey(app)) {
                appDateTimeTimeoutList = appDateTimeTimeoutLoggerMap.get(app);
                appDateTimeTimeoutList.add(logString);
            } else {
                appDateTimeTimeoutList.add(logString);
            }
            appDateTimeTimeoutLoggerMap.put(app, appDateTimeTimeoutList);
        }
    };

    private static final Logger logger = LogManager.getLogger(DowntimeSM.class);
    private static final String MESSAGE = "%s downtimer %s at [%s]%s";
    private static final String MESSAGE_TIMEOUT = "%s Timeout downtimer %s at [%s]%s";

    public static Date getDate(final float adjust) {
        final Calendar instance = Calendar.getInstance();
        instance.add(Calendar.SECOND, (int) (-adjust));
        return instance.getTime();
    }

    public static Map<String, Map<Date, Date>> appDateTimeMap = new HashMap<>();
    public static Map<String, Map<Date, Date>> appDateTimeTimeoutMap = new HashMap<>();

    public static Map<String, List<String>> appDateTimeLoggerMap = new HashMap<>();
    public static Map<String, List<String>> appDateTimeTimeoutLoggerMap = new HashMap<>();

    abstract void startDowntime(final DownTimeHandler handler, final String app);
    abstract void startTimeoutDowntime(final DownTimeHandler handler, final String app);

    abstract void stopDowntime(final DownTimeHandler handler, final String app, final float adjust);
    abstract void stopDowntimeWithTimeStamp(final DownTimeHandler handler, final String app, final long time, final Date date);
    abstract void stopTimeoutDowntime(final DownTimeHandler handler, final String app, final float adjust);

}