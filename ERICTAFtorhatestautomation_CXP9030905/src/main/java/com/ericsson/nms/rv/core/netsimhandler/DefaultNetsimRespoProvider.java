package com.ericsson.nms.rv.core.netsimhandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.ericsson.cifwk.taf.handlers.netsim.NetSimCommandHandler;
import com.ericsson.cifwk.taf.handlers.netsim.domain.NetworkElement;
import com.ericsson.nms.rv.core.netsimhandler.iterator.NetsimRepository;

/**
 * Default implementation of the NetsimProvider which will get all the available nodes
 * from all the netsims.
 */
class DefaultNetsimRespoProvider extends NetsimRepositoryAbstract {

    private static final Logger logger = LogManager.getLogger(DefaultNetsimRespoProvider.class);
    private final NetSimCommandHandler netsimCommandHandler;
    private final Map<String, List<NetworkElement>> neListCache;
    public static Map<String, String> netsimWorkloadNodeMap = new HashMap<>();

    /**
     * Creates an instance of the DefaultNetsimRepoProvider.
     *
     * @param netsimCommandHandler the netsimCommangHandler object
     */
    DefaultNetsimRespoProvider(final NetSimCommandHandler netsimCommandHandler) {
        this.netsimCommandHandler = netsimCommandHandler;
        neListCache = netsimCommandHandler.getAllStartedNEs().stream().collect(Collectors.groupingBy(NetworkElement::getNodeType));
    }

    /**
     * The predicate if the java stream's filter
     */
    private static Predicate<NetworkElement> getPredicateOfStartedNEs(final String nodeType) {
        return networkElement -> networkElement.getNodeType().equals(nodeType);
    }

    public Map<String, List<NetworkElement>> getNeListCache() {
        return neListCache;
    }

    @Override
    protected NetsimRepository createNetsimRepository(final String nodeType, final List<String> list, final NodeInfo nodeInfo) throws HaNetsimHandlerException {

        logger.info("Creating Netsim Repository of Node type : {}.", nodeType);
        List<NetworkElement> neList = neListCache.get(nodeType);
        if (nodeType.equalsIgnoreCase("SpitFire") && neList == null) {
            neList = neListCache.get("R6672");
            if (neList == null) {
                neList = neListCache.get("R6675");
            }
        }
        if (neList == null) {
            try {
                if (nodeType.equalsIgnoreCase("SpitFire")) {
                    neList = netsimCommandHandler.getAllStartedNEs().parallelStream().filter(getPredicateOfStartedNEs("R6672")).sequential().collect(Collectors.toList());
                    if (neList.isEmpty()) {
                        neList = netsimCommandHandler.getAllStartedNEs().parallelStream().filter(getPredicateOfStartedNEs("R6675")).sequential().collect(Collectors.toList());
                    }
                } else {
                    neList = netsimCommandHandler.getAllStartedNEs().parallelStream().filter(getPredicateOfStartedNEs(nodeType)).sequential().collect(Collectors.toList());
                }
                neListCache.put(nodeType, neList);
                logger.info("amount of nodes of type {} started and ready to use: {} ", nodeType, neList.size());
            } catch (final IndexOutOfBoundsException e) {
                logger.warn("NetsimCommandHandler fails to get network elements ");
                throw new HaNetsimHandlerException("NetsimCommandHandler fails to get network elements ", e);
            }
        }

        List<String> nodeWithoutPrefix = new ArrayList<>();
            for (final String workloadNode : list) {
                try {
                    if (nodeType.equalsIgnoreCase("SpitFire") && !workloadNode.contains("ieatnetsim")) {
                        nodeWithoutPrefix.add(workloadNode);
                    } else if (nodeType.equalsIgnoreCase("BSC") && !workloadNode.contains("ieatnetsim")) {
                        nodeWithoutPrefix.add(workloadNode);
                    } else if (nodeType.equalsIgnoreCase(NodeType.RADIO.getType()) && !workloadNode.contains("ieatnetsim")) {
                        nodeWithoutPrefix.add(workloadNode);
                    } else if (nodeType.equalsIgnoreCase("ERBS") && workloadNode.contains("_")){
                        nodeWithoutPrefix.add(workloadNode.split("_")[1]);
                    }
                    for (final NetworkElement ne : neList) {
                        if (workloadNode.contains(ne.getName())) {
                            netsimWorkloadNodeMap.put(ne.getName(), workloadNode);
                        }
                    }
                } catch (final Exception e) {
                    logger.info("error in create Netsim Repository {}", e.getMessage());
                }
            }
            logger.info("netsimWorkloadNodeMap : {}", netsimWorkloadNodeMap.toString());

        final List<NetworkElement> networkElements = new ArrayList<>();

        if (list.isEmpty()) {
            networkElements.addAll(neList);
        } else {
                networkElements.addAll(neList.parallelStream().filter(networkElement -> nodeWithoutPrefix.contains(networkElement.getName())).sequential().collect(Collectors.toList()));
        }

        String hostname = StringUtils.EMPTY;
        if (!networkElements.isEmpty()) {
            hostname = networkElements.get(0).getHostName();
        }

        final NetsimRepository netsimRepository = new NetsimRepository(networkElements, nodeInfo, hostname, nodeType);
        repositoryCache.put(nodeType, netsimRepository);
        logger.info("Repository of Node type {} created Successfully.", nodeType);
        return netsimRepository;
    }
}