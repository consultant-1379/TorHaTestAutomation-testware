package com.ericsson.nms.rv.core.downtime;

import java.time.LocalDateTime;

public class DowntimeEvent {
    private final LocalDateTime localDateTime;
    private final State state;

    public DowntimeEvent(final LocalDateTime localDateTime, final State state) {
        this.localDateTime = localDateTime;
        this.state = state;
    }

    @Override
    public String toString() {
        return "DowntimeEvent{" +
                "localDateTime=" + localDateTime +
                ", state=" + state +
                '}';
    }

    public LocalDateTime getLocalDateTime() {
        return localDateTime;
    }

    public State getState() {
        return state;
    }

    public enum State {
        START, STOP
    }
}

