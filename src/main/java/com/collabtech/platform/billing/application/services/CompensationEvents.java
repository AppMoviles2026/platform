package com.collabtech.platform.billing.application.services;
import com.collabtech.platform.billing.application.ports.CompensationPorts.*;
import com.collabtech.platform.billing.application.ports.BillingPorts.*;
import com.collabtech.platform.billing.domain.model.CompensationModels.*;
import java.util.*;
import java.time.*;
public final class CompensationEvents {
    private final Store store;private final CompensationService service;private final Secrets secrets;private final UnitOfWork work;private final Clock clock;
    public CompensationEvents(Store store,CompensationService service,Secrets secrets,UnitOfWork work,Clock clock){this.store=store;this.service=service;this.secrets=secrets;this.work=work;this.clock=clock;}
    private static String text(Object value){return value instanceof String v&&!v.isBlank()&&v.length()<=128?v:null;}
    private static UUID uuid(Object value){try{return UUID.fromString(String.valueOf(value));}catch(Exception ignored){return null;}}
    private static Map<?,?> map(Object value){return value instanceof Map<?,?> m?m:Map.of();}
    public String enqueue(String id,Map<String,Object> payload){
        String type=text(payload.get("event_type"));if(type==null||!(payload.get("resource") instanceof Map<?,?> resource))return "DEFERRED";
        String kind=null,reference=null,operation=null;UUID hint=uuid(resource.get("custom_id"));
        if(type.startsWith("CHECKOUT.ORDER.")){
            kind="ORDER";reference=text(resource.get("id"));
            if(resource.get("purchase_units") instanceof List<?> list&&!list.isEmpty()){var unit=map(list.get(0));hint=uuid(unit.get("custom_id"));operation=text(unit.get("reference_id"));}
        }else if(type.startsWith("PAYMENT.CAPTURE.")){
            String order=text(map(map(resource.get("supplementary_data")).get("related_ids")).get("order_id"));
            if(order!=null){kind="ORDER";reference=order;}else{kind="CAPTURE";reference=text(resource.get("id"));}
        }else if(type.startsWith("PAYMENT.PAYOUTS-BATCH.")){kind="BATCH";reference=text(resource.get("payout_batch_id"));if(reference==null)reference=text(resource.get("id"));
            operation=text(map(resource.get("sender_batch_header")).get("sender_batch_id"));
        }else if(type.startsWith("PAYMENT.PAYOUTS-ITEM.")){kind="PAYOUT_ITEM";reference=text(resource.get("payout_item_id"));if(reference==null)reference=text(resource.get("id"));
            operation=text(map(resource.get("payout_item")).get("sender_item_id"));
        }else return "DEFERRED";
        if(reference==null)return "DEFERRED";
        if(store.event(id,false).isPresent())return "QUEUED";
        String hash=secrets.digest(kind+":"+reference);
        var known=store.reference(hash);if(known.isEmpty()&&operation!=null)known=store.reference(secrets.digest("OPERATION:"+operation));
        if(known.isPresent())hint=known.get().compensationId();
        if(hint!=null&&store.byId(hint).isEmpty())hint=null;
        if(known.isEmpty()&&hint==null)return "DEFERRED";
        store.event(new EventWork(id,hint,hash,secrets.encrypt(kind+":"+reference),"PENDING",0,clock.instant(),null,null));
        return "QUEUED";
    }
    public void process(int limit){
        for(var candidate:store.dueEvents(clock.instant(),limit)){
            var job=work.execute(()->{var current=store.event(candidate.eventId(),true).orElseThrow();
                if(current.status().equals("DONE")||current.nextAttemptAt().isAfter(clock.instant()))return null;
                var claimed=new EventWork(current.eventId(),current.hint(),current.referenceHash(),current.encryptedHint(),"WORKING",current.attempts()+1,clock.instant().plusSeconds(120),UUID.randomUUID(),null);
                store.event(claimed);return claimed;});
            if(job==null)continue;
            boolean result=false;String error=null;
            try{
                UUID root=job.hint();if(root==null){var reference=store.reference(job.referenceHash());if(reference.isPresent())root=reference.get().compensationId();}
                if(root!=null){String value=secrets.decrypt(job.encryptedHint());int colon=value.indexOf(':');
                    result=service.acceptReference(root,value.substring(0,colon),value.substring(colon+1));
                }else error="COMPENSATION_NOT_MATCHED";
            }catch(RuntimeException failure){error="RECONCILIATION_FAILED";}
            boolean done=result;String code=error;
            work.execute(()->{var current=store.event(job.eventId(),true).orElseThrow();if(!Objects.equals(current.lease(),job.lease()))return null;
                store.event(new EventWork(job.eventId(),job.hint(),job.referenceHash(),job.encryptedHint(),done?"DONE":"PENDING",job.attempts(),clock.instant().plusSeconds(Math.min(300,15L*job.attempts())),null,code));
                if(done)store.processed(job.eventId(),clock.instant());return null;});
        }
    }
}
