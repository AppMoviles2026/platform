package com.collabtech.platform.collaboration.domain.repositories;
import com.collabtech.platform.collaboration.domain.model.Agreement;
import java.util.*;
public interface AgreementRepository {
    Optional<Agreement> byId(UUID id,boolean lock);
    Optional<Agreement> byApplication(UUID applicationId,boolean lock);
    Agreement save(Agreement agreement);
}
