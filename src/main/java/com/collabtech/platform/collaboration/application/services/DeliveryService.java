package com.collabtech.platform.collaboration.application.services;

import com.collabtech.platform.collaboration.application.ports.CollaborationPorts.*;
import com.collabtech.platform.collaboration.application.ports.DeliveryPorts.*;
import com.collabtech.platform.collaboration.domain.exceptions.CollaborationFailure;
import com.collabtech.platform.collaboration.domain.exceptions.CollaborationFailure.Code;
import com.collabtech.platform.collaboration.domain.model.*;
import com.collabtech.platform.collaboration.domain.repositories.*;
import com.collabtech.platform.shared.application.pagination.*;
import java.time.Clock;
import java.time.Instant;
import java.util.*;

public final class DeliveryService {
    public record Submit(UUID requestId, UUID obligationId, DeliveryContent content) {}
    public record Correction(UUID requestId, long expectedVersion, DeliveryContent content) {}
    public record Validation(UUID requestId, UUID submissionId, long expectedVersion,
                             String observations, Instant correctionDeadline) {}
    public record View(UUID id, UUID collaborationId, UUID obligationId, int pieceNumber, long version,
                       Deliverable.Status status, Instant createdAt, Deliverable.Submission currentSubmission,
                       Deliverable.Review currentReview, List<String> availableActions, Instant effectiveCorrectionDeadline) {}
    /** Current resource plus the immutable submission affected by this request, also on a replay. */
    public record Result(View deliverable, UUID affectedSubmissionId, boolean replayed) {}
    public record AssetView(Asset asset, String downloadPath) {}
    public record Download(Asset asset, byte[] bytes) {}
    private record Scope(Actor actor, Collaboration collaboration, Deliverable piece) {}
    private record Fingerprint(String operation, UUID target, Object body) {}

    private final Participants participants;
    private final UnitOfWork work;
    private final CollaborationRepository collaborations;
    private final DeliverableRepository deliveries;
    private final Queries queries;
    private final Attachments attachments;
    private final FileInspector files;
    private final Commands commands;
    private final DeliveryEvents events;
    private final Clock clock;

    public DeliveryService(Participants participants, UnitOfWork work, CollaborationRepository collaborations,
                           DeliverableRepository deliveries, Queries queries, Attachments attachments,
                           FileInspector files, Commands commands, DeliveryEvents events, Clock clock) {
        this.participants=participants; this.work=work; this.collaborations=collaborations;
        this.deliveries=deliveries; this.queries=queries; this.attachments=attachments;
        this.files=files; this.commands=commands; this.events=events; this.clock=clock;
    }

    public Result submit(UUID account, UUID collaboration, Submit body) {
        return work.execute(() -> {
            var actor=participants.actor(account);
            var c=collaboration(collaboration,true);
            authorize(actor,c); requireParty(actor,Agreement.Party.CREATOR);
            String hash=hash("SUBMIT",c.id(),body);
            var replay=replay(c,actor,body.requestId(),"SUBMIT",hash);
            if (replay.isPresent()) return replay.get();
            c.requireExecutionOpen();
            var obligation=obligation(c,body.obligationId());
            var pieces=deliveries.forCollaboration(c.id());
            var used=pieces.stream().filter(p->p.obligationId().equals(obligation.id())).map(Deliverable::pieceNumber).toList();
            int piece=1;
            while (used.contains(piece)) piece++;
            if (piece>obligation.quantity()) throw new CollaborationFailure(Code.QUANTITY_EXCEEDED);
            validateAssets(body.content(),actor,c);
            var now=clock.instant();
            var d=Deliverable.submit(c.id(),obligation.id(),piece,body.content(),actor.accountId(),obligation.deadline(),now);
            persist(c,d,actor,"DeliverableSubmitted",now);
            record(c,actor,body.requestId(),"SUBMIT",hash,d.submission().id(),now);
            return result(d,c,actor,d.submission().id(),false);
        });
    }

    public Result correct(UUID account, UUID deliverable, Correction body) {
        return work.execute(() -> {
            var scope=scope(account,deliverable,true);
            var c=scope.collaboration(); var d=scope.piece(); var actor=scope.actor();
            requireParty(actor,Agreement.Party.CREATOR);
            String hash=hash("CORRECT",deliverable,body);
            var replay=replay(c,actor,body.requestId(),"CORRECT",hash);
            if (replay.isPresent()) return replay.get();
            c.requireExecutionOpen(); validateAssets(body.content(),actor,c);
            var now=clock.instant();
            d.revise(body.expectedVersion(),body.content(),actor.accountId(),obligation(c,d.obligationId()).deadline(),now);
            persist(c,d,actor,"DeliverableRevisionSubmitted",now);
            record(c,actor,body.requestId(),"CORRECT",hash,d.submission().id(),now);
            return result(d,c,actor,d.submission().id(),false);
        });
    }

    public Result validate(UUID account, UUID deliverable, Validation body, Deliverable.Decision decision) {
        return work.execute(() -> {
            var scope=scope(account,deliverable,true);
            var c=scope.collaboration(); var d=scope.piece(); var actor=scope.actor();
            requireParty(actor,Agreement.Party.BRAND);
            String operation=decision==Deliverable.Decision.APPROVED?"APPROVE":"REJECT";
            String hash=hash(operation,deliverable,body);
            var replay=replay(c,actor,body.requestId(),operation,hash);
            if (replay.isPresent()) return replay.get();
            c.requireExecutionOpen();
            var now=clock.instant();
            d.review(body.submissionId(),body.expectedVersion(),decision,body.observations(),body.correctionDeadline(),actor.accountId(),now);
            persist(c,d,actor,decision==Deliverable.Decision.APPROVED?"DeliverableApproved":"DeliverableRejected",now);
            record(c,actor,body.requestId(),operation,hash,d.submission().id(),now);
            return result(d,c,actor,d.submission().id(),false);
        });
    }

    private void persist(Collaboration c, Deliverable d, Actor actor, String type, Instant now) {
        if (c.expire(now)) emit(c,d,actor,"CollaborationExpired",now);
        var previous=c.status();
        deliveries.save(d);
        c.reconcile(deliveries.forCollaboration(c.id()),now);
        collaborations.save(c);
        emit(c,d,actor,type,now);
        if (previous!=Collaboration.Status.EXPIRED && c.status()==Collaboration.Status.EXPIRED) emit(c,d,actor,"CollaborationExpired",now);
        if (c.status()==Collaboration.Status.COMPLETED) emit(c,d,actor,"CollaborationCompleted",now);
    }

    private void emit(Collaboration c, Deliverable d, Actor actor, String type, Instant now) {
        events.append(new DeliveryEvent(UUID.randomUUID(),c.id(),type,d.id(),d.submission().id(),
                d.submission().revision(),actor.accountId(),now,c.snapshot()));
    }

    public View get(UUID account, UUID id) {
        return work.execute(() -> { var s=scope(account,id,false); return view(s.piece(),s.collaboration(),s.actor()); });
    }
    public PageResult<View> list(UUID account, UUID collaboration, Deliverable.Status status, PageRequest page) {
        return work.execute(() -> {
            var actor=participants.actor(account); var c=collaboration(collaboration,false); authorize(actor,c);
            var data=queries.list(c.id(),status,page);
            return new PageResult<>(data.items().stream().map(d->view(d,c,actor)).toList(),data.total(),data.page(),data.size());
        });
    }
    public PageResult<Deliverable.Revision> revisions(UUID account, UUID id, PageRequest page) {
        return work.execute(() -> { scope(account,id,false); return queries.revisions(id,page); });
    }

    public AssetView upload(UUID account, UUID collaboration, UUID requestId, String filename, String media, byte[] bytes) {
        return work.execute(() -> {
            var actor=participants.actor(account); var c=collaboration(collaboration,true);
            authorize(actor,c); requireParty(actor,Agreement.Party.CREATOR);
            var inspected=files.inspect(filename,media,bytes);
            String hash=hash("UPLOAD",c.id(),inspected);
            var prior=commands.find(c.id(),actor.accountId(),requestId);
            if (prior.isPresent()) {
                checkCommand(prior.get(),"UPLOAD",hash);
                return assetView(attachments.metadata(prior.get().resultId()).orElseThrow(()->new CollaborationFailure(Code.NOT_FOUND)));
            }
            c.requireExecutionOpen();
            var now=clock.instant();
            var asset=new Asset(UUID.randomUUID(),c.id(),actor.accountId(),inspected.filename(),inspected.mediaType(),
                    inspected.byteSize(),inspected.sha256(),now.truncatedTo(java.time.temporal.ChronoUnit.MICROS));
            attachments.save(asset,bytes);
            record(c,actor,requestId,"UPLOAD",hash,asset.id(),now);
            return assetView(asset);
        });
    }
    public AssetView asset(UUID account, UUID id) {
        return work.execute(() -> assetView(authorizedAsset(account,id)));
    }
    public Download download(UUID account, UUID id) {
        return work.execute(() -> {
            var asset=authorizedAsset(account,id);
            return new Download(asset,attachments.bytes(id));
        });
    }
    private Asset authorizedAsset(UUID account, UUID id) {
        var actor=participants.actor(account);
        var asset=attachments.metadata(id).orElseThrow(()->new CollaborationFailure(Code.NOT_FOUND));
        var c=collaboration(asset.collaborationId(),false); authorize(actor,c);
        if (!actor.accountId().equals(asset.ownerAccountId()) && !attachments.referenced(id)) throw new CollaborationFailure(Code.FORBIDDEN);
        return asset;
    }
    private void validateAssets(DeliveryContent content, Actor actor, Collaboration c) {
        Objects.requireNonNull(content,"Content is required");
        for (var id:content.assetIds()) {
            var asset=attachments.metadata(id).orElseThrow(()->new CollaborationFailure(Code.ASSET_NOT_AVAILABLE));
            if (!asset.collaborationId().equals(c.id()) || !asset.ownerAccountId().equals(actor.accountId())) {
                throw new CollaborationFailure(Code.ASSET_NOT_AVAILABLE);
            }
        }
    }

    private Optional<Result> replay(Collaboration c, Actor actor, UUID request, String operation, String hash) {
        var prior=commands.find(c.id(),actor.accountId(),Objects.requireNonNull(request));
        if (prior.isEmpty()) return Optional.empty();
        checkCommand(prior.get(),operation,hash);
        var d=deliveries.bySubmission(prior.get().resultId()).orElseThrow(()->new CollaborationFailure(Code.NOT_FOUND));
        return Optional.of(result(d,c,actor,prior.get().resultId(),true));
    }
    private void checkCommand(Command command, String operation, String hash) {
        if (!command.operation().equals(operation) || !command.fingerprint().equals(hash)) throw new CollaborationFailure(Code.IDEMPOTENCY_CONFLICT);
    }
    private void record(Collaboration c, Actor actor, UUID request, String operation, String hash, UUID result, Instant now) {
        commands.save(c.id(),actor.accountId(),request,new Command(operation,hash,result),now);
    }
    private String hash(String operation, UUID target, Object body) { return commands.fingerprint(new Fingerprint(operation,target,body)); }
    private Scope scope(UUID account, UUID id, boolean lock) {
        var actor=participants.actor(account);
        var d=deliveries.byId(id).orElseThrow(()->new CollaborationFailure(Code.NOT_FOUND));
        var c=collaboration(d.collaborationId(),lock); authorize(actor,c);
        if (lock) d=deliveries.byId(id).orElseThrow(()->new CollaborationFailure(Code.NOT_FOUND));
        return new Scope(actor,c,d);
    }
    private Collaboration collaboration(UUID id, boolean lock) {
        return collaborations.byId(id,lock).orElseThrow(()->new CollaborationFailure(Code.NOT_FOUND));
    }
    private Collaboration.Obligation obligation(Collaboration c, UUID id) {
        return c.obligations().stream().filter(o->o.id().equals(id)).findFirst().orElseThrow(()->new CollaborationFailure(Code.NOT_FOUND));
    }
    private void authorize(Actor a, Collaboration c) {
        UUID owner=a.party()==Agreement.Party.BRAND?c.snapshot().brandId():c.snapshot().creatorId();
        if (!a.profileId().equals(owner)) throw new CollaborationFailure(Code.FORBIDDEN);
    }
    private void requireParty(Actor a, Agreement.Party party) {
        if (a.party()!=party) throw new CollaborationFailure(Code.FORBIDDEN);
    }
    private Result result(Deliverable d, Collaboration c, Actor actor, UUID affected, boolean replayed) {
        return new Result(view(d,c,actor),affected,replayed);
    }
    private View view(Deliverable d, Collaboration c, Actor actor) {
        boolean open=c.status()!=Collaboration.Status.COMPLETED && c.status()!=Collaboration.Status.CANCELLED && c.status()!=Collaboration.Status.DISPUTED;
        var actions=new ArrayList<String>();
        if (open && actor.party()==Agreement.Party.BRAND && d.status()==Deliverable.Status.PENDING_VALIDATION) {
            actions.add("APPROVE_DELIVERABLE"); actions.add("REJECT_DELIVERABLE");
        }
        if (open && actor.party()==Agreement.Party.CREATOR && d.status()==Deliverable.Status.REJECTED
                && clock.instant().isBefore(d.effectiveCorrectionDeadline())) actions.add("SUBMIT_REVISION");
        return new View(d.id(),d.collaborationId(),d.obligationId(),d.pieceNumber(),d.version(),d.status(),
                d.createdAt(),d.submission(),d.review(),List.copyOf(actions),d.effectiveCorrectionDeadline());
    }
    private AssetView assetView(Asset asset) { return new AssetView(asset,"/api/v1/collaboration-assets/"+asset.id()+"/content"); }
}
