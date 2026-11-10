package com.collabtech.platform.performance.domain.repositories;
import com.collabtech.platform.performance.domain.model.AttributionModels.*;
import java.util.*;
public interface AttributionRepository {
    Optional<Mechanism> mechanism(UUID id);
    Optional<Mechanism> byToken(String token);
    void create(Mechanism mechanism);
    void updateStatus(Mechanism mechanism,long expectedVersion);
    Optional<Interaction> event(UUID report,String eventKey);
    Optional<Interaction> interaction(UUID id);
    void save(Interaction interaction);
}
