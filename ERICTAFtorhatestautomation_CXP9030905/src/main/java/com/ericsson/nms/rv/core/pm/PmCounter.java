/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2017
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/

package com.ericsson.nms.rv.core.pm;

/**
 * {@code PmCounter} represents a pm counter.
 */
public final class PmCounter {

    /**
     * the group name for a subscription
     */
    private final String groupName;

    /**
     * the event counter for a subscription
     */
    private final String counterName;

    /**
     * Creates a new {@code PmCounter} object.
     *
     * @param groupName   the group name
     * @param counterName the counter name
     */
    public PmCounter(final String groupName, final String counterName) {
        this.groupName = groupName;
        this.counterName = counterName;
    }

    public final String getGroupName() {
        return groupName;
    }

    public final String getCounterName() {
        return counterName;
    }
}
