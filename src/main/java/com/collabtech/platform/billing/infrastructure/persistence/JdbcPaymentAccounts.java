package com.collabtech.platform.billing.infrastructure.persistence;

import com.collabtech.platform.billing.domain.model.BillingModels.*;
import com.collabtech.platform.billing.domain.repositories.PaymentAccountRepository;
import com.collabtech.platform.billing.domain.exceptions.BillingFailure;
import com.collabtech.platform.billing.domain.exceptions.BillingFailure.Code;
import com.collabtech.platform.billing.application.ports.BillingPorts.Queries;
import com.collabtech.platform.shared.application.pagination.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Repository;
import org.springframework.context.annotation.Profile;

@Repository @Profile("!skeleton")
public class JdbcPaymentAccounts implements PaymentAccountRepository,Queries {
    private final JdbcTemplate jdbc;
    public JdbcPaymentAccounts(JdbcTemplate jdbc){this.jdbc=jdbc;}
    private static UUID id(ResultSet r,String key)throws SQLException{String v=r.getString(key);return v==null?null:UUID.fromString(v);}
    private static Instant time(ResultSet r,String key)throws SQLException{var v=r.getTimestamp(key);return v==null?null:v.toInstant();}
    private static Timestamp ts(Instant v){return v==null?null:Timestamp.from(v);}
    private static final RowMapper<Account> ACCOUNT=(r,n)->new Account(id(r,"payment_account_id"),id(r,"account_id"),r.getString("role"),time(r,"created_at"),r.getLong("version"));
    private static final RowMapper<Authorization> AUTH=(r,n)->{
        var a=new Authorization(id(r,"authorization_id"),id(r,"payment_account_id"),id(r,"request_id"),r.getString("request_hash"),
            Purpose.valueOf(r.getString("purpose")),Client.valueOf(r.getString("client")),r.getString("state_hash"),r.getString("encrypted_state"),
            Status.valueOf(r.getString("status")),time(r,"created_at"),time(r,"expires_at"),time(r,"updated_at"),r.getLong("version"));
        a.encryptedSetup=r.getString("encrypted_setup_id");a.setupHash=r.getString("setup_hash");a.encryptedApproval=r.getString("encrypted_approval_url");
        a.encryptedCustomer=r.getString("encrypted_customer_id");a.completionHash=r.getString("completion_hash");
        a.encryptedAccess=r.getString("encrypted_access");a.accessExpiresAt=time(r,"access_expires_at");a.methodId=id(r,"method_id");a.errorCode=r.getString("error_code");return a;
    };
    private static final RowMapper<Method> METHOD=(r,n)->new Method(id(r,"method_id"),id(r,"payment_account_id"),Purpose.valueOf(r.getString("purpose")),
        MethodStatus.valueOf(r.getString("effective_status")),r.getString("provider"),r.getString("environment"),r.getString("encrypted_reference"),
        r.getString("reference_hash"),r.getString("subject_hash"),r.getString("masked_label"),time(r,"linked_at"),time(r,"updated_at"),r.getLong("version"));
    private static final String METHODS="SELECT m.*,CASE WHEN r.revoked=TRUE THEN 'REVOKED' ELSE m.status END AS effective_status FROM billing_method m JOIN billing_resource r ON r.resource_hash=m.reference_hash ";
    private <T>Optional<T> one(String sql,RowMapper<T> mapper,Object... args){return jdbc.query(sql,mapper,args).stream().findFirst();}
    public Account ensure(UUID account,String role,Instant now){
        jdbc.update("INSERT INTO billing_account(payment_account_id,account_id,role,created_at) VALUES(?,?,?,?) ON DUPLICATE KEY UPDATE account_id=account_id",
            UUID.randomUUID().toString(),account.toString(),role,ts(now));return account(account,true).orElseThrow();
    }
    public Optional<Account> account(UUID account,boolean lock){return one("SELECT * FROM billing_account WHERE account_id=?"+(lock?" FOR UPDATE":""),ACCOUNT,account.toString());}
    public Optional<Account> byId(UUID id,boolean lock){return one("SELECT * FROM billing_account WHERE payment_account_id=?"+(lock?" FOR UPDATE":""),ACCOUNT,id.toString());}
    public Optional<Authorization> authorization(UUID id){return one("SELECT * FROM billing_authorization WHERE authorization_id=?",AUTH,id.toString());}
    public Optional<Authorization> request(UUID account,UUID request){return one("SELECT * FROM billing_authorization WHERE payment_account_id=? AND request_id=?",AUTH,account.toString(),request.toString());}
    public Optional<Authorization> state(String hash){return one("SELECT * FROM billing_authorization WHERE state_hash=?",AUTH,hash);}
    public void create(Authorization a){
        jdbc.update("INSERT INTO billing_authorization(authorization_id,payment_account_id,request_id,request_hash,purpose,client,state_hash,encrypted_state,status,created_at,expires_at,updated_at,version) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",
            a.id.toString(),a.paymentAccountId.toString(),a.requestId.toString(),a.requestHash,a.purpose.name(),a.client.name(),a.stateHash,a.encryptedState,a.status.name(),ts(a.createdAt),ts(a.expiresAt),ts(a.updatedAt),a.version);
    }
    public void save(Authorization a){
        int changed=jdbc.update("UPDATE billing_authorization SET encrypted_state=?,status=?,updated_at=?,encrypted_setup_id=?,setup_hash=?,encrypted_approval_url=?,encrypted_customer_id=?,completion_hash=?,encrypted_access=?,access_expires_at=?,method_id=?,error_code=?,version=version+1 WHERE authorization_id=? AND version=?",
            a.encryptedState,a.status.name(),ts(a.updatedAt),a.encryptedSetup,a.setupHash,a.encryptedApproval,a.encryptedCustomer,a.completionHash,a.encryptedAccess,ts(a.accessExpiresAt),
            a.methodId==null?null:a.methodId.toString(),a.errorCode,a.id.toString(),a.version);
        if(changed!=1)throw new BillingFailure(Code.CONCURRENT_UPDATE);a.version++;
    }
    public Optional<Method> method(UUID id){return one(METHODS+"WHERE m.method_id=?",METHOD,id.toString());}
    public Optional<Method> reference(String hash){return one(METHODS+"WHERE m.reference_hash=?",METHOD,hash);}
    public Optional<Method> subject(Purpose purpose,String hash){return one(METHODS+"WHERE m.purpose=? AND m.subject_hash=?",METHOD,purpose.name(),hash);}
    public void save(Method m){
        try{
        var old=method(m.id());
        if(old.isEmpty())jdbc.update("INSERT INTO billing_method(method_id,payment_account_id,purpose,status,provider,environment,encrypted_reference,reference_hash,subject_hash,masked_label,linked_at,updated_at,version) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",
            m.id().toString(),m.paymentAccountId().toString(),m.purpose().name(),m.status().name(),m.provider(),m.environment(),m.encryptedReference(),m.referenceHash(),m.subjectHash(),m.maskedLabel(),ts(m.linkedAt()),ts(m.updatedAt()),m.version());
        else if(jdbc.update("UPDATE billing_method SET status=?,encrypted_reference=?,reference_hash=?,masked_label=?,updated_at=?,version=? WHERE method_id=? AND version=?",
            m.status().name(),m.encryptedReference(),m.referenceHash(),m.maskedLabel(),ts(m.updatedAt()),m.version(),m.id().toString(),m.version()-1)!=1)throw new BillingFailure(Code.CONCURRENT_UPDATE);
        }catch(org.springframework.dao.DuplicateKeyException conflict){throw new BillingFailure(Code.OWNERSHIP_CONFLICT);}
    }
    public boolean resource(String hash,boolean lock,Instant now){
        jdbc.update("INSERT INTO billing_resource(resource_hash,revoked,updated_at) VALUES(?,FALSE,?) ON DUPLICATE KEY UPDATE resource_hash=resource_hash",hash,ts(now));
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT revoked FROM billing_resource WHERE resource_hash=?"+(lock?" FOR UPDATE":""),Boolean.class,hash));
    }
    public void revokeResource(String hash,Instant now){resource(hash,true,now);jdbc.update("UPDATE billing_resource SET revoked=TRUE,updated_at=? WHERE resource_hash=?",ts(now),hash);}
    public Optional<Event> event(String id){return one("SELECT * FROM billing_webhook_event WHERE event_id=? FOR UPDATE",(r,n)->new Event(r.getString("event_id"),r.getString("event_type"),r.getString("resource_hash"),r.getString("payload_hash"),r.getString("encrypted_payload"),r.getString("status"),time(r,"received_at"),time(r,"processed_at"),r.getInt("attempts")),id);}
    public boolean reserve(Event e){return jdbc.update("INSERT IGNORE INTO billing_webhook_event(event_id,event_type,resource_hash,payload_hash,encrypted_payload,status,received_at,processed_at,attempts) VALUES(?,?,?,?,?,?,?,?,?)",e.id(),e.type(),e.resourceHash(),e.payloadHash(),e.encryptedPayload(),e.status(),ts(e.receivedAt()),ts(e.processedAt()),e.attempts())==1;}
    public void event(Event e){jdbc.update("UPDATE billing_webhook_event SET status=?,processed_at=?,attempts=? WHERE event_id=?",e.status(),ts(e.processedAt()),e.attempts(),e.id());}
    public void audit(UUID account,UUID auth,UUID method,String type,Instant now){
        jdbc.update("INSERT INTO billing_audit(audit_id,payment_account_id,authorization_id,method_id,event_type,occurred_at) VALUES(?,?,?,?,?,?)",
            UUID.randomUUID().toString(),account==null?null:account.toString(),auth==null?null:auth.toString(),method==null?null:method.toString(),type,ts(now));
    }
    public PageResult<Method> methods(UUID account,PageRequest page){
        long total=jdbc.queryForObject("SELECT COUNT(*) FROM billing_method WHERE payment_account_id=?",Long.class,account.toString());
        return new PageResult<>(jdbc.query(METHODS+"WHERE m.payment_account_id=? ORDER BY m.linked_at DESC,m.method_id LIMIT ? OFFSET ?",METHOD,account.toString(),page.size(),(long)page.page()*page.size()),total,page.page(),page.size());
    }
}
