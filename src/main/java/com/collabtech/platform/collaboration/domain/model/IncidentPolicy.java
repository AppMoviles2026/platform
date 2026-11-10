package com.collabtech.platform.collaboration.domain.model;

import java.time.Duration;
import java.time.Instant;

/** User-approved policy: seven elapsed days from the recorded terminal fact; no inferred dates. */
public final class IncidentPolicy {
    public static final Duration RECENTLY_FINISHED = Duration.ofDays(7);
    public Instant closesAt(Collaboration collaboration) {
        Instant terminal = collaboration.cancelledAt() != null ? collaboration.cancelledAt() : collaboration.completedAt();
        return terminal == null ? null : terminal.plus(RECENTLY_FINISHED);
    }
    public boolean canOpen(Collaboration collaboration, Instant now) {
        var limit = closesAt(collaboration);
        if (limit != null) return now.isBefore(limit);
        return collaboration.status() != Collaboration.Status.COMPLETED && collaboration.status() != Collaboration.Status.CANCELLED;
    }
}
