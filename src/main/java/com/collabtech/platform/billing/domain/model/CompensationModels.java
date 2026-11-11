package com.collabtech.platform.billing.domain.model;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
public final class CompensationModels {
    private CompensationModels(){}
    public enum Kind { FUNDING,PAYOUT }
    public static final class Operation {
        public UUID id,leaseToken,methodId;
        public Kind kind;
        public String status="PREPARING",encryptedReference,referenceHash,encryptedApproval,encryptedState,stateHash,encryptedRecipient,payerHash,returnUrlSnapshot,client,errorCode,transactionIdHash;
        public Instant captureDispatchAt,createdAt,firstDispatchAt,leaseUntil,providerUpdatedAt,confirmedAt;
        public boolean uncertain,captureRequested,closedWithoutFunding;
        public boolean terminalNoTransfer(){return Set.of("FAILED","DENIED","BLOCKED","CANCELED","VOIDED","DECLINED","RETURNED").contains(status)&&confirmedAt==null;}
        public boolean claim(Instant now){if(leaseToken!=null&&leaseUntil!=null&&now.isBefore(leaseUntil))return false;leaseToken=UUID.randomUUID();leaseUntil=now.plusSeconds(120);return true;}
        public void release(){leaseToken=null;leaseUntil=null;}
        public boolean owns(UUID token){return Objects.equals(token,leaseToken);}
        @Override public String toString(){return "CompensationOperation[id="+id+",kind="+kind+",status="+status+"]";}
    }
    public static final class Compensation {
        public UUID id,collaborationId,agreementId,brandProfileId,creatorProfileId;
        public String type,currency,description,termsHash,latestExecutionStatus,errorCode,fundingStatus;
        public BigDecimal amount;
        public Instant createdAt,updatedAt,nextCheckAt,fundedAt,paidAt;
        public String fundedCaptureHash;
        public int openIncidents;
        public boolean fulfilled,eligible,wasPaid,financialReversal,requiresReview;
        public long version,executionVersion;
        public UUID currentFunding,currentPayout;
        public List<Operation> operations=new ArrayList<>();
        public Compensation(){}
        public boolean cash(){return "CASH".equals(type);}
        public Operation funding(){return currentFunding==null?null:operation(currentFunding);}
        public Operation payout(){return currentPayout==null?null:operation(currentPayout);}
        public Operation operation(UUID id){return operations.stream().filter(o->o.id.equals(id)).findFirst().orElseThrow();}
        public String status(){
            if(wasPaid)return financialReversal?"PAYMENT_REVERSED":"PAID";
            if(openIncidents>0)return "BLOCKED_INCIDENT";
            if("CANCELLED".equals(latestExecutionStatus))return "CANCELLED";
            if(!cash())return "NON_MONETARY";
            if(!eligible)return "PENDING_DELIVERABLES";
            if(financialReversal||requiresReview)return "RECONCILIATION_REQUIRED";
            var p=payout();if(p!=null&&fundedAt!=null){
                if(p.terminalNoTransfer())return "PAYMENT_FAILED";
                if(p.errorCode!=null&&p.uncertain)return "RECONCILIATION_REQUIRED";
                return "PAYOUT_PENDING";
            }
            if(fundedAt!=null)return "FUNDED";
            var f=funding();if(f!=null){
                if(f.terminalNoTransfer())return "PAYMENT_FAILED";
                if(f.errorCode!=null&&f.uncertain)return "RECONCILIATION_REQUIRED";
                if("CAPTURING".equals(f.status)||"CAPTURE_PENDING".equals(f.status))return "CAPTURE_PENDING";
                return "AWAITING_COMPANY_APPROVAL";
            }
            return "READY_FOR_PAYMENT";
        }
        @Override public String toString(){return "Compensation[id="+id+",status="+status()+"]";}
    }
    public record Command(UUID compensationId,UUID requestId,String action,String hash,UUID operationId,Instant createdAt){}
    public record Reference(String hash,UUID compensationId,UUID operationId,String kind,String encryptedReference){}
    public record EventWork(String eventId,UUID hint,String referenceHash,String encryptedHint,String status,int attempts,Instant nextAttemptAt,UUID lease,String errorCode){}
    public record Audit(UUID id,UUID operationId,UUID actor,String type,Instant time){}
}
