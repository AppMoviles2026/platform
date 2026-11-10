package com.collabtech.platform.collaboration.infrastructure.acl;

import com.collabtech.platform.collaboration.interfaces.acl.CollaborationExecutionFacade;
import com.collabtech.platform.collaboration.domain.model.Collaboration;
import com.collabtech.platform.collaboration.domain.repositories.CollaborationRepository;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;

@Component @Profile("!skeleton")
public class CollaborationExecutionAdapter implements CollaborationExecutionFacade {
    private final CollaborationRepository collaborations;
    public CollaborationExecutionAdapter(CollaborationRepository collaborations){this.collaborations=collaborations;}
    @Transactional(readOnly=true)
    public Optional<Execution> find(UUID id){return collaborations.byId(id,false).map(this::facts);}
    @Transactional(propagation=Propagation.MANDATORY)
    public Optional<Execution> findForUpdate(UUID id){return collaborations.byId(id,true).map(this::facts);}
    private Execution facts(Collaboration c) {
        boolean fulfilled=c.obligations().stream().noneMatch(Collaboration.Obligation::outstanding);
        var money=c.snapshot().compensation();
        return new Execution(c.id(),c.agreementId(),c.snapshot().campaignId(),c.snapshot().brandId(),c.snapshot().creatorId(),
                c.status().name(),c.completedAt(),c.cancelledAt(),c.openIncidentCount(),fulfilled,
                fulfilled&&c.completedAt()!=null&&c.cancelledAt()==null&&c.openIncidentCount()==0&&c.status()==Collaboration.Status.COMPLETED,
                c.version(),new Compensation(money.type(),money.amount(),money.currency(),money.description()));
    }
}
