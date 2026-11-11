package com.collabtech.platform.billing.application.services;
import com.collabtech.platform.billing.application.ports.SubscriptionPorts.*;
import com.collabtech.platform.billing.application.ports.BillingPorts.*;
import com.collabtech.platform.billing.domain.model.SubscriptionModels.*;
import com.collabtech.platform.billing.domain.model.BillingModels.*;
import com.collabtech.platform.billing.domain.model.BillingModels;
import com.collabtech.platform.billing.domain.repositories.PaymentAccountRepository;
import com.collabtech.platform.billing.domain.exceptions.BillingFailure;
import com.collabtech.platform.billing.domain.exceptions.BillingFailure.Code;
import com.collabtech.platform.shared.application.pagination.*;
import java.time.*;
import java.util.*;
import java.math.BigDecimal;

public final class SubscriptionService {
    public record PlanView(UUID planId,String code,String name,String description,BigDecimal amount,String currency,String interval,
                           List<String> benefits,boolean demonstration,boolean available,String errorCode){}
    public record View(UUID subscriptionId,UUID planId,String planName,BigDecimal amount,String currency,String status,String providerStatus,
                       String provider,String environment,UUID methodId,boolean benefitsActive,List<String> benefits,String approvalUrl,
                       Instant consentExpiresAt,Instant paidThrough,Instant lastCheckedAt,String errorCode,long version){}
    public record Callback(UUID subscriptionId,String status,String errorCode,String androidReturnUrl){}
    private record Attempt(Subscription value,boolean launch){}
    private final Store store;private final PaymentAccountRepository accounts;private final Access access;private final Provider provider;private final Secrets secrets;private final UnitOfWork work;private final Clock clock;
    public SubscriptionService(Store store,PaymentAccountRepository accounts,Access access,Provider provider,Secrets secrets,UnitOfWork work,Clock clock){
        this.store=store;this.accounts=accounts;this.access=access;this.provider=provider;this.secrets=secrets;this.work=work;this.clock=clock;
    }
    private Instant now(){return BillingModels.micros(clock.instant());}
    private void owner(UUID actor,UUID account){if(!actor.equals(account)||!"BRAND".equals(access.actor(actor).role()))throw new BillingFailure(Code.FORBIDDEN);}
    private Plan plan(UUID id){return store.plan(id).orElseThrow(()->new BillingFailure(Code.PLAN_UNAVAILABLE));}
    private Subscription lock(UUID id){var first=store.byId(id).orElseThrow(()->new BillingFailure(Code.NOT_FOUND));accounts.byId(first.paymentAccountId,true).orElseThrow();return store.byId(id).orElseThrow();}
    private void authorize(UUID actor,UUID id){var s=store.byId(id).orElseThrow(()->new BillingFailure(Code.NOT_FOUND));owner(actor,accounts.byId(s.paymentAccountId,false).orElseThrow().accountId());}
    private void validatePlan(Plan p){
        if(p.providerPlanId()==null||p.providerPlanId().isBlank())throw new BillingFailure(Code.PLAN_UNAVAILABLE);
        var remote=provider.plan(p.providerPlanId());
        if(!p.providerPlanId().equals(remote.id())||!"ACTIVE".equals(remote.status())||!remote.supported()||
            !p.currency().equals(remote.currency())||p.amount().compareTo(remote.amount())!=0)throw new BillingFailure(Code.PLAN_UNAVAILABLE);
    }
    public List<PlanView> plans(UUID actor){if(!"BRAND".equals(access.actor(actor).role()))throw new BillingFailure(Code.FORBIDDEN);
        return store.availablePlans().stream().map(p->{String error=null;try{if(!provider.configured())throw new BillingFailure(Code.PROVIDER_NOT_CONFIGURED);validatePlan(p);}
            catch(BillingFailure failure){error=failure.code().name();}
            return new PlanView(p.id(),p.code(),p.name(),p.description(),p.amount(),p.currency(),"MONTH",p.benefits(),p.demonstration(),error==null,error);}).toList();
    }
    private View view(Subscription s){var p=plan(s.planId);boolean active=s.benefitsActive(now());
        return new View(s.id,p.id(),p.name(),p.amount(),p.currency(),s.status.name(),s.providerStatus,"PAYPAL","SANDBOX",s.methodId,active,active?p.benefits():List.of(),
            s.encryptedApproval!=null&&!s.cancelRequested&&now().isBefore(s.consentExpiresAt)?secrets.decrypt(s.encryptedApproval):null,
            s.consentExpiresAt,s.paidThrough,s.lastCheckedAt,s.errorCode,s.version);
    }
    public View begin(UUID actor,UUID account,UUID requestId,UUID planId,UUID methodId,Client client){
        owner(actor,account);Objects.requireNonNull(requestId);Objects.requireNonNull(client);
        String hash=secrets.fingerprint(Map.of("planId",planId.toString(),"methodId",methodId.toString(),"client",client.name()));
        var root=accounts.account(account,false);var replay=root.flatMap(r->store.request(r.id(),requestId));
        if(replay.isPresent()){replay.get().requireReplay(hash);return prepare(replay.get().id);}
        if(!provider.configured())throw new BillingFailure(Code.PROVIDER_NOT_CONFIGURED);var p=plan(planId);if(!store.available(planId))throw new BillingFailure(Code.PLAN_UNAVAILABLE);validatePlan(p);
        UUID id=work.execute(()->{
            var a=accounts.ensure(account,"BRAND",now());var existing=store.request(a.id(),requestId);
            if(existing.isPresent()){existing.get().requireReplay(hash);return existing.get().id;}
            if(store.current(a.id()).isPresent())throw new BillingFailure(Code.SUBSCRIPTION_EXISTS);
            if(!store.available(planId))throw new BillingFailure(Code.PLAN_UNAVAILABLE);
            var m=accounts.method(methodId).filter(v->v.paymentAccountId().equals(a.id())&&v.purpose()==Purpose.PAYMENT&&v.status()==MethodStatus.ACTIVE)
                .orElseThrow(()->new BillingFailure(Code.METHOD_REQUIRED));
            var s=new Subscription();s.id=UUID.randomUUID();s.paymentAccountId=a.id();s.planId=planId;s.methodId=methodId;s.requestId=requestId;
            s.returnUrlSnapshot=provider.returnUrl();s.payerHash=m.subjectHash();s.requestHash=hash;s.client=client.name();s.status=SubscriptionStatus.PREPARING;
            String state=secrets.random();s.stateHash=secrets.digest(state);s.encryptedState=secrets.encrypt(state);
            s.createdAt=now();s.updatedAt=now();s.nextCheckAt=now();s.consentExpiresAt=now().plusSeconds(1800);store.create(s);
            accounts.audit(a.id(),null,null,"SUBSCRIPTION_CREATED",now());return s.id;
        });
        return prepare(id);
    }
    private View prepare(UUID id){
        var attempt=work.execute(()->{var s=lock(id);
            if(s.terminal()||s.encryptedProvider!=null||s.cancelRequested)return new Attempt(s,false);
            if(s.attemptedAt!=null&&!now().isBefore(s.attemptedAt.plusSeconds(48*3600))){s.status=SubscriptionStatus.RECONCILIATION_REQUIRED;s.errorCode="IDEMPOTENCY_WINDOW_EXPIRED";store.save(s);return new Attempt(s,false);}
            if(!s.claim(now()))return new Attempt(s,false);
            if(s.attemptedAt==null)s.attemptedAt=now();s.nextCheckAt=now().plusSeconds(120);store.save(s);return new Attempt(s,true);
        });
        var s=attempt.value();if(!attempt.launch())return view(s);UUID lease=s.leaseToken;
        try{
            validatePlan(plan(s.planId));var created=provider.create(s.id,plan(s.planId).providerPlanId(),secrets.decrypt(s.encryptedState),s.returnUrlSnapshot);
            return work.execute(()->{var current=lock(id);if(!current.owns(lease))return view(current);
                current.encryptedProvider=secrets.encrypt(created.id());current.providerHash=secrets.digest("SUBSCRIPTION:"+created.id());
                current.encryptedApproval=created.approvalUrl()==null?null:secrets.encrypt(created.approvalUrl());
                current.status=SubscriptionStatus.APPROVAL_PENDING;current.errorCode=null;current.release();current.nextCheckAt=now().plusSeconds(30);
                store.save(current);return view(current);});
        }catch(BillingFailure failure){return failure(id,lease,failure);}
    }
    private View failure(UUID id,UUID lease,BillingFailure failure){return work.execute(()->{var s=lock(id);if(!s.owns(lease))return view(s);
        s.errorCode=failure.code().name();
        if(Set.of(Code.OWNERSHIP_CONFLICT,Code.PROVIDER_ACCOUNT_MISMATCH,Code.INVALID_PROVIDER_RESPONSE).contains(failure.code())){
            s.status=SubscriptionStatus.RECONCILIATION_REQUIRED;s.paidThrough=null;
        }
        s.release();s.nextCheckAt=now().plusSeconds(60);s.updatedAt=now();
        if(s.cancelRequested)s.status=SubscriptionStatus.CANCEL_PENDING;
        else if(s.encryptedProvider==null)s.status=SubscriptionStatus.PREPARING;
        store.save(s);return view(s);});}
    public View current(UUID actor,UUID account){owner(actor,account);var root=accounts.account(account,false);
        var value=root.flatMap(r->store.current(r.id()).or(()->store.latest(r.id())));
        if(value.isEmpty())return new View(null,null,null,null,null,"NONE",null,"PAYPAL","SANDBOX",null,false,List.of(),null,null,null,null,null,0);
        return detail(actor,value.get().id,true);
    }
    public View detail(UUID actor,UUID id,boolean refresh){authorize(actor,id);if(refresh)refresh(id,null);return view(store.byId(id).orElseThrow());}
    public PageResult<View> history(UUID actor,UUID account,PageRequest page){owner(actor,account);var root=accounts.account(account,false);
        if(root.isEmpty())return new PageResult<>(List.of(),0,page.page(),page.size());var result=store.history(root.get().id(),page);
        return new PageResult<>(result.items().stream().map(this::view).toList(),result.total(),result.page(),result.size());}
    public View cancel(UUID actor,UUID id){authorize(actor,id);requestCancel(id);refresh(id,null);return view(store.byId(id).orElseThrow());}
    private void requestCancel(UUID id){work.execute(()->{var s=lock(id);if(s.terminal())return null;
        if(s.encryptedProvider==null&&s.attemptedAt==null){s.end(SubscriptionStatus.CANCELLED,now());store.save(s);return null;}
        s.cancelRequested=true;s.status=SubscriptionStatus.CANCEL_PENDING;s.paidThrough=null;s.nextCheckAt=now();store.save(s);return null;});}
    public View resumeApproval(UUID actor,UUID id,long expectedVersion){
        authorize(actor,id);
        var snapshot=store.byId(id).orElseThrow();
        if(snapshot.encryptedProvider==null||snapshot.terminal())throw new BillingFailure(Code.CONCURRENT_UPDATE);
        var remote=provider.read(secrets.decrypt(snapshot.encryptedProvider));verifyIdentity(snapshot,remote);
        if(!"APPROVAL_PENDING".equals(remote.status()))throw new BillingFailure(Code.CONCURRENT_UPDATE);
        return work.execute(()->{var s=lock(id);if(s.version!=expectedVersion||s.leaseToken!=null||!now().isBefore(s.consentExpiresAt))throw new BillingFailure(Code.CONCURRENT_UPDATE);
            s.cancelRequested=false;s.status=SubscriptionStatus.APPROVAL_PENDING;s.errorCode=null;store.save(s);return view(s);});
    }
    public Callback callback(String state,boolean cancelled){
        if(state==null||!state.matches("[A-Za-z0-9_-]{43}"))throw new BillingFailure(Code.INVALID_STATE);
        var s=store.byState(secrets.digest(state)).orElseThrow(()->new BillingFailure(Code.INVALID_STATE));
        var root=accounts.byId(s.paymentAccountId,false).orElseThrow();owner(root.accountId(),root.accountId());
        if(!now().isBefore(s.consentExpiresAt)&&!s.terminal()&&Set.of("PREPARING","APPROVAL_PENDING").contains(s.status.name()))throw new BillingFailure(Code.AUTHORIZATION_EXPIRED);
        if(cancelled){
            work.execute(()->{var current=lock(s.id);
                if(current.status==SubscriptionStatus.APPROVAL_PENDING&&!current.cancelRequested){current.errorCode="APPROVAL_CANCELLED";store.save(current);}return null;});
        }else refresh(s.id,null);
        var latest=store.byId(s.id).orElseThrow();
        return new Callback(s.id,latest.status.name(),latest.errorCode,"ANDROID".equals(s.client)?"collabpro://subscription-return?subscriptionId="+s.id+"&status="+latest.status.name()+(cancelled?"&cancelled=true":""):null);
    }
    /** Remote reads occur outside transactions; a lease fences stale workers. */
    public boolean refresh(UUID id,String candidateProvider){
        var attempt=work.execute(()->{var s=lock(id);if(s.terminal())return new Attempt(s,false);
            if(s.encryptedProvider==null&&candidateProvider==null)return new Attempt(s,false);
            if(!s.claim(now()))return new Attempt(s,false);s.nextCheckAt=now().plusSeconds(120);store.save(s);return new Attempt(s,true);});
        var s=attempt.value();if(!attempt.launch())return s.terminal();UUID lease=s.leaseToken;
        try{
            String reference=s.encryptedProvider==null?candidateProvider:secrets.decrypt(s.encryptedProvider);
            var remote=provider.read(reference);verifyIdentity(s,remote);
            if(s.cancelRequested&&!"CANCELLED".equals(remote.status())&&!"EXPIRED".equals(remote.status())){
                provider.cancel(reference);remote=provider.read(reference);verifyIdentity(s,remote);
            }
            var result=remote;
            return work.execute(()->apply(id,lease,result));
        }catch(BillingFailure failure){failure(id,lease,failure);return false;}
    }
    private void verifyIdentity(Subscription s,Remote remote){
        if(!s.id.toString().equals(remote.customId())||!plan(s.planId).providerPlanId().equals(remote.planId())||
            (s.encryptedProvider!=null&&!secrets.decrypt(s.encryptedProvider).equals(remote.id())))throw new BillingFailure(Code.OWNERSHIP_CONFLICT);
        if(!s.cancelRequested&&remote.payerId()!=null&&!secrets.digest(remote.payerId()).equals(s.payerHash))throw new BillingFailure(Code.PROVIDER_ACCOUNT_MISMATCH);
    }
    private boolean apply(UUID id,UUID lease,Remote remote){
        var s=lock(id);if(!s.owns(lease))return false;
        if(remote.updatedAt()==null||(s.providerUpdatedAt!=null&&remote.updatedAt().isBefore(s.providerUpdatedAt))){
            s.errorCode="STALE_PROVIDER_RESPONSE";s.release();s.nextCheckAt=now().plusSeconds(60);store.save(s);return false;}
        if(s.encryptedProvider==null){s.encryptedProvider=secrets.encrypt(remote.id());s.providerHash=secrets.digest("SUBSCRIPTION:"+remote.id());}
        if("ACTIVE".equals(remote.status())&&s.latestPaymentHash!=null){
            var previous=store.payment(s.latestPaymentHash);var last=remote.charges().stream().filter(c->c.time()!=null).max(Comparator.comparing(Charge::time));
            if(previous.isPresent()&&(last.isEmpty()||last.get().time().isBefore(previous.get().paidAt()))){
                s.errorCode="STALE_PAYMENT_SNAPSHOT";s.release();s.nextCheckAt=now().plusSeconds(60);store.save(s);return false;
            }
        }
        if(!"APPROVAL_PENDING".equals(remote.status())){s.encryptedState=null;s.encryptedApproval=null;}
        s.providerStatus=remote.status();s.providerUpdatedAt=remote.updatedAt();s.lastCheckedAt=now();s.updatedAt=now();s.errorCode=null;s.paidThrough=null;s.release();s.nextCheckAt=now().plusSeconds(60);
        var p=plan(s.planId);
        for(var charge:remote.charges()){
            if(charge.time()==null||charge.time().isAfter(now().plusSeconds(120)))throw new BillingFailure(Code.INVALID_PROVIDER_RESPONSE);
            store.payment(new Payment(secrets.digest("SALE:"+charge.id()),s.id,secrets.encrypt(charge.id()),charge.status(),charge.amount(),charge.currency(),charge.time(),now()));
        }
        switch(remote.status()){
            case "CANCELLED"->s.end(SubscriptionStatus.CANCELLED,now());
            case "EXPIRED"->s.end(SubscriptionStatus.EXPIRED,now());
            case "SUSPENDED"->s.status=SubscriptionStatus.SUSPENDED;
            case "APPROVAL_PENDING"->s.status=SubscriptionStatus.APPROVAL_PENDING;
            case "APPROVED"->s.status=SubscriptionStatus.PAYMENT_PENDING;
            case "ACTIVE"->{
                var latest=remote.charges().stream().filter(c->!c.time().isBefore(s.createdAt.minusSeconds(60))).max(Comparator.comparing(Charge::time));
                s.status=remote.failedCount()>0?SubscriptionStatus.PAYMENT_FAILED:SubscriptionStatus.PAYMENT_PENDING;
                if(latest.isPresent()){
                    var c=latest.get();var payment=store.payment(secrets.digest("SALE:"+c.id())).orElseThrow();
                    boolean valid="COMPLETED".equals(c.status())&&!payment.reversed()&&p.amount().compareTo(c.amount())==0&&p.currency().equals(c.currency())
                        &&remote.payerId()!=null&&remote.failedCount()==0&&remote.nextBillingAt()!=null&&now().isBefore(remote.nextBillingAt())
                        &&remote.nextBillingAt().isAfter(c.time())&&remote.nextBillingAt().isBefore(c.time().plusSeconds(33*86400L))
                        &&(remote.lastFailureAt()==null||!remote.lastFailureAt().isAfter(c.time()))
                        &&(s.paymentFailureSignalAt==null||!s.paymentFailureSignalAt.isAfter(c.time()));
                    if(valid){s.status=SubscriptionStatus.ACTIVE;s.paidThrough=remote.nextBillingAt();s.latestPaymentHash=payment.hash();}
                    else{s.errorCode=Code.PAYMENT_UNCONFIRMED.name();if(payment.reversed()||!"COMPLETED".equals(c.status())||remote.failedCount()>0||(s.paymentFailureSignalAt!=null&&s.paymentFailureSignalAt.isAfter(c.time())))s.status=SubscriptionStatus.PAYMENT_FAILED;}
                }
            }
            default->throw new BillingFailure(Code.INVALID_PROVIDER_RESPONSE);
        }
        if(s.cancelRequested&&!s.terminal()){s.status=SubscriptionStatus.CANCEL_PENDING;s.paidThrough=null;}
        store.save(s);accounts.audit(s.paymentAccountId,null,null,"SUBSCRIPTION_"+s.status.name(),now());return true;
    }
    public void poll(int limit){for(var id:store.pollable(now(),limit))try{refresh(id,null);}catch(RuntimeException ignored){/* Durable next_check_at enables recovery without provider writes. */}}
}
