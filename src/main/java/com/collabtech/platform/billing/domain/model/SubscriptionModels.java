package com.collabtech.platform.billing.domain.model;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import com.collabtech.platform.billing.domain.exceptions.BillingFailure;
import com.collabtech.platform.billing.domain.exceptions.BillingFailure.Code;

public final class SubscriptionModels {
    private SubscriptionModels(){}
    public enum SubscriptionStatus { PREPARING,APPROVAL_PENDING,PAYMENT_PENDING,ACTIVE,PAYMENT_FAILED,SUSPENDED,CANCEL_PENDING,CANCELLED,EXPIRED,FAILED,RECONCILIATION_REQUIRED }
    public record Plan(UUID id,String code,String name,String description,BigDecimal amount,String currency,List<String> benefits,
                       String providerPlanId,boolean demonstration,String definitionHash) {
        public Plan {
            benefits=List.copyOf(benefits);Objects.requireNonNull(id);
            if(amount==null||amount.signum()<=0||amount.scale()>2||amount.precision()>12||!"USD".equals(currency)||!demonstration)
                throw new IllegalArgumentException("Only positive USD Sandbox demonstration plans are supported");
        }
    }
    /** Persisted state contains encrypted provider references; HTTP responses use dedicated views. */
    public static final class Subscription {
        public UUID id,paymentAccountId,planId,methodId,requestId,leaseToken;
        public String returnUrlSnapshot,payerHash,latestPaymentHash,requestHash,stateHash,encryptedState,encryptedProvider,providerHash,encryptedApproval,client,errorCode,providerStatus;
        public SubscriptionStatus status;
        public Instant paymentFailureSignalAt,nextCheckAt,createdAt,updatedAt,consentExpiresAt,attemptedAt,leaseUntil,lastCheckedAt,providerUpdatedAt,paidThrough;
        public long version;
        public boolean current=true,cancelRequested=false;
        public Subscription(){}
        public boolean terminal(){return status==SubscriptionStatus.CANCELLED||status==SubscriptionStatus.EXPIRED||status==SubscriptionStatus.FAILED;}
        public boolean benefitsActive(Instant now){return status==SubscriptionStatus.ACTIVE&&!cancelRequested&&paidThrough!=null&&now.isBefore(paidThrough);}
        public void end(SubscriptionStatus next,Instant now){
            if(next!=SubscriptionStatus.CANCELLED&&next!=SubscriptionStatus.EXPIRED&&next!=SubscriptionStatus.FAILED)throw new IllegalArgumentException("Not terminal");
            status=next;current=false;encryptedState=null;encryptedApproval=null;paidThrough=null;updatedAt=BillingModels.micros(now);
        }
        public boolean claim(Instant now){
            if(leaseToken!=null&&leaseUntil!=null&&now.isBefore(leaseUntil))return false;
            leaseToken=UUID.randomUUID();leaseUntil=now.plusSeconds(120);updatedAt=BillingModels.micros(now);return true;
        }
        public boolean owns(UUID lease){return Objects.equals(leaseToken,lease);}
        public void release(){leaseToken=null;leaseUntil=null;}
        public void requireReplay(String hash){if(!requestHash.equals(hash))throw new BillingFailure(Code.IDEMPOTENCY_CONFLICT);}
        @Override public String toString(){return "Subscription[id="+id+",status="+status+"]";}
    }
    public record Payment(String hash,UUID subscriptionId,String encryptedReference,String status,BigDecimal amount,String currency,Instant paidAt,Instant updatedAt){
        public boolean reversed(){return Set.of("REFUNDED","REVERSED","PARTIALLY_REFUNDED").contains(status);}
        @Override public String toString(){return "SubscriptionPayment[status="+status+"]";}
    }
    public record EventWork(String eventId,UUID subscriptionHint,String encryptedProvider,String providerHash,String paymentHash,String eventType,
                            String status,int attempts,Instant nextAttemptAt,UUID leaseToken,String errorCode){}
}
