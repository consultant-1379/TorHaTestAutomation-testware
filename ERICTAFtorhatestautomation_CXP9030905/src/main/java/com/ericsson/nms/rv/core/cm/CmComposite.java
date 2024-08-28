package com.ericsson.nms.rv.core.cm;

import java.util.ArrayList;
import java.util.List;

import com.ericsson.nms.rv.core.EnmApplication;
import com.ericsson.nms.rv.core.node.Node;
import com.ericsson.nms.rv.core.util.HaTimeoutException;
import com.ericsson.oss.testware.availability.common.exception.EnmErrorType;
import com.ericsson.oss.testware.availability.common.exception.EnmException;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class CmComposite {
    private static final Logger logger = LogManager.getLogger(CmComposite.class);

    private static final List<CmComponent> components = new ArrayList<>();

    private final CmContext cmContext;

    private final EnmApplication enmApplication;

    CmComposite(final EnmApplication enmApplication, final List<Node> cmNodesList, final int numberOfSyncNodes) {
        cmContext = new CmContext(enmApplication, cmNodesList, numberOfSyncNodes);
        this.enmApplication=enmApplication;
    }

    public void add(final CmComponent cmComponent) {
        components.add(cmComponent);
    }

    void clear() {
        components.clear();
    }

    void execute(final CmThreadExecutor cmThreadExecutor) throws EnmException, HaTimeoutException {
        for (final CmComponent component : this.components) {
                component.execute(this.cmContext, cmThreadExecutor);
        }
    }
}