package com.ericsson.nms.rv.core.nbi;

import java.util.ArrayList;
import java.util.List;

import com.ericsson.nms.rv.core.util.HaTimeoutException;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.ericsson.de.tools.cli.CliToolShell;
import com.ericsson.nms.rv.core.EnmApplication;
import com.ericsson.nms.rv.core.node.Node;
import com.ericsson.oss.testware.availability.common.exception.EnmException;

public class NbiFlow {

    private static final Logger logger = LogManager.getLogger(NbiFlow.class);

    private final List<NbiComponent> nbiServiceComponents;
    private final NbiContext nbiContext;

    public NbiFlow(final List<Node> nbiNodesList, final EnmApplication enmApplication) {
        nbiServiceComponents = new ArrayList<>();
        nbiContext = new NbiContext(nbiNodesList, enmApplication);
    }

    public boolean isAlarmReceived() {
        return nbiContext.isAlarmReceived();
    }

    public void add(final NbiComponent nbiComponent) {
        nbiServiceComponents.add(nbiComponent);
    }

    public void clear() {
        nbiServiceComponents.clear();
    }



    public void execute(long threadId) throws EnmException, HaTimeoutException {
        for (final NbiComponent component : this.nbiServiceComponents) {
            try {
                component.execute(this.nbiContext,threadId);
            }  catch (final Exception e) {
                logger.error("ThreadId: {} Error on nbi component, trying to close ssh connection if object is not null: {}", threadId, e.getMessage());
                final CliToolShell shell = nbiContext.getShell();
                if (shell != null) {
                    shell.close();
                    logger.error("ThreadId: {} Error on nbi component, connection closed", threadId);
                }
                nbiContext.getEnmApplication().stopAppTimeoutDownTime();
                nbiContext.getEnmApplication().startAppDownTime();
                throw e;
            }
        }
    }

    public void execute() throws EnmException, HaTimeoutException {
        for (final NbiComponent component : this.nbiServiceComponents) {
            try {
                component.execute(this.nbiContext);
            } catch (final Exception e) {
                logger.error("Error on nbi component, trying to close ssh connection if object is not null: {}", e.getMessage());
                final CliToolShell shell = nbiContext.getShell();
                if (shell != null) {
                    shell.close();
                    logger.error("Error on nbi component, connection closed");
                }
                throw e;
            }
        }
    }
}