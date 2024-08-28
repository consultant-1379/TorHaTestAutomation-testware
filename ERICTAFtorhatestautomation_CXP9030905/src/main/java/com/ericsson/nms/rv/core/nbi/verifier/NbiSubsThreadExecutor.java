package com.ericsson.nms.rv.core.nbi.verifier;


import com.ericsson.nms.rv.core.nbi.NbiFlow;
import com.ericsson.nms.rv.core.node.Node;
import com.ericsson.nms.rv.core.util.CommonUtils;
import com.ericsson.nms.rv.core.util.HaTimeoutException;
import com.ericsson.oss.testware.availability.common.exception.EnmException;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import java.util.List;

public class NbiSubsThreadExecutor implements Runnable {
    private static final Logger logger = LogManager.getLogger(NbiSubsThreadExecutor.class);
    private final NbiFlow nbiFlow;
    final List<Node> nbiNodesList;
    private Thread worker;
    private boolean completed;
    private boolean timedOut;
    private boolean ErrorOccurance;
    private long threadId;

    public boolean isCompleted() {
        return completed;
    }


    public boolean isErrorOcurred() {
        return ErrorOccurance;
    }


    public boolean isTimedOut() {
        return timedOut;
    }

    public long getID() {
        return worker.getId();
    }

    NbiSubsThreadExecutor(List nbiNodesList,NbiFlow nbiFlow) {
        this.nbiNodesList = nbiNodesList;
        this.nbiFlow = nbiFlow;
    }

    public void execute () {
        worker = new Thread(this);
        threadId = worker.getId();
        worker.start();
    }


    @Override
    public void run() {
        try {
            logger.info("ThreadId: {} Entered into the NbiSubsThread....", threadId);
            executeNbiCurrentFlow(threadId);
            completed = true;
        }
        catch (EnmException e) {
            logger.info("ThreadId: {} Error occured in NBI createSubs : ", threadId, e);
            ErrorOccurance = true;
        } catch (HaTimeoutException e) {
            logger.info("ThreadId: {} NBI createSubs timed out", threadId, e);
            timedOut = true;
        } catch (Exception e) {
            logger.info("ThreadId: {} NBI createSubs failed ", threadId, e);
            ErrorOccurance = true;
        } finally {
            CommonUtils.sleep(1);
            Thread.currentThread().stop();
        }
    }

    private void executeNbiCurrentFlow(long threadId) throws EnmException,HaTimeoutException {
        try {
            if (nbiNodesList.isEmpty()) {
                logger.info("ThreadId: {} nothing to verify nbi nodes are empty", threadId);
            } else {
                nbiFlow.execute(threadId);
            }
        } catch (final EnmException | HaTimeoutException e) {
            logger.error("ThreadId: {} error executing nbi service {} ",threadId, e);
            throw e;
        }
    }


}
