package com.ericsson.nms.rv.core.util;

import com.ericsson.oss.testware.availability.common.exception.EnmErrorType;

public class HaTimeoutException extends Exception {

    private final EnmErrorType enmErrorType;

    protected HaTimeoutException() {
        this.enmErrorType = EnmErrorType.WARNING;
    }

    public HaTimeoutException(String message) {
        super(message);
        this.enmErrorType = EnmErrorType.WARNING;
    }

    public HaTimeoutException(Throwable rootCause) {
        super(rootCause);
        this.enmErrorType = EnmErrorType.WARNING;
    }

    public HaTimeoutException(String message, Throwable rootCause) {
        super(message, rootCause);
        this.enmErrorType = EnmErrorType.WARNING;
    }

    public HaTimeoutException(String message, EnmErrorType errorType) {
        super(message);
        this.enmErrorType = errorType;
    }
}

