package com.collabtech.platform.collaboration.application.services;

import com.collabtech.platform.collaboration.application.ports.CollaborationPorts.*;
import com.collabtech.platform.collaboration.application.ports.DeliveryPorts.*;
import com.collabtech.platform.collaboration.application.ports.IncidentPorts;
import com.collabtech.platform.collaboration.domain.model.*;
import com.collabtech.platform.collaboration.domain.repositories.*;
import com.collabtech.platform.collaboration.domain.exceptions.CollaborationFailure;
import com.collabtech.platform.collaboration.domain.exceptions.CollaborationFailure.Code;
import com.collabtech.platform.shared.application.pagination.*;
import java.time.*;
import java.util.*;

public final class IncidentService {
    public record Open(UUID requestId,Incident.Category category,String description,UUID deliverableId,List<DeliveryContent.Reference> evidence){}
    public record Propose(UUID requestId,long expectedVersion,Incident.Terms terms){}
    public record Decide(UUID requestId,long expectedVersion,Incident.Decision decision,String reason){}
    public record View(UUID id,UUID collaborationId,UUID openedBy,Incident.Category category,String description,
                       UUID deliverableId,List<DeliveryContent.Reference> evidence,Instant openedAt,Incident.Status status,
                       Instant resolvedAt,long version,Incident.Proposal currentResolution,List<String> availableActions){}
    public record Result(View incident,UUID affectedResolutionId,boolean replayed){}
    private record Scope(Actor actor,Collaboration collaboration,Incident incident){}
    private record Fingerprint(String operation,UUID target,Object body){}

    private final Participants participants;
    private final UnitOfWork work;
    private final CollaborationRepository collaborations;
    private final DeliverableRepository deliveries;
    private final IncidentRepository incidents;
    private final IncidentPorts.Queries queries;
    private final IncidentPorts.Extensions extensions;
    private final IncidentPorts.Events events;
    private final Commands commands;
    private final Attachments attachments;
    private final FileInspector files;
    private final IncidentPolicy policy;
    private final Clock clock;

    public IncidentService(Participants participants,UnitOfWork work,CollaborationRepository collaborations,
                           DeliverableRepository deliveries,IncidentRepository incidents,IncidentPorts.Queries queries,
                           IncidentPorts.Extensions extensions,IncidentPorts.Events events,Commands commands,
                           Attachments attachments,FileInspector files,IncidentPolicy policy,Clock clock) {
        this.participants=participants;this.work=work;this.collaborations=collaborations;this.deliveries=deliveries;
        this.incidents=incidents;this.queries=queries;this.extensions=extensions;this.events=events;this.commands=commands;
        this.attachments=attachments;this.files=files;this.policy=policy;this.clock=clock;
    }

    public Result open(UUID account,UUID collaboration,Open body) {
        return work.execute(()->{
            var actor=participants.actor(account);var c=collaboration(collaboration,true);authorize(actor,c);
            String hash=hash("OPEN_INCIDENT",c.id(),body);
            var replay=replay(c,actor,body.requestId(),"OPEN_INCIDENT",hash);
            if(replay.isPresent())return replay.get();
            var now=clock.instant();
            if(!policy.canOpen(c,now))throw new CollaborationFailure(Code.INCIDENT_WINDOW_CLOSED);
            if(body.deliverableId()!=null)piece(c,body.deliverableId());
            evidence(c,actor,body.evidence());
            var incident=Incident.open(c.id(),actor.accountId(),body.category(),body.description(),body.deliverableId(),body.evidence(),now);
            int previous=c.openIncidentCount();
            incidents.save(incident);
            c.refreshAfterIncidents(deliveries.forCollaboration(c.id()),queries.openCount(c.id()),now);
            collaborations.save(c);
            emit(c,incident,actor,"IncidentOpened",now);
            if(previous==0)emit(c,incident,actor,"CollaborationDisputed",now);
            record(c,actor,body.requestId(),"OPEN_INCIDENT",hash,incident.id(),now);
            return new Result(view(incident,c,actor),null,false);
        });
    }

    public Result propose(UUID account,UUID incidentId,Propose body) {
        return work.execute(()->{
            var s=scope(account,incidentId,true);var c=s.collaboration();var incident=s.incident();var actor=s.actor();
            String hash=hash("PROPOSE_RESOLUTION",incidentId,body);
            var replay=replay(c,actor,body.requestId(),"PROPOSE_RESOLUTION",hash);
            if(replay.isPresent())return replay.get();
            validateTerms(c,body.terms());
            evidence(c,actor,body.terms().evidence());
            var now=clock.instant();
            incident.propose(body.expectedVersion(),actor.accountId(),body.terms(),now);
            incidents.save(incident);
            emit(c,incident,actor,"ResolutionProposed",now);
            record(c,actor,body.requestId(),"PROPOSE_RESOLUTION",hash,incident.proposal().id(),now);
            return new Result(view(incident,c,actor),incident.proposal().id(),false);
        });
    }

    public Result decide(UUID account,UUID incidentId,UUID resolution,Decide body) {
        return work.execute(()->{
            var s=scope(account,incidentId,true);var c=s.collaboration();var incident=s.incident();var actor=s.actor();
            String operation="RESOLUTION_"+body.decision().name();
            String hash=hash(operation,resolution,body);
            var replay=replay(c,actor,body.requestId(),operation,hash);
            if(replay.isPresent())return replay.get();
            var now=clock.instant();boolean cancelled=false,extended=false;
            // Decide validates the opposite signer and exact proposal before any side effect.
            incident.decide(body.expectedVersion(),resolution,actor.accountId(),body.decision(),body.reason(),now);
            incidents.save(incident);
            if(body.decision()==Incident.Decision.ACCEPT) {
                var terms=incident.proposal().terms();
                validateTerms(c,terms);
                if(terms.outcome()==Incident.Outcome.CANCEL_COLLABORATION)cancelled=c.cancelByAgreement(now);
                if(terms.outcome()==Incident.Outcome.EXTEND_CORRECTION) {
                    var d=piece(c,terms.deliverableId());var previous=d.effectiveCorrectionDeadline();
                    d.extendCorrection(terms.submissionId(),terms.expectedDeliverableVersion(),terms.correctionDeadline(),now);
                    deliveries.save(d);
                    extensions.save(d.id(),new Deliverable.CorrectionExtension(resolution,d.submission().id(),resolution,previous,
                            d.effectiveCorrectionDeadline(),incident.proposal().proposedBy(),actor.accountId(),now.truncatedTo(java.time.temporal.ChronoUnit.MICROS)));
                    extended=true;
                }
                c.refreshAfterIncidents(deliveries.forCollaboration(c.id()),queries.openCount(c.id()),now);
                collaborations.save(c);
                emit(c,incident,actor,"IncidentResolved",now);
                if(extended)emit(c,incident,actor,"CorrectionWindowExtended",now);
                if(cancelled)emit(c,incident,actor,"CollaborationCancelled",now);
                if(c.openIncidentCount()==0)emit(c,incident,actor,"CollaborationDisputeCleared",now);
            } else emit(c,incident,actor,body.decision()==Incident.Decision.REJECT?"ResolutionRejected":"ResolutionWithdrawn",now);
            record(c,actor,body.requestId(),operation,hash,resolution,now);
            return new Result(view(incident,c,actor),resolution,false);
        });
    }

    private void validateTerms(Collaboration c,Incident.Terms terms) {
        Objects.requireNonNull(terms);
        if(terms.outcome()==Incident.Outcome.CANCEL_COLLABORATION && c.completedAt()!=null)throw new CollaborationFailure(Code.RESOLUTION_INAPPLICABLE);
        if(terms.outcome()==Incident.Outcome.EXTEND_CORRECTION) {
            if(c.cancelledAt()!=null || c.completedAt()!=null)throw new CollaborationFailure(Code.RESOLUTION_INAPPLICABLE);
            var d=piece(c,terms.deliverableId());
            if(d.status()!=Deliverable.Status.REJECTED || !d.submission().id().equals(terms.submissionId())
                    || d.version()!=terms.expectedDeliverableVersion() || !terms.correctionDeadline().isAfter(clock.instant())
                    || !terms.correctionDeadline().isAfter(d.effectiveCorrectionDeadline()))throw new CollaborationFailure(Code.RESOLUTION_STALE);
        }
    }

    public View get(UUID account,UUID id) {
        return work.execute(()->{var s=scope(account,id,false);return view(s.incident(),s.collaboration(),s.actor());});
    }
    public PageResult<View> list(UUID account,UUID collaboration,Incident.Status status,PageRequest page) {
        return work.execute(()->{
            var actor=participants.actor(account);var c=collaboration(collaboration,false);authorize(actor,c);
            var rows=queries.list(c.id(),status,page);
            return new PageResult<>(rows.items().stream().map(i->view(i,c,actor)).toList(),rows.total(),rows.page(),rows.size());
        });
    }
    public PageResult<Incident.Proposal> proposals(UUID account,UUID id,PageRequest page) {
        return work.execute(()->{scope(account,id,false);return queries.proposals(id,page);});
    }

    public DeliveryService.AssetView upload(UUID account,UUID collaboration,UUID requestId,String name,String type,byte[] bytes) {
        return work.execute(()->{
            var actor=participants.actor(account);var c=collaboration(collaboration,true);authorize(actor,c);
            var file=files.inspect(name,type,bytes);String hash=hash("INCIDENT_UPLOAD",c.id(),file);
            var prior=commands.find(c.id(),actor.accountId(),requestId);
            if(prior.isPresent()) {
                check(prior.get(),"INCIDENT_UPLOAD",hash);
                return assetView(attachments.metadata(prior.get().resultId()).orElseThrow(()->new CollaborationFailure(Code.NOT_FOUND)));
            }
            if(c.openIncidentCount()==0 && !policy.canOpen(c,clock.instant()))throw new CollaborationFailure(Code.INCIDENT_WINDOW_CLOSED);
            var now=clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
            var asset=new Asset(UUID.randomUUID(),c.id(),actor.accountId(),file.filename(),file.mediaType(),file.byteSize(),file.sha256(),now);
            attachments.save(asset,bytes);
            record(c,actor,requestId,"INCIDENT_UPLOAD",hash,asset.id(),now);
            return assetView(asset);
        });
    }

    private void evidence(Collaboration c,Actor actor,List<DeliveryContent.Reference> references) {
        if(references==null)return;
        if(references.stream().anyMatch(Objects::isNull))throw new IllegalArgumentException("Evidence cannot contain null references");
        for(var id:references.stream().map(DeliveryContent.Reference::assetId).filter(Objects::nonNull).distinct().toList()) {
            var a=attachments.metadata(id).orElseThrow(()->new CollaborationFailure(Code.ASSET_NOT_AVAILABLE));
            if(!a.collaborationId().equals(c.id()) || !a.ownerAccountId().equals(actor.accountId())&&!attachments.referenced(id)) {
                throw new CollaborationFailure(Code.ASSET_NOT_AVAILABLE);
            }
        }
    }
    private Scope scope(UUID account,UUID id,boolean lock) {
        var actor=participants.actor(account);
        var incident=incidents.byId(id).orElseThrow(()->new CollaborationFailure(Code.NOT_FOUND));
        var c=collaboration(incident.collaborationId(),lock);authorize(actor,c);
        if(lock)incident=incidents.byId(id).orElseThrow(()->new CollaborationFailure(Code.NOT_FOUND));
        return new Scope(actor,c,incident);
    }
    private Collaboration collaboration(UUID id,boolean lock){return collaborations.byId(id,lock).orElseThrow(()->new CollaborationFailure(Code.NOT_FOUND));}
    private Deliverable piece(Collaboration c,UUID id) {
        var d=deliveries.byId(id).orElseThrow(()->new CollaborationFailure(Code.NOT_FOUND));
        if(!d.collaborationId().equals(c.id()))throw new CollaborationFailure(Code.NOT_FOUND);
        return d;
    }
    private void authorize(Actor actor,Collaboration c) {
        if(!actor.profileId().equals(actor.party()==Agreement.Party.BRAND?c.snapshot().brandId():c.snapshot().creatorId()))throw new CollaborationFailure(Code.FORBIDDEN);
    }
    private Optional<Result> replay(Collaboration c,Actor actor,UUID request,String operation,String hash) {
        var prior=commands.find(c.id(),actor.accountId(),Objects.requireNonNull(request));
        if(prior.isEmpty())return Optional.empty();
        check(prior.get(),operation,hash);
        var incident=(operation.equals("OPEN_INCIDENT")?incidents.byId(prior.get().resultId()):incidents.byProposal(prior.get().resultId()))
                .orElseThrow(()->new CollaborationFailure(Code.NOT_FOUND));
        return Optional.of(new Result(view(incident,c,actor),operation.equals("OPEN_INCIDENT")?null:prior.get().resultId(),true));
    }
    private void check(Command command,String operation,String hash){if(!command.operation().equals(operation)||!command.fingerprint().equals(hash))throw new CollaborationFailure(Code.IDEMPOTENCY_CONFLICT);}
    private void record(Collaboration c,Actor actor,UUID request,String operation,String hash,UUID result,Instant now) {
        commands.save(c.id(),actor.accountId(),request,new Command(operation,hash,result),now);
    }
    private String hash(String operation,UUID target,Object body){return commands.fingerprint(new Fingerprint(operation,target,body));}
    private void emit(Collaboration c,Incident incident,Actor actor,String type,Instant now) {
        var proposal=incident.proposal();
        events.append(new IncidentEvent(UUID.randomUUID(),c.id(),type,incident.id(),proposal==null?null:proposal.id(),
                actor.accountId(),now,c.openIncidentCount(),c.status(),proposal==null?null:proposal.terms()));
    }
    private View view(Incident incident,Collaboration c,Actor actor) {
        var actions=new ArrayList<String>();var p=incident.proposal();
        if(incident.status()==Incident.Status.OPEN)actions.add("PROPOSE_RESOLUTION");
        else if(incident.status()==Incident.Status.RESOLUTION_PROPOSED) {
            if(p.proposedBy().equals(actor.accountId()))actions.add("WITHDRAW_RESOLUTION");
            else {
                try{validateTerms(c,p.terms());actions.add("ACCEPT_RESOLUTION");}catch(CollaborationFailure stale){/* A stale proposal can still be rejected. */}
                actions.add("REJECT_RESOLUTION");
            }
        }
        return new View(incident.id(),c.id(),incident.openedBy(),incident.category(),incident.description(),incident.deliverableId(),
                incident.evidence(),incident.openedAt(),incident.status(),incident.resolvedAt(),incident.version(),p,List.copyOf(actions));
    }
    private DeliveryService.AssetView assetView(Asset asset){return new DeliveryService.AssetView(asset,"/api/v1/collaboration-assets/"+asset.id()+"/content");}
}
