package com.collabtech.platform.collaboration.application.ports;

import com.collabtech.platform.collaboration.domain.model.*;
import com.collabtech.platform.shared.application.pagination.*;
import java.time.Instant;
import java.util.UUID;

public final class IncidentPorts {
    private IncidentPorts() {}
    public interface Queries {
        int openCount(UUID collaborationId);
        PageResult<Incident> list(UUID collaborationId, Incident.Status status, PageRequest page);
        PageResult<Incident.Proposal> proposals(UUID incidentId, PageRequest page);
    }
    public interface Events { void append(IncidentEvent event); }
    public interface Extensions {
        void save(UUID deliverableId, Deliverable.CorrectionExtension extension);
    }
    public interface RelatedReferences {
        References read(UUID collaborationId);
    }
    public record Reference(UUID collaborationId, String availability, String resourceId) {}
    public record References(Reference compensation, Reference performance) {}
}
