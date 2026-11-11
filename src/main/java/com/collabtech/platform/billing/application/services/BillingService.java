package com.collabtech.platform.billing.application.services;

import com.collabtech.platform.billing.application.ports.BillingPorts.*;
import com.collabtech.platform.billing.domain.model.BillingModels.*;
import com.collabtech.platform.billing.domain.model.BillingModels;
import com.collabtech.platform.billing.domain.repositories.PaymentAccountRepository;
import com.collabtech.platform.billing.domain.exceptions.BillingFailure;
import com.collabtech.platform.billing.domain.exceptions.BillingFailure.Code;
import com.collabtech.platform.shared.application.pagination.*;
import java.time.*;
import java.util.*;

public final class BillingService {
    public record AuthorizationView(UUID authorizationId,Purpose purpose,String provider,String environment,Status status,
                                    Instant expiresAt,String approvalUrl,UUID methodId,String errorCode,long version){}
    public record MethodView(UUID methodId,Purpose purpose,MethodStatus status,String provider,String environment,String maskedLabel,Instant linkedAt,long version){}
    public record ReturnView(UUID authorizationId,Status status,String errorCode,String androidReturnUrl){}
    public record Readiness(UUID methodId,Purpose purpose,String provider,String environment){}
    public record WebhookView(String eventId,String status,boolean duplicate){}
    private record Start(Authorization authorization,boolean launch){}
    private record Completion(Authorization authorization,Account account,boolean launch){}
    private final PaymentAccountRepository repo;private final Queries queries;private final Access access;private final Gateway gateway;
    private final Secrets secrets;private final UnitOfWork work;private final Clock clock;
    public BillingService(PaymentAccountRepository repo,Queries queries,Access access,Gateway gateway,Secrets secrets,UnitOfWork work,Clock clock){
        this.repo=repo;this.queries=queries;this.access=access;this.gateway=gateway;this.secrets=secrets;this.work=work;this.clock=clock;
    }
    private Instant now(){return BillingModels.micros(clock.instant());}
    private void owner(UUID actor,UUID account){if(!actor.equals(account))throw new BillingFailure(Code.FORBIDDEN);access.actor(actor);}
    private Authorization locked(UUID id){var first=repo.authorization(id).orElseThrow(()->new BillingFailure(Code.NOT_FOUND));
        repo.byId(first.paymentAccountId,true).orElseThrow(()->new BillingFailure(Code.NOT_FOUND));return repo.authorization(id).orElseThrow();}
    private AuthorizationView view(Authorization a){return new AuthorizationView(a.id,a.purpose,"PAYPAL","SANDBOX",a.status,a.expiresAt,
        a.encryptedApproval==null?null:secrets.decrypt(a.encryptedApproval),a.methodId,a.errorCode,a.version);}
    public AuthorizationView begin(UUID actor,UUID account,UUID requestId,Client client){
        owner(actor,account);Objects.requireNonNull(requestId);Objects.requireNonNull(client);var who=access.actor(account);
        var config=gateway.configuration();if(!config.enabled()||!config.credentialsConfigured())throw new BillingFailure(Code.PROVIDER_NOT_CONFIGURED);
        Purpose purpose=BillingModels.purpose(who.role());String hash=secrets.fingerprint(Map.of("client",client.name(),"purpose",purpose.name()));
        var started=work.execute(()->{
            var root=repo.ensure(account,who.role(),now());var old=repo.request(root.id(),requestId);
            if(old.isPresent()){var a=old.get();if(!hash.equals(a.requestHash))throw new BillingFailure(Code.IDEMPOTENCY_CONFLICT);
                if(a.expire(now()))repo.save(a);
                boolean retry=a.status==Status.PREPARING&&(a.errorCode!=null||a.updatedAt.plusSeconds(60).isBefore(now()));
                if(retry){a.updatedAt=now();a.errorCode=null;repo.save(a);}return new Start(a,retry);
            }
            String state=secrets.random();var a=new Authorization(UUID.randomUUID(),root.id(),requestId,hash,purpose,client,secrets.digest(state),
                secrets.encrypt(state),Status.PREPARING,now(),now().plusSeconds(config.authorizationSeconds()),now(),0);
            repo.create(a);repo.audit(root.id(),a.id,null,"AUTHORIZATION_STARTED",now());return new Start(a,true);
        });
        var a=started.authorization();if(!started.launch())return view(a);
        try{
            String state=secrets.decrypt(a.encryptedState);Preparation p=a.purpose==Purpose.PAYMENT?gateway.prepare(a.id,account,state):
                new Preparation(null,null,gateway.identityApproval(state));
            return work.execute(()->{var current=locked(a.id);if(current.expire(now())){repo.save(current);return view(current);}
                if(current.status==Status.PREPARING){current.prepared(p.setupId()==null?null:secrets.encrypt(p.setupId()),p.setupId()==null?null:secrets.digest(p.setupId()),
                    secrets.encrypt(p.approvalUrl()),p.customerId()==null?null:secrets.encrypt(p.customerId()),now());repo.save(current);}
                return view(current);
            });
        }catch(BillingFailure failure){return failed(a.id,failure,false);}
    }
    private AuthorizationView failed(UUID id,BillingFailure error,boolean completing){
        return work.execute(()->{var a=locked(id);if(a.expire(now())){repo.save(a);return view(a);}
            if(a.terminal())return view(a);
            if(error.retryable()){
                if(completing)a.status=Status.PENDING_APPROVAL;
                a.transientFailure(error.code(),now());
            }else a.finish(Status.FAILED,error.code().name(),now());
            repo.save(a);repo.audit(a.paymentAccountId,a.id,null,"AUTHORIZATION_"+a.status.name(),now());return view(a);});
    }
    public AuthorizationView authorization(UUID actor,UUID id){
        var a=repo.authorization(id).orElseThrow(()->new BillingFailure(Code.NOT_FOUND));
        var root=repo.byId(a.paymentAccountId,false).orElseThrow();owner(actor,root.accountId());
        return work.execute(()->{var current=locked(id);if(current.expire(now()))repo.save(current);return view(current);});
    }
    public AuthorizationView cancel(UUID actor,UUID id){
        authorization(actor,id);
        return work.execute(()->{var a=locked(id);if(!a.terminal()){
            if(a.status==Status.COMPLETING)throw new BillingFailure(Code.CONCURRENT_UPDATE);
            a.finish(Status.CANCELLED,null,now());repo.save(a);repo.audit(a.paymentAccountId,a.id,null,"AUTHORIZATION_CANCELLED",now());}return view(a);});
    }
    public PageResult<MethodView> methods(UUID actor,UUID account,PageRequest page){
        owner(actor,account);var root=repo.account(account,false);if(root.isEmpty())return new PageResult<>(List.of(),0,page.page(),page.size());
        var result=queries.methods(root.get().id(),page);return new PageResult<>(result.items().stream().map(m->new MethodView(m.id(),m.purpose(),m.status(),
            m.provider(),m.environment(),m.maskedLabel(),m.linkedAt(),m.version())).toList(),result.total(),result.page(),result.size());
    }
    public Readiness requireUsable(UUID actor,UUID account,UUID methodId){
        owner(actor,account);var root=repo.account(account,false).orElseThrow(()->new BillingFailure(Code.METHOD_REQUIRED));
        var method=repo.method(methodId).filter(m->m.paymentAccountId().equals(root.id())&&m.purpose()==BillingModels.purpose(root.role())&&m.status()==MethodStatus.ACTIVE)
            .orElseThrow(()->new BillingFailure(Code.METHOD_REQUIRED));
        return new Readiness(method.id(),method.purpose(),method.provider(),method.environment());
    }
    public ReturnView callback(Purpose purpose,String state,String code,boolean cancelled){
        if(state==null||!state.matches("[A-Za-z0-9_-]{43}"))throw new BillingFailure(Code.INVALID_STATE);
        var found=repo.state(secrets.digest(state)).orElseThrow(()->new BillingFailure(Code.INVALID_STATE));
        var root=repo.byId(found.paymentAccountId,false).orElseThrow();access.actor(root.accountId());
        if(found.purpose!=purpose)throw new BillingFailure(Code.INVALID_STATE);
        if(!cancelled&&purpose==Purpose.PAYOUT&&(code==null||code.isBlank()||code.length()>2000))throw new BillingFailure(Code.INVALID_STATE);
        String completion=secrets.digest(cancelled?"cancelled":purpose==Purpose.PAYMENT?"funding":code);
        var claimed=work.execute(()->{
            var a=locked(found.id);
            if(a.terminal()){if(!cancelled&&a.completionHash!=null&&!a.completionHash.equals(completion))throw new BillingFailure(Code.IDEMPOTENCY_CONFLICT);return new Completion(a,root,false);}
            if(a.expire(now())){repo.save(a);return new Completion(a,root,false);}
            if(cancelled){
                if(a.status==Status.COMPLETING)throw new BillingFailure(Code.CONCURRENT_UPDATE);
                a.finish(Status.CANCELLED,null,now());repo.save(a);return new Completion(a,root,false);
            }
            if(a.status==Status.PREPARING)return new Completion(a,root,false);
            a.bindCompletion(completion);
            if(a.status==Status.COMPLETING){
                if(a.updatedAt.plusSeconds(120).isAfter(now()))return new Completion(a,root,false);
                // An authorization code may already have been consumed before a crash. Never exchange it twice.
                if(purpose==Purpose.PAYOUT&&a.encryptedAccess==null){a.finish(Status.FAILED,Code.PROVIDER_REJECTED.name(),now());repo.save(a);return new Completion(a,root,false);}
            }
            a.status=Status.COMPLETING;a.updatedAt=now();repo.save(a);return new Completion(a,root,true);
        });
        var a=claimed.authorization();if(!claimed.launch())return returned(a);
        try{
            Verified verified;
            if(purpose==Purpose.PAYMENT)verified=gateway.completeFunding(a.id,root.accountId(),secrets.decrypt(a.encryptedSetup),secrets.decrypt(a.encryptedCustomer));
            else{
                if(a.encryptedAccess==null){
                    Token token;
                    try{token=gateway.exchangeIdentity(code);}
                    catch(BillingFailure failure){
                        // A lost token response is ambiguous: ask for fresh consent rather than reuse a one-time code.
                        failed(a.id,new BillingFailure(failure.code()),true);return returned(repo.authorization(a.id).orElseThrow());
                    }
                    a=work.execute(()->{var current=locked(found.id);if(current.expire(now())){repo.save(current);return current;}
                        current.encryptedAccess=secrets.encrypt(token.value());current.accessExpiresAt=token.expiresAt();repo.save(current);return current;});
                }
                if(a.terminal())return returned(a);
                if(!now().isBefore(a.accessExpiresAt))throw new BillingFailure(Code.AUTHORIZATION_EXPIRED);
                verified=gateway.identity(secrets.decrypt(a.encryptedAccess));
            }
            Verified result=verified;
            a=work.execute(()->link(found.id,result));return returned(a);
        }catch(BillingFailure error){failed(found.id,error,true);return returned(repo.authorization(found.id).orElseThrow());}
    }
    private Authorization link(UUID id,Verified verified){
        var a=locked(id);if(a.expire(now())){repo.save(a);return a;}if(a.terminal())return a;
        if(verified.payerId()==null||!verified.payerId().matches("[A-Z0-9]{13}")||verified.email()==null||
            !verified.email().matches("[^@\\s]{1,128}@[^@\\s]{1,120}")||verified.reference()==null||!verified.reference().matches("[A-Za-z0-9_-]{1,128}"))
            throw new BillingFailure(Code.INVALID_PROVIDER_RESPONSE);
        String reference=secrets.digest(a.purpose.name()+":"+verified.reference()),subject=secrets.digest(verified.payerId());
        var previous=repo.subject(a.purpose,subject);
        if(previous.isPresent()&&!previous.get().paymentAccountId().equals(a.paymentAccountId))throw new BillingFailure(Code.OWNERSHIP_CONFLICT);
        var other=repo.reference(reference);
        if(other.isPresent()&&!other.get().paymentAccountId().equals(a.paymentAccountId))throw new BillingFailure(Code.OWNERSHIP_CONFLICT);
        if(repo.resource(reference,true,now()))throw new BillingFailure(Code.PROVIDER_REJECTED);
        var old=previous.orElse(null);String email=verified.email();int at=email.indexOf('@');
        String masked=email.substring(0,1)+"***"+email.substring(at);
        UUID method=old==null?UUID.randomUUID():old.id();var m=new Method(method,a.paymentAccountId,a.purpose,MethodStatus.ACTIVE,"PAYPAL","SANDBOX",
            secrets.encrypt(verified.reference()),reference,subject,masked,old==null?now():old.linkedAt(),now(),old==null?0:old.version()+1);
        repo.save(m);a.complete(method,now());repo.save(a);repo.audit(a.paymentAccountId,a.id,method,"METHOD_LINKED",now());return a;
    }
    private ReturnView returned(Authorization a){
        String url=a.client==Client.ANDROID?gateway.configuration().androidReturnUrl()+"?authorizationId="+a.id+"&status="+a.status.name():null;
        return new ReturnView(a.id,a.status,a.errorCode,url);
    }
    public WebhookView webhook(Signature signature,Map<String,Object> payload,String raw){
        if(!gateway.verify(signature,payload))throw new BillingFailure(Code.SIGNATURE_INVALID);
        String id=eventString(payload,"id"),type=eventString(payload,"event_type");
        String hash=secrets.fingerprint(payload);Object resource=payload.get("resource");
        String reference=null;
        if(resource instanceof Map<?,?> map&&map.get("id") instanceof String value&&!value.isBlank()&&value.length()<=128)
            reference=secrets.digest(Purpose.PAYMENT.name()+":"+value);
        String resourceHash=reference;
        // Reserve the verified event ID before effects; concurrent deliveries wait for the same inbox row.
        return work.execute(()->{
            boolean fresh=repo.reserve(new Event(id,type,resourceHash,hash,secrets.encrypt(raw),"RECEIVED",now(),null,0));
            var stored=repo.event(id).orElseThrow();
            if(!stored.payloadHash().equals(hash))throw new BillingFailure(Code.IDEMPOTENCY_CONFLICT);
            if(!fresh)return new WebhookView(id,stored.status(),true);
            String status="IGNORED";
            boolean revoke=Set.of("VAULT.PAYMENT-TOKEN.DELETED","VAULT.PAYMENT-TOKEN.DELETION-INITIATED").contains(type);
            if(revoke){
                if(resourceHash==null)throw new BillingFailure(Code.INVALID_WEBHOOK);
                var method=repo.reference(resourceHash);
                if(method.isPresent())repo.byId(method.get().paymentAccountId(),true);
                repo.revokeResource(resourceHash,now());
                if(method.isPresent()){var current=repo.method(method.get().id()).orElseThrow();repo.save(current.revoke(now()));
                    repo.audit(current.paymentAccountId(),null,current.id(),"METHOD_REVOKED",now());}
                status="PROCESSED";
            }else if(type.equals("VAULT.PAYMENT-TOKEN.CREATED"))status="UNMATCHED";
            else if(type.startsWith("BILLING.")||type.startsWith("PAYMENT.")||type.startsWith("CHECKOUT."))status="DEFERRED";
            repo.event(new Event(id,type,resourceHash,hash,secrets.encrypt(raw),status,now(),"DEFERRED".equals(status)?null:now(),1));
            return new WebhookView(id,status,false);
        });
    }
    private static String eventString(Map<String,Object> payload,String key){
        if(!(payload.get(key) instanceof String value)||!value.matches("[A-Za-z0-9._-]{1,128}"))throw new BillingFailure(Code.INVALID_WEBHOOK);return value;
    }
}
