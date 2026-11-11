package com.collabtech.platform.billing.domain.model;

import com.collabtech.platform.billing.domain.exceptions.BillingFailure;
import com.collabtech.platform.billing.domain.exceptions.BillingFailure.Code;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

public final class BillingModels {
    private BillingModels(){}
    public enum Purpose { PAYMENT,PAYOUT }
    public enum Client { WEB,ANDROID }
    public enum Status { PREPARING,PENDING_APPROVAL,COMPLETING,LINKED,CANCELLED,FAILED,EXPIRED }
    public enum MethodStatus { ACTIVE,REVOKED }
    public record Account(UUID id,UUID accountId,String role,Instant createdAt,long version){}
    public record Method(UUID id,UUID paymentAccountId,Purpose purpose,MethodStatus status,String provider,String environment,
                         String encryptedReference,String referenceHash,String subjectHash,String maskedLabel,
                         Instant linkedAt,Instant updatedAt,long version) {
        public Method {
            Objects.requireNonNull(id);Objects.requireNonNull(paymentAccountId);Objects.requireNonNull(purpose);Objects.requireNonNull(status);
            if(!"PAYPAL".equals(provider)||!"SANDBOX".equals(environment))throw new IllegalArgumentException("Only PayPal Sandbox is enabled");
            if(maskedLabel==null||maskedLabel.isBlank()||maskedLabel.length()>254||version<0)throw new IllegalArgumentException("Invalid payment method");
        }
        public Method revoke(Instant now){return new Method(id,paymentAccountId,purpose,MethodStatus.REVOKED,provider,environment,encryptedReference,
                referenceHash,subjectHash,maskedLabel,linkedAt,micros(now),version+1);}
        @Override public String toString(){return "PaymentMethod[id="+id+",purpose="+purpose+",status="+status+",provider=PAYPAL,SANDBOX]";}
    }
    public static final class Authorization {
        public final UUID id,paymentAccountId,requestId;public final String requestHash,stateHash;
        public final Purpose purpose;public final Client client;public final Instant createdAt,expiresAt;
        public String encryptedState,encryptedSetup,setupHash,encryptedApproval,encryptedCustomer,completionHash,encryptedAccess,errorCode;
        public Instant updatedAt,accessExpiresAt;public Status status;public UUID methodId;public long version;
        public Authorization(UUID id,UUID paymentAccountId,UUID requestId,String requestHash,Purpose purpose,Client client,String stateHash,
                             String encryptedState,Status status,Instant createdAt,Instant expiresAt,Instant updatedAt,long version){
            this.id=Objects.requireNonNull(id);this.paymentAccountId=Objects.requireNonNull(paymentAccountId);this.requestId=Objects.requireNonNull(requestId);
            this.requestHash=Objects.requireNonNull(requestHash);this.purpose=Objects.requireNonNull(purpose);this.client=Objects.requireNonNull(client);
            this.stateHash=Objects.requireNonNull(stateHash);this.encryptedState=encryptedState;this.status=Objects.requireNonNull(status);
            this.createdAt=micros(createdAt);this.expiresAt=micros(expiresAt);this.updatedAt=micros(updatedAt);this.version=version;
            if(!this.expiresAt.isAfter(this.createdAt)||version<0)throw new IllegalArgumentException("Invalid authorization lifetime");
        }
        public boolean terminal(){return status==Status.LINKED||status==Status.CANCELLED||status==Status.FAILED||status==Status.EXPIRED;}
        public boolean expire(Instant now){
            if(!terminal()&&!micros(now).isBefore(expiresAt)){finish(Status.EXPIRED,Code.AUTHORIZATION_EXPIRED.name(),now);return true;}return false;
        }
        public void bindCompletion(String hash){
            if(completionHash!=null&&!completionHash.equals(hash))throw new BillingFailure(Code.IDEMPOTENCY_CONFLICT);completionHash=hash;
        }
        public void prepared(String setup,String hash,String approval,String customer,Instant now){
            if(status!=Status.PREPARING)throw new BillingFailure(Code.CONCURRENT_UPDATE);
            encryptedSetup=setup;setupHash=hash;encryptedApproval=approval;encryptedCustomer=customer;
            status=Status.PENDING_APPROVAL;errorCode=null;updatedAt=micros(now);
        }
        public void complete(UUID method,Instant now){methodId=Objects.requireNonNull(method);finish(Status.LINKED,null,now);}
        public void finish(Status next,String error,Instant now){
            if(terminal()&&status!=next)throw new BillingFailure(Code.CONCURRENT_UPDATE);
            status=next;errorCode=error;updatedAt=micros(now);
            if(terminal()){encryptedState=null;encryptedAccess=null;accessExpiresAt=null;encryptedApproval=null;}
        }
        public void transientFailure(Code error,Instant now){errorCode=error.name();updatedAt=micros(now);}
        @Override public String toString(){return "BillingAuthorization[id="+id+",purpose="+purpose+",status="+status+"]";}
    }
    public record Event(String id,String type,String resourceHash,String payloadHash,String encryptedPayload,String status,
                        Instant receivedAt,Instant processedAt,int attempts){
        @Override public String toString(){return "BillingEvent[id="+id+",type="+type+",status="+status+"]";}
    }
    public static Instant micros(Instant now){return Objects.requireNonNull(now).truncatedTo(ChronoUnit.MICROS);}
    public static Purpose purpose(String role){return switch(role){case "BRAND"->Purpose.PAYMENT;case "CREATOR"->Purpose.PAYOUT;default->throw new BillingFailure(Code.FORBIDDEN);};}
}
