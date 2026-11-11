package com.collabtech.platform.billing.infrastructure.events;
import com.collabtech.platform.billing.application.services.*;
import com.collabtech.platform.billing.application.ports.SubscriptionPorts.Store;
import com.collabtech.platform.billing.application.ports.BillingPorts.*;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.core.type.TypeReference;
@Component @Profile("!skeleton") @ConditionalOnProperty(name="billing.subscriptions.scheduling-enabled",havingValue="true",matchIfMissing=true)
public class SubscriptionReconciliation {
    private final SubscriptionService service;private final SubscriptionEvents events;private final Store store;private final Secrets secrets;private final UnitOfWork work;private final ObjectMapper json;
    public SubscriptionReconciliation(SubscriptionService service,SubscriptionEvents events,Store store,Secrets secrets,UnitOfWork work,ObjectMapper json){
        this.service=service;this.events=events;this.store=store;this.secrets=secrets;this.work=work;this.json=json;}
    @Scheduled(scheduler="billingSubscriptionScheduler",fixedDelayString="${billing.subscriptions.poll-delay-ms:30000}",initialDelay=30000)
    public void reconcile(){
        for(var old:store.deferredEvents(50))try{
            Map<String,Object> payload=json.readValue(secrets.decrypt(old.encryptedPayload()),new TypeReference<Map<String,Object>>(){});
            work.execute(()->events.enqueue(old.id(),payload));
        }catch(RuntimeException ignored){/* Retain the encrypted inbox entry for a later retry. */}
        events.process(50);service.poll(50);
    }
}
