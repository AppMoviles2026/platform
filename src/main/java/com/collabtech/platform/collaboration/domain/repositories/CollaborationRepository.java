package com.collabtech.platform.collaboration.domain.repositories;
import com.collabtech.platform.collaboration.domain.model.Collaboration;
import java.util.*;
public interface CollaborationRepository {
    Optional<Collaboration> byId(UUID id,boolean lock);
    Optional<Collaboration> byAgreement(UUID agreementId);
    Collaboration save(Collaboration collaboration);
}
