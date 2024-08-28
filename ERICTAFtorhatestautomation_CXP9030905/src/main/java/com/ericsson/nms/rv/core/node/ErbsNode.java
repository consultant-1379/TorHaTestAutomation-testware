package com.ericsson.nms.rv.core.node;

import static com.ericsson.nms.rv.taf.tools.Constants.EMPTY_STRING;

import com.ericsson.nms.rv.core.util.HaTimeoutException;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.ericsson.nms.rv.core.netsimhandler.NodeInfo;
import com.ericsson.oss.testware.availability.common.exception.EnmException;

/**
 * {@code ErbsNode} represents a network node of type {@code ERBS}.
 */
public final class ErbsNode extends Node {

    private static final Logger logger = LogManager.getLogger(ErbsNode.class);
    /**
     * the command to create the security credentials
     */
    private static final String CREATE_SECURITY_CREDENTIALS_COMMAND =
            "secadm credentials create --rootusername u1 --rootuserpassword pw1 --secureusername %s --secureuserpassword %s --normalusername u3 --normaluserpassword pw3 -n %s";

    /**
     * Creates a new {@code ErbsNode} object.
     *
     * @param nodeInfo the tokens parsed by {@code NodeInfo}
     */
    public ErbsNode(final NodeInfo nodeInfo) {
        super(nodeInfo);
    }

    @Override
    public String getNodeType() {
        return "ERBS";
    }

}
