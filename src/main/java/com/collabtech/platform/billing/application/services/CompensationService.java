package com.collabtech.platform.billing.application.services;
import com.collabtech.platform.billing.application.ports.CompensationPorts.*;
import com.collabtech.platform.billing.application.ports.BillingPorts.Secrets;
import com.collabtech.platform.billing.application.ports.BillingPorts.Queries;
import com.collabtech.platform.billing.application.ports.BillingPorts.UnitOfWork;
import com.collabtech.platform.billing.domain.model.CompensationModels.*;
import com.collabtech.platform.billing.domain.model.BillingModels.*;
import com.collabtech.platform.billing.domain.model.BillingModels;
import com.collabtech.platform.billing.domain.repositories.PaymentAccountRepository;
import com.collabtech.platform.billing.domain.exceptions.BillingFailure;
import com.collabtech.platform.billing.domain.exceptions.BillingFailure.Code;
import com.collabtech.platform.shared.application.pagination.*;
import java.time.*;
import java.util.*;
import java.math.BigDecimal;

public final class CompensationService {
    public record View(UUID compensationId,UUID collaborationId,UUID agreementId,String type,BigDecimal amount,String currency,String description,
        String status,boolean paymentApplicable,boolean eligible,int openIncidentCount,boolean wasPaid,Instant fundedAt,Instant paidAt,
        String fundingStatus,String payoutStatus,String provider,String environment,String approvalUrl,String errorCode,
        String unavailableReason,long version,List<String> availableActions){}
    public record Callback(UUID compensationId,String status,String androidReturnUrl){}
    private record Claimed(Compensation root,UUID operationId,UUID lease,boolean launch){}
    private final Context context;private final Store store;private final PaymentAccountRepository accounts;private final Queries methods;private final Provider provider;
    private final Secrets secrets;private final UnitOfWork work;private final Clock clock;
    public CompensationService(Context context,Store store,PaymentAccountRepository accounts,Queries methods,Provider provider,Secrets secrets,UnitOfWork work,Clock clock){
        this.context=context;this.store=store;this.accounts=accounts;this.methods=methods;this.provider=provider;this.secrets=secrets;this.work=work;this.clock=clock;
    }
    private Instant now(){return BillingModels.micros(clock.instant());}
    private Execution execution(UUID id,boolean lock){return context.execution(id,lock).orElseThrow(()->new BillingFailure(Code.NOT_FOUND));}
    private void authorize(Actor actor,Execution e,boolean brandOnly){
        boolean brand="BRAND".equals(actor.role())&&actor.profileId().equals(e.brandProfileId());
        boolean creator="CREATOR".equals(actor.role())&&actor.profileId().equals(e.creatorProfileId());
        if(!brand&&(!creator||brandOnly))throw new BillingFailure(Code.FORBIDDEN);
    }
    private String hash(Execution e){var value=new LinkedHashMap<String,Object>();value.put("agreementId",e.agreementId());value.put("brand",e.brandProfileId());value.put("creator",e.creatorProfileId());
        value.put("type",e.type());value.put("amount",e.amount()==null?null:e.amount().stripTrailingZeros().toPlainString());value.put("currency",e.currency());value.put("description",e.description());return secrets.fingerprint(value);}
    private Compensation project(Execution e){
        var found=store.byCollaboration(e.collaborationId(),true);
        if(found.isEmpty()){
            var c=new Compensation();c.id=UUID.randomUUID();c.collaborationId=e.collaborationId();c.agreementId=e.agreementId();c.brandProfileId=e.brandProfileId();c.creatorProfileId=e.creatorProfileId();
            c.type=e.type();c.amount=e.amount();c.currency=e.currency();c.description=e.description();c.termsHash=hash(e);c.createdAt=now();c.updatedAt=now();c.nextCheckAt=now().plusSeconds(300);facts(c,e);store.create(c);
            store.audit(c.id,null,null,"COMPENSATION_PROJECTED",now());return c;
        }
        var c=found.get();if(!c.termsHash.equals(hash(e))||!c.agreementId.equals(e.agreementId())||!c.brandProfileId.equals(e.brandProfileId())||!c.creatorProfileId.equals(e.creatorProfileId())||!c.type.equals(e.type())||!Objects.equals(c.currency,e.currency())||!Objects.equals(c.description,e.description())||(c.amount==null?e.amount()!=null:e.amount()==null||c.amount.compareTo(e.amount())!=0))throw new BillingFailure(Code.FROZEN_TERMS_CHANGED);
        if(c.executionVersion!=e.version()||!Objects.equals(c.latestExecutionStatus,e.status())||c.openIncidents!=e.openIncidents()||c.eligible!=e.eligible()){
            facts(c,e);c.updatedAt=now();c.nextCheckAt=now();store.save(c);
        }return c;
    }
    private void facts(Compensation c,Execution e){c.executionVersion=e.version();c.latestExecutionStatus=e.status();c.eligible=e.eligible();c.fulfilled=e.fulfilled();c.openIncidents=e.openIncidents();}
    private Compensation locked(UUID id){var previous=store.byId(id).orElseThrow(()->new BillingFailure(Code.NOT_FOUND));return project(execution(previous.collaborationId,true));}
    public void project(UUID collaboration){work.execute(()->{project(execution(collaboration,true));return null;});}
    private void eligible(Compensation c){
        if(!c.cash())throw new BillingFailure(Code.NON_MONETARY_COMPENSATION);
        if(!c.eligible)throw new BillingFailure(Code.COMPENSATION_NOT_ELIGIBLE);
        if(!provider.settings().currencies().contains(c.currency))throw new BillingFailure(Code.UNSUPPORTED_PAYMENT_CURRENCY);
        if(!provider.settings().enabled())throw new BillingFailure(Code.PROVIDER_NOT_CONFIGURED);
        if(c.wasPaid||c.financialReversal||c.requiresReview)throw new BillingFailure(Code.RECONCILIATION_REQUIRED);
    }
    private View view(Compensation c,Actor actor){
        var actions=new ArrayList<String>();String reason=null;
        if(!c.cash())reason=Code.NON_MONETARY_COMPENSATION.name();
        else if(!c.eligible)reason=Code.COMPENSATION_NOT_ELIGIBLE.name();
        else if(!provider.settings().currencies().contains(c.currency))reason=Code.UNSUPPORTED_PAYMENT_CURRENCY.name();
        else if(!provider.settings().enabled())reason=Code.PROVIDER_NOT_CONFIGURED.name();
        if("BRAND".equals(actor.role())&&reason==null&&!c.wasPaid&&!c.financialReversal&&!c.requiresReview){
            if(c.funding()==null||c.funding().closedWithoutFunding||c.fundedAt!=null&&(c.payout()==null||c.payout().terminalNoTransfer()))actions.add("INITIATE_PAYMENT");
            if(c.funding()!=null&&c.fundedAt==null)actions.add("CONFIRM_FUNDING");
        }
        var f=c.funding();var p=c.payout();String approval=f!=null&&c.fundedAt==null&&!c.wasPaid&&"BRAND".equals(actor.role())&&f.encryptedApproval!=null&&now().isBefore(f.createdAt.plusSeconds(1800))?secrets.decrypt(f.encryptedApproval):null;
        return new View(c.id,c.collaborationId,c.agreementId,c.type,c.amount,c.currency,c.description,c.status(),c.cash(),c.eligible,c.openIncidents,c.wasPaid,
            c.fundedAt,c.paidAt,c.fundingStatus,p==null?null:p.status,"PAYPAL","SANDBOX",approval,c.errorCode,reason,c.version,List.copyOf(actions));
    }
    public View readResource(UUID actorId,UUID id){
        var root=store.byId(id).orElseThrow(()->new BillingFailure(Code.NOT_FOUND));
        return read(actorId,root.collaborationId,true);
    }
    public View read(UUID actorId,UUID collaboration,boolean refresh){
        var actor=context.actor(actorId);authorize(actor,execution(collaboration,false),false);
        var c=work.execute(()->project(execution(collaboration,true)));if(refresh)reconcile(c.id,false);return view(work.execute(()->locked(c.id)),actor);
    }
    private Method method(UUID accountId,UUID id,Purpose purpose){
        var root=accounts.account(accountId,true).orElseThrow(()->new BillingFailure(Code.METHOD_REQUIRED));
        return accounts.method(id).filter(m->m.paymentAccountId().equals(root.id())&&m.status()==MethodStatus.ACTIVE&&m.purpose()==purpose).orElseThrow(()->new BillingFailure(Code.METHOD_REQUIRED));
    }
    private Method recipient(UUID creatorProfile){
        var party=context.party(creatorProfile,"CREATOR");if(!party.active())throw new BillingFailure(Code.ACCOUNT_NOT_ACTIVE);
        var root=accounts.account(party.accountId(),true).orElseThrow(()->new BillingFailure(Code.METHOD_REQUIRED));
        var selected=methods.active(root.id(),Purpose.PAYOUT)
            .orElseThrow(()->new BillingFailure(Code.METHOD_REQUIRED));
        String receiver=secrets.decrypt(selected.encryptedReference());if(!selected.subjectHash().equals(secrets.digest(receiver)))throw new BillingFailure(Code.OWNERSHIP_CONFLICT);return selected;
    }
    private Operation operation(Compensation c,Kind kind,Method method,String client){
        var op=new Operation();op.id=UUID.randomUUID();op.kind=kind;op.methodId=method.id();op.createdAt=now();op.client=client;op.payerHash=method.subjectHash();
        if(kind==Kind.FUNDING){String state=secrets.random();op.encryptedState=secrets.encrypt(state);op.stateHash=secrets.digest("STATE:"+state);op.returnUrlSnapshot=provider.settings().returnUrl();
            store.reference(new Reference(op.stateHash,c.id,op.id,"STATE",op.encryptedState));}
        else op.encryptedRecipient=method.encryptedReference();
        c.operations.add(op);store.reference(new Reference(secrets.digest("OPERATION:"+op.id),c.id,op.id,"OPERATION",secrets.encrypt(op.id.toString())));return op;
    }
    private boolean replay(Compensation c,UUID request,String action,String hash){
        var found=store.command(c.id,request);if(found.isEmpty())return false;
        if(!found.get().action().equals(action)||!found.get().hash().equals(hash))throw new BillingFailure(Code.IDEMPOTENCY_CONFLICT);return true;
    }
    public View start(UUID actorId,UUID collaboration,UUID request,UUID methodId,Client client,long expectedVersion){
        var actor=context.actor(actorId);authorize(actor,execution(collaboration,false),true);
        String fingerprint=secrets.fingerprint(Map.of("action","START","method",methodId==null?"":methodId.toString(),"client",client.name()));
        boolean existingCommand=work.execute(()->{var c=project(execution(collaboration,true));if(replay(c,request,"START",fingerprint))return true;
            if(c.version!=expectedVersion)throw new BillingFailure(Code.CONCURRENT_UPDATE);eligible(c);return false;});
        if(!existingCommand)provider.preflight();
        UUID id=work.execute(()->{
            var c=project(execution(collaboration,true));
            if(replay(c,request,"START",fingerprint))return c.id;
            if(c.version!=expectedVersion)throw new BillingFailure(Code.CONCURRENT_UPDATE);eligible(c);
            if(c.funding()!=null&&c.fundedAt==null&&!c.funding().closedWithoutFunding)throw new BillingFailure(Code.PAYMENT_ALREADY_IN_PROGRESS);
            if(c.payout()!=null&&!c.payout().terminalNoTransfer()&&c.fundedAt!=null)throw new BillingFailure(Code.PAYMENT_ALREADY_IN_PROGRESS);
            var creator=recipient(c.creatorProfileId);
            if(c.fundedAt==null){
                if(methodId==null)throw new BillingFailure(Code.METHOD_REQUIRED);
                var sender=method(actorId,methodId,Purpose.PAYMENT);
                c.currentFunding=operation(c,Kind.FUNDING,sender,client.name()).id;
            }
            c.currentPayout=operation(c,Kind.PAYOUT,creator,client.name()).id;c.errorCode=null;c.nextCheckAt=now();c.updatedAt=now();
            store.save(c);store.command(new Command(c.id,request,"START",fingerprint,c.fundedAt==null?c.currentFunding:c.currentPayout,now()));
            store.audit(c.id,c.fundedAt==null?c.currentFunding:c.currentPayout,actorId,"PAYMENT_AUTHORIZED",now());return c.id;
        });
        createOrder(id);if(provider.settings().automaticPayouts())sendPayout(id);return view(work.execute(()->locked(id)),actor);
    }
    private Claimed claim(UUID id,Kind kind,boolean requireEligible){
        return work.execute(()->{var c=locked(id);if(requireEligible)eligible(c);
            var op=kind==Kind.FUNDING?c.funding():c.payout();
            if(op==null||!op.claim(now()))return new Claimed(c,op==null?null:op.id,null,false);
            c.nextCheckAt=now().plusSeconds(120);store.save(c);return new Claimed(c,op.id,op.leaseToken,true);});
    }
    private void failed(Claimed attempt,BillingFailure error){
        work.execute(()->{var c=locked(attempt.root().id);var op=c.operation(attempt.operationId());if(!op.owns(attempt.lease()))return null;
            op.errorCode=error.code().name();op.uncertain=op.firstDispatchAt!=null||op.captureDispatchAt!=null;
            c.errorCode=op.errorCode;if(Set.of(Code.RECONCILIATION_REQUIRED,Code.OWNERSHIP_CONFLICT,Code.FROZEN_TERMS_CHANGED).contains(error.code()))c.requiresReview=true;op.release();c.nextCheckAt=now().plusSeconds(60);store.save(c);
            store.audit(c.id,op.id,null,"PAYMENT_ATTEMPT_FAILED",now());return null;});
    }
    private void createOrder(UUID id){
        var first=store.byId(id).orElseThrow();if(first.funding()==null||first.fundedAt!=null||first.funding().encryptedReference!=null||first.wasPaid)return;
        var claim=claim(id,Kind.FUNDING,true);if(!claim.launch())return;var op=claim.root().operation(claim.operationId());
        try{
            boolean dispatch=work.execute(()->{var c=locked(id);eligible(c);var current=c.operation(op.id);if(!current.owns(claim.lease()))return false;
                if(current.firstDispatchAt!=null&&!now().isBefore(current.firstDispatchAt.plusSeconds(provider.settings().createRetrySeconds())))throw new BillingFailure(Code.RECONCILIATION_REQUIRED);
                if(current.firstDispatchAt==null)current.firstDispatchAt=now();store.save(c);return true;});
            if(!dispatch)return;
            var created=provider.createOrder(new Funding(op.id,id,claim.root().amount,claim.root().currency,secrets.decrypt(op.encryptedState),op.returnUrlSnapshot));
            work.execute(()->{var c=locked(id);var current=c.operation(op.id);if(!current.owns(claim.lease()))return null;
                bind(c,current,"ORDER",created.id());current.encryptedApproval=created.approvalUrl()==null?null:secrets.encrypt(created.approvalUrl());current.status="CREATED";current.uncertain=false;current.errorCode=null;current.release();c.errorCode=null;c.nextCheckAt=now().plusSeconds(30);store.save(c);return null;});
        }catch(BillingFailure failure){failed(claim,failure);}
    }
    private void bind(Compensation c,Operation op,String kind,String reference){
        String hash=secrets.digest(kind+":"+reference);store.reference(new Reference(hash,c.id,op.id,kind,secrets.encrypt(reference)));
        if(kind.equals("ORDER")||kind.equals("BATCH")){op.encryptedReference=secrets.encrypt(reference);op.referenceHash=hash;}
    }
    private void verifyOrder(Compensation c,Operation op,Order order){
        if(!c.id.toString().equals(order.compensationId())||!op.id.toString().equals(order.operationId())||c.amount.compareTo(order.amount())!=0||!c.currency.equals(order.currency())||
            op.encryptedReference!=null&&!secrets.decrypt(op.encryptedReference).equals(order.id()))throw new BillingFailure(Code.OWNERSHIP_CONFLICT);
        if(order.payerId()!=null&&!op.payerHash.equals(secrets.digest(order.payerId())))throw new BillingFailure(Code.PROVIDER_ACCOUNT_MISMATCH);
    }
    private void applyOrder(Compensation c,Operation op,Order order){
        verifyOrder(c,op,order);
        if(op.providerUpdatedAt!=null&&order.updatedAt()!=null&&order.updatedAt().isBefore(op.providerUpdatedAt))return;
        bind(c,op,"ORDER",order.id());op.providerUpdatedAt=order.updatedAt();op.status=order.status();op.errorCode=null;op.uncertain=false;c.errorCode=null;
        if(c.fundedAt==null&&("VOIDED".equals(order.status())||"COMPLETED".equals(order.status())&&!order.captures().isEmpty()&&order.captures().stream().allMatch(p->Set.of("DECLINED","FAILED","DENIED").contains(p.status())))){op.closedWithoutFunding=true;op.status="FAILED";}
        for(var capture:order.captures()){
            if(c.amount.compareTo(capture.amount())!=0||!c.currency.equals(capture.currency()))throw new BillingFailure(Code.PAYMENT_UNCONFIRMED);
            String hash=secrets.digest("CAPTURE:"+capture.id());store.reference(new Reference(hash,c.id,op.id,"CAPTURE",secrets.encrypt(capture.id())));
            if("COMPLETED".equals(capture.status())){
                if(capture.time()==null||order.payerId()==null)throw new BillingFailure(Code.INVALID_PROVIDER_RESPONSE);
                if(c.fundedCaptureHash!=null&&!c.fundedCaptureHash.equals(hash))throw new BillingFailure(Code.RECONCILIATION_REQUIRED);
                c.fundedCaptureHash=hash;if(c.fundedAt==null)c.fundedAt=capture.time();c.fundingStatus="COMPLETED";op.confirmedAt=c.fundedAt;op.status="CAPTURED";op.encryptedApproval=null;op.encryptedState=null;
            }else if(Set.of("REFUNDED","PARTIALLY_REFUNDED","REVERSED").contains(capture.status())){
                c.financialReversal=true;c.fundingStatus=capture.status();c.errorCode=Code.RECONCILIATION_REQUIRED.name();
            }else if(c.fundedAt==null){c.fundingStatus=capture.status();op.status=Set.of("DECLINED","FAILED","DENIED").contains(capture.status())?capture.status():"CAPTURE_PENDING";}
        }
    }
    public View confirm(UUID actorId,UUID collaboration,UUID request,long expectedVersion){
        var actor=context.actor(actorId);authorize(actor,execution(collaboration,false),true);
        var snapshot=work.execute(()->project(execution(collaboration,true)));if(snapshot.funding()==null||snapshot.funding().encryptedReference==null)throw new BillingFailure(Code.PAYMENT_UNCONFIRMED);
        String hash=secrets.fingerprint(Map.of("action","CONFIRM","funding",snapshot.currentFunding.toString()));
        if(work.execute(()->replay(locked(snapshot.id),request,"CONFIRM",hash))){reconcile(snapshot.id,true);return view(work.execute(()->locked(snapshot.id)),actor);}
        var observed=provider.readOrder(secrets.decrypt(snapshot.funding().encryptedReference));verifyOrder(snapshot,snapshot.funding(),observed);
        UUID id=work.execute(()->{var c=locked(snapshot.id);if(replay(c,request,"CONFIRM",hash))return c.id;
            if(c.version!=expectedVersion)throw new BillingFailure(Code.CONCURRENT_UPDATE);eligible(c);
            if(!Set.of("APPROVED","COMPLETED").contains(observed.status()))throw new BillingFailure(Code.AWAITING_APPROVAL);
            if(!Objects.equals(c.currentFunding,snapshot.currentFunding))throw new BillingFailure(Code.CONCURRENT_UPDATE);
            c.funding().captureRequested=true;store.command(new Command(c.id,request,"CONFIRM",hash,c.currentFunding,now()));store.save(c);
            store.audit(c.id,c.currentFunding,actorId,"FUNDING_CONFIRMED_BY_COMPANY",now());return c.id;});
        capture(id);if(provider.settings().automaticPayouts())sendPayout(id);return view(work.execute(()->locked(id)),actor);
    }
    private void capture(UUID id){
        var first=store.byId(id).orElseThrow();if(first.funding()==null||first.fundedAt!=null||!first.funding().captureRequested||first.wasPaid)return;
        var claim=claim(id,Kind.FUNDING,true);if(!claim.launch())return;var op=claim.root().operation(claim.operationId());
        try{
            var order=provider.readOrder(secrets.decrypt(op.encryptedReference));verifyOrder(claim.root(),op,order);
            boolean completed=order.captures().stream().anyMatch(p->"COMPLETED".equals(p.status()));
            if(!completed){
                if(!"APPROVED".equals(order.status()))throw new BillingFailure(Code.AWAITING_APPROVAL);
                work.execute(()->{var c=locked(id);eligible(c);var current=c.operation(op.id);if(!current.owns(claim.lease()))throw new BillingFailure(Code.CONCURRENT_UPDATE);
                    if(current.captureDispatchAt!=null&&!now().isBefore(current.captureDispatchAt.plusSeconds(provider.settings().createRetrySeconds())))throw new BillingFailure(Code.RECONCILIATION_REQUIRED);
                    if(current.captureDispatchAt==null)current.captureDispatchAt=now();current.status="CAPTURING";store.save(c);return null;});
                provider.capture(order.id(),op.id);order=provider.readOrder(order.id());
            }
            var result=order;work.execute(()->{var c=locked(id);var current=c.operation(op.id);if(!current.owns(claim.lease()))return null;
                applyOrder(c,current,result);current.release();c.nextCheckAt=now().plusSeconds(30);store.save(c);store.audit(c.id,current.id,null,"FUNDING_RECONCILED",now());return null;});
        }catch(BillingFailure failure){failed(claim,failure);}
    }
    private void verifyPayout(Compensation c,Operation op,Payout payout){
        if(!op.id.toString().equals(payout.senderBatchId())||payout.amount()==null||c.amount.compareTo(payout.amount())!=0||!c.currency.equals(payout.currency()))
            throw new BillingFailure(Code.OWNERSHIP_CONFLICT);
        if(payout.itemId()!=null&&(!op.id.toString().equals(payout.senderItemId())||!secrets.decrypt(op.encryptedRecipient).equals(payout.receiver())))throw new BillingFailure(Code.OWNERSHIP_CONFLICT);
    }
    private void applyPayout(Compensation c,Operation op,Payout payout){
        verifyPayout(c,op,payout);
        if(op.providerUpdatedAt!=null&&payout.updatedAt()!=null&&payout.updatedAt().isBefore(op.providerUpdatedAt))return;
        bind(c,op,"BATCH",payout.batchId());if(payout.itemId()!=null)bind(c,op,"PAYOUT_ITEM",payout.itemId());
        if(payout.transactionId()!=null){bind(c,op,"TRANSACTION",payout.transactionId());op.transactionIdHash=secrets.digest("TRANSACTION:"+payout.transactionId());}
        if(op.confirmedAt!=null&&Set.of("PENDING","PROCESSING","UNCLAIMED","ONHOLD").contains(payout.status()))return;
        op.status=payout.status();op.providerUpdatedAt=payout.updatedAt();op.errorCode=null;op.uncertain=false;c.errorCode=null;
        if("SUCCESS".equals(payout.status())){
            if(payout.itemId()==null||payout.transactionId()==null||payout.updatedAt()==null)throw new BillingFailure(Code.INVALID_PROVIDER_RESPONSE);
            if(!c.wasPaid){c.wasPaid=true;c.paidAt=payout.updatedAt();store.audit(c.id,op.id,null,"COMPENSATION_PAID",now());}
            op.confirmedAt=c.paidAt;
        }else if(op.confirmedAt!=null&&Set.of("RETURNED","REFUNDED","REVERSED").contains(payout.status())){
            c.financialReversal=true;c.errorCode=Code.RECONCILIATION_REQUIRED.name();
        }
    }
    private void sendPayout(UUID id){
        var first=store.byId(id).orElseThrow();var existing=first.payout();if(first.fundedAt==null||existing==null||existing.encryptedReference!=null||existing.terminalNoTransfer()||first.wasPaid||first.financialReversal)return;
        var claim=claim(id,Kind.PAYOUT,true);if(!claim.launch())return;var op=claim.root().operation(claim.operationId());
        try{
            // Re-read funding before the irreversible outbound operation.
            var funding=claim.root().funding();var order=provider.readOrder(secrets.decrypt(funding.encryptedReference));
            verifyOrder(claim.root(),funding,order);
            if(order.captures().stream().noneMatch(p->"COMPLETED".equals(p.status())&&secrets.digest("CAPTURE:"+p.id()).equals(claim.root().fundedCaptureHash)))throw new BillingFailure(Code.PAYMENT_UNCONFIRMED);
            work.execute(()->{var c=locked(id);eligible(c);var current=c.operation(op.id);if(!current.owns(claim.lease()))throw new BillingFailure(Code.CONCURRENT_UPDATE);
                var creator=context.party(c.creatorProfileId,"CREATOR");if(!creator.active())throw new BillingFailure(Code.ACCOUNT_NOT_ACTIVE);
                method(creator.accountId(),current.methodId,Purpose.PAYOUT);
                if(current.firstDispatchAt!=null&&!now().isBefore(current.firstDispatchAt.plusSeconds(provider.settings().payoutRetrySeconds())))throw new BillingFailure(Code.RECONCILIATION_REQUIRED);
                if(current.firstDispatchAt==null)current.firstDispatchAt=now();current.status="DISPATCHING";store.save(c);store.audit(c.id,current.id,null,"PAYOUT_DISPATCH_AUTHORIZED",now());return null;});
            var result=provider.createPayout(op.id,id,secrets.decrypt(op.encryptedRecipient),claim.root().amount,claim.root().currency);
            work.execute(()->{var c=locked(id);var current=c.operation(op.id);if(!current.owns(claim.lease()))return null;applyPayout(c,current,result);current.release();c.nextCheckAt=now().plusSeconds(60);store.save(c);return null;});
        }catch(BillingFailure failure){failed(claim,failure);}
    }
    /** GET reconciliation never creates or captures a funding order. */
    public boolean reconcile(UUID id,boolean allowDispatch){
        var before=store.byId(id).orElseThrow();
        try{
            var f=before.funding();if(f!=null&&f.encryptedReference!=null){
                var remote=provider.readOrder(secrets.decrypt(f.encryptedReference));
                work.execute(()->{var c=locked(id);var current=c.operation(f.id);applyOrder(c,current,remote);c.nextCheckAt=now().plusSeconds(60);store.save(c);return null;});
            }
            var c=store.byId(id).orElseThrow();var p=c.payout();
            if(p!=null&&p.encryptedReference!=null){var result=provider.readPayout(secrets.decrypt(p.encryptedReference));
                work.execute(()->{var current=locked(id);applyPayout(current,current.operation(p.id),result);current.nextCheckAt=now().plusSeconds(60);store.save(current);return null;});}
            if(allowDispatch){var current=work.execute(()->locked(id));if(current.eligible&&!current.wasPaid&&!current.financialReversal){
                createOrder(id);capture(id);if(provider.settings().automaticPayouts())sendPayout(id);
            }}
            work.execute(()->{var latest=locked(id);latest.nextCheckAt=now().plusSeconds(60);store.save(latest);return null;});return true;
        }catch(BillingFailure failure){
            work.execute(()->{var c=locked(id);c.errorCode=failure.code().name();if(Set.of(Code.RECONCILIATION_REQUIRED,Code.OWNERSHIP_CONFLICT,Code.FROZEN_TERMS_CHANGED).contains(failure.code()))c.requiresReview=true;c.nextCheckAt=now().plusSeconds(60);store.save(c);return null;});return false;
        }
    }
    public Callback callback(String state,boolean cancelled){
        if(state==null||!state.matches("[A-Za-z0-9_-]{43}"))throw new BillingFailure(Code.INVALID_STATE);
        var c=store.state(secrets.digest("STATE:"+state)).orElseThrow(()->new BillingFailure(Code.INVALID_STATE));
        var ref=store.reference(secrets.digest("STATE:"+state)).orElseThrow();var op=c.operation(ref.operationId());
        if(!now().isBefore(op.createdAt.plusSeconds(1800))&&c.fundedAt==null)throw new BillingFailure(Code.AUTHORIZATION_EXPIRED);
        if(!cancelled)reconcile(c.id,false);
        var latest=store.byId(c.id).orElseThrow();
        return new Callback(c.id,latest.status(),"ANDROID".equals(op.client)?"collabpro://compensation-return?collaborationId="+c.collaborationId+"&status="+latest.status()+(cancelled?"&cancelled=true":""):null);
    }
    public PageResult<Audit> history(UUID actor,UUID collaboration,PageRequest page){
        var a=context.actor(actor);authorize(a,execution(collaboration,false),false);var c=work.execute(()->project(execution(collaboration,true)));return store.audit(c.id,page);
    }
    public void poll(int limit){for(var id:store.due(now(),limit))try{reconcile(id,true);}catch(RuntimeException failure){
        work.execute(()->{var c=locked(id);c.nextCheckAt=now().plusSeconds(60);store.save(c);return null;});}}
    public boolean acceptReference(UUID id,String kind,String reference){
        try{
            var c=store.byId(id).orElseThrow();
            if("ORDER".equals(kind)){
                var remote=provider.readOrder(reference);
                work.execute(()->{var current=locked(id);var op=current.operation(UUID.fromString(remote.operationId()));if(op.kind!=Kind.FUNDING)throw new BillingFailure(Code.OWNERSHIP_CONFLICT);
                    applyOrder(current,op,remote);current.nextCheckAt=now();store.save(current);return null;});
            }else if("BATCH".equals(kind)||"PAYOUT_ITEM".equals(kind)){
                var remote="BATCH".equals(kind)?provider.readPayout(reference):provider.readPayoutItem(reference);
                work.execute(()->{var current=locked(id);var op=current.operation(UUID.fromString(remote.senderBatchId()));if(op.kind!=Kind.PAYOUT)throw new BillingFailure(Code.OWNERSHIP_CONFLICT);
                    applyPayout(current,op,remote);current.nextCheckAt=now();store.save(current);return null;});
            }else return reconcile(id,false);
            return true;
        }catch(BillingFailure failure){return false;}
    }
    public Optional<UUID> reference(UUID collaboration){return store.byCollaboration(collaboration,false).map(c->c.id);}
}
