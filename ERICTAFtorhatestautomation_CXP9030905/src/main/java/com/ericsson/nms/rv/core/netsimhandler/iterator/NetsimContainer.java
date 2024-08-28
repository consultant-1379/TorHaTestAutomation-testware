package com.ericsson.nms.rv.core.netsimhandler.iterator;

/*
* Netsim container to iterate on all the netsim availables
* */
@FunctionalInterface
interface NetsimContainer {
    NetsimIterator getIterator();
}
