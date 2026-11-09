package com.collabtech.platform.campaign.interfaces.acl;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Published selection contract. Implementations authorize the caller before returning the frozen campaign terms. */
public interface CampaignAgreementFacade {
    Selection selectedFor(UUID accountId, UUID applicationId);
    record Requirement(UUID id,String description,boolean mandatory,String ruleType,String expectedValue) {}
    record Deliverable(UUID id,String contentType,String description,int quantity,Instant deadline) {}
    record Selection(UUID applicationId,long applicationVersion,Instant selectedAt,UUID campaignId,long campaignVersion,
            UUID brandId,UUID creatorId,String title,String objective,String description,String category,String targetAudience,String location,
            String message,Set<UUID> confirmedRequirements,List<Requirement> requirements,List<Deliverable> deliverables,
            String compensationType,BigDecimal amount,String currency,String compensationDescription,Instant applicationDeadline) {
        public Selection { confirmedRequirements=Set.copyOf(confirmedRequirements);requirements=List.copyOf(requirements);deliverables=List.copyOf(deliverables); }
    }
    final class Unavailable extends RuntimeException {
        public enum Reason { NOT_FOUND, FORBIDDEN, NOT_SELECTED, INACTIVE }
        private final Reason reason;
        public Unavailable(Reason reason){super(reason.name());this.reason=reason;}
        public Reason reason(){return reason;}
    }
}
