package com.collabtech.platform.billing.infrastructure.events;
import com.collabtech.platform.billing.application.services.*;
import com.collabtech.platform.billing.application.ports.BillingPorts.*;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.core.type.TypeReference;
@Component @Profile("!skeleton") @ConditionalOnProperty(name="billing.compensation.scheduling-enabled",havingValue="true",matchIfMissing=true)
public class CompensationReconciliation {
    private final CompensationService service;private final CompensationEvents events;private final Secrets secrets;private final UnitOfWork work;private final JdbcTemplate jdbc;private final ObjectMapper json;private final int limit;
    public CompensationReconciliation(CompensationService service,CompensationEvents events,Secrets secrets,UnitOfWork work,JdbcTemplate jdbc,ObjectMapper json,@Value("${billing.compensation.batch-size:50}")int limit){
        if(limit<1||limit>1000)throw new IllegalArgumentException("Invalid compensation worker batch size");this.service=service;this.events=events;this.secrets=secrets;this.work=work;this.jdbc=jdbc;this.json=json;this.limit=limit;}
    @Scheduled(scheduler="billingCompensationScheduler",fixedDelayString="${billing.compensation.poll-delay-ms:30000}",initialDelay=30000)
    public void reconcile(){
        var deferred=jdbc.query("SELECT event_id,encrypted_payload FROM billing_webhook_event WHERE status='DEFERRED' AND (event_type LIKE 'CHECKOUT.ORDER.%' OR event_type LIKE 'PAYMENT.CAPTURE.%' OR event_type LIKE 'PAYMENT.PAYOUTS-%') AND NOT EXISTS(SELECT 1 FROM billing_compensation_event c WHERE c.event_id=billing_webhook_event.event_id) ORDER BY received_at LIMIT ?",
            (r,n)->Map.entry(r.getString(1),r.getString(2)),limit);
        for(var event:deferred)try{Map<String,Object> body=json.readValue(secrets.decrypt(event.getValue()),new TypeReference<Map<String,Object>>(){});work.execute(()->events.enqueue(event.getKey(),body));}
            catch(RuntimeException ignored){/* Keep durable inbox entries for retry. */}
        events.process(limit);service.poll(limit);
    }
}
