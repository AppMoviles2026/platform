package com.collabtech.platform.collaboration.domain.model;

import com.collabtech.platform.collaboration.domain.exceptions.CollaborationFailure;
import com.collabtech.platform.collaboration.domain.exceptions.CollaborationFailure.Code;
import java.time.Instant;
import java.util.*;

public final class Collaboration {
    public enum Status { ACTIVE, UNDER_REVIEW, COMPLETED, EXPIRED, CANCELLED, DISPUTED }

    public record Obligation(UUID id, UUID specificationId, String contentType, String description,
                             int quantity, int approvedQuantity, Instant deadline, int submittedQuantity,
                             int pendingQuantity, int rejectedQuantity, Instant correctionDeadline, Instant latestCorrectionDeadline) {
        public Obligation {
            Objects.requireNonNull(id); Objects.requireNonNull(specificationId); Objects.requireNonNull(deadline);
            if (quantity < 1 || approvedQuantity < 0 || submittedQuantity > quantity || pendingQuantity < 0
                    || rejectedQuantity < 0 || approvedQuantity + pendingQuantity + rejectedQuantity != submittedQuantity) {
                throw new IllegalArgumentException("Invalid obligation progress");
            }
        }
        /** Compatibility with snapshots persisted before deliveries existed. */
        public Obligation(UUID id, UUID specificationId, String type, String description,
                          int quantity, int approved, Instant deadline) {
            this(id, specificationId, type, description, quantity, approved, deadline, approved, 0, 0, null, null);
        }
        public boolean outstanding() { return approvedQuantity < quantity; }
        public Instant nextActionDeadline() {
            Instant next = submittedQuantity < quantity ? deadline : null;
            if (rejectedQuantity > 0 && correctionDeadline != null && (next == null || correctionDeadline.isBefore(next))) {
                next = correctionDeadline;
            }
            return next;
        }
    }

    private final UUID id, agreementId;
    private final AgreementSnapshot snapshot;
    private final Instant startedAt;
    private List<Obligation> obligations;
    private Status status;
    private Instant expiredAt, completedAt;
    private long version;

    public Collaboration(UUID id, UUID agreementId, AgreementSnapshot snapshot, Instant startedAt, Status status,
                         List<Obligation> obligations, Instant expiredAt, long version) {
        this(id, agreementId, snapshot, startedAt, status, obligations, expiredAt, null, version);
    }

    public Collaboration(UUID id, UUID agreementId, AgreementSnapshot snapshot, Instant startedAt, Status status,
                         List<Obligation> obligations, Instant expiredAt, Instant completedAt, long version) {
        this.id = Objects.requireNonNull(id); this.agreementId = Objects.requireNonNull(agreementId);
        this.snapshot = Objects.requireNonNull(snapshot); this.startedAt = Objects.requireNonNull(startedAt);
        this.status = Objects.requireNonNull(status); this.obligations = List.copyOf(obligations);
        this.expiredAt = expiredAt; this.completedAt = completedAt; this.version = version;
        if (obligations.isEmpty() || version < 0) throw new IllegalArgumentException("Invalid collaboration");
    }

    public static Collaboration start(Agreement agreement) {
        if (agreement.status() != Agreement.Status.ACCEPTED || agreement.brandAcceptedAt() == null || agreement.creatorAcceptedAt() == null) {
            throw new CollaborationFailure(Code.AGREEMENT_NOT_ACCEPTED);
        }
        return new Collaboration(UUID.randomUUID(), agreement.id(), agreement.snapshot(), agreement.decidedAt(), Status.ACTIVE,
                agreement.snapshot().deliverables().stream()
                        .map(t -> new Obligation(UUID.randomUUID(), t.id(), t.contentType(), t.description(), t.quantity(), 0, t.deadline()))
                        .toList(), null, 0);
    }

    public void requireExecutionOpen() {
        if (status == Status.COMPLETED || status == Status.CANCELLED || status == Status.DISPUTED) {
            throw new CollaborationFailure(Code.COLLABORATION_CLOSED);
        }
    }

    /** Called with the parent's write lock and all its current pieces, after one execution change. */
    public void reconcile(List<Deliverable> pieces, Instant now) {
        requireExecutionOpen();
        var ids = new HashSet<UUID>();
        for (var piece : pieces) {
            var obligation = obligations.stream().filter(o -> o.id().equals(piece.obligationId())).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Unknown obligation"));
            if (!piece.collaborationId().equals(id) || piece.pieceNumber() > obligation.quantity() || !ids.add(piece.id())) {
                throw new IllegalArgumentException("Invalid collaboration piece");
            }
        }
        obligations = obligations.stream().map(o -> {
            var group = pieces.stream().filter(p -> p.obligationId().equals(o.id())).toList();
            if (group.stream().map(Deliverable::pieceNumber).distinct().count() != group.size()) throw new IllegalArgumentException("Duplicate piece");
            int approved = (int)group.stream().filter(p -> p.status() == Deliverable.Status.APPROVED).count();
            int pending = (int)group.stream().filter(p -> p.status() == Deliverable.Status.PENDING_VALIDATION).count();
            int rejected = group.size() - approved - pending;
            var correction = group.stream().filter(p -> p.status() == Deliverable.Status.REJECTED)
                    .map(p -> p.review().correctionDeadline()).filter(Objects::nonNull).min(Comparator.naturalOrder()).orElse(null);
            var lastCorrection = group.stream().filter(p -> p.status() == Deliverable.Status.REJECTED)
                    .map(p -> p.review().correctionDeadline()).filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);
            return new Obligation(o.id(), o.specificationId(), o.contentType(), o.description(), o.quantity(), approved,
                    o.deadline(), group.size(), pending, rejected, correction, lastCorrection);
        }).toList();
        if (obligations.stream().noneMatch(Obligation::outstanding)) {
            status = Status.COMPLETED; completedAt = now.truncatedTo(java.time.temporal.ChronoUnit.MICROS); return;
        }
        var next = nextDeadline();
        if (next != null && !now.isBefore(next)) {
            status = Status.EXPIRED;
            if (expiredAt == null) expiredAt = now;
        } else {
            status = obligations.stream().anyMatch(o -> o.pendingQuantity() > 0) ? Status.UNDER_REVIEW : Status.ACTIVE;
        }
    }

    public Instant nextDeadline() {
        return obligations.stream().map(Obligation::nextActionDeadline).filter(Objects::nonNull)
                .min(Comparator.naturalOrder()).orElse(null);
    }
    public Status effectiveStatus(Instant now) {
        var next = nextDeadline();
        return (status == Status.ACTIVE || status == Status.UNDER_REVIEW) && next != null && !next.isAfter(now) ? Status.EXPIRED : status;
    }
    public boolean expire(Instant now) {
        if (effectiveStatus(now) == Status.EXPIRED && status != Status.EXPIRED) {
            status = Status.EXPIRED; if (expiredAt == null) expiredAt = now; return true;
        }
        return false;
    }

    public UUID id() { return id; }
    public UUID agreementId() { return agreementId; }
    public AgreementSnapshot snapshot() { return snapshot; }
    public Instant startedAt() { return startedAt; }
    public Status status() { return status; }
    public List<Obligation> obligations() { return obligations; }
    public Instant expiredAt() { return expiredAt; }
    public Instant completedAt() { return completedAt; }
    public long version() { return version; }
    public void restoreVersion(long value) {
        if (value < 0) throw new IllegalArgumentException("Invalid version");
        version = value;
    }
}
