package com.collabtech.platform.collaboration.application.services;

import com.collabtech.platform.collaboration.application.ports.CollaborationPorts.*;
import com.collabtech.platform.collaboration.application.ports.HistoryPorts.*;
import com.collabtech.platform.collaboration.application.ports.IncidentPorts.*;
import com.collabtech.platform.collaboration.domain.model.Collaboration;
import com.collabtech.platform.shared.application.pagination.*;
import java.util.UUID;

/** History reads reuse participant authorization and the original agreement snapshot. */
public final class CollaborationHistoryService {
    public record Resources(String agreement,String deliveries,String incidents,String timeline) {}
    public record Detail(CollaborationService.CollaborationView collaboration,
                         CollaborationService.AgreementView agreement,Resources resources,References references) {}
    private final CollaborationService service;
    private final UnitOfWork work;
    private final History history;
    private final RelatedReferences references;
    public CollaborationHistoryService(CollaborationService service,UnitOfWork work,History history,RelatedReferences references) {
        this.service=service;this.work=work;this.history=history;this.references=references;
    }
    public PageResult<CollaborationService.CollaborationView> list(UUID account,Collaboration.Status status,PageRequest page) {
        return service.collaborations(account,status,page);
    }
    public Detail detail(UUID account,UUID id) {
        return work.execute(()->{
            var c=service.collaboration(account,id);
            var agreement=service.agreement(account,c.agreementId());
            return new Detail(c,agreement,new Resources("/api/v1/agreements/"+c.agreementId(),
                    "/api/v1/collaborations/"+id+"/deliverables","/api/v1/collaborations/"+id+"/incidents",
                    "/api/v1/collaborations/"+id+"/history"),references.read(id));
        });
    }
    public PageResult<Entry> timeline(UUID account,UUID id,PageRequest page) {
        return work.execute(()->{service.collaboration(account,id);return history.list(id,page);});
    }
}
