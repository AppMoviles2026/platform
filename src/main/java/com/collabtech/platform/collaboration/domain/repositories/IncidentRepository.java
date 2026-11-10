package com.collabtech.platform.collaboration.domain.repositories;

import com.collabtech.platform.collaboration.domain.model.Incident;
import java.util.Optional;
import java.util.UUID;

public interface IncidentRepository {
    Optional<Incident> byId(UUID id);
    Optional<Incident> byProposal(UUID proposalId);
    Incident save(Incident incident);
}
