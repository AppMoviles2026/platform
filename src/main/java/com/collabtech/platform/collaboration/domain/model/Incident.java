package com.collabtech.platform.collaboration.domain.model;

import com.collabtech.platform.collaboration.domain.exceptions.CollaborationFailure;
import com.collabtech.platform.collaboration.domain.exceptions.CollaborationFailure.Code;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

public final class Incident {
    public enum Category { CONDITIONS, DELIVERABLE, COMPENSATION, OTHER }
    public enum Status { OPEN, RESOLUTION_PROPOSED, RESOLVED }
    public enum Outcome { KEEP_AGREEMENT, EXTEND_CORRECTION, CANCEL_COLLABORATION }
    public enum Decision { ACCEPT, REJECT, WITHDRAW }
    public enum ProposalStatus { PENDING, ACCEPTED, REJECTED, WITHDRAWN }

    public record Terms(Outcome outcome, String description, List<DeliveryContent.Reference> evidence,
                        UUID deliverableId, UUID submissionId, Long expectedDeliverableVersion, Instant correctionDeadline) {
        public Terms {
            Objects.requireNonNull(outcome);
            description = text(description, 4000);
            evidence = references(evidence);
            if (outcome == Outcome.EXTEND_CORRECTION) {
                Objects.requireNonNull(deliverableId); Objects.requireNonNull(submissionId);
                if (expectedDeliverableVersion == null || expectedDeliverableVersion < 0 || correctionDeadline == null) {
                    throw new IllegalArgumentException("A correction extension needs its exact rejected version and deadline");
                }
                correctionDeadline = correctionDeadline.truncatedTo(ChronoUnit.MICROS);
            } else if (deliverableId != null || submissionId != null || expectedDeliverableVersion != null || correctionDeadline != null) {
                throw new IllegalArgumentException("Only a correction extension can include delivery amendment fields");
            }
        }
    }

    public record Proposal(UUID id, Terms terms, UUID proposedBy, Instant proposedAt, ProposalStatus status,
                           UUID decidedBy, Instant decidedAt, String decisionReason) {
        public Proposal {
            Objects.requireNonNull(id); Objects.requireNonNull(terms); Objects.requireNonNull(proposedBy);
            Objects.requireNonNull(proposedAt); Objects.requireNonNull(status);
            proposedAt = proposedAt.truncatedTo(ChronoUnit.MICROS);
            if (decidedAt != null) decidedAt = decidedAt.truncatedTo(ChronoUnit.MICROS);
        }
    }

    private final UUID id, collaborationId, openedBy, deliverableId;
    private final Category category;
    private final String description;
    private final List<DeliveryContent.Reference> evidence;
    private final Instant openedAt;
    private Status status;
    private Instant resolvedAt;
    private Proposal proposal;
    private long version;

    public Incident(UUID id, UUID collaborationId, UUID openedBy, Category category, String description,
                    UUID deliverableId, List<DeliveryContent.Reference> evidence, Instant openedAt, Status status,
                    Instant resolvedAt, Proposal proposal, long version) {
        this.id=Objects.requireNonNull(id); this.collaborationId=Objects.requireNonNull(collaborationId);
        this.openedBy=Objects.requireNonNull(openedBy); this.category=Objects.requireNonNull(category);
        this.description=text(description,4000); this.deliverableId=deliverableId; this.evidence=references(evidence);
        this.openedAt=Objects.requireNonNull(openedAt).truncatedTo(ChronoUnit.MICROS);
        this.status=Objects.requireNonNull(status); this.resolvedAt=resolvedAt; this.proposal=proposal; this.version=version;
        if (version<0) throw new IllegalArgumentException("Invalid version");
    }
    public static Incident open(UUID collaboration, UUID actor, Category category, String description,
                                UUID deliverable, List<DeliveryContent.Reference> evidence, Instant now) {
        return new Incident(UUID.randomUUID(),collaboration,actor,category,description,deliverable,evidence,now,Status.OPEN,null,null,0);
    }
    public void propose(long expectedVersion, UUID actor, Terms terms, Instant now) {
        requireVersion(expectedVersion);
        if (status==Status.RESOLVED) throw new CollaborationFailure(Code.INCIDENT_RESOLVED);
        if (status!=Status.OPEN) throw new CollaborationFailure(Code.RESOLUTION_PENDING);
        if (terms.correctionDeadline()!=null && !terms.correctionDeadline().isAfter(now)) {
            throw new CollaborationFailure(Code.RESOLUTION_STALE);
        }
        proposal=new Proposal(UUID.randomUUID(),terms,actor,now,ProposalStatus.PENDING,null,null,null);
        status=Status.RESOLUTION_PROPOSED;
    }
    public void decide(long expectedVersion, UUID resolution, UUID actor, Decision decision, String reason, Instant now) {
        requireVersion(expectedVersion);
        if (proposal==null || !proposal.id().equals(resolution)) throw new CollaborationFailure(Code.STALE_RESOLUTION);
        if (status==Status.RESOLVED) throw new CollaborationFailure(Code.INCIDENT_RESOLVED);
        if (status!=Status.RESOLUTION_PROPOSED || proposal.status()!=ProposalStatus.PENDING) throw new CollaborationFailure(Code.STALE_RESOLUTION);
        boolean proposer=actor.equals(proposal.proposedBy());
        if ((decision==Decision.WITHDRAW && !proposer) || (decision!=Decision.WITHDRAW && proposer)) throw new CollaborationFailure(Code.FORBIDDEN);
        reason = reason == null ? null : reason.strip();
        if (reason!=null && reason.length()>2000) throw new IllegalArgumentException("Decision reason is too long");
        if (decision==Decision.REJECT && (reason==null || reason.isBlank())) throw new IllegalArgumentException("Rejection requires a reason");
        if (decision==Decision.ACCEPT && proposal.terms().correctionDeadline()!=null && !now.isBefore(proposal.terms().correctionDeadline())) {
            throw new CollaborationFailure(Code.RESOLUTION_STALE);
        }
        var finalStatus=switch(decision) {
            case ACCEPT -> ProposalStatus.ACCEPTED; case REJECT -> ProposalStatus.REJECTED; case WITHDRAW -> ProposalStatus.WITHDRAWN;
        };
        proposal=new Proposal(proposal.id(),proposal.terms(),proposal.proposedBy(),proposal.proposedAt(),finalStatus,actor,now,reason);
        status=decision==Decision.ACCEPT ? Status.RESOLVED : Status.OPEN;
        if (status==Status.RESOLVED) resolvedAt=now.truncatedTo(ChronoUnit.MICROS);
    }
    private void requireVersion(long expected) {
        if (version!=expected) throw new CollaborationFailure(Code.CONCURRENT_UPDATE);
    }
    private static String text(String value, int max) {
        if (value==null || value.isBlank() || value.strip().length()>max) throw new IllegalArgumentException("Description is required and must fit its limit");
        return value.strip();
    }
    private static List<DeliveryContent.Reference> references(List<DeliveryContent.Reference> values) {
        if(values!=null&&values.stream().anyMatch(Objects::isNull))throw new IllegalArgumentException("Evidence cannot contain null references");
        var result=values==null?List.<DeliveryContent.Reference>of():List.copyOf(values);
        if (result.size()>10 || result.stream().distinct().count()!=result.size()) throw new IllegalArgumentException("Use at most ten distinct references");
        return result;
    }
    public UUID id(){return id;} public UUID collaborationId(){return collaborationId;} public UUID openedBy(){return openedBy;}
    public UUID deliverableId(){return deliverableId;} public Category category(){return category;} public String description(){return description;}
    public List<DeliveryContent.Reference> evidence(){return evidence;} public Instant openedAt(){return openedAt;}
    public Status status(){return status;} public Instant resolvedAt(){return resolvedAt;} public Proposal proposal(){return proposal;}
    public long version(){return version;} public void restoreVersion(long value){if(value<0)throw new IllegalArgumentException("Invalid version");version=value;}
}
