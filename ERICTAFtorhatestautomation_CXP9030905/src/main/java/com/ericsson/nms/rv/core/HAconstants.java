package com.ericsson.nms.rv.core;

import com.ericsson.nms.rv.taf.tools.Constants;

public class HAconstants extends Constants {

    public interface HAtime extends Time {
        /**
         * the ROP time in seconds
         */
        int ROP_TIME_IN_SECONDS = Time.ONE_MINUTE_IN_SECONDS * HAPropertiesReader.getRopTime();

    }
}
