package com.collabtech.platform.collaboration.domain.model;

import com.collabtech.platform.collaboration.domain.exceptions.CollaborationFailure;
import com.collabtech.platform.collaboration.domain.exceptions.CollaborationFailure.Code;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** A proposal is not an active collaboration. Decisions refer to termsVersion, not the other participant's mutable acknowledgement. */
public final class Agreement {
    public enum Status { PENDING, ACCEPTED, REJECTED, EXPIRED }
    public enum Party { BRAND, CREATOR }
    public enum Decision { ACCEPT, REJECT }
    private final UUID id; private final AgreementSnapshot snapshot;
    private Status status; private Instant brandAcceptedAt,creatorAcceptedAt,decidedAt;
    private UUID brandAcceptedBy,creatorAcceptedBy,rejectedBy; private long version;
    public Agreement(UUID id,AgreementSnapshot snapshot,Status status,Instant brandAt,UUID brandBy,Instant creatorAt,UUID creatorBy,
            Instant decidedAt,UUID rejectedBy,long version){
        this.id=Objects.requireNonNull(id);this.snapshot=Objects.requireNonNull(snapshot);this.status=Objects.requireNonNull(status);
        this.brandAcceptedAt=brandAt;this.brandAcceptedBy=brandBy;this.creatorAcceptedAt=creatorAt;this.creatorAcceptedBy=creatorBy;
        this.decidedAt=decidedAt;this.rejectedBy=rejectedBy;this.version=version;
    }
    public static Agreement propose(AgreementSnapshot terms,Instant now){
        if(!terms.acceptanceDeadline().isAfter(now))throw new CollaborationFailure(Code.TERMS_EXPIRED);
        return new Agreement(UUID.randomUUID(),terms,Status.PENDING,null,null,null,null,null,null,0);
    }
    public void decide(Party party,UUID actor,UUID termsVersion,Decision decision,Instant now){
        Objects.requireNonNull(party);Objects.requireNonNull(actor);Objects.requireNonNull(decision);Objects.requireNonNull(now);
        if(!snapshot.termsVersion().equals(termsVersion))throw new CollaborationFailure(Code.TERMS_CHANGED);
        // A successful request remains replayable after activation or the temporal boundary.
        boolean alreadyAccepted=party==Party.BRAND ? brandAcceptedAt!=null : creatorAcceptedAt!=null;
        UUID acceptedBy=party==Party.BRAND ? brandAcceptedBy : creatorAcceptedBy;
        if(decision==Decision.ACCEPT && alreadyAccepted && actor.equals(acceptedBy) && (status==Status.PENDING||status==Status.ACCEPTED))return;
        if(decision==Decision.REJECT && status==Status.REJECTED && actor.equals(rejectedBy))return;
        if(status!=Status.PENDING)throw new CollaborationFailure(status==Status.EXPIRED?Code.TERMS_EXPIRED:Code.AGREEMENT_CLOSED);
        if(!snapshot.acceptanceDeadline().isAfter(now))throw new CollaborationFailure(Code.TERMS_EXPIRED);
        if(alreadyAccepted)throw new CollaborationFailure(Code.DECISION_ALREADY_RECORDED);
        if(decision==Decision.REJECT){status=Status.REJECTED;decidedAt=now;rejectedBy=actor;return;}
        if(party==Party.BRAND){brandAcceptedAt=now;brandAcceptedBy=actor;}else{creatorAcceptedAt=now;creatorAcceptedBy=actor;}
        if(brandAcceptedAt!=null&&creatorAcceptedAt!=null){status=Status.ACCEPTED;decidedAt=now;}
    }
    public boolean expire(Instant now){if(status==Status.PENDING&&!snapshot.acceptanceDeadline().isAfter(now)){status=Status.EXPIRED;decidedAt=now;return true;}return false;}
    public Status effectiveStatus(Instant now){return status==Status.PENDING&&!snapshot.acceptanceDeadline().isAfter(now)?Status.EXPIRED:status;}
    public UUID id(){return id;}public AgreementSnapshot snapshot(){return snapshot;}public Status status(){return status;}
    public Instant brandAcceptedAt(){return brandAcceptedAt;}public UUID brandAcceptedBy(){return brandAcceptedBy;}
    public Instant creatorAcceptedAt(){return creatorAcceptedAt;}public UUID creatorAcceptedBy(){return creatorAcceptedBy;}
    public Instant decidedAt(){return decidedAt;}public UUID rejectedBy(){return rejectedBy;}public long version(){return version;}
    public void restoreVersion(long version){if(version<0)throw new IllegalArgumentException("Invalid version");this.version=version;}
}
