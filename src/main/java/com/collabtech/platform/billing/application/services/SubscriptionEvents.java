package com.collabtech.platform.billing.application.services;
import com.collabtech.platform.billing.application.ports.SubscriptionPorts.*;
import com.collabtech.platform.billing.application.ports.BillingPorts.*;
import com.collabtech.platform.billing.domain.model.SubscriptionModels.*;
import com.collabtech.platform.billing.domain.repositories.PaymentAccountRepository;
import java.time.*;
import java.util.*;
public final class SubscriptionEvents implements EventRouter {
    private final Store store;private final PaymentAccountRepository accounts;private final Secrets secrets;private final UnitOfWork work;private final SubscriptionService service;private final Clock clock;
    public SubscriptionEvents(Store store,PaymentAccountRepository accounts,Secrets secrets,UnitOfWork work,SubscriptionService service,Clock clock){
        this.store=store;this.accounts=accounts;this.secrets=secrets;this.work=work;this.service=service;this.clock=clock;
    }
    private static String string(Object v){return v instanceof String s&&!s.isBlank()&&s.length()<=128?s:null;}
    private static UUID uuid(Object v){try{return UUID.fromString(String.valueOf(v));}catch(Exception ignored){return null;}}
    public String enqueue(String eventId,Map<String,Object> payload){
        String type=string(payload.get("event_type"));if(type==null||!(payload.get("resource") instanceof Map<?,?> resource))return "DEFERRED";
        boolean subscription=type.startsWith("BILLING.SUBSCRIPTION."),sale=type.startsWith("PAYMENT.SALE.");
        if(!subscription&&!sale)return "DEFERRED";
        String provider=string(resource.get(subscription?"id":"billing_agreement_id"));
        String saleId=string(resource.get(type.endsWith("REFUNDED")?"sale_id":"id"));
        String paymentHash=sale&&saleId!=null?secrets.digest("SALE:"+saleId):null;
        var payment=paymentHash==null?Optional.<Payment>empty():store.payment(paymentHash);
        if(!subscription&&provider==null&&payment.isEmpty())return "DEFERRED";
        if(store.event(eventId,false).isPresent())return "QUEUED";
        UUID hint=payment.map(Payment::subscriptionId).orElseGet(()->uuid(resource.get("custom_id")));
        String hash=provider==null?null:secrets.digest("SUBSCRIPTION:"+provider);
        var associated=hash==null?Optional.<Subscription>empty():store.byProvider(hash);
        if(associated.isEmpty()&&hint!=null)associated=store.byId(hint);
        if(associated.isPresent()){
            var first=associated.get();accounts.byId(first.paymentAccountId,true);var s=store.byId(first.id).orElseThrow();
            boolean reversal=type.endsWith("REFUNDED")||type.endsWith("REVERSED");
            if(reversal&&payment.isPresent()){
                var p=payment.get();store.payment(new Payment(p.hash(),p.subscriptionId(),p.encryptedReference(),type.endsWith("REFUNDED")?"REFUNDED":"REVERSED",p.amount(),p.currency(),p.paidAt(),clock.instant()));
                if(!s.terminal()&&Objects.equals(s.latestPaymentHash,p.hash())){s.status=SubscriptionStatus.PAYMENT_FAILED;s.paidThrough=null;s.errorCode="PAYMENT_REVERSED";store.save(s);}
            }
            if(type.equals("BILLING.SUBSCRIPTION.PAYMENT.FAILED")&&!s.terminal()){
                Instant occurred;try{occurred=Instant.parse(String.valueOf(payload.get("create_time")));}catch(Exception ignored){occurred=clock.instant();}
                var last=s.latestPaymentHash==null?Optional.<Payment>empty():store.payment(s.latestPaymentHash);
                if(last.isEmpty()||occurred.isAfter(last.get().paidAt())){
                    s.paymentFailureSignalAt=occurred;s.paidThrough=null;s.status=SubscriptionStatus.PAYMENT_FAILED;s.errorCode="PAYMENT_FAILED";store.save(s);
                }
            }
        }
        store.event(new EventWork(eventId,hint,provider==null?null:secrets.encrypt(provider),hash,paymentHash,type,"PENDING",0,clock.instant(),null,null));
        return "QUEUED";
    }
    public void process(int limit){
        for(var candidate:store.due(clock.instant(),limit)){
            var job=work.execute(()->{
                var current=store.event(candidate.eventId(),true).orElseThrow();
                if(current.status().equals("DONE")||current.nextAttemptAt().isAfter(clock.instant()))return null;
                var claimed=new EventWork(current.eventId(),current.subscriptionHint(),current.encryptedProvider(),current.providerHash(),current.paymentHash(),current.eventType(),
                    "WORKING",current.attempts()+1,clock.instant().plusSeconds(120),UUID.randomUUID(),null);store.event(claimed);return claimed;
            });
            if(job==null)continue;
            boolean completed=false;String error=null;
            try{
                var s=job.providerHash()==null?Optional.<Subscription>empty():store.byProvider(job.providerHash());
                if(s.isEmpty()&&job.subscriptionHint()!=null)s=store.byId(job.subscriptionHint());
                if(s.isEmpty()&&job.paymentHash()!=null)s=store.payment(job.paymentHash()).flatMap(p->store.byId(p.subscriptionId()));
                if(s.isPresent())completed=service.refresh(s.get().id,job.encryptedProvider()==null?null:secrets.decrypt(job.encryptedProvider()));
                else error="SUBSCRIPTION_NOT_MATCHED";
            }catch(RuntimeException failure){error="RECONCILIATION_FAILED";}
            boolean done=completed;String code=error;
            work.execute(()->{var current=store.event(job.eventId(),true).orElseThrow();if(!Objects.equals(current.leaseToken(),job.leaseToken()))return null;
                store.event(new EventWork(job.eventId(),job.subscriptionHint(),job.encryptedProvider(),job.providerHash(),job.paymentHash(),job.eventType(),
                    done?"DONE":"PENDING",job.attempts(),clock.instant().plusSeconds(Math.min(300,15L*Math.max(1,job.attempts()))),null,code));
                if(done)store.processed(job.eventId(),clock.instant());return null;});
        }
    }
}
