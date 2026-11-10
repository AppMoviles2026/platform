package com.collabtech.platform.collaboration.application.ports;

import com.collabtech.platform.shared.application.pagination.*;
import java.time.Instant;
import java.util.*;

public final class HistoryPorts {
    private HistoryPorts() {}
    public record Entry(UUID id, UUID collaborationId, long sequence, String type, UUID actorAccountId,
                        Instant occurredAt, UUID sourceId, boolean reconstructed, Map<String,Object> details) {}
    public interface History {
        void record(UUID collaboration, String type, UUID source, UUID actor, Instant now, Map<String,Object> details);
        void initialize(UUID collaboration);
        PageResult<Entry> list(UUID collaboration, PageRequest page);
        List<UUID> uninitialized(int limit);
    }
}
