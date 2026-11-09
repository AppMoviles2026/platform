package com.collabtech.platform.collaboration.domain.model;

import com.collabtech.platform.collaboration.domain.exceptions.CollaborationFailure;
import com.collabtech.platform.collaboration.domain.exceptions.CollaborationFailure.Code;
import java.time.Instant;
import java.util.*;

public final class Collaboration {
    public enum Status { ACTIVE, UNDER_REVIEW, COMPLETED, EXPIRED, CANCELLED, DISPUTED }
    public record Obligation(UUID id,UUID specificationId,String contentType,String description,int quantity,int approvedQuantity,Instant deadline){
        public Obligation {Objects.requireNonNull(id);Objects.requireNonNull(specificationId);Objects.requireNonNull(deadline);
            if(quantity<1||approvedQuantity<0||approvedQuantity>quantity)throw new IllegalArgumentException("Invalid obligation progress");}
        public boolean outstanding(){return approvedQuantity<quantity;}
    }
    private final UUID id,agreementId;private final AgreementSnapshot snapshot;private final Instant startedAt;
    private final List<Obligation> obligations;private Status status;private Instant expiredAt;private long version;
    public Collaboration(UUID id,UUID agreementId,AgreementSnapshot snapshot,Instant startedAt,Status status,List<Obligation> obligations,Instant expiredAt,long version){
        this.id=Objects.requireNonNull(id);this.agreementId=Objects.requireNonNull(agreementId);this.snapshot=Objects.requireNonNull(snapshot);
        this.startedAt=Objects.requireNonNull(startedAt);this.status=Objects.requireNonNull(status);this.obligations=List.copyOf(obligations);this.expiredAt=expiredAt;this.version=version;
        if(obligations.isEmpty())throw new IllegalArgumentException("Required deliverables missing");
    }
    public static Collaboration start(Agreement agreement){
        if(agreement.status()!=Agreement.Status.ACCEPTED||agreement.brandAcceptedAt()==null||agreement.creatorAcceptedAt()==null)
            throw new CollaborationFailure(Code.AGREEMENT_NOT_ACCEPTED);
        return new Collaboration(UUID.randomUUID(),agreement.id(),agreement.snapshot(),agreement.decidedAt(),Status.ACTIVE,
                agreement.snapshot().deliverables().stream().map(t->new Obligation(UUID.randomUUID(),t.id(),t.contentType(),t.description(),t.quantity(),0,t.deadline())).toList(),null,0);
    }
    public Instant nextDeadline(){return obligations.stream().filter(Obligation::outstanding).map(Obligation::deadline).min(Comparator.naturalOrder()).orElse(null);}
    public Status effectiveStatus(Instant now){var next=nextDeadline();return (status==Status.ACTIVE||status==Status.UNDER_REVIEW)&&next!=null&&!next.isAfter(now)?Status.EXPIRED:status;}
    public boolean expire(Instant now){if(effectiveStatus(now)==Status.EXPIRED&&status!=Status.EXPIRED){status=Status.EXPIRED;expiredAt=now;return true;}return false;}
    public UUID id(){return id;}public UUID agreementId(){return agreementId;}public AgreementSnapshot snapshot(){return snapshot;}
    public Instant startedAt(){return startedAt;}public Status status(){return status;}public List<Obligation> obligations(){return obligations;}
    public Instant expiredAt(){return expiredAt;}public long version(){return version;}
    public void restoreVersion(long version){if(version<0)throw new IllegalArgumentException("Invalid version");this.version=version;}
}
