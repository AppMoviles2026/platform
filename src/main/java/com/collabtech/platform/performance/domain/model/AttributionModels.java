package com.collabtech.platform.performance.domain.model;

import java.time.Instant;
import java.util.*;
import com.collabtech.platform.performance.domain.exceptions.PerformanceFailure;
import com.collabtech.platform.performance.domain.exceptions.PerformanceFailure.Code;

public final class AttributionModels {
    private AttributionModels(){}
    public enum Kind { LINK,CODE }
    public enum Status { ACTIVE,INACTIVE }
    public enum InteractionType { CLICK,INQUIRY,CODE_REDEMPTION }
    public enum Source { FIRST_PARTY_TRACKING,BRAND_DECLARATION }
    public record Mechanism(UUID id,UUID reportId,Kind kind,String label,String destinationId,String destination,
                            String token,String code,Status status,boolean demonstration,UUID createdBy,
                            Instant createdAt,Instant updatedAt,long version) {
        public Mechanism {
            Objects.requireNonNull(id);Objects.requireNonNull(reportId);Objects.requireNonNull(kind);Objects.requireNonNull(status);Objects.requireNonNull(createdBy);
            if(label==null||label.isBlank()||label.strip().length()>120)throw new IllegalArgumentException("A label is required");
            label=label.strip();createdAt=PerformanceModels.precision(createdAt);updatedAt=PerformanceModels.precision(updatedAt);
            if(version<0||updatedAt.isBefore(createdAt))throw new IllegalArgumentException("Invalid mechanism version or time");
            if(kind==Kind.LINK&&(destinationId==null||destination==null||token==null||!token.matches("[A-Za-z0-9_-]{43}")||code!=null))
                throw new IllegalArgumentException("A link needs its configured destination and tracking token");
            if(kind==Kind.CODE&&(destinationId!=null||destination!=null||token!=null||code==null||!code.matches("CP-[A-Z2-9]{12}")))
                throw new IllegalArgumentException("A code cannot redirect");
        }
        public Mechanism status(long expected,Status next,Instant now) {
            if(version!=expected)throw new PerformanceFailure(Code.CONCURRENT_UPDATE);
            Objects.requireNonNull(next);
            return new Mechanism(id,reportId,kind,label,destinationId,destination,token,code,next,demonstration,createdBy,createdAt,now,
                    version+(next==status?0:1));
        }
    }
    public record Interaction(UUID id,UUID reportId,UUID mechanismId,InteractionType type,Source source,String eventKey,
                              String fingerprint,UUID recordedBy,Instant occurredAt,Instant recordedAt,UUID evidenceId,boolean demonstration) {
        public Interaction {
            Objects.requireNonNull(id);Objects.requireNonNull(reportId);Objects.requireNonNull(mechanismId);Objects.requireNonNull(type);Objects.requireNonNull(source);
            if(eventKey==null||!eventKey.matches("[a-f0-9]{64}")||fingerprint==null||!fingerprint.matches("[a-f0-9]{64}"))
                throw new IllegalArgumentException("Invalid interaction identity");
            occurredAt=PerformanceModels.precision(occurredAt);recordedAt=PerformanceModels.precision(recordedAt);
            if(occurredAt.isAfter(recordedAt))throw new IllegalArgumentException("Future interaction");
            if(source==Source.FIRST_PARTY_TRACKING&&(type!=InteractionType.CLICK||recordedBy!=null||evidenceId!=null))
                throw new IllegalArgumentException("Only clicks are measured by the redirect");
            if(source==Source.BRAND_DECLARATION&&(type==InteractionType.CLICK||recordedBy==null||evidenceId==null))
                throw new IllegalArgumentException("Declared interactions need an actor and evidence");
        }
    }
    public record Counts(long clicks,long inquiries,long codeRedemptions) {
        public Counts {if(clicks<0||inquiries<0||codeRedemptions<0)throw new IllegalArgumentException("Negative counts");}
        public static Counts empty(){return new Counts(0,0,0);}
        public long total(){return clicks+inquiries+codeRedemptions;}
    }
}
