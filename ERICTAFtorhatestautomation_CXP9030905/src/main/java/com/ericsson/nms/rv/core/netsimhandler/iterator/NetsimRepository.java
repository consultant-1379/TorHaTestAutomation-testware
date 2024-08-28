package com.ericsson.nms.rv.core.netsimhandler.iterator;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.ericsson.cifwk.taf.data.DataHandler;
import com.ericsson.cifwk.taf.data.Host;
import com.ericsson.cifwk.taf.data.HostType;
import com.ericsson.cifwk.taf.handlers.netsim.domain.NetworkElement;
import com.ericsson.nms.rv.core.HAPropertiesReader;
import com.ericsson.nms.rv.core.host.NetSim;
import com.ericsson.nms.rv.core.netsimhandler.HaNetsimService;
import com.ericsson.nms.rv.core.netsimhandler.NodeInfo;
import com.ericsson.nms.rv.core.util.CliResult;
import com.ericsson.nms.rv.core.util.CliShell;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/**
 * Contains a list of network elements.
 */
public class NetsimRepository implements NetsimContainer {

    private static final Host netsimTaf = DataHandler.getHostByType(HostType.NETSIM);
    private static final Host workload = HAPropertiesReader.getWorkload();
    private static final String NODE_CREDENTIALS_COMMAND = "cli_app \"secadm credentials get --plaintext show --nodelist %s\"";
    private static final Logger logger = LogManager.getLogger(NetsimRepository.class);
    private final List<NetworkElement> neList;
    private final String nodeType;
    private final NetSim netsim;
    private final NetsimIterator netsimIterator;
    private NodeInfo nodeInfo;

    public NetsimRepository(final List<NetworkElement> neList,
                            final NodeInfo nodeInfo,
                            final String hostName,
                            final String nodeType) {
        this.neList = neList;
        this.nodeInfo = nodeInfo;
        this.nodeType = nodeType;
        netsim = new NetSim(hostName, netsimTaf.getUser(), netsimTaf.getPass());
        netsimIterator = new NetworkElementIterator();

    }

    public void setNodeInfo(final NodeInfo nodeInfo) {
        this.nodeInfo = nodeInfo;
    }

    @Override
    public NetsimIterator getIterator() {
        return netsimIterator;
    }

    /**
     * Iterator of the network elements.
     */
    private class NetworkElementIterator implements NetsimIterator {
        private final AtomicInteger index = new AtomicInteger(0);

        @Override
        public boolean hasNext() {
            synchronized (this) {
                return index.get() < neList.size();
            }
        }

        @Override
        public NodeInfo next() {
            if (hasNext()) {
                synchronized (this) {
                    final NetworkElement ne = neList.get(index.getAndIncrement());

                    final NodeInfo nodeInfoWrapper = new NodeInfo(nodeInfo);
                    final List<String> credList = nodeCredentials(ne);
                    nodeInfoWrapper.setIpAddress(ne.getIp());
                    nodeInfoWrapper.setNetworkElementId(ne.getName());
                    nodeInfoWrapper.setNetsim(netsim);
                    nodeInfoWrapper.setSecureUserName(credList.get(0));
                    nodeInfoWrapper.setSecureUserPassword(credList.get(1));
                    nodeInfoWrapper.setNeType(nodeType);
                    nodeInfoWrapper.setNetworkElement(ne);
                    return nodeInfoWrapper;
                }
            }
            return null;
        }

    }

    private static List<String> nodeCredentials(NetworkElement ne){
        final List<String> credList = new ArrayList<>();
        credList.add("netsim");
        credList.add("netsim");
        String workloadNodeName = HaNetsimService.netsimWorkloadNodeMap(ne.getName());;
        logger.info("workloadNode is : " + HaNetsimService.netsimWorkloadNodeMap(ne.getName()));
        logger.info("node type : {}", ne.getNodeType());
        try {
            final CliShell clishell = new CliShell(workload);
            final String command = String.format(NODE_CREDENTIALS_COMMAND, workloadNodeName);
            logger.info("command to get node credentials is : " + command);
            final CliResult result = clishell.execute(command);
            final String output = result.getOutput();
            logger.info("Output of cred command is : " + output);
            String[] lines = output.split("\n");
            String credLine = "";
            for (String line : lines) {
                if (line.contains("secureUserName")) {
                    credLine = line;
                    break;
                }
            }
            String[] split = credLine.split("secureUserName:");
            String[] credentials = split[1].split("secureUserPassword:");
            credList.set(0, credentials[0]);
            credList.set(1, credentials[1]);
        } catch (Exception e){
            logger.warn("Exception occur in getting node credentials : {}", e.getMessage());
            e.printStackTrace();
        }
        logger.info("secureUsername : " + credList.get(0) + " secureUserPassword : " + credList.get(1) + " for Node : "+ workloadNodeName);
        return credList;
    }
}
