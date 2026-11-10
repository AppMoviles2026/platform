package com.collabtech.platform.collaboration.interfaces.acl;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/** Published facts for Billing/Performance. Consumers never import collaboration repositories or aggregates. */
public interface CollaborationExecutionFacade {
    record Compensation(String type,BigDecimal amount,String currency,String description) {}
    record Execution(UUID collaborationId,UUID agreementId,UUID campaignId,UUID brandProfileId,UUID creatorProfileId,
                     String status,Instant completedAt,Instant cancelledAt,int openIncidentCount,boolean fulfilled,
                     boolean compensationEligible,long version,Compensation compensation) {}
    Optional<Execution> find(UUID collaborationId);
    /** Requires a caller transaction; eligibility remains locked through the consumer's local mutation. */
    Optional<Execution> findForUpdate(UUID collaborationId);
}
