package com.ericsson.nms.rv.core.netsimhandler.iterator;

import com.ericsson.nms.rv.core.netsimhandler.NodeInfo;

/**
 * Interface of the netsim iterator design pattern
 */
public interface NetsimIterator {
    boolean hasNext();

    NodeInfo next();
}
