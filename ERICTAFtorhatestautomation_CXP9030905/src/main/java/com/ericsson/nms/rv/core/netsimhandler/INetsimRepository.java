package com.ericsson.nms.rv.core.netsimhandler;


import java.util.List;

import com.ericsson.nms.rv.core.netsimhandler.iterator.NetsimRepository;

@FunctionalInterface
interface INetsimRepository {

    /**
     * Returns the next netsim repository available to work with.
     *
     * @param nodeType the node type
     * @param nodeInfo the node info
     * @throws HaNetsimHandlerException if there are not enough nodes
     */
    NetsimRepository getNetsimRepository(final String nodeType, final List<String> list, final NodeInfo nodeInfo) throws HaNetsimHandlerException;

}