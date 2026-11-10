package com.collabtech.platform.performance.application.services;

import com.collabtech.platform.performance.application.ports.AttributionPorts.*;
import com.collabtech.platform.performance.application.ports.PerformancePorts.Access;
import com.collabtech.platform.performance.application.ports.PerformancePorts.Actor;
import com.collabtech.platform.performance.application.ports.PerformancePorts.Scope;
import com.collabtech.platform.performance.application.ports.PerformancePorts.UnitOfWork;
import com.collabtech.platform.performance.application.ports.PerformancePorts.Commands;
import com.collabtech.platform.performance.application.ports.PerformancePorts.Command;
import com.collabtech.platform.performance.domain.model.*;
import com.collabtech.platform.performance.domain.model.AttributionModels.*;
import com.collabtech.platform.performance.domain.model.PerformanceModels.Period;
import com.collabtech.platform.performance.domain.repositories.*;
import com.collabtech.platform.performance.domain.exceptions.PerformanceFailure;
import com.collabtech.platform.performance.domain.exceptions.PerformanceFailure.Code;
import com.collabtech.platform.shared.application.pagination.*;
import java.time.Clock;
import java.time.Instant;
import java.util.*;

public final class AttributionService {
    public static final String WARNING="Estimación: los clics y las declaraciones se asocian al mecanismo, no prueban personas únicas, ventas verificadas ni causalidad de la colaboración. Considera estos resultados con cautela.";
    public record Create(UUID requestId,Kind kind,String label,String destinationId){}
    public record ChangeStatus(UUID requestId,long expectedVersion,Status status){}
    public record RecordInteraction(UUID requestId,UUID eventId,InteractionType type,Instant occurredAt,UUID evidenceId){}
    public record MechanismView(UUID id,UUID collaborationId,Kind kind,String label,String destinationId,String destination,
                                String trackingUrl,String code,Status status,String availability,boolean demonstration,Instant createdAt,Instant updatedAt,long version){}
    public record MechanismResult(MechanismView mechanism,boolean replayed){}
    public record InteractionView(UUID id,UUID attributionId,InteractionType type,Source source,UUID recordedBy,
                                  Instant occurredAt,Instant recordedAt,UUID evidenceId,String evidenceStatus,boolean includedInTotals,boolean demonstration){}
    public record InteractionResult(InteractionView interaction,boolean replayed){}
    public record MechanismResults(MechanismView mechanism,Counts interactions){}
    public record Results(UUID collaborationId,UUID reportId,String availability,Period period,String periodEndBoundary,
                          Counts totals,Counts demonstrationTotals,PageResult<MechanismResults> mechanisms,
                          boolean isEstimate,boolean verifiedSalesAvailable,boolean uniquePeopleMeasured,Map<InteractionType,Source> sources,String warning){}
    public record Redirect(String destination,boolean counted,boolean replayed){}
    private record Fingerprint(String operation,UUID target,Object payload){}
    private record EventKey(String source,UUID eventId,UUID mechanism,String visitorKey){}
    private final Access access;private final UnitOfWork work;private final PerformanceRepository reports;
    private final AttributionRepository repository;private final Queries queries;private final Commands commands;
    private final Destinations destinations;private final Identifiers identifiers;private final Clock clock;
    public AttributionService(Access access,UnitOfWork work,PerformanceRepository reports,AttributionRepository repository,
                              Queries queries,Commands commands,Destinations destinations,Identifiers identifiers,Clock clock){
        this.access=access;this.work=work;this.reports=reports;this.repository=repository;this.queries=queries;
        this.commands=commands;this.destinations=destinations;this.identifiers=identifiers;this.clock=clock;
    }
    public List<Destination> destinations(UUID account){access.actor(account);return destinations.available();}
    public MechanismResult create(UUID account,UUID collaboration,Create input){return create(account,collaboration,input,false);}
    public MechanismResult createDemonstration(UUID account,UUID collaboration,Create input){return create(account,collaboration,input,true);}
    private MechanismResult create(UUID account,UUID collaboration,Create input,boolean demo){
        initialize(account,collaboration);
        return work.execute(()->{
            var actor=access.actor(account);var scope=access.scope(collaboration,false);authorize(actor,scope);brand(actor);
            var r=reports.report(collaboration,true).orElseThrow();String hash=hash(demo?"CREATE_DEMO_ATTRIBUTION":"CREATE_ATTRIBUTION",collaboration,input);
            var replay=replay(r,actor,input.requestId(),"CREATE_ATTRIBUTION",hash);
            if(replay.isPresent())return new MechanismResult(view(repository.mechanism(replay.get()).orElseThrow(),collaboration),true);
            writable(scope);Objects.requireNonNull(input.kind());String target=null,token=null,code=null;
            if(input.kind()==Kind.LINK){target=destinations.resolve(input.destinationId()).orElseThrow(()->new PerformanceFailure(Code.DESTINATION_NOT_ALLOWED));token=identifiers.token();}
            else {if(input.destinationId()!=null)throw new IllegalArgumentException("Codes have no destination");code=identifiers.code();}
            var now=now();var mechanism=new Mechanism(UUID.randomUUID(),r.id(),input.kind(),input.label(),input.destinationId(),target,token,code,
                    Status.ACTIVE,demo,actor.accountId(),now,now,0);
            repository.create(mechanism);reports.touch(r.id(),now);record(r,actor,input.requestId(),"CREATE_ATTRIBUTION",hash,mechanism.id(),now);
            return new MechanismResult(view(mechanism,collaboration),false);
        });
    }
    public MechanismResult status(UUID account,UUID id,ChangeStatus input){
        return work.execute(()->{
            var before=repository.mechanism(id).orElseThrow(()->new PerformanceFailure(Code.NOT_FOUND));
            var r=reports.reportById(before.reportId(),true).orElseThrow();var actor=access.actor(account);var scope=access.scope(r.collaborationId(),false);
            authorize(actor,scope);brand(actor);String hash=hash("ATTRIBUTION_STATUS",id,input);
            var replay=replay(r,actor,input.requestId(),"ATTRIBUTION_STATUS",hash);
            if(replay.isPresent())return new MechanismResult(view(repository.mechanism(id).orElseThrow(),r.collaborationId()),true);
            writable(scope);var current=repository.mechanism(id).orElseThrow();var now=now();
            var changed=current.status(input.expectedVersion(),input.status(),now);
            if(changed.version()!=current.version())repository.updateStatus(changed,current.version());
            else changed=current;
            reports.touch(r.id(),now);record(r,actor,input.requestId(),"ATTRIBUTION_STATUS",hash,id,now);
            return new MechanismResult(view(changed,r.collaborationId()),false);
        });
    }
    public InteractionResult record(UUID account,UUID id,RecordInteraction input){
        return work.execute(()->{
            var before=repository.mechanism(id).orElseThrow(()->new PerformanceFailure(Code.NOT_FOUND));
            var r=reports.reportById(before.reportId(),true).orElseThrow();var actor=access.actor(account);var scope=access.scope(r.collaborationId(),false);
            authorize(actor,scope);brand(actor);String hash=hash("RECORD_INTERACTION",id,input);
            var replay=replay(r,actor,input.requestId(),"RECORD_INTERACTION",hash);
            if(replay.isPresent())return new InteractionResult(interactionView(repository.interaction(replay.get()).orElseThrow()),true);
            String eventKey=commands.fingerprint(new EventKey("DECLARATION",input.eventId(),null,null));
            String eventHash=hash("DECLARED_EVENT",id,List.of(input.type(),PerformanceModels.precision(input.occurredAt()),input.evidenceId()));
            var existing=repository.event(r.id(),eventKey);
            if(existing.isPresent()){
                if(!existing.get().fingerprint().equals(eventHash))throw new PerformanceFailure(Code.IDEMPOTENCY_CONFLICT);
                record(r,actor,input.requestId(),"RECORD_INTERACTION",hash,existing.get().id(),now());
                return new InteractionResult(interactionView(existing.get()),true);
            }
            writable(scope);var m=repository.mechanism(id).orElseThrow();active(m);
            if(input.type()==null||input.type()==InteractionType.CLICK||input.type()==InteractionType.CODE_REDEMPTION&&m.kind()!=Kind.CODE)
                throw new IllegalArgumentException("Clicks are server measured; code redemptions require a code");
            Objects.requireNonNull(input.eventId());var now=now();var occurred=PerformanceModels.precision(input.occurredAt());
            if(occurred.isBefore(m.createdAt())||occurred.isAfter(now))throw new IllegalArgumentException("Interaction must be within mechanism lifetime");
            var evidence=reports.evidence(Objects.requireNonNull(input.evidenceId())).orElseThrow(()->new PerformanceFailure(Code.EVIDENCE_NOT_AVAILABLE));
            if(!evidence.reportId().equals(r.id())||evidence.status()==PerformanceEvidence.Status.REJECTED||evidence.demonstration()!=m.demonstration())
                throw new PerformanceFailure(Code.EVIDENCE_NOT_AVAILABLE);
            var interaction=new Interaction(UUID.randomUUID(),r.id(),m.id(),input.type(),Source.BRAND_DECLARATION,eventKey,eventHash,
                    actor.accountId(),occurred,now,evidence.id(),m.demonstration());
            repository.save(interaction);reports.touch(r.id(),now);record(r,actor,input.requestId(),"RECORD_INTERACTION",hash,interaction.id(),now);
            return new InteractionResult(interactionView(interaction),false);
        });
    }
    /** visitorKey is an opaque browser session or an explicit retry UUID. Never contains IP, UA, or identity. */
    public Redirect redirect(String token,String visitorKey,boolean count){
        if(token==null||!token.matches("[A-Za-z0-9_-]{43}"))throw new PerformanceFailure(Code.NOT_FOUND);
        if(visitorKey==null||visitorKey.length()>100)throw new IllegalArgumentException("Invalid visit identity");
        return work.execute(()->{
            var before=repository.byToken(token).orElseThrow(()->new PerformanceFailure(Code.NOT_FOUND));
            var r=reports.reportById(before.reportId(),true).orElseThrow();var scope=access.scope(r.collaborationId(),false);
            var m=repository.mechanism(before.id()).orElseThrow();
            if(scope.cancelled()||m.status()!=Status.ACTIVE)throw new PerformanceFailure(Code.ATTRIBUTION_INACTIVE);
            if(destinations.resolve(m.destinationId()).filter(m.destination()::equals).isEmpty())throw new PerformanceFailure(Code.ATTRIBUTION_INACTIVE);
            if(!count)return new Redirect(m.destination(),false,false);
            String key=commands.fingerprint(new EventKey("CLICK",null,m.id(),visitorKey));
            if(repository.event(r.id(),key).isPresent())return new Redirect(m.destination(),false,true);
            var now=now();
            repository.save(new Interaction(UUID.randomUUID(),r.id(),m.id(),InteractionType.CLICK,Source.FIRST_PARTY_TRACKING,key,
                    hash("CLICK",m.id(),m.id()),null,now,now,null,m.demonstration()));
            reports.touch(r.id(),now);return new Redirect(m.destination(),true,false);
        });
    }
    public Results results(UUID account,UUID collaboration,Instant start,Instant end,PageRequest page){
        return work.execute(()->{
            var scope=access.scope(collaboration,false);authorize(access.actor(account),scope);var r=reports.report(collaboration,true);
            var period=period(r.orElse(null),start,end);
            if(r.isEmpty())return new Results(collaboration,null,"NO_MECHANISM",period,"EXCLUSIVE",Counts.empty(),Counts.empty(),
                    new PageResult<>(List.of(),0,page.page(),page.size()),true,false,false,sources(),WARNING);
            var report=r.get();var rows=queries.mechanisms(report.id(),page);var totals=queries.counts(report.id(),null,period,false);
            var demo=queries.counts(report.id(),null,period,true);
            var items=rows.items().stream().map(m->new MechanismResults(view(m,collaboration,scope.cancelled()),queries.counts(report.id(),m.id(),period,m.demonstration()))).toList();
            String availability=rows.total()==0?"NO_MECHANISM":totals.total()==0?"NO_INTERACTIONS":"AVAILABLE";
            return new Results(collaboration,report.id(),availability,period,"EXCLUSIVE",totals,demo,
                    new PageResult<>(items,rows.total(),rows.page(),rows.size()),true,false,false,sources(),WARNING);
        });
    }
    public PageResult<InteractionView> interactions(UUID account,UUID id,Instant start,Instant end,PageRequest page){
        return work.execute(()->{
            var m=repository.mechanism(id).orElseThrow(()->new PerformanceFailure(Code.NOT_FOUND));
            var r=reports.reportById(m.reportId(),true).orElseThrow();authorize(access.actor(account),access.scope(r.collaborationId(),false));
            var rows=queries.interactions(r.id(),m.id(),period(r,start,end),page);
            return new PageResult<>(rows.items().stream().map(this::interactionView).toList(),rows.total(),rows.page(),rows.size());
        });
    }
    private Period period(PerformanceReport r,Instant start,Instant end){
        if((start==null)!=(end==null))throw new IllegalArgumentException("Provide both start and end");
        var now=now();if(start==null){end=now;start=r==null?now:r.createdAt();if(start.isAfter(end))start=end;}
        if(start.isBefore(Instant.parse("1000-01-01T00:00:00Z"))||end.isAfter(now))throw new IllegalArgumentException("Invalid result period");
        return new Period(start,end);
    }
    private InteractionView interactionView(Interaction i){
        var evidence=i.evidenceId()==null?null:reports.evidence(i.evidenceId()).orElse(null);
        boolean included=i.source()==Source.FIRST_PARTY_TRACKING||evidence!=null&&evidence.status()!=PerformanceEvidence.Status.REJECTED;
        return new InteractionView(i.id(),i.mechanismId(),i.type(),i.source(),i.recordedBy(),i.occurredAt(),i.recordedAt(),i.evidenceId(),
                evidence==null?null:evidence.status().name(),included,i.demonstration());
    }
    private MechanismView view(Mechanism m,UUID collaboration){return view(m,collaboration,access.scope(collaboration,false).cancelled());}
    private MechanismView view(Mechanism m,UUID collaboration,boolean cancelled){
        String availability=cancelled?"COLLABORATION_CANCELLED":m.status()!=Status.ACTIVE?"INACTIVE":
                m.kind()==Kind.LINK&&destinations.resolve(m.destinationId()).filter(m.destination()::equals).isEmpty()?"DESTINATION_UNAVAILABLE":"AVAILABLE";
        return new MechanismView(m.id(),collaboration,m.kind(),m.label(),m.destinationId(),m.destination(),
                m.kind()==Kind.LINK?destinations.publicUrl(m.token()):null,m.code(),m.status(),availability,m.demonstration(),m.createdAt(),m.updatedAt(),m.version());
    }
    private void initialize(UUID account,UUID c){work.execute(()->{
        var a=access.actor(account);var s=access.scope(c,false);authorize(a,s);brand(a);
        if(reports.report(c,false).isPresent())return null;
        authorize(a,access.scope(c,true));if(reports.report(c,true).isEmpty())reports.create(PerformanceReport.create(c,s.brandId(),s.creatorId(),now()));return null;
    });}
    private Optional<UUID> replay(PerformanceReport r,Actor a,UUID key,String op,String hash){
        var prior=commands.command(r.id(),a.accountId(),Objects.requireNonNull(key));if(prior.isEmpty())return Optional.empty();
        if(!prior.get().operation().equals(op)||!prior.get().fingerprint().equals(hash))throw new PerformanceFailure(Code.IDEMPOTENCY_CONFLICT);
        return Optional.of(prior.get().resultId());
    }
    private void record(PerformanceReport r,Actor a,UUID key,String op,String hash,UUID id,Instant now){commands.command(r.id(),a.accountId(),key,new Command(op,hash,id),now);}
    private String hash(String op,UUID id,Object payload){return commands.fingerprint(new Fingerprint(op,id,payload));}
    private Map<InteractionType,Source> sources(){return Map.of(InteractionType.CLICK,Source.FIRST_PARTY_TRACKING,InteractionType.INQUIRY,Source.BRAND_DECLARATION,InteractionType.CODE_REDEMPTION,Source.BRAND_DECLARATION);}
    private Instant now(){return PerformanceModels.precision(clock.instant());}
    private void authorize(Actor a,Scope s){if(!a.profileId().equals(a.role().equals("BRAND")?s.brandId():s.creatorId()))throw new PerformanceFailure(Code.FORBIDDEN);}
    private void brand(Actor a){if(!a.role().equals("BRAND"))throw new PerformanceFailure(Code.FORBIDDEN);}
    private void writable(Scope s){if(s.cancelled())throw new PerformanceFailure(Code.COLLABORATION_CLOSED);}
    private void active(Mechanism m){if(m.status()!=Status.ACTIVE)throw new PerformanceFailure(Code.ATTRIBUTION_INACTIVE);}
}
