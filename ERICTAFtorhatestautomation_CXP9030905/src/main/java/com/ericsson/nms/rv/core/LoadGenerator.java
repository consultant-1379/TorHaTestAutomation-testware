package com.ericsson.nms.rv.core;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.ericsson.nms.rv.core.netsimhandler.NodeType;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.ericsson.cifwk.taf.data.Host;
import com.ericsson.nms.rv.core.node.Node;
import com.ericsson.nms.rv.core.node.NodeParser;
import com.ericsson.nms.rv.core.util.CliResult;
import com.ericsson.nms.rv.core.util.CliShell;
import com.ericsson.nms.rv.taf.tools.Constants;

/**
 * Loads the nodes to be used
 */
public class LoadGenerator {
    private static final Host workload = HAPropertiesReader.getWorkload();
    private static final Logger logger = LogManager.getLogger(LoadGenerator.class);
    private static final List<Node> nodes = new ArrayList<>();
    private static final String GET_ALL_NODES_FROM_WORKLOAD = "/bin/grep 'Nodes used by HA' /var/log/enmutils/daemon/ha_01.log | /usr/bin/tail -1 | /bin/awk -F '[' '{print $2}' | /usr/bin/tr -d ']' | /bin/sed -e 's/,/\\n/g' | /bin/sort -u | /usr/bin/xargs | /bin/sed -e 's/ /,/g'";
    private static final String GET_NODES_NETSIM_FROM_WORKLOAD = "/bin/grep 'Nodes used by HA' /var/log/enmutils/daemon/ha_01.log | /usr/bin/tail -1 | /bin/awk -F '[' '{print $2}' | /usr/bin/tr -d \" ]'\" | /bin/sed -e 's/,/\\n/g' | /bin/awk -F '_' '{print $%s}' | /bin/sort -u | /usr/bin/xargs | /bin/sed -e 's/ /,/g' | /bin/grep -i '%s'";
    private static final String FLAGS_PATTERN = "[FPMNSETLRUBG]+";
    private static CliShell wlCliToolShell;
    public static boolean isRouterNodesAvailable = false;
    public static boolean isBscNodesAvailable = false;
    public static boolean isRadioNodesAvailable = false;
    public static boolean isErbsNodesAvailable = false;

    static {
        if (workload != null) {
            wlCliToolShell = new CliShell(workload);
        }
    }
    /**
     * Creates a new {@code LoadGenerator} object.
     */
    private LoadGenerator() {
    }

    public static void initialiseLoad()  {
        if (nodes.isEmpty()) {
            if (workload == null) {
                logger.warn("WORKLOAD has not properly configured.");
            } else {
                logger.warn("adding nodes from WORKLOAD");
                addNodeFromWorkload();
            }
        }
    }

    public static List<Node> getNodes() {
        return nodes;
    }

    public static List<Node> getAllNodesWithFlag(final String flag) {
        return addAllNodes(nodes, flagsContains(flag));
    }

    private static List<Node> addAllNodes(final List<Node> nodes, final Predicate<Node> predicate) {
        return nodes.parallelStream().filter(predicate).sequential().collect(Collectors.toList());
    }

    private static Predicate<Node> flagsContains(final String str) {
        return node -> node.getFlags().contains(str);
    }

    private static Predicate<Node> flagsMatches() {
        return node -> node.getFlags().matches(FLAGS_PATTERN);
    }

    private static void addNodeFromWorkload() {
        final List<String> nodesFromWorkload = new ArrayList<>();
        final List<Node> parseNodes = new ArrayList<>();
        nodesFromWorkload.addAll(getAllNodesFromWorkload());

        if (!nodesFromWorkload.isEmpty()) {
            final List<String> netSimsFromWorkload = getNetSimsFromWorkload();
            parseNodes.addAll(getParseNodes(nodesFromWorkload, netSimsFromWorkload));
        } else {
            logger.warn("List of nodes from Workload is EMPTY!!!");
        }
        nodes.addAll(addAllNodes(parseNodes, flagsEmpty().or(flagsMatches())));
        for (Node node : nodes) {
            if (node.getNodeType().equalsIgnoreCase("SpitFire")) {
                isRouterNodesAvailable = true;
            } else if (node.getNodeType().equalsIgnoreCase("BSC")) {
                isBscNodesAvailable = true;
            } else if (node.getNodeType().equalsIgnoreCase(NodeType.RADIO.getType())) {
                isRadioNodesAvailable = true;
            } else if (node.getNodeType().equalsIgnoreCase(NodeType.LTE.getType())) {
                isErbsNodesAvailable = true;
            }
            logger.info("Workload Nodes : " + node.getNetworkElementId() + "," + node.getFlags() + "," + node.getNodeType() +
                    ","+node.getNetworkElement());
        }
    }

    private static Predicate<Node> flagsEmpty() {
        return node -> node.getFlags().isEmpty();
    }

    private static List<Node> getParseNodes(final List<String> nodesFromWorkload, final List<String> netSimsFromWorkload) {
        final List<Node> parseSimulations = new ArrayList<>();
        parseSimulations.addAll(new NodeParser().parseNetsimConfiguration(nodesFromWorkload, netSimsFromWorkload));

        return parseSimulations;
    }

    /**
     * Returns {@code true} if there are nodes in the simulation.
     *
     * @return {@code true} if there are nodes in the simulation
     */
    public static boolean hasNodes() {
        return !nodes.isEmpty();
    }

    private static List<String> getAllNodesFromWorkload() {
        final List<String> list = new ArrayList<>();
        CliResult execResult;
        int retry = 0;
        do {
            execResult = wlCliToolShell.execute(GET_ALL_NODES_FROM_WORKLOAD);
            logger.warn("execResult: {}, ExitCode: {}", execResult.getOutput(), execResult.getExitCode());
            logger.info("workload Ip: {}", workload.getIp());
            if (execResult.isSuccess() && execResult.getExitCode() == 0) {
                final String[] split = execResult.getOutput().split(",");
                if (split.length > 0) {
                    list.addAll(Stream.of(split).collect(Collectors.toList()));
                } else {
                    try {
                        Thread.sleep(60L * (long) Constants.TEN_EXP_3);
                    } catch (final Exception e) {
                        logger.info("exception in thread sleep {}", e.getMessage());
                    }
                }
            } else {
                try {
                    Thread.sleep(60L * (long) Constants.TEN_EXP_3);
                } catch (final Exception e) {
                    logger.info("exception in thread sleep {}", e.getMessage());
                }
            }
            logger.info("retry {} execResult {} ExitCode {} ", retry, execResult.isSuccess(), execResult.getExitCode());
        } while (list.isEmpty() && retry++ < 3);
        logger.info("List of All HA_01 Nodes from WorkLoad : {}", list);
        return list;
    }

    private static List<String> getNetSimsFromWorkload() {
        final List<String> list = new ArrayList<>();

        final String command = String.format(GET_NODES_NETSIM_FROM_WORKLOAD, "1", "netsim");
        int retry = 0;
        do {
            final CliResult execResult = wlCliToolShell.execute(command);
            final String result = execResult.getOutput();
            logger.info("getNetSimsFromWorkload execResult : {}", result);
            if (execResult.getExitCode() == 0) {
                if (result.contains("exitcode")) {
                    final String[] resultList = result.split("\n");
                    for (final String resultString : resultList) {
                        if (resultString.contains("ieatnetsim")) {
                            logger.info("resultString : {}", resultString);
                            if (resultString.contains(",")) {
                                final String[] netsims = resultString.split(",");
                                for (String entry : netsims) {
                                    if (entry.contains("ieatnetsim")) {
                                        list.add(entry);
                                    }
                                }
                            } else if (resultString.contains("ieatnetsim")) {
                                list.add(resultString);
                            }
                        } else {
                            logger.warn("Ignoring resultString : {}", resultString);
                        }
                    }
                } else {
                    final String[] split = execResult.getOutput().split(",");
                    for (String entry : split) {
                        if (entry.contains("ieatnetsim")) {
                            list.add(entry);
                        }
                    }
                }
            }
            if (list.isEmpty()) {
                try {
                    Thread.sleep(60L * (long) Constants.TEN_EXP_3);
                } catch (final Exception e) {
                    logger.info("exception in thread sleep {}", e.getMessage());
                }
            }
        } while (list.isEmpty() && retry++ < 3);
        if (list.size() < 3) {
            logger.warn("\n================ Warning ================\nEnvironment does not have enough number of Netsims required to execute test cases." +
                    " Testware may not execute properly. Available number of Netsims : {}.\n================ Warning ================\n", list.size());
        }
        logger.info("List of Netsims for ERBS nodes from WorkLoad : {}", list);
        return list;
    }

    public static boolean verifyAllnodesAreAdded(final List<Node> nodeList) {
        final List<Node> nodesNotAdded = nodeList.parallelStream().filter(node -> !node.isNodeAdded()).sequential().collect(Collectors.toList());
        if (!nodesNotAdded.isEmpty()) {
            nodesNotAdded.parallelStream().forEach(node -> logger.error("network element: {} failed some step of preparation, could not be used", node.getNetworkElementId()));
            return false;
        }
        return true;
    }

}
