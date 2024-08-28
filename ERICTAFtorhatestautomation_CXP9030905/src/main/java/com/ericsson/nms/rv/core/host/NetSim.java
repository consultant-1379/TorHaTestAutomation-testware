package com.ericsson.nms.rv.core.host;

import static com.ericsson.nms.rv.taf.tools.Constants.EMPTY_STRING;

import com.ericsson.cifwk.taf.data.DataHandler;
import com.ericsson.cifwk.taf.data.Host;
import com.ericsson.cifwk.taf.handlers.netsim.domain.NetworkElement;
import com.ericsson.nms.rv.core.node.Node;
import com.ericsson.nms.rv.core.util.CliResult;
import com.ericsson.nms.rv.core.util.CliShell;
import com.ericsson.nms.rv.taf.tools.host.HaHost;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import java.util.Random;


/**
 * {@code NetSim} represents a NETSim host.
 */
public class NetSim extends HaHost {
    private static final Logger logger = LogManager.getLogger(NetSim.class);

    private static final String SEND_ALARM_BURST_COMMAND = "/bin/echo -e \'alarmburst:freq=%d,num_alarms=%d,loop=false," +
            "idle_time=0,mo_class=no_value,mo_instance=\"%%mibprefix,ManagedElement=1,Equipment=1,Subrack=MS," +
            "Slot=%%unique{40}\",system_dn=\"%%mibprefix\",cause=%d,severity=%d,problem=\"%s\",type=ET_COMMUNICATIONS_ALARM," +
            "cease_after=%d,clear_after_burst=true,id=1234;\'| /netsim/inst/netsim_pipe -ne %s -sim %s";
    private static final String SEND_ROUTER_ALARM_BURST_COMMAND = "/bin/echo -e 'alarmburst:freq=1,num_alarms=%d,loop=false,idle_time=0," +
            "severity=warning,managed_object=\"SysM=1\",specific_problem=\"%s\"," +
            "probable_cause=m3100AlarmIndicationSignal,cease_after=1,clear_after_burst=true,id=1234;'" +
            "| /netsim/inst/netsim_pipe -ne %s -sim %s";
    private static String SEND_BSC_ALARM_BURST_COMMAND = "axealarmburst:name=\"HA-Test-Alarm\",freq=0.1,burst=%d,i_time=2,active=%d," +
            "text=\"unique 5\",prca=42,class=3,cat=0,id=%d,loop=\"false\",info1=\"%s\";";
    private Random random = new Random();
    /**
     * Creates a new {@code NetSim} object.
     *
     * @param name     the NetSim's host name or IP address
     * @param username the user's account
     * @param password the user's password
     */
    public NetSim(final String name, final String username, final String password) {
        super(name, name, EMPTY_STRING, username, password, null);
    }

    /**
     * Sends a burst of alarms to the given network element in Netsim.
     *
     * @param frequency the frequency in which alarms will be sent per second
     * @param alarms    the total number of alarms to be sent
     * @param cause     the numeric cause of the alarm
     * @param severity  the severity of the alarm
     * @param problem   the problem description of the alarm
     * @throws NetsimException if the alarm burst cannot be sent
     */
    public void sendAlarmBurst(final int frequency,
                               final int alarms, final int cause,
                               final int severity,
                               final String problem,
                               final NetworkElement ne) throws NetsimException {

        final String command = String.format(SEND_ALARM_BURST_COMMAND, frequency, alarms, cause, severity, problem, 1,
                ne.getName(), ne.getSimulationName());

        logger.info("Alarm Burst Command for FM: {}", command);
        final Host netsimHost = DataHandler.getHostByName(ne.getHostName());
        final CliShell shell = new CliShell(netsimHost);
        final CliResult result = shell.execute(command);

        if (!result.isSuccess()) {
            logger.info("FM send alarm result = {}", result.getOutput());
            throw new NetsimException("Failed to send alarm burst to " + ne.getName());
        }
    }

    public void sendAlarmBurstToRouter(final int alarms, final String problem, final NetworkElement ne) throws NetsimException {

        final CliResult result;
        final String command = String.format(SEND_ROUTER_ALARM_BURST_COMMAND, alarms, problem, ne.getName(), ne.getSimulationName());
        logger.info("Alarm Burst Command for FMR: {}", command);
        try {
            final Host netsimHost = DataHandler.getHostByName(ne.getHostName());
            final CliShell shell = new CliShell(netsimHost);
            result = shell.execute(command);
            if (!result.isSuccess()) {
                logger.info("FMR send alarm result = {}", result.getOutput());
                throw new NetsimException("Failed to send alarm burst to " + ne.getName());
            }
        } catch (final Exception e) {
            logger.error("Failed to send alarm to Router node: {}", e.getMessage());
            throw new NetsimException("Failed to send alarm burst to " + ne.getName());
        }
    }

    public void sendAlarmBurstToBsc(final int alarms, final String problem, final Node node) throws NetsimException {
        logger.info("Sending alarm with specific problem: {}", problem);
        final CliResult result;
        final int id = random.nextInt(90) + 10;
        final String command = String.format(SEND_BSC_ALARM_BURST_COMMAND, alarms, alarms, id, problem);
        logger.info("Alarm Burst Command for FMB: {}", command);
        try {
            final NetworkElement ne = node.getNetworkElement();
            final Host netsimHost = DataHandler.getHostByName(ne.getHostName());
            final String nodeIp = ne.getIp().contains(",") ? ne.getIp().split(",")[0] : ne.getIp();
            logger.info("Netsim: {}, node {} ip: {}", netsimHost.getHostname(), ne.getName(), nodeIp);
            final CliShell shell = new CliShell(netsimHost);
            result = shell.executeOnNode(command, nodeIp, node);
            logger.info("Send alarm result : {}", result.getOutput());
            if (!result.isSuccess()) {
                logger.info("FMB send alarm result = {}", result.getOutput());
                throw new NetsimException("Failed to send alarm burst to " + ne.getName());
            }
        } catch (final Exception e) {
            logger.error("Failed to send alarm to BSC node: {}", e.getMessage());
            throw new NetsimException("Failed to send alarm burst to " + node.getNetworkElement().getName());
        }
    }

    /**
     * Starts a given node in a Network Simulation.
     *
     * @param networkElementId the name of the network element that will be started
     * @throws NetsimException if the node cannot be started
     */
    public final boolean startNode(final NetworkElement ne, final String networkElementId) throws NetsimException {

        if (!ne.isStarted()) {
            if (!ne.start()) {
                throw new NetsimException("Failed to start the node " + networkElementId);
            }

            // wait for the node to start up before performing any other actions on it in the verification method
            try {
                Thread.sleep(30_000);
            } catch (final InterruptedException ignore) {
                logger.warn("Failed to startNode.", ignore);
            }
        }

        return true;
    }

    /**
     * Stops a given node in a Network Simulation.
     *
     * @param networkElementId the name of the network element that will be stopped
     * @throws NetsimException if the node cannot be stopped
     */
    public final boolean stopNode(final NetworkElement ne, final String networkElementId) throws NetsimException {

        if (ne.isStarted() && !ne.stop()) {
            throw new NetsimException("Failed to stop the node " + networkElementId);
        }
        return true;
    }

}
