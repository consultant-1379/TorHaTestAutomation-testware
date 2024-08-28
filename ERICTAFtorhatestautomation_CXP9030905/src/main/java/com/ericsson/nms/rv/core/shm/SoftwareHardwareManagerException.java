package com.ericsson.nms.rv.core.shm;

import com.ericsson.oss.testware.availability.common.exception.EnmErrorType;
import com.ericsson.oss.testware.availability.common.exception.EnmException;

public class SoftwareHardwareManagerException extends EnmException {

    /**
     * Creates a new {@code SoftwareHardwareManagerException} object.
     *
     * @param message the detail message
     */
    public SoftwareHardwareManagerException(final String message) {
        super(message);
    }

    /**
     * Creates a new {@code SoftwareHardwareManagerException} object.
     *
     * @param rootCause the root cause
     */
    public SoftwareHardwareManagerException(final Throwable rootCause) {
        super(rootCause);
    }

    /**
     * Creates a new {@code SoftwareHardwareManagerException} object.
     *
     * @param message   the detail message
     * @param rootCause the root cause
     */
    public SoftwareHardwareManagerException(final String message, final Throwable rootCause) {
        super(message, rootCause);
    }

    /**
     * Creates a new {@code SoftwareHardwareManagerException} object.
     *
     * @param message   the detail message
     * @param errorType the error type
     */
    public SoftwareHardwareManagerException(final String message, final EnmErrorType errorType) {
        super(message, errorType);

    }

}