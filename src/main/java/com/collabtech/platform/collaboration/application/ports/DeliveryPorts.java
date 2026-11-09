package com.collabtech.platform.collaboration.application.ports;

import com.collabtech.platform.collaboration.domain.model.*;
import com.collabtech.platform.shared.application.pagination.*;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public final class DeliveryPorts {
    private DeliveryPorts() {}
    public record Asset(UUID id, UUID collaborationId, UUID ownerAccountId, String filename,
                        String mediaType, long byteSize, String sha256, Instant createdAt) {}
    public record InspectedFile(String filename, String mediaType, long byteSize, String sha256) {}
    public record Command(String operation, String fingerprint, UUID resultId) {}
    public interface Attachments {
        Optional<Asset> metadata(UUID id);
        void save(Asset metadata, byte[] bytes);
        byte[] bytes(UUID id);
        boolean referenced(UUID assetId);
    }
    public interface FileInspector {
        InspectedFile inspect(String filename, String declaredMediaType, byte[] bytes);
    }
    public interface Commands {
        Optional<Command> find(UUID collaboration, UUID actor, UUID request);
        void save(UUID collaboration, UUID actor, UUID request, Command command, Instant now);
        String fingerprint(Object command);
    }
    public interface Queries {
        PageResult<Deliverable> list(UUID collaboration, Deliverable.Status status, PageRequest page);
        PageResult<Deliverable.Revision> revisions(UUID deliverable, PageRequest page);
    }
    public interface DeliveryEvents { void append(DeliveryEvent event); }
}
