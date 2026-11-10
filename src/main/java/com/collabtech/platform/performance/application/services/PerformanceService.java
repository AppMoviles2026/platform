package com.collabtech.platform.performance.application.services;

import com.collabtech.platform.performance.application.ports.PerformancePorts.*;
import com.collabtech.platform.performance.domain.model.*;
import com.collabtech.platform.performance.domain.model.PerformanceModels.*;
import com.collabtech.platform.performance.domain.repositories.PerformanceRepository;
import com.collabtech.platform.performance.domain.exceptions.PerformanceFailure;
import com.collabtech.platform.performance.domain.exceptions.PerformanceFailure.Code;
import com.collabtech.platform.shared.application.pagination.*;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.*;

public final class PerformanceService {
    public record RefreshInput(UUID requestId,UUID socialId,UUID submissionId,String postId){}
    public record EvidenceInput(UUID requestId,UUID submissionId,String description,List<Reference> references,
                                Map<MetricType,Long> claimedMetrics,Instant observedAt,Period period){}
    public record ReviewInput(UUID requestId,long expectedVersion,PerformanceEvidence.Status decision,String observations){}
    public record AutomaticSnapshot(UUID id,UUID publicationId,UUID submissionId,String sourceType,String provider,String postId,
                                    String periodKind,Instant capturedAt,Period period,Map<MetricType,Long> metrics){}
    public record Observation(Publication publication,List<Binding> bindings,boolean appliesToCurrentContent,
                              String availability,AutomaticSnapshot snapshot,Refresh lastRefresh){}
    public record Report(UUID reportId,UUID collaborationId,String automaticAvailability,long availablePublications,
                         PageResult<Observation> publications,long manualEvidenceCount,boolean manualEvidenceIsAutomatic){}
    public record RefreshResult(Refresh refresh,AutomaticSnapshot snapshot,boolean replayed){}
    public record EvidenceView(UUID id,UUID collaborationId,UUID submittedBy,UUID submissionId,String sourceType,
                               String description,List<Reference> references,Map<MetricType,Long> claimedMetrics,Instant observedAt,
                               Period period,Instant submittedAt,PerformanceEvidence.Status status,boolean demonstration,long version,
                               UUID reviewedBy,Instant reviewedAt,String observations){}
    public record EvidenceResult(EvidenceView evidence,boolean replayed){}
    public record AssetView(Asset asset,String downloadPath){}
    public record Download(Asset asset,byte[] bytes){}
    private record Fingerprint(String operation,UUID target,Object body){}
    private final Access access;private final UnitOfWork work;private final PerformanceRepository repository;
    private final Queries queries;private final Commands commands;private final Files files;private final SocialMetricsProvider provider;private final Clock clock;
    public PerformanceService(Access access,UnitOfWork work,PerformanceRepository repository,Queries queries,Commands commands,
                              Files files,SocialMetricsProvider provider,Clock clock) {
        this.access=access;this.work=work;this.repository=repository;this.queries=queries;this.commands=commands;
        this.files=files;this.provider=provider;this.clock=clock;
    }
    public Report report(UUID account,UUID collaboration,PageRequest page) {
        return work.execute(()->{
            var actor=access.actor(account);var scope=access.scope(collaboration,false);authorize(actor,scope);
            var existing=repository.report(collaboration,false);
            if(existing.isEmpty())return new Report(null,collaboration,"NO_DATA",0,new PageResult<>(List.of(),0,page.page(),page.size()),0,false);
            var r=existing.get();var current=access.currentSubmissions(collaboration);
            var rows=queries.publications(r.id(),page);
            var observations=rows.items().stream().map(p->{
                var bindings=repository.bindings(p.id());boolean applies=bindings.stream().anyMatch(b->current.contains(b.submissionId()));
                var snapshot=repository.latestSnapshot(p.id()).orElse(null);var last=repository.latestRefresh(p.id()).orElse(null);
                String availability=!applies?"HISTORICAL_ONLY":snapshot==null?"NO_DATA":last!=null&&last.status()!=FetchStatus.AVAILABLE?"AVAILABLE_STALE":"AVAILABLE";
                return new Observation(p,bindings,applies,availability,snapshot==null?null:snapshotView(snapshot),last);
            }).toList();
            long available=queries.availablePublications(r.id(),current);
            return new Report(r.id(),collaboration,available==0?"NO_DATA":"AVAILABLE",available,
                    new PageResult<>(observations,rows.total(),rows.page(),rows.size()),queries.evidenceCount(r.id()),false);
        });
    }
    public PageResult<AutomaticSnapshot> snapshots(UUID account,UUID collaboration,PageRequest page) {
        return work.execute(()->{
            var actor=access.actor(account);authorize(actor,access.scope(collaboration,false));
            var r=repository.report(collaboration,false);
            if(r.isEmpty())return new PageResult<>(List.of(),0,page.page(),page.size());
            var rows=queries.snapshots(r.get().id(),page);
            return new PageResult<>(rows.items().stream().map(this::snapshotView).toList(),rows.total(),rows.page(),rows.size());
        });
    }
    public RefreshResult refresh(UUID account,UUID collaboration,RefreshInput input) {
        initialize(account,collaboration,false);
        return work.execute(()->{
            var actor=access.actor(account);var scope=access.scope(collaboration,false);authorize(actor,scope);
            var report=repository.report(collaboration,true).orElseThrow();
            String hash=hash("REFRESH",collaboration,input);
            var replay=replay(report,actor,input.requestId(),"REFRESH",hash);
            if(replay.isPresent())return refreshResult(repository.refresh(replay.get()).orElseThrow(),true);
            writable(scope);
            var content=access.content(collaboration,input.submissionId());
            if(!content.current()||content.reviewStatus().equals("REJECTED"))throw new PerformanceFailure(Code.SOURCE_NOT_CURRENT);
            if(content.url()==null)throw new PerformanceFailure(Code.SOURCE_NOT_PUBLISHED);
            String platform=access.socialPlatform(scope.creatorId(),input.socialId());
            validatePublication(platform,input.postId(),content.url());
            var now=PerformanceModels.precision(clock.instant());
            var publication=repository.publication(report.id(),input.socialId(),input.postId())
                    .orElse(new Publication(UUID.randomUUID(),report.id(),input.socialId(),platform,input.postId(),now));
            repository.save(publication,new Binding(publication.id(),input.submissionId(),content.deliverableId(),content.url(),now));
            var fetched=provider.fetch(scope.creatorId(),input.socialId(),platform,input.postId());
            now=PerformanceModels.precision(clock.instant());
            if(!fetched.platform().equals(platform)||fetched.period()!=null&&fetched.period().end().isAfter(now))
                fetched=FetchResult.unavailable(FetchStatus.INVALID_RESPONSE,platform);
            Snapshot snapshot=null;
            if(fetched.status()==FetchStatus.AVAILABLE) {
                var previous=repository.latestSnapshot(publication.id()).orElse(null);
                if(previous!=null&&previous.capturedAt().equals(now)&&previous.metrics().equals(fetched.metrics())&&previous.period().equals(fetched.period()))snapshot=previous;
                else {
                    snapshot=new Snapshot(UUID.randomUUID(),publication.id(),input.submissionId(),now,fetched.period(),fetched.metrics(),report.version()+1);
                    repository.save(snapshot);
                }
            }
            var refresh=new Refresh(UUID.randomUUID(),publication.id(),actor.accountId(),now,fetched.status(),snapshot==null?null:snapshot.id(),report.version()+1);
            repository.save(refresh);repository.touch(report.id(),now);
            record(report,actor,input.requestId(),"REFRESH",hash,refresh.id(),now);
            return new RefreshResult(refresh,snapshot==null?null:snapshotView(snapshot),false);
        });
    }
    public EvidenceResult register(UUID account,UUID collaboration,EvidenceInput input){return register(account,collaboration,input,false);}
    public EvidenceResult registerDemonstration(UUID account,UUID collaboration,EvidenceInput input){return register(account,collaboration,input,true);}
    private EvidenceResult register(UUID account,UUID collaboration,EvidenceInput input,boolean demo) {
        initialize(account,collaboration,true);
        return work.execute(()->{
            var actor=access.actor(account);var scope=access.scope(collaboration,false);authorize(actor,scope);creator(actor);
            var r=repository.report(collaboration,true).orElseThrow();String hash=hash(demo?"DEMO_EVIDENCE":"EVIDENCE",collaboration,input);
            var replay=replay(r,actor,input.requestId(),"EVIDENCE",hash);
            if(replay.isPresent())return new EvidenceResult(evidenceView(repository.evidence(replay.get()).orElseThrow(),collaboration),true);
            writable(scope);
            if(input.submissionId()!=null)access.content(collaboration,input.submissionId());
            validateReferences(r,actor,input.references());
            var now=PerformanceModels.precision(clock.instant());
            var evidence=PerformanceEvidence.create(r.id(),actor.accountId(),input.submissionId(),input.description(),input.references(),
                    input.claimedMetrics(),input.observedAt(),input.period(),now,demo);
            repository.save(evidence);repository.touch(r.id(),now);record(r,actor,input.requestId(),"EVIDENCE",hash,evidence.id(),now);
            return new EvidenceResult(evidenceView(evidence,collaboration),false);
        });
    }
    public PageResult<EvidenceView> evidence(UUID account,UUID collaboration,PerformanceEvidence.Status status,PageRequest page) {
        return work.execute(()->{
            var actor=access.actor(account);authorize(actor,access.scope(collaboration,false));
            var r=repository.report(collaboration,false);if(r.isEmpty())return new PageResult<>(List.of(),0,page.page(),page.size());
            var rows=queries.evidence(r.get().id(),status,page);
            return new PageResult<>(rows.items().stream().map(e->evidenceView(e,collaboration)).toList(),rows.total(),rows.page(),rows.size());
        });
    }
    public EvidenceView evidence(UUID account,UUID id) {
        return work.execute(()->{var e=repository.evidence(id).orElseThrow(()->new PerformanceFailure(Code.NOT_FOUND));
            var r=repository.reportById(e.reportId(),false).orElseThrow();authorize(access.actor(account),access.scope(r.collaborationId(),false));return evidenceView(e,r.collaborationId());});
    }
    public EvidenceResult review(UUID account,UUID id,ReviewInput input) {
        return work.execute(()->{
            var actor=access.actor(account);var before=repository.evidence(id).orElseThrow(()->new PerformanceFailure(Code.NOT_FOUND));
            var r=repository.reportById(before.reportId(),true).orElseThrow();var scope=access.scope(r.collaborationId(),false);authorize(actor,scope);
            if(!actor.role().equals("BRAND"))throw new PerformanceFailure(Code.FORBIDDEN);
            String hash=hash("REVIEW_EVIDENCE",id,input);
            var replay=replay(r,actor,input.requestId(),"REVIEW_EVIDENCE",hash);
            if(replay.isPresent())return new EvidenceResult(evidenceView(repository.evidence(replay.get()).orElseThrow(),r.collaborationId()),true);
            writable(scope);var e=repository.evidence(id).orElseThrow();var now=PerformanceModels.precision(clock.instant());
            e.review(input.expectedVersion(),input.decision(),actor.accountId(),input.observations(),now);
            repository.save(e);repository.touch(r.id(),now);record(r,actor,input.requestId(),"REVIEW_EVIDENCE",hash,id,now);
            return new EvidenceResult(evidenceView(e,r.collaborationId()),false);
        });
    }
    public AssetView upload(UUID account,UUID collaboration,UUID request,String name,String media,byte[] bytes) {
        initialize(account,collaboration,true);
        return work.execute(()->{
            var actor=access.actor(account);var scope=access.scope(collaboration,false);authorize(actor,scope);creator(actor);
            var r=repository.report(collaboration,true).orElseThrow();var file=files.inspect(name,media,bytes);String hash=hash("UPLOAD",collaboration,file);
            var replay=replay(r,actor,request,"UPLOAD",hash);
            if(replay.isPresent())return assetView(files.asset(replay.get()).orElseThrow());
            writable(scope);var now=PerformanceModels.precision(clock.instant());
            var asset=new Asset(UUID.randomUUID(),r.id(),actor.accountId(),file.filename(),file.mediaType(),file.byteSize(),file.sha256(),now);
            files.save(asset,bytes);repository.touch(r.id(),now);record(r,actor,request,"UPLOAD",hash,asset.id(),now);return assetView(asset);
        });
    }
    public AssetView asset(UUID account,UUID id){return work.execute(()->assetView(authorizedAsset(account,id)));}
    public Download download(UUID account,UUID id){return work.execute(()->{var a=authorizedAsset(account,id);return new Download(a,files.bytes(id));});}
    private Asset authorizedAsset(UUID account,UUID id) {
        var actor=access.actor(account);var a=files.asset(id).orElseThrow(()->new PerformanceFailure(Code.NOT_FOUND));
        var r=repository.reportById(a.reportId(),false).orElseThrow();authorize(actor,access.scope(r.collaborationId(),false));
        if(!a.owner().equals(actor.accountId())&&!files.shared(id))throw new PerformanceFailure(Code.FORBIDDEN);
        return a;
    }
    private void initialize(UUID account,UUID collaboration,boolean requireCreator) {
        work.execute(()->{
            var actor=access.actor(account);var scope=access.scope(collaboration,false);authorize(actor,scope);if(requireCreator)creator(actor);
            if(repository.report(collaboration,false).isPresent())return null;
            scope=access.scope(collaboration,true);authorize(actor,scope);
            if(repository.report(collaboration,true).isEmpty())repository.create(PerformanceReport.create(collaboration,scope.brandId(),scope.creatorId(),clock.instant()));
            return null;
        });
    }
    private void validateReferences(PerformanceReport r,Actor actor,List<Reference> refs) {
        if(refs==null||refs.stream().anyMatch(Objects::isNull))throw new IllegalArgumentException("Evidence references are required");
        for(var id:refs.stream().map(Reference::assetId).filter(Objects::nonNull).distinct().toList()) {
            var asset=files.asset(id).orElseThrow(()->new PerformanceFailure(Code.ASSET_NOT_AVAILABLE));
            if(!asset.reportId().equals(r.id())||!asset.owner().equals(actor.accountId()))throw new PerformanceFailure(Code.ASSET_NOT_AVAILABLE);
        }
    }
    private void validatePublication(String platform,String post,String url) {
        if(post==null||!post.matches("[A-Za-z0-9_-]{1,128}"))throw new IllegalArgumentException("Invalid provider post ID");
        if(platform.equals("tiktok")) {
            var uri=URI.create(url);var host=uri.getHost();var matcher=java.util.regex.Pattern.compile("/@[^/]+/video/([0-9]{1,40})/?").matcher(uri.getPath());
            if(!post.matches("[0-9]{1,40}")||host==null||!Set.of("www.tiktok.com","tiktok.com").contains(host.toLowerCase(Locale.ROOT))
                    ||!matcher.matches()||!matcher.group(1).equals(post))throw new PerformanceFailure(Code.POST_URL_MISMATCH);
        }
    }
    private AutomaticSnapshot snapshotView(Snapshot s) {
        var p=repository.publicationById(s.publicationId()).orElseThrow();
        return new AutomaticSnapshot(s.id(),p.id(),s.submissionId(),"AUTOMATED",p.platform(),p.postId(),"LIFETIME",
                s.capturedAt(),s.period(),s.metrics());
    }
    private RefreshResult refreshResult(Refresh r,boolean replayed){return new RefreshResult(r,r.snapshotId()==null?null:snapshotView(repository.snapshot(r.snapshotId()).orElseThrow()),replayed);}
    private EvidenceView evidenceView(PerformanceEvidence e,UUID c){return new EvidenceView(e.id(),c,e.submittedBy(),e.submissionId(),"MANUAL",e.description(),
            e.references(),e.claimedMetrics(),e.observedAt(),e.period(),e.submittedAt(),e.status(),e.demonstration(),e.version(),e.reviewedBy(),e.reviewedAt(),e.observations());}
    private AssetView assetView(Asset a){return new AssetView(a,"/api/v1/performance-assets/"+a.id()+"/content");}
    private Optional<UUID> replay(PerformanceReport r,Actor actor,UUID request,String op,String hash) {
        var prior=commands.command(r.id(),actor.accountId(),Objects.requireNonNull(request));if(prior.isEmpty())return Optional.empty();
        if(!prior.get().operation().equals(op)||!prior.get().fingerprint().equals(hash))throw new PerformanceFailure(Code.IDEMPOTENCY_CONFLICT);
        return Optional.of(prior.get().resultId());
    }
    private String hash(String op,UUID target,Object body){return commands.fingerprint(new Fingerprint(op,target,body));}
    private void record(PerformanceReport r,Actor a,UUID request,String op,String hash,UUID id,Instant now){commands.command(r.id(),a.accountId(),request,new Command(op,hash,id),now);}
    private void creator(Actor actor){if(!actor.role().equals("CREATOR"))throw new PerformanceFailure(Code.FORBIDDEN);}
    private void writable(Scope scope){if(scope.cancelled())throw new PerformanceFailure(Code.COLLABORATION_CLOSED);}
    private void authorize(Actor a,Scope s){if(!a.profileId().equals(a.role().equals("BRAND")?s.brandId():s.creatorId()))throw new PerformanceFailure(Code.FORBIDDEN);}
}
