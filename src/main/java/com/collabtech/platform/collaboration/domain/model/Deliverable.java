package com.collabtech.platform.collaboration.domain.model;

import com.collabtech.platform.collaboration.domain.exceptions.CollaborationFailure;
import com.collabtech.platform.collaboration.domain.exceptions.CollaborationFailure.Code;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** One required piece. Corrections replace its current revision, never its immutable history. */
public final class Deliverable {
    public enum Status { PENDING_VALIDATION, APPROVED, REJECTED }
    public enum Decision { APPROVED, REJECTED }
    public record Submission(UUID id, int revision, UUID submittedBy, Instant submittedAt,
                             boolean late, DeliveryContent content) {
        public Submission {
            Objects.requireNonNull(id); Objects.requireNonNull(submittedBy);
            Objects.requireNonNull(submittedAt); Objects.requireNonNull(content);
            submittedAt = submittedAt.truncatedTo(java.time.temporal.ChronoUnit.MICROS);
            if (revision < 1) throw new IllegalArgumentException("Invalid revision");
        }
    }
    public record Review(UUID id, UUID submissionId, UUID reviewedBy, Instant reviewedAt,
                         Decision decision, String observations, Instant correctionDeadline) {
        public Review {
            Objects.requireNonNull(id); Objects.requireNonNull(submissionId);
            Objects.requireNonNull(reviewedBy); Objects.requireNonNull(reviewedAt); Objects.requireNonNull(decision);
            reviewedAt = reviewedAt.truncatedTo(java.time.temporal.ChronoUnit.MICROS);
            if (correctionDeadline != null) correctionDeadline = correctionDeadline.truncatedTo(java.time.temporal.ChronoUnit.MICROS);
            observations = observations == null ? null : observations.strip();
            if (observations != null && observations.length() > 4000) throw new IllegalArgumentException("Observations exceed 4000 characters");
            if (decision == Decision.REJECTED && (observations == null || observations.isBlank())) {
                throw new IllegalArgumentException("Rejection requires observations");
            }
            if (decision == Decision.REJECTED && correctionDeadline == null) throw new IllegalArgumentException("Rejection requires a correction deadline");
            if (decision == Decision.APPROVED && correctionDeadline != null) throw new IllegalArgumentException("Approval has no correction deadline");
            if (correctionDeadline != null && !correctionDeadline.isAfter(reviewedAt)) {
                throw new IllegalArgumentException("Correction deadline must be in the future");
            }
        }
    }
    public record CorrectionExtension(UUID id, UUID submissionId, UUID resolutionId, Instant previousDeadline,
                                      Instant deadline, UUID proposedBy, UUID acceptedBy, Instant acceptedAt) {}
    public record Revision(Submission submission, Review review, java.util.List<CorrectionExtension> correctionExtensions) {
        public Revision(Submission submission, Review review) { this(submission,review,java.util.List.of()); }
    }

    private final UUID id, collaborationId, obligationId;
    private final int pieceNumber;
    private final Instant createdAt;
    private Submission submission;
    private Review review;
    private long version;
    private Instant correctionDeadlineOverride;

    public Deliverable(UUID id, UUID collaborationId, UUID obligationId, int pieceNumber,
                       Instant createdAt, Submission submission, Review review, long version) {
        this(id, collaborationId, obligationId, pieceNumber, createdAt, submission, review, null, version);
    }

    public Deliverable(UUID id, UUID collaborationId, UUID obligationId, int pieceNumber,
                       Instant createdAt, Submission submission, Review review, Instant correctionDeadlineOverride, long version) {
        this.correctionDeadlineOverride = correctionDeadlineOverride;
        this.id = Objects.requireNonNull(id);
        this.collaborationId = Objects.requireNonNull(collaborationId);
        this.obligationId = Objects.requireNonNull(obligationId);
        this.createdAt = Objects.requireNonNull(createdAt).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        this.submission = Objects.requireNonNull(submission);
        this.review = review;
        this.pieceNumber = pieceNumber;
        this.version = version;
        if (pieceNumber < 1 || version < 0 || review != null && !review.submissionId().equals(submission.id())) {
            throw new IllegalArgumentException("Invalid deliverable state");
        }
    }

    public static Deliverable submit(UUID collaboration, UUID obligation, int piece, DeliveryContent content,
                                     UUID actor, Instant deadline, Instant now) {
        return new Deliverable(UUID.randomUUID(), collaboration, obligation, piece, now,
                new Submission(UUID.randomUUID(), 1, actor, now, !now.isBefore(deadline), content), null, 0);
    }

    public void review(UUID targetSubmission, long expectedVersion, Decision decision, String observations,
                       Instant correctionDeadline, UUID actor, Instant now) {
        requireVersion(expectedVersion);
        if (!submission.id().equals(targetSubmission)) throw new CollaborationFailure(Code.STALE_SUBMISSION);
        if (review != null) throw new CollaborationFailure(Code.DELIVERABLE_ALREADY_REVIEWED);
        review = new Review(UUID.randomUUID(), submission.id(), actor, now, decision, observations, correctionDeadline);
    }

    public void revise(long expectedVersion, DeliveryContent content, UUID actor, Instant originalDeadline, Instant now) {
        requireVersion(expectedVersion);
        if (status() != Status.REJECTED) throw new CollaborationFailure(Code.REVISION_NOT_ALLOWED);
        if (effectiveCorrectionDeadline() != null && !now.isBefore(effectiveCorrectionDeadline())) {
            throw new CollaborationFailure(Code.CORRECTION_WINDOW_CLOSED);
        }
        submission = new Submission(UUID.randomUUID(), Math.addExact(submission.revision(), 1), actor, now,
                !now.isBefore(originalDeadline), content);
        review = null;
        correctionDeadlineOverride = null;
    }

    public Instant effectiveCorrectionDeadline() {
        return review == null || status() != Status.REJECTED ? null
                : correctionDeadlineOverride == null ? review.correctionDeadline() : correctionDeadlineOverride;
    }

    public void extendCorrection(UUID submissionId, long expectedVersion, Instant deadline, Instant now) {
        requireVersion(expectedVersion);
        if (status() != Status.REJECTED || !submission.id().equals(submissionId)) throw new CollaborationFailure(Code.RESOLUTION_STALE);
        deadline = Objects.requireNonNull(deadline).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        if (!deadline.isAfter(now) || !deadline.isAfter(effectiveCorrectionDeadline())) throw new CollaborationFailure(Code.RESOLUTION_STALE);
        correctionDeadlineOverride = deadline;
    }

    public Instant correctionDeadlineOverride() { return correctionDeadlineOverride; }

    private void requireVersion(long expected) {
        if (version != expected) throw new CollaborationFailure(Code.CONCURRENT_UPDATE);
    }
    public Status status() { return review == null ? Status.PENDING_VALIDATION : Status.valueOf(review.decision().name()); }
    public UUID id() { return id; }
    public UUID collaborationId() { return collaborationId; }
    public UUID obligationId() { return obligationId; }
    public int pieceNumber() { return pieceNumber; }
    public Instant createdAt() { return createdAt; }
    public Submission submission() { return submission; }
    public Review review() { return review; }
    public long version() { return version; }
    public void restoreVersion(long value) {
        if (value < 0) throw new IllegalArgumentException("Invalid version");
        version = value;
    }
}
