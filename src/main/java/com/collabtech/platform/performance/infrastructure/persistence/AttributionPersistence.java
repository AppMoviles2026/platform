package com.collabtech.platform.performance.infrastructure.persistence;

import com.collabtech.platform.performance.application.ports.AttributionPorts.Queries;
import com.collabtech.platform.performance.domain.model.AttributionModels.*;
import com.collabtech.platform.performance.domain.model.PerformanceModels.Period;
import com.collabtech.platform.performance.domain.repositories.AttributionRepository;
import com.collabtech.platform.performance.domain.exceptions.PerformanceFailure;
import com.collabtech.platform.performance.domain.exceptions.PerformanceFailure.Code;
import com.collabtech.platform.shared.application.pagination.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;

public final class AttributionPersistence implements AttributionRepository,Queries {
    private final JdbcTemplate jdbc;
    public AttributionPersistence(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public Optional<Mechanism> mechanism(UUID id){return jdbc.query("select * from performance_attribution where attribution_id=?",this::mapMechanism,id.toString()).stream().findFirst();}
    // MySQL text collations may ignore case; the opaque public token must match exactly.
    public Optional<Mechanism> byToken(String token){return jdbc.query("select * from performance_attribution where tracking_token=?",this::mapMechanism,token).stream().filter(m->m.token().equals(token)).findFirst();}
    public void create(Mechanism m){jdbc.update("""
            insert into performance_attribution(attribution_id,report_id,kind,label,destination_id,destination_url,tracking_token,attribution_code,
            status,is_demonstration,created_by,created_at,updated_at,version) values(?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """,m.id().toString(),m.reportId().toString(),m.kind().name(),m.label(),m.destinationId(),m.destination(),m.token(),m.code(),m.status().name(),
            m.demonstration(),m.createdBy().toString(),time(m.createdAt()),time(m.updatedAt()),m.version());}
    public void updateStatus(Mechanism m,long expected){if(jdbc.update("update performance_attribution set status=?,updated_at=?,version=? where attribution_id=? and version=?",
            m.status().name(),time(m.updatedAt()),m.version(),m.id().toString(),expected)!=1)throw new PerformanceFailure(Code.CONCURRENT_UPDATE);}
    public Optional<Interaction> event(UUID report,String key){return jdbc.query("select * from performance_interaction where report_id=? and event_key=?",this::mapInteraction,report.toString(),key).stream().findFirst();}
    public Optional<Interaction> interaction(UUID id){return jdbc.query("select * from performance_interaction where interaction_id=?",this::mapInteraction,id.toString()).stream().findFirst();}
    public void save(Interaction i){jdbc.update("""
            insert into performance_interaction(interaction_id,report_id,attribution_id,interaction_type,source,event_key,payload_fingerprint,
            recorded_by,occurred_at,recorded_at,evidence_id,is_demonstration) values(?,?,?,?,?,?,?,?,?,?,?,?)
            """,i.id().toString(),i.reportId().toString(),i.mechanismId().toString(),i.type().name(),i.source().name(),i.eventKey(),i.fingerprint(),
            string(i.recordedBy()),time(i.occurredAt()),time(i.recordedAt()),string(i.evidenceId()),i.demonstration());}
    public PageResult<Mechanism> mechanisms(UUID report,PageRequest page){
        long count=jdbc.queryForObject("select count(*) from performance_attribution where report_id=?",Long.class,report.toString());
        var rows=jdbc.query("select * from performance_attribution where report_id=? order by created_at,attribution_id limit "+page.size()+" offset "+offset(page),
                this::mapMechanism,report.toString());
        return new PageResult<>(rows,count,page.page(),page.size());
    }
    public Counts counts(UUID report,UUID mechanism,Period period,boolean demo){
        String filter=" where i.report_id=? and i.occurred_at>=? and i.occurred_at<? and i.is_demonstration=?"
                +(mechanism==null?"":" and i.attribution_id=?")
                +" and (i.source='FIRST_PARTY_TRACKING' or exists(select 1 from performance_evidence e where e.evidence_id=i.evidence_id and e.status<>'REJECTED'))";
        var params=new ArrayList<Object>(List.of(report.toString(),time(period.start()),time(period.end()),demo));if(mechanism!=null)params.add(mechanism.toString());
        return jdbc.queryForObject("""
                select coalesce(sum(case when interaction_type='CLICK' then 1 else 0 end),0),
                coalesce(sum(case when interaction_type='INQUIRY' then 1 else 0 end),0),
                coalesce(sum(case when interaction_type='CODE_REDEMPTION' then 1 else 0 end),0)
                from performance_interaction i
                """+filter,(r,n)->new Counts(r.getLong(1),r.getLong(2),r.getLong(3)),params.toArray());
    }
    public PageResult<Interaction> interactions(UUID report,UUID mechanism,Period period,PageRequest page){
        Object[] params={report.toString(),mechanism.toString(),time(period.start()),time(period.end())};
        String where=" where report_id=? and attribution_id=? and occurred_at>=? and occurred_at<?";
        long count=jdbc.queryForObject("select count(*) from performance_interaction"+where,Long.class,params);
        var rows=jdbc.query("select * from performance_interaction"+where+" order by occurred_at desc,interaction_id limit "+page.size()+" offset "+offset(page),
                this::mapInteraction,params);
        return new PageResult<>(rows,count,page.page(),page.size());
    }
    private Mechanism mapMechanism(ResultSet r,int n)throws SQLException{return new Mechanism(uuid(r,"attribution_id"),uuid(r,"report_id"),Kind.valueOf(r.getString("kind")),
            r.getString("label"),r.getString("destination_id"),r.getString("destination_url"),r.getString("tracking_token"),r.getString("attribution_code"),
            Status.valueOf(r.getString("status")),r.getBoolean("is_demonstration"),uuid(r,"created_by"),instant(r,"created_at"),instant(r,"updated_at"),r.getLong("version"));}
    private Interaction mapInteraction(ResultSet r,int n)throws SQLException{return new Interaction(uuid(r,"interaction_id"),uuid(r,"report_id"),uuid(r,"attribution_id"),
            InteractionType.valueOf(r.getString("interaction_type")),Source.valueOf(r.getString("source")),r.getString("event_key"),r.getString("payload_fingerprint"),
            uuid(r,"recorded_by"),instant(r,"occurred_at"),instant(r,"recorded_at"),uuid(r,"evidence_id"),r.getBoolean("is_demonstration"));}
    private static UUID uuid(ResultSet r,String key)throws SQLException{var value=r.getString(key);return value==null?null:UUID.fromString(value);}
    private static Instant instant(ResultSet r,String key)throws SQLException{return r.getTimestamp(key).toInstant();}
    private static Timestamp time(Instant i){return Timestamp.from(i);}
    private static String string(UUID id){return id==null?null:id.toString();}
    private static int offset(PageRequest page){long offset=(long)page.page()*page.size();if(offset>Integer.MAX_VALUE)throw new IllegalArgumentException("Invalid page");return(int)offset;}
}
