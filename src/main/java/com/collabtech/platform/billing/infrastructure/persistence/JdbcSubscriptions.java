package com.collabtech.platform.billing.infrastructure.persistence;
import com.collabtech.platform.billing.application.ports.SubscriptionPorts.Store;
import com.collabtech.platform.billing.domain.model.SubscriptionModels.*;
import com.collabtech.platform.billing.domain.model.BillingModels.Event;
import com.collabtech.platform.billing.domain.exceptions.BillingFailure;
import com.collabtech.platform.billing.domain.exceptions.BillingFailure.Code;
import com.collabtech.platform.shared.application.pagination.*;
import java.time.Instant;
import java.sql.*;
import java.util.*;
import org.springframework.stereotype.Repository;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.*;
import tools.jackson.databind.ObjectMapper;

@Repository @Profile("!skeleton")
public class JdbcSubscriptions implements Store {
    private final JdbcTemplate jdbc;private final ObjectMapper json;
    public JdbcSubscriptions(JdbcTemplate jdbc,ObjectMapper json){this.jdbc=jdbc;this.json=json;}
    private static Timestamp ts(Instant v){return v==null?null:Timestamp.from(v);}
    private static Instant time(ResultSet r,String f)throws SQLException{var v=r.getTimestamp(f);return v==null?null:v.toInstant();}
    private static UUID uuid(String v){return v==null?null:UUID.fromString(v);}
    private <T>Optional<T> one(String sql,RowMapper<T> mapper,Object...args){return jdbc.query(sql,mapper,args).stream().findFirst();}
    private final RowMapper<EventWork> jobs=(r,n)->new EventWork(r.getString("event_id"),uuid(r.getString("subscription_hint")),r.getString("encrypted_provider"),r.getString("provider_hash"),
        r.getString("payment_hash"),r.getString("event_type"),r.getString("status"),r.getInt("attempts"),time(r,"next_attempt_at"),uuid(r.getString("lease_token")),r.getString("error_code"));
    private Subscription decode(ResultSet r,int n)throws SQLException{return json.readValue(r.getString("state_json"),Subscription.class);}
    public void catalog(Plan p){jdbc.update("INSERT INTO billing_plan(plan_id,code,definition_hash,available,demonstration,definition_json) VALUES(?,?,?,TRUE,TRUE,?) ON DUPLICATE KEY UPDATE available=TRUE",p.id().toString(),p.code(),p.definitionHash(),json.writeValueAsString(p));}
    public void deactivateDemoCatalog(){jdbc.update("UPDATE billing_plan SET available=FALSE WHERE demonstration=TRUE");}
    public List<Plan> availablePlans(){return jdbc.query("SELECT definition_json FROM billing_plan WHERE available=TRUE ORDER BY code,plan_id",(r,n)->json.readValue(r.getString(1),Plan.class));}
    public Optional<Plan> plan(UUID id){return one("SELECT definition_json FROM billing_plan WHERE plan_id=?",(r,n)->json.readValue(r.getString(1),Plan.class),id.toString());}
    public boolean available(UUID id){return Boolean.TRUE.equals(jdbc.queryForObject("SELECT available FROM billing_plan WHERE plan_id=?",Boolean.class,id.toString()));}
    public void create(Subscription s){jdbc.update("INSERT INTO billing_subscription(subscription_id,payment_account_id,plan_id,method_id,request_id,request_hash,current_slot,provider_hash,state_hash,next_check_at,status,created_at,version,state_json) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
        s.id.toString(),s.paymentAccountId.toString(),s.planId.toString(),s.methodId.toString(),s.requestId.toString(),s.requestHash,s.current?"CURRENT":null,s.providerHash,s.stateHash,ts(s.nextCheckAt),s.status.name(),ts(s.createdAt),s.version,json.writeValueAsString(s));}
    public void save(Subscription s){long old=s.version;s.version++;
        if(jdbc.update("UPDATE billing_subscription SET current_slot=?,provider_hash=?,next_check_at=?,status=?,version=?,state_json=? WHERE subscription_id=? AND version=?",
            s.current?"CURRENT":null,s.providerHash,ts(s.nextCheckAt),s.status.name(),s.version,json.writeValueAsString(s),s.id.toString(),old)!=1)throw new BillingFailure(Code.CONCURRENT_UPDATE);}
    public Optional<Subscription> byId(UUID id){return one("SELECT state_json FROM billing_subscription WHERE subscription_id=?",this::decode,id.toString());}
    public Optional<Subscription> byState(String hash){return one("SELECT state_json FROM billing_subscription WHERE state_hash=?",this::decode,hash);}
    public Optional<Subscription> byProvider(String hash){return one("SELECT state_json FROM billing_subscription WHERE provider_hash=?",this::decode,hash);}
    public Optional<Subscription> request(UUID account,UUID request){return one("SELECT state_json FROM billing_subscription WHERE payment_account_id=? AND request_id=?",this::decode,account.toString(),request.toString());}
    public Optional<Subscription> current(UUID account){return one("SELECT state_json FROM billing_subscription WHERE payment_account_id=? AND current_slot='CURRENT'",this::decode,account.toString());}
    public Optional<Subscription> latest(UUID account){return one("SELECT state_json FROM billing_subscription WHERE payment_account_id=? ORDER BY created_at DESC,subscription_id DESC LIMIT 1",this::decode,account.toString());}
    public PageResult<Subscription> history(UUID account,PageRequest page){return new PageResult<>(jdbc.query("SELECT state_json FROM billing_subscription WHERE payment_account_id=? ORDER BY created_at DESC,subscription_id DESC LIMIT ? OFFSET ?",
        this::decode,account.toString(),page.size(),(long)page.page()*page.size()),jdbc.queryForObject("SELECT COUNT(*) FROM billing_subscription WHERE payment_account_id=?",Long.class,account.toString()),page.page(),page.size());}
    public Optional<Payment> payment(String hash){return one("SELECT * FROM billing_subscription_payment WHERE payment_hash=?",(r,n)->new Payment(r.getString("payment_hash"),uuid(r.getString("subscription_id")),r.getString("encrypted_reference"),r.getString("status"),r.getBigDecimal("amount"),r.getString("currency"),time(r,"paid_at"),time(r,"updated_at")),hash);}
    public void payment(Payment p){var old=payment(p.hash());if(old.isPresent()&&!old.get().subscriptionId().equals(p.subscriptionId()))throw new BillingFailure(Code.OWNERSHIP_CONFLICT);
        if(old.isPresent()&&old.get().reversed())return;
        jdbc.update("INSERT INTO billing_subscription_payment(payment_hash,subscription_id,encrypted_reference,status,amount,currency,paid_at,updated_at) VALUES(?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE status=?,amount=?,currency=?,paid_at=?,updated_at=?",
            p.hash(),p.subscriptionId().toString(),p.encryptedReference(),p.status(),p.amount(),p.currency(),ts(p.paidAt()),ts(p.updatedAt()),p.status(),p.amount(),p.currency(),ts(p.paidAt()),ts(p.updatedAt()));}
    public void event(EventWork e){jdbc.update("INSERT INTO billing_subscription_event(event_id,subscription_hint,encrypted_provider,provider_hash,payment_hash,event_type,status,attempts,next_attempt_at,lease_token,error_code) VALUES(?,?,?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE status=?,attempts=?,next_attempt_at=?,lease_token=?,error_code=?",
        e.eventId(),e.subscriptionHint()==null?null:e.subscriptionHint().toString(),e.encryptedProvider(),e.providerHash(),e.paymentHash(),e.eventType(),e.status(),e.attempts(),ts(e.nextAttemptAt()),e.leaseToken()==null?null:e.leaseToken().toString(),e.errorCode(),
        e.status(),e.attempts(),ts(e.nextAttemptAt()),e.leaseToken()==null?null:e.leaseToken().toString(),e.errorCode());}
    public Optional<EventWork> event(String id,boolean lock){return one("SELECT * FROM billing_subscription_event WHERE event_id=?"+(lock?" FOR UPDATE":""),jobs,id);}
    public List<UUID> pollable(Instant now,int limit){return jdbc.query("SELECT subscription_id FROM billing_subscription WHERE current_slot IS NOT NULL AND provider_hash IS NOT NULL AND next_check_at<=? ORDER BY next_check_at,subscription_id LIMIT ?",(r,n)->uuid(r.getString(1)),ts(now),limit);}
    public List<EventWork> due(Instant now,int limit){return jdbc.query("SELECT * FROM billing_subscription_event WHERE status<>'DONE' AND next_attempt_at<=? ORDER BY next_attempt_at,event_id LIMIT ?",jobs,ts(now),limit);}
    public void processed(String id,Instant now){jdbc.update("UPDATE billing_webhook_event SET status='PROCESSED',processed_at=?,attempts=attempts+1 WHERE event_id=?",ts(now),id);}
    public List<Event> deferredEvents(int limit){return jdbc.query("SELECT * FROM billing_webhook_event WHERE status='DEFERRED' AND (event_type LIKE 'BILLING.SUBSCRIPTION.%' OR event_type LIKE 'PAYMENT.SALE.%') AND NOT EXISTS(SELECT 1 FROM billing_subscription_event j WHERE j.event_id=billing_webhook_event.event_id) ORDER BY received_at LIMIT ?",
        (r,n)->new Event(r.getString("event_id"),r.getString("event_type"),r.getString("resource_hash"),r.getString("payload_hash"),r.getString("encrypted_payload"),r.getString("status"),time(r,"received_at"),time(r,"processed_at"),r.getInt("attempts")),limit);}
}
