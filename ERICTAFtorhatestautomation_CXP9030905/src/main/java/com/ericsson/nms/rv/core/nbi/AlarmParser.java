package com.ericsson.nms.rv.core.nbi;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.ericsson.nms.rv.core.nbi.error.ErrorType;
import com.ericsson.nms.rv.core.nbi.error.NbiException;


public class AlarmParser {
    private static final Logger logger = LogManager.getLogger(AlarmParser.class);

    public List<String> getAlarms(final String text, final List<String> alarmList, final String aPattern) throws NbiException {
        parseConnectionError(text);
        final Pattern pattern = Pattern.compile(aPattern);
        final Matcher matcher = pattern.matcher(text);

        while (matcher.find()) {
            alarmList.add(matcher.group());
        }
        return alarmList;
    }

    public void readAlarmsOutput(final String output, final Map<String, Set<String>> alarmMap) throws NbiException {
        parseConnectionError(output);
        String[] alarmOutputList = output.split("Received an Event");
        for (String alarm : alarmOutputList) {
            String NV_SPECIFIC_PROBLEM = "";
            String NV_ALARM_ID = "";
            String[] lines = alarm.split("\n");
            for (String line : lines) {
                if (line.contains("NV_SPECIFIC_PROBLEM")) {
                    NV_SPECIFIC_PROBLEM = line.split(":")[1].trim();
                }
                if (line.contains("NV_ALARM_ID")) {
                    NV_ALARM_ID = line.split(":")[1].trim();
                }
            }
            if (!NV_SPECIFIC_PROBLEM.isEmpty() && !NV_ALARM_ID.isEmpty()) {
                Set<String> alarmSet;
                if(alarmMap.containsKey(NV_SPECIFIC_PROBLEM)) {
                    alarmSet = alarmMap.get(NV_SPECIFIC_PROBLEM);
                } else {
                    alarmSet = new HashSet<>();
                }
                alarmSet.add(NV_ALARM_ID);
                alarmMap.put(NV_SPECIFIC_PROBLEM, alarmSet);
            }
        }
    }

    public String getSubscriptionId(final String output) throws NbiException {
        parseConnectionError(output);

        final Pattern pattern = Pattern.compile("Subscription id = \\d*");
        final Matcher matcher = pattern.matcher(output);
        String subscriptionId = StringUtils.EMPTY;
        if (matcher.find()) {
            subscriptionId = matcher.group().split("Subscription id = ")[1].trim();
            logger.info("subscription Id found {} ", subscriptionId);
        }
        return subscriptionId;
    }

    private void parseConnectionError(final String output) throws NbiException {
        final String connectionProblem = "couldn't reconnect to";

        if (StringUtils.isNotEmpty(output) && output.contains(connectionProblem)) {
            throw new NbiException("Connection to visinamingnb has failed. ", ErrorType.CONNECTION);
        }
    }

}
