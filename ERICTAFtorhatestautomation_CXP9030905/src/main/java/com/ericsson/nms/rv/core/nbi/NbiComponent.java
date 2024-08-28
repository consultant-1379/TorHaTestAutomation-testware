package com.ericsson.nms.rv.core.nbi;


import com.ericsson.nms.rv.core.util.HaTimeoutException;
import com.ericsson.oss.testware.availability.common.exception.EnmException;

public interface NbiComponent {
    void execute(final NbiContext context) throws EnmException, HaTimeoutException;
    void execute(final NbiContext context,long threadId) throws EnmException, HaTimeoutException;
}
