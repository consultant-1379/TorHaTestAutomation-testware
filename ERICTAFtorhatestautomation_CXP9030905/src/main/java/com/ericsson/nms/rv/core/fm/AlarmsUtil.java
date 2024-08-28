package com.ericsson.nms.rv.core.fm;

import java.util.List;

import com.ericsson.cifwk.taf.data.DataHandler;
import com.ericsson.cifwk.taf.data.Host;
import com.ericsson.cifwk.taf.handlers.netsim.domain.NetworkElement;
import com.ericsson.nms.rv.core.host.NetsimException;
import com.ericsson.nms.rv.core.util.CliResult;
import com.ericsson.nms.rv.core.util.CliShell;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.ericsson.cifwk.taf.handlers.netsim.CommandOutput;
import com.ericsson.cifwk.taf.handlers.netsim.NetSimCommand;
import com.ericsson.cifwk.taf.handlers.netsim.commands.NetSimCommands;
import com.ericsson.nms.rv.core.node.Node;

public class AlarmsUtil {

    private static final Logger logger = LogManager.getLogger(AlarmsUtil.class);

    public void ceasealarmAll(final List<Node> nodes, final Class application) {
        NetSimCommand ceasealarmAll = NetSimCommands.newComponent().setObject("ceasealarm:all;");
        logger.info("ceasealarm all for {} command: {} ", application.getName(), "ceasealarm:all;");
        try {
            for (final Node node : nodes) {
                logger.info("ceasealarm all for {} node ", node.getNetworkElementId());

                CommandOutput[] output =
                        node.getNetworkElement().exec(ceasealarmAll).getOutput();
                logCommandOutput(output, "ceasealarm:all;");
            }
        } catch (final Exception e) {
            logger.error("Failed to Cease alarm: {}", e.getMessage());
        }
    }

    public void ceaseAlarmAllBsc(final List<Node> nodes) {
        CliResult result;
        final String command = "ceasealarm:all;";
        logger.info("ceasealarm all for FM-BSC command: {} ", command);
        for (final Node node : nodes) {
            try {
                final NetworkElement ne = node.getNetworkElement();
                final Host netsimHost = DataHandler.getHostByName(ne.getHostName());
                logger.info("ceasealarm all for {} node ", node.getNetworkElementId());
                final String nodeIp = ne.getIp().contains(",") ? ne.getIp().split(",")[0] : ne.getIp();
                logger.info("Netsim: {}, node {} ip: {}", netsimHost.getHostname(), ne.getName(), nodeIp);
                final CliShell shell = new CliShell(netsimHost);
                result = shell.executeOnNode(command, nodeIp, node);
                logger.info("cease alarm result : {}", result.getOutput());

                if (!result.isSuccess()) {
                    logger.info("FMB cease alarm result = {}", result.getOutput());
                }
            } catch (final Exception e) {
                logger.error("Failed to Cease alarm for BSC node: {}", e.getMessage());
            }
        }
    }

    /**
     * Check if the netsim's command output was success
     */
    private void logCommandOutput(final CommandOutput[] output, final String comandName) {

        for (final CommandOutput netsimCmdOutput : output) {
            logger.debug("output of command {}: {}", comandName, netsimCmdOutput.getRawOutput());
            if (netsimCmdOutput.getRawOutput().contains("OK")) {
                logger.debug("command was successfully executed");
            }
        }
    }

}
