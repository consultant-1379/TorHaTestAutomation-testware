package com.ericsson.nms.rv.core.host;

import com.ericsson.oss.testware.availability.common.exception.EnmException;

/**
 * A {@code NetsimException} is thrown when an error occurs executing a command on a NETSim.
 */
public final class NetsimException extends EnmException {

    /**
     * Creates a new {@code NetsimException} object.
     *
     * @param message the detail message
     */
    public NetsimException(final String message) {
        super(message);
    }

}
