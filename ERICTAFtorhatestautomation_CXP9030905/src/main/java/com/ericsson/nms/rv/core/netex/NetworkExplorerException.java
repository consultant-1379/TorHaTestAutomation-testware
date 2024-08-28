package com.ericsson.nms.rv.core.netex;

import com.ericsson.oss.testware.availability.common.exception.EnmErrorType;
import com.ericsson.oss.testware.availability.common.exception.EnmException;

public class NetworkExplorerException extends EnmException {

    /**
     * Creates a new {@code NetworkExplorerException} object.
     *
     * @param message the detail message
     */
    public NetworkExplorerException(final String message) {
        super(message);
    }

    /**
     * Creates a new {@code NetworkExplorerException} object.
     *
     * @param rootCause the root cause
     */
    public NetworkExplorerException(final Throwable rootCause) {
        super(rootCause);
    }

    /**
     * Creates a new {@code NetworkExplorerException} object.
     *
     * @param message   the detail message
     * @param rootCause the root cause
     */
    public NetworkExplorerException(final String message, final Throwable rootCause) {
        super(message, rootCause);
    }

    /**
     * Creates a new {@code NetworkExplorerException} object.
     *
     * @param message   the detail message
     * @param errorType the error type
     */
    public NetworkExplorerException(final String message, final EnmErrorType errorType) {
        super(message, errorType);

    }

    /**
     * @param message   the detail message
     * @param errorType the error type
     * @param rootCause the root cause
     */
    NetworkExplorerException(final String message, final EnmErrorType errorType, final EnmException rootCause) {
        super(message, errorType, rootCause);
    }
}