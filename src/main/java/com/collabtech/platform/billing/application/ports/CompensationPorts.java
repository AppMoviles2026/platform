package com.collabtech.platform.billing.application.ports;
import com.collabtech.platform.billing.domain.model.CompensationModels.*;
import com.collabtech.platform.shared.application.pagination.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
public final class CompensationPorts {
    private CompensationPorts(){}
    public record Actor(UUID accountId,UUID profileId,String role){}
    public record Party(UUID accountId,UUID profileId,String role,boolean active){}
    public record Execution(UUID collaborationId,UUID agreementId,UUID brandProfileId,UUID creatorProfileId,String status,
        boolean fulfilled,boolean eligible,int openIncidents,long version,String type,BigDecimal amount,String currency,String description){}
    public interface Context {
        Actor actor(UUID accountId);
        Party party(UUID profileId,String role);
        Optional<Execution> execution(UUID collaborationId,boolean lock);
    }
    public record Funding(UUID operationId,UUID compensationId,BigDecimal amount,String currency,String state,String returnUrl){}
    public record Created(String id,String approvalUrl){@Override public String toString(){return "CreatedFunding[redacted]";}}
    public record Capture(String id,String status,BigDecimal amount,String currency,Instant time){}
    public record Order(String id,String status,String compensationId,String operationId,String payerId,BigDecimal amount,String currency,
        Instant updatedAt,List<Capture> captures){public Order{captures=List.copyOf(captures);}@Override public String toString(){return "FundingOrder[status="+status+"]";}}
    public record Payout(String batchId,String itemId,String senderBatchId,String senderItemId,String receiver,String status,
        BigDecimal amount,String currency,String transactionId,Instant updatedAt){@Override public String toString(){return "Disbursement[status="+status+"]";}}
    public record Settings(boolean enabled,Set<String> currencies,String returnUrl,long createRetrySeconds,long payoutRetrySeconds,boolean automaticPayouts){}
    public interface Provider {
        Settings settings();
        default void preflight(){}
        Created createOrder(Funding request);
        Order readOrder(String reference);
        void capture(String reference,UUID operation);
        Payout createPayout(UUID operation,UUID compensation,String receiver,BigDecimal amount,String currency);
        Payout readPayout(String reference);
        Payout readPayoutItem(String reference);
    }
    public interface Store {
        Optional<Compensation> byCollaboration(UUID id,boolean lock);
        Optional<Compensation> byId(UUID id);
        void create(Compensation c);
        void save(Compensation c);
        Optional<Command> command(UUID compensation,UUID request);
        void command(Command command);
        Optional<Reference> reference(String hash);
        void reference(Reference reference);
        Optional<Compensation> state(String stateHash);
        List<UUID> due(Instant now,int limit);
        void audit(UUID compensation,UUID operation,UUID actor,String type,Instant now);
        PageResult<Audit> audit(UUID compensation,PageRequest page);
        void event(EventWork event);
        Optional<EventWork> event(String eventId,boolean lock);
        List<EventWork> dueEvents(Instant now,int limit);
        void processed(String eventId,Instant now);
    }
}
