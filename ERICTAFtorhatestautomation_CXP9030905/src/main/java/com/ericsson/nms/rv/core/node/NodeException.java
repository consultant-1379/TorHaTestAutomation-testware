package com.ericsson.nms.rv.core.node;

import com.ericsson.oss.testware.availability.common.exception.EnmException;

/**
 * A {@code NodeException} is thrown when an operation on a {@link Node} failed.
 */
public class NodeException extends EnmException {

    /**
     * Creates a new {@code NodeException} object.
     *
     * @param message the detail message
     */
    public NodeException(final String message) {
        super(message);
    }

}
