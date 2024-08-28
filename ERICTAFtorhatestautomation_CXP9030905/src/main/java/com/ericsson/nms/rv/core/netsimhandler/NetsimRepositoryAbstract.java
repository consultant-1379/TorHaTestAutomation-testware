package com.ericsson.nms.rv.core.netsimhandler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.ericsson.nms.rv.core.netsimhandler.iterator.NetsimRepository;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/**
 * Abstract NetsimRepository which contains the logic to get a NetsimRepository.
 */
abstract class NetsimRepositoryAbstract implements INetsimRepository {

    static final Map<String, NetsimRepository> repositoryCache = new HashMap<>();
    private static final Logger logger = LogManager.getLogger(NetsimRepository.class);
    /**
     * Returns the next netsim repository available to work with.
     *
     * @param nodeType the node type
     * @param nodeInfo the node info
     * @throws HaNetsimHandlerException if there are not enough nodes
     */
    @Override
    public NetsimRepository getNetsimRepository(final String nodeType, final List<String> list, final NodeInfo nodeInfo) throws HaNetsimHandlerException {

        final NetsimRepository netsimRepository = repositoryCache.get(nodeType);
        if (netsimRepository != null) {
            logger.info("Get Netsim Repository : {}", netsimRepository);
            if (netsimRepository.getIterator().hasNext()) {
                netsimRepository.setNodeInfo(nodeInfo);
                return netsimRepository;
            } else {
                repositoryCache.remove(nodeType);
                return createNetsimRepository(nodeType, list, nodeInfo);
            }

        } else {
            return createNetsimRepository(nodeType, list, nodeInfo);
        }
    }

    /**
     * creates the next netsim repository available to work with.
     *
     * @param nodeType the node type
     * @param nodeInfo the node info
     * @throws HaNetsimHandlerException if there are not enough nodes
     */
    protected abstract NetsimRepository createNetsimRepository(final String nodeType, final List<String> list, final NodeInfo nodeInfo) throws HaNetsimHandlerException;


}
