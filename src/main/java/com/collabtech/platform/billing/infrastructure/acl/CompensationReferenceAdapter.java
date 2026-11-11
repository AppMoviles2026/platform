package com.collabtech.platform.billing.infrastructure.acl;
import com.collabtech.platform.billing.interfaces.acl.CompensationReferenceFacade;
import com.collabtech.platform.billing.application.services.CompensationService;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
@Component @Profile("!skeleton")
public class CompensationReferenceAdapter implements CompensationReferenceFacade {
    private final CompensationService service;
    public CompensationReferenceAdapter(CompensationService service){this.service=service;}
    public Optional<UUID> find(UUID id){return service.reference(id);}
}
