package com.collabtech.platform.collaboration.application.services;
import com.collabtech.platform.collaboration.application.ports.CollaborationPorts.*;
import com.collabtech.platform.collaboration.domain.model.*;
import com.collabtech.platform.collaboration.domain.repositories.*;
import com.collabtech.platform.collaboration.domain.exceptions.CollaborationFailure;
import com.collabtech.platform.collaboration.domain.exceptions.CollaborationFailure.Code;
import com.collabtech.platform.shared.application.pagination.*;
import java.time.*;
import java.util.*;

public final class CollaborationService {
    private final Participants participants;private final SelectedTerms terms;private final UnitOfWork work;
    private final AgreementRepository agreements;private final CollaborationRepository collaborations;private final Reads reads;private final Events events;private final Clock clock;
    public CollaborationService(Participants participants,SelectedTerms terms,UnitOfWork work,AgreementRepository agreements,
            CollaborationRepository collaborations,Reads reads,Events events,Clock clock){
        this.participants=participants;this.terms=terms;this.work=work;this.agreements=agreements;this.collaborations=collaborations;this.reads=reads;this.events=events;this.clock=clock;
    }
    public record AgreementView(UUID id,AgreementSnapshot snapshot,Agreement.Status status,Instant brandAcceptedAt,UUID brandAcceptedBy,
            Instant creatorAcceptedAt,UUID creatorAcceptedBy,Instant decidedAt,UUID rejectedBy,long version,UUID collaborationId,List<String> availableActions){}
    public record Progress(UUID deliverableId,UUID specificationId,String contentType,String description,int requiredQuantity,int approvedQuantity,Instant deadline,boolean overdue){}
    public record CollaborationView(UUID id,UUID agreementId,AgreementSnapshot snapshot,Collaboration.Status status,Instant startedAt,Instant expiredAt,
            long version,Instant nextDeadline,int requiredQuantity,int approvedQuantity,List<Progress> deliverables,List<String> pendingActions,List<String> availableActions){}
    public AgreementView propose(UUID account,UUID application){return work.execute(()->{
        var actor=participants.actor(account);var snapshot=terms.capture(account,application,clock.instant());
        var existing=agreements.byApplication(application,true);
        if(existing.isPresent()){authorize(actor,existing.get().snapshot());return agreementView(existing.get(),actor);}
        authorize(actor,snapshot);var a=Agreement.propose(snapshot,clock.instant());agreements.save(a);return agreementView(a,actor);
    });}
    public AgreementView agreement(UUID account,UUID id){return work.execute(()->{var actor=participants.actor(account);var a=agreement(id,false);authorize(actor,a.snapshot());return agreementView(a,actor);});}
    private record DecisionResult(AgreementView view,Code error){}
    public AgreementView decide(UUID account,UUID id,UUID termsVersion,Agreement.Decision decision){
        var result=work.execute(()->{
            var actor=participants.actor(account);var a=agreement(id,true);authorize(actor,a.snapshot());
            if(!a.snapshot().termsVersion().equals(termsVersion))throw new CollaborationFailure(Code.TERMS_CHANGED);
            if(a.expire(clock.instant())){agreements.save(a);return new DecisionResult(agreementView(a,actor),Code.TERMS_EXPIRED);}
            if(a.status()==Agreement.Status.PENDING)requireActiveParticipants(a.snapshot());
            a.decide(actor.party(),actor.accountId(),termsVersion,decision,clock.instant());agreements.save(a);
            if(a.status()==Agreement.Status.ACCEPTED)activate(a);
            return new DecisionResult(agreementView(a,actor),null);
        });
        if(result.error()!=null)throw new CollaborationFailure(result.error());return result.view();
    }
    public CollaborationView create(UUID account,UUID agreementId){return work.execute(()->{
        var actor=participants.actor(account);var a=agreement(agreementId,true);authorize(actor,a.snapshot());
        return collaborationView(activate(a),actor);
    });}
    private Collaboration activate(Agreement a){
        var existing=collaborations.byAgreement(a.id());if(existing.isPresent())return existing.get();
        if(a.status()!=Agreement.Status.ACCEPTED)throw new CollaborationFailure(Code.AGREEMENT_NOT_ACCEPTED);
        requireActiveParticipants(a.snapshot());var c=Collaboration.start(a);collaborations.save(c);
        events.append(c.id(),"CollaborationCreated",c.snapshot(),c.startedAt());return c;
    }
    public CollaborationView collaboration(UUID account,UUID id){return work.execute(()->{
        var actor=participants.actor(account);var c=collaborations.byId(id,false).orElseThrow(()->new CollaborationFailure(Code.NOT_FOUND));
        authorize(actor,c.snapshot());return collaborationView(c,actor);
    });}
    public PageResult<AgreementView> agreements(UUID account,Agreement.Status status,PageRequest page){return work.execute(()->{
        var actor=participants.actor(account);var data=reads.agreements(actor,status,page,clock.instant());
        return new PageResult<>(data.items().stream().map(a->agreementView(a,actor)).toList(),data.total(),data.page(),data.size());
    });}
    public PageResult<CollaborationView> collaborations(UUID account,Collaboration.Status status,PageRequest page){return work.execute(()->{
        var actor=participants.actor(account);var data=reads.collaborations(actor,status,page,clock.instant());
        return new PageResult<>(data.items().stream().map(c->collaborationView(c,actor)).toList(),data.total(),data.page(),data.size());
    });}
    public void expireDue(){
        for(var id:work.execute(()->reads.dueAgreements(clock.instant(),100)))work.execute(()->{var a=agreement(id,true);if(a.expire(clock.instant()))agreements.save(a);return null;});
        for(var id:work.execute(()->reads.dueCollaborations(clock.instant(),100)))work.execute(()->{
            var c=collaborations.byId(id,true).orElseThrow();if(c.expire(clock.instant())){collaborations.save(c);events.append(c.id(),"CollaborationExpired",c.snapshot(),c.expiredAt());}return null;});
    }
    private Agreement agreement(UUID id,boolean lock){return agreements.byId(id,lock).orElseThrow(()->new CollaborationFailure(Code.NOT_FOUND));}
    private void requireActiveParticipants(AgreementSnapshot s){if(!participants.active(s.brandId(),Agreement.Party.BRAND)||!participants.active(s.creatorId(),Agreement.Party.CREATOR))throw new CollaborationFailure(Code.ACCOUNT_NOT_ACTIVE);}
    private void authorize(Actor a,AgreementSnapshot s){if(!a.profileId().equals(a.party()==Agreement.Party.BRAND?s.brandId():s.creatorId()))throw new CollaborationFailure(Code.FORBIDDEN);}
    private AgreementView agreementView(Agreement a,Actor actor){
        var status=a.effectiveStatus(clock.instant());boolean accepted=actor.party()==Agreement.Party.BRAND?a.brandAcceptedAt()!=null:a.creatorAcceptedAt()!=null;
        var actions=status==Agreement.Status.PENDING&&!accepted?List.of("ACCEPT_TERMS","REJECT_TERMS"):List.<String>of();
        return new AgreementView(a.id(),a.snapshot(),status,a.brandAcceptedAt(),a.brandAcceptedBy(),a.creatorAcceptedAt(),a.creatorAcceptedBy(),a.decidedAt(),a.rejectedBy(),a.version(),
                collaborations.byAgreement(a.id()).map(Collaboration::id).orElse(null),actions);
    }
    private CollaborationView collaborationView(Collaboration c,Actor actor){
        var now=clock.instant();var status=c.effectiveStatus(now);int required=c.obligations().stream().mapToInt(Collaboration.Obligation::quantity).sum();
        int approved=c.obligations().stream().mapToInt(Collaboration.Obligation::approvedQuantity).sum();
        boolean needsDelivery=approved<required&&status!=Collaboration.Status.COMPLETED&&status!=Collaboration.Status.CANCELLED;
        var pending=needsDelivery?List.of(actor.party()==Agreement.Party.CREATOR?(status==Collaboration.Status.EXPIRED?"SUBMIT_LATE_DELIVERABLE":"SUBMIT_DELIVERABLE"):"WAIT_FOR_DELIVERY"):List.<String>of();
        return new CollaborationView(c.id(),c.agreementId(),c.snapshot(),status,c.startedAt(),c.expiredAt(),c.version(),c.nextDeadline(),required,approved,
                c.obligations().stream().map(d->new Progress(d.id(),d.specificationId(),d.contentType(),d.description(),d.quantity(),d.approvedQuantity(),d.deadline(),d.outstanding()&&!d.deadline().isAfter(now))).toList(),pending,List.of());
    }
}
