package com.collabtech.platform.billing.application.ports;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import com.collabtech.platform.billing.domain.model.SubscriptionModels.*;
import com.collabtech.platform.shared.application.pagination.*;
public final class SubscriptionPorts {
    private SubscriptionPorts(){}
    public record ProviderPlan(String id,String status,BigDecimal amount,String currency,boolean supported){}
    public record Created(String id,String approvalUrl){@Override public String toString(){return "CreatedSubscription[redacted]";}}
    public record Charge(String id,String status,BigDecimal amount,String currency,Instant time){}
    public record Remote(String id,String customId,String planId,String status,String payerId,Instant updatedAt,Instant nextBillingAt,
                         int failedCount,Instant lastFailureAt,List<Charge> charges){
        public Remote {charges=List.copyOf(charges);}
        @Override public String toString(){return "RemoteSubscription[status="+status+"]";}
    }
    public interface Provider {
        boolean configured();
        String returnUrl();
        ProviderPlan plan(String providerId);
        Created create(UUID subscription,String providerPlanId,String state);
        default Created create(UUID subscription,String providerPlanId,String state,String returnUrl){return create(subscription,providerPlanId,state);}
        Remote read(String providerId);
        void cancel(String providerId);
    }
    public interface EventRouter {String enqueue(String eventId,Map<String,Object> payload);}
    public interface Store {
        void catalog(Plan plan);
        void deactivateDemoCatalog();
        List<Plan> availablePlans();
        Optional<Plan> plan(UUID id);
        boolean available(UUID id);
        void create(Subscription subscription);
        void save(Subscription subscription);
        Optional<Subscription> byId(UUID id);
        Optional<Subscription> byState(String hash);
        Optional<Subscription> byProvider(String hash);
        Optional<Subscription> request(UUID account,UUID request);
        Optional<Subscription> current(UUID account);
        Optional<Subscription> latest(UUID account);
        PageResult<Subscription> history(UUID account,PageRequest page);
        Optional<Payment> payment(String hash);
        void payment(Payment payment);
        void event(EventWork event);
        Optional<EventWork> event(String id,boolean lock);
        List<UUID> pollable(Instant now,int limit);
        List<EventWork> due(Instant now,int limit);
        void processed(String eventId,Instant now);
        List<com.collabtech.platform.billing.domain.model.BillingModels.Event> deferredEvents(int limit);
    }
}
