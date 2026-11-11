package com.collabtech.platform.billing.infrastructure.persistence;
import com.collabtech.platform.billing.application.ports.CompensationPorts.Store;
import com.collabtech.platform.billing.domain.model.CompensationModels.*;
import com.collabtech.platform.billing.domain.exceptions.BillingFailure;
import com.collabtech.platform.billing.domain.exceptions.BillingFailure.Code;
import com.collabtech.platform.shared.application.pagination.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.jdbc.core.*;
import tools.jackson.databind.ObjectMapper;
@Repository @Profile("!skeleton")
public class JdbcCompensations implements Store {
    private final JdbcTemplate jdbc;private final ObjectMapper json;
    public JdbcCompensations(JdbcTemplate jdbc,ObjectMapper json){this.jdbc=jdbc;this.json=json;}
    private static Timestamp ts(Instant v){return v==null?null:Timestamp.from(v);}
    private static Instant time(ResultSet r,String field)throws SQLException{var t=r.getTimestamp(field);return t==null?null:t.toInstant();}
    private static UUID uuid(String v){return v==null?null:UUID.fromString(v);}
    private <T> Optional<T> one(String sql,RowMapper<T> mapper,Object...args){return jdbc.query(sql,mapper,args).stream().findFirst();}
    public Optional<Compensation> byCollaboration(UUID id,boolean lock){return one("SELECT state_json FROM billing_compensation WHERE collaboration_id=?"+(lock?" FOR UPDATE":""),(r,n)->json.readValue(r.getString(1),Compensation.class),id.toString());}
    public Optional<Compensation> byId(UUID id){return one("SELECT state_json FROM billing_compensation WHERE compensation_id=?",(r,n)->json.readValue(r.getString(1),Compensation.class),id.toString());}
    public void create(Compensation c){jdbc.update("INSERT INTO billing_compensation(compensation_id,collaboration_id,brand_profile_id,creator_profile_id,terms_hash,next_check_at,version,state_json) VALUES(?,?,?,?,?,?,?,?)",
        c.id.toString(),c.collaborationId.toString(),c.brandProfileId.toString(),c.creatorProfileId.toString(),c.termsHash,ts(c.nextCheckAt),c.version,json.writeValueAsString(c));}
    public void save(Compensation c){long previous=c.version;c.version++;if(jdbc.update("UPDATE billing_compensation SET next_check_at=?,version=?,state_json=? WHERE compensation_id=? AND version=?",
        ts(c.nextCheckAt),c.version,json.writeValueAsString(c),c.id.toString(),previous)!=1)throw new BillingFailure(Code.CONCURRENT_UPDATE);}
    public Optional<Command> command(UUID c,UUID r){return one("SELECT * FROM billing_compensation_request WHERE compensation_id=? AND request_id=?",(rs,n)->new Command(c,r,rs.getString("action"),rs.getString("request_hash"),uuid(rs.getString("operation_id")),time(rs,"created_at")),c.toString(),r.toString());}
    public void command(Command c){jdbc.update("INSERT INTO billing_compensation_request(compensation_id,request_id,action,request_hash,operation_id,created_at) VALUES(?,?,?,?,?,?)",c.compensationId().toString(),c.requestId().toString(),c.action(),c.hash(),c.operationId().toString(),ts(c.createdAt()));}
    private final RowMapper<Reference> refs=(r,n)->new Reference(r.getString("reference_hash"),uuid(r.getString("compensation_id")),uuid(r.getString("operation_id")),r.getString("kind"),r.getString("encrypted_reference"));
    public Optional<Reference> reference(String hash){return one("SELECT * FROM billing_compensation_reference WHERE reference_hash=?",refs,hash);}
    public void reference(Reference r){
        var old=reference(r.hash());if(old.isPresent()){
            if(!old.get().compensationId().equals(r.compensationId())||!old.get().operationId().equals(r.operationId()))throw new BillingFailure(Code.OWNERSHIP_CONFLICT);return;
        }
        jdbc.update("INSERT INTO billing_compensation_reference(reference_hash,compensation_id,operation_id,kind,encrypted_reference) VALUES(?,?,?,?,?)",r.hash(),r.compensationId().toString(),r.operationId().toString(),r.kind(),r.encryptedReference());
    }
    public Optional<Compensation> state(String hash){return reference(hash).filter(r->"STATE".equals(r.kind())).flatMap(r->byId(r.compensationId()));}
    public List<UUID> due(Instant now,int limit){return jdbc.query("SELECT compensation_id FROM billing_compensation WHERE next_check_at<=? ORDER BY next_check_at,compensation_id LIMIT ?",(r,n)->uuid(r.getString(1)),ts(now),limit);}
    public void audit(UUID c,UUID op,UUID actor,String type,Instant now){jdbc.update("INSERT INTO billing_compensation_audit(audit_id,compensation_id,operation_id,actor_account_id,event_type,occurred_at) VALUES(?,?,?,?,?,?)",UUID.randomUUID().toString(),c.toString(),op==null?null:op.toString(),actor==null?null:actor.toString(),type,ts(now));}
    public PageResult<Audit> audit(UUID c,PageRequest p){return new PageResult<>(jdbc.query("SELECT * FROM billing_compensation_audit WHERE compensation_id=? ORDER BY occurred_at,audit_id LIMIT ? OFFSET ?",
        (r,n)->new Audit(uuid(r.getString("audit_id")),uuid(r.getString("operation_id")),uuid(r.getString("actor_account_id")),r.getString("event_type"),time(r,"occurred_at")),c.toString(),p.size(),(long)p.page()*p.size()),
        jdbc.queryForObject("SELECT COUNT(*) FROM billing_compensation_audit WHERE compensation_id=?",Long.class,c.toString()),p.page(),p.size());}
    private final RowMapper<EventWork> events=(r,n)->new EventWork(r.getString("event_id"),uuid(r.getString("compensation_hint")),r.getString("reference_hash"),r.getString("encrypted_hint"),r.getString("status"),r.getInt("attempts"),time(r,"next_attempt_at"),uuid(r.getString("lease_token")),r.getString("error_code"));
    public void event(EventWork e){jdbc.update("INSERT INTO billing_compensation_event(event_id,compensation_hint,reference_hash,encrypted_hint,status,attempts,next_attempt_at,lease_token,error_code) VALUES(?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE status=?,attempts=?,next_attempt_at=?,lease_token=?,error_code=?",
        e.eventId(),e.hint()==null?null:e.hint().toString(),e.referenceHash(),e.encryptedHint(),e.status(),e.attempts(),ts(e.nextAttemptAt()),e.lease()==null?null:e.lease().toString(),e.errorCode(),
        e.status(),e.attempts(),ts(e.nextAttemptAt()),e.lease()==null?null:e.lease().toString(),e.errorCode());}
    public Optional<EventWork> event(String id,boolean lock){return one("SELECT * FROM billing_compensation_event WHERE event_id=?"+(lock?" FOR UPDATE":""),events,id);}
    public List<EventWork> dueEvents(Instant now,int limit){return jdbc.query("SELECT * FROM billing_compensation_event WHERE status<>'DONE' AND next_attempt_at<=? ORDER BY next_attempt_at,event_id LIMIT ?",events,ts(now),limit);}
    public void processed(String id,Instant now){jdbc.update("UPDATE billing_webhook_event SET status='PROCESSED',processed_at=?,attempts=attempts+1 WHERE event_id=?",ts(now),id);}
}
