package com.collabtech.platform.collaboration.domain.repositories;

import com.collabtech.platform.collaboration.domain.model.Deliverable;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeliverableRepository {
    Optional<Deliverable> byId(UUID id);
    Optional<Deliverable> bySubmission(UUID submissionId);
    List<Deliverable> forCollaboration(UUID collaborationId);
    /** Must be called while holding the parent collaboration lock, inside the same unit of work. */
    Deliverable save(Deliverable deliverable);
}
