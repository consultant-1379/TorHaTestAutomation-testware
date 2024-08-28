package com.ericsson.nms.rv.core.nbi.component;

import static java.lang.Thread.sleep;

import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.ericsson.nms.rv.core.HAPropertiesReader;
import com.ericsson.nms.rv.core.netsimhandler.NodeType;
import com.ericsson.oss.testware.availability.common.exception.EnmErrorType;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.ericsson.nms.rv.core.host.NetSim;
import com.ericsson.nms.rv.core.host.NetsimException;
import com.ericsson.nms.rv.core.nbi.AlarmParser;
import com.ericsson.nms.rv.core.nbi.NbiComponent;
import com.ericsson.nms.rv.core.nbi.NbiContext;
import com.ericsson.nms.rv.core.nbi.error.NbiException;
import com.ericsson.nms.rv.core.node.Node;
import com.ericsson.nms.rv.taf.tools.Constants;
import com.ericsson.oss.testware.availability.common.exception.EnmException;

public class NbiVerifyAlarms implements NbiComponent {
    private static final Logger logger = LogManager.getLogger(NbiVerifyAlarms.class);

    private static final AlarmParser alarmParser = new AlarmParser();
    private static final int NUMBER_OF_ALARMS = 1;
    private static final Map<String, Set<String>> alarmMap = new ConcurrentHashMap<>();

    @Override
    public void execute(final NbiContext context) throws EnmException {
        long threadId = 0L;
        if (context.getShell() != null) {
            for (final Node node : context.getNbiNodes()) {
                final NetSim netsim = node.getNetsim();
                final Random random = new Random(System.currentTimeMillis());
                int randomNumber = random.nextInt();
                String problem = String.format("HA Testing %d", randomNumber);
                logger.info("verifying alarms, alarms expected: {} ", NUMBER_OF_ALARMS);
                context.setHaProblem(problem);
                if(verifyAlarms(context, netsim, node, problem, threadId)) {
                    alarmMap.remove(problem);
                }
            }
        } else {
            logger.error("CorbaCLICommandHelper cannot be null to verify alarms");
        }
    }

    @Override
    public void execute(final NbiContext context,long threadId) throws  EnmException {

        if (context.getShell() != null) {
            for (final Node node : context.getNbiNodes()) {
                final NetSim netsim = node.getNetsim();
                final Random random = new Random(System.currentTimeMillis());
                int randomNumber = random.nextInt();
                String problem = String.format("HA Testing %d", randomNumber);
                logger.info("ThreadId: {} verifying alarms, alarms expected: {} ", threadId, NUMBER_OF_ALARMS);
                context.setHaProblem(problem);
                if(verifyAlarms(context, netsim, node, problem, threadId)) {
                    alarmMap.remove(problem);
                }
            }
        } else {
            logger.error("ThreadId: {} CorbaCLICommandHelper cannot be null to verify alarms", threadId);
        }
    }

    private void sendAlarm(final NetSim netsim, final Node node, final String problem, long threadId) throws NetsimException {
        logger.info("ThreadId: {} Sent alarm burst to {} with problem {} ne netsim {}, ne ip {}, waiting for alarm", threadId, node.getNetworkElementId(), problem, node.getNetsim(), node.getNetworkElement().getIp());

        if (node.getNodeType().equalsIgnoreCase(NodeType.LTE.getType())) {
            netsim.sendAlarmBurst(Constants.FREQUENCY,
                    NUMBER_OF_ALARMS,
                    2,
                    2,
                    problem,
                    node.getNetworkElement());
        } else if (node.getNodeType().equalsIgnoreCase(NodeType.RADIO.getType())) {
            node.getNetsim().sendAlarmBurstToRouter(NUMBER_OF_ALARMS, problem, node.getNetworkElement());
        }
    }

    private int getAlarms(final String output, final NbiContext context, long threadId, String problem) throws EnmException {
        try {
            alarmParser.readAlarmsOutput(output, alarmMap);
            logger.info("ThreadId: {} problem: {} alarmSet: {}", threadId, problem, alarmMap.get(problem));
            return alarmMap.get(problem).size();
        } catch (final NbiException e) {
            logger.error("ThreadId: {} connection error on getting alarms from the ssh connection, " +
                    "the connection should be already created and should be not lose if nbi services goes down.", threadId, e);
            context.getEnmApplication().startAppDownTime();
            throw new EnmException("NBI get alarms have failed: " + e.getMessage(), EnmErrorType.APPLICATION);
        }
    }

    private boolean verifyAlarms(final NbiContext context, final NetSim netsim, final Node node, final String problem, long threadId) throws EnmException {
        boolean isAlarmInBuffer = false;
        try {
            alarmMap.put(problem, new HashSet<>());
            sendAlarm(netsim, node, problem, threadId);
            if (waitForAllAlarm(context, threadId, problem)) {
                logger.info("ThreadId: {} alarm were received on NBI client,", threadId);
                isAlarmInBuffer = true;
                context.getEnmApplication().stopAppDownTime();
                context.getEnmApplication().stopAppTimeoutDownTime();
            } else {

                logger.info("ThreadId: {} alarms were not received on time, trying to send alarm again before report failure,", threadId);
                sendAlarm(netsim, node, problem, threadId);

                if (waitForAllAlarm(context, threadId, problem)) {
                    logger.info("ThreadId: {} alarm were received on NBI client on second attempt, nbi is working", threadId);
                    context.getEnmApplication().setFirstTimeFail(true);
                    context.getEnmApplication().stopAppDownTime();
                    context.getEnmApplication().stopAppTimeoutDownTime();
                    isAlarmInBuffer = true;

                } else {
                    logger.info("ThreadId: {} alarm were not received on NBI client on second attempt ", threadId);
                    if (context.getEnmApplication().isFirstTimeFail()) {
                        context.getEnmApplication().setFirstTimeFail(false);
                    } else {
                        context.getEnmApplication().startAppDownTime();
                    }
                }
            }
        } catch (final Exception e) {
            logger.error("ThreadId: {} problem in sending or verifying the fm alarm {}", threadId, e.getMessage());
            isAlarmInBuffer = false;
        }
        context.setIsAlarmReceived(isAlarmInBuffer);
        return isAlarmInBuffer;
    }

    private boolean waitForAllAlarm(final NbiContext context, long threadId, String problem) throws EnmException {

        int totalAlarms = 0;
        final long start = System.nanoTime();

        if(HAPropertiesReader.isEnvCloudNative()) {
            logger.info("ThreadId: {} context.getFmVipAddress() : {}", threadId, NbiContext.getFmVipAddress());
        } else {
            logger.info("ThreadId: {} context.getVisinamingPub() : {}", threadId, context.getVisinamingPub());
        }

        String output;
        int alarmsReceived;
        while (System.nanoTime() - start < context.getTimeToWait()) {
            output = context.getShell().getBufferedShell().getOutput();
            logger.info("ThreadId: {} output in verify alarms is : {}", threadId, output);

            if (output.contains("Killed")) {
                final String createSubscription = "./testclient.sh subscribe category 1f1 filter \"'%s'~\\$f\" nshost %s subscriptionTime 1440; echo \"taf.jsch.exitcode=0\"";
                final String hostIp = (HAPropertiesReader.isEnvCloudNative())? NbiContext.getFmVipAddress() : context.getVisinamingPub();
                final String command = String.format(createSubscription, context.getNbiNodes().get(0).getNetworkElementId(), hostIp);
                logger.warn("ThreadId: {} TestClient has been killed externally .. restarting client again.", threadId);
                logger.warn("ThreadId: {} COMMAND: {}", threadId, command);

                boolean isSuccess;
                do {
                    try {
                        context.getShell().execute(command);
                        sleep(3L);
                        isSuccess = true;
                    } catch (final IllegalStateException | InterruptedException ex) {
                        context.getEnmApplication().setFirstTimeFail(true);
                        context.getEnmApplication().stopAppDownTime();
                        context.getEnmApplication().startAppTimeoutDowntime();
                        logger.warn("ThreadId: {} Message: {}", threadId, ex.getMessage());
                        logger.warn("ThreadId: {} Failed to initialize test client (Timeout) .. Retrying", threadId);
                        isSuccess = false;
                    } catch (final Exception e) {
                        logger.warn("ThreadId: {} Message: {}", threadId, e.getMessage());
                        context.getEnmApplication().stopAppTimeoutDownTime();
                        if (context.getEnmApplication().isFirstTimeFail()) {
                            context.getEnmApplication().setFirstTimeFail(false);
                        } else {
                            context.getEnmApplication().startAppDownTime();
                        }
                        logger.warn("ThreadId: {} Failed to initialize test client .. Retrying", threadId);
                        isSuccess = false;
                    }
                } while(!isSuccess);
                return false;
            }
            alarmsReceived = getAlarms(output, context, threadId, problem);

            logger.info("ThreadId: {} alarms received on iteration: {}", threadId, alarmsReceived);
            totalAlarms += alarmsReceived;

            logger.debug("ThreadId: {} cli's output: {}", threadId, output);

            if (totalAlarms >= NUMBER_OF_ALARMS) {
                logger.info("ThreadId: {} time to receive alarm: {} seconds", threadId, (System.nanoTime() - start) / Constants.TEN_EXP_9);
                logger.info("ThreadId: {} total alarms received: {}", threadId, totalAlarms);
                return true;
            }
        }

        logger.info("ThreadId: {} the complete alarms were not received on nbi client, the total alarms are: {} from {} expected", threadId, totalAlarms, NUMBER_OF_ALARMS);
        logger.info("ThreadId: {} time to receive {} alarm: {} seconds", threadId, totalAlarms, (System.nanoTime() - start) / Constants.TEN_EXP_9);

        return false;
    }

}