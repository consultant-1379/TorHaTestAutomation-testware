package com.ericsson.nms.rv.core.cm.components;

import com.ericsson.nms.rv.core.cm.CmComponent;
import com.ericsson.nms.rv.core.cm.CmContext;
import com.ericsson.nms.rv.core.cm.CmThreadExecutor;
import com.ericsson.nms.rv.core.util.HaTimeoutException;
import com.ericsson.oss.testware.availability.common.exception.EnmException;

public class GetPlatformType implements CmComponent {

    @Override
    public void execute(final CmContext context,final CmThreadExecutor cmThreadExecutor) throws EnmException, HaTimeoutException {
        final String property = "platformType";
        runCommand(context, property, cmThreadExecutor);
    }
}