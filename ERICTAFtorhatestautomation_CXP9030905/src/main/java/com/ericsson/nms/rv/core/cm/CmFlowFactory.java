package com.ericsson.nms.rv.core.cm;

import java.util.List;

import com.ericsson.nms.rv.core.EnmApplication;
import com.ericsson.nms.rv.core.cm.components.GetNeProductVersion;
import com.ericsson.nms.rv.core.cm.components.GetNeType;
import com.ericsson.nms.rv.core.cm.components.GetNodeModelIdentity;
import com.ericsson.nms.rv.core.cm.components.GetOssModelIdentity;
import com.ericsson.nms.rv.core.cm.components.GetOssPrefix;
import com.ericsson.nms.rv.core.cm.components.GetPlatformType;
import com.ericsson.nms.rv.core.cm.components.GetRelease;
import com.ericsson.nms.rv.core.cm.components.GetTechnologyDomain;
import com.ericsson.nms.rv.core.node.Node;


class CmFlowFactory {
    private CmFlowFactory() {
    }

    static CmComposite createCompleteCmFlow(final EnmApplication enmApplication,
                                            final List<Node> cmNodesList,
                                            final int numberOfSyncNodes) {
        final CmComposite cmComposite = new CmComposite(enmApplication, cmNodesList, numberOfSyncNodes);

        cmComposite.add(new GetNeProductVersion());
        cmComposite.add(new GetNeType());
        cmComposite.add(new GetNodeModelIdentity());
        cmComposite.add(new GetOssModelIdentity());
        cmComposite.add(new GetOssPrefix());
        cmComposite.add(new GetPlatformType());
        cmComposite.add(new GetRelease());
        cmComposite.add(new GetTechnologyDomain());

        return cmComposite;
    }
}
