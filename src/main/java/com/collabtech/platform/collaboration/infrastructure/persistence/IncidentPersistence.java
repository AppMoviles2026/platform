package com.collabtech.platform.collaboration.infrastructure.persistence;

import com.collabtech.platform.collaboration.application.ports.IncidentPorts.*;
import com.collabtech.platform.collaboration.domain.model.*;
import com.collabtech.platform.collaboration.domain.repositories.IncidentRepository;
import com.collabtech.platform.collaboration.domain.exceptions.CollaborationFailure;
import com.collabtech.platform.collaboration.domain.exceptions.CollaborationFailure.Code;
import com.collabtech.platform.shared.application.pagination.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

public final class IncidentPersistence implements IncidentRepository, Queries, Extensions {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private static final String SELECT = """
            select i.*,r.resolution_id,r.terms,r.proposed_by,r.proposed_at,r.status as resolution_status,
            r.decided_by,r.decided_at,r.decision_reason from collaboration_incident i
            left join collaboration_resolution r on i.current_resolution_id=r.resolution_id
            """;
    public IncidentPersistence(JdbcTemplate jdbc,ObjectMapper json) { this.jdbc=jdbc; this.json=json; }
    public Optional<Incident> byId(UUID id) {
        return jdbc.query(SELECT+" where i.incident_id=?",this::incident,id.toString()).stream().findFirst();
    }
    public Optional<Incident> byProposal(UUID id) {
        return jdbc.query(SELECT+" where i.incident_id=(select incident_id from collaboration_resolution where resolution_id=?)",
                this::incident,id.toString()).stream().findFirst();
    }
    public Incident save(Incident incident) {
        var rows=jdbc.queryForList("select version from collaboration_incident where incident_id=?",incident.id().toString());
        boolean create=rows.isEmpty();
        if (create) {
            jdbc.update("""
                    insert into collaboration_incident(incident_id,collaboration_id,opened_by,category,description,deliverable_id,
                    evidence,status,opened_at,version) values(?,?,?,?,?,?,?,?,?,0)
                    """,incident.id().toString(),incident.collaborationId().toString(),incident.openedBy().toString(),
                    incident.category().name(),incident.description(),string(incident.deliverableId()),
                    json.writeValueAsString(incident.evidence()),incident.status().name(),time(incident.openedAt()));
            linkAssets(incident.id(),incident.evidence());
        } else if (((Number)rows.getFirst().get("version")).longValue()!=incident.version()) throw new CollaborationFailure(Code.CONCURRENT_UPDATE);
        var p=incident.proposal();
        if(p!=null) {
            var stored=jdbc.queryForList("select status from collaboration_resolution where resolution_id=?",p.id().toString());
            if(stored.isEmpty()) {
                jdbc.update("""
                        insert into collaboration_resolution(resolution_id,incident_id,terms,proposed_by,proposed_at,status,decided_by,decided_at,decision_reason)
                        values(?,?,?,?,?,?,?,?,?)
                        """,p.id().toString(),incident.id().toString(),json.writeValueAsString(p.terms()),p.proposedBy().toString(),
                        time(p.proposedAt()),p.status().name(),string(p.decidedBy()),time(p.decidedAt()),p.decisionReason());
                linkAssets(incident.id(),p.terms().evidence());
            } else if (!stored.getFirst().get("status").toString().equals(p.status().name())) {
                int changed=jdbc.update("""
                        update collaboration_resolution set status=?,decided_by=?,decided_at=?,decision_reason=?
                        where resolution_id=? and status='PENDING'
                        """,p.status().name(),string(p.decidedBy()),time(p.decidedAt()),p.decisionReason(),p.id().toString());
                if(changed!=1)throw new CollaborationFailure(Code.CONCURRENT_UPDATE);
            }
        }
        if(!create) {
            int changed=jdbc.update("""
                    update collaboration_incident set status=?,resolved_at=?,current_resolution_id=?,version=version+1 where incident_id=? and version=?
                    """,incident.status().name(),time(incident.resolvedAt()),p==null?null:p.id().toString(),incident.id().toString(),incident.version());
            if(changed!=1)throw new CollaborationFailure(Code.CONCURRENT_UPDATE);
            incident.restoreVersion(incident.version()+1);
        }
        return incident;
    }
    private void linkAssets(UUID incident, List<DeliveryContent.Reference> references) {
        for(var id:references.stream().map(DeliveryContent.Reference::assetId).filter(Objects::nonNull).distinct().toList()) {
            if(jdbc.queryForObject("select count(*) from collaboration_incident_asset where incident_id=? and asset_id=?",Integer.class,incident.toString(),id.toString())==0) {
                jdbc.update("insert into collaboration_incident_asset(incident_id,asset_id) values(?,?)",incident.toString(),id.toString());
            }
        }
    }
    public int openCount(UUID collaboration) {
        return jdbc.queryForObject("select count(*) from collaboration_incident where collaboration_id=? and status in ('OPEN','RESOLUTION_PROPOSED')",
                Integer.class,collaboration.toString());
    }
    public PageResult<Incident> list(UUID collaboration, Incident.Status status, PageRequest page) {
        String where=" where i.collaboration_id=?"+(status==null?"":" and i.status=?");
        Object[] args=status==null?new Object[]{collaboration.toString()}:new Object[]{collaboration.toString(),status.name()};
        long total=jdbc.queryForObject("select count(*) from collaboration_incident i"+where,Long.class,args);
        var items=jdbc.query(SELECT+where+" order by i.opened_at desc,i.incident_id limit "+page.size()+" offset "+offset(page),this::incident,args);
        return new PageResult<>(items,total,page.page(),page.size());
    }
    public PageResult<Incident.Proposal> proposals(UUID incident, PageRequest page) {
        long total=jdbc.queryForObject("select count(*) from collaboration_resolution where incident_id=?",Long.class,incident.toString());
        var rows=jdbc.query("select r.*,r.status as resolution_status from collaboration_resolution r where incident_id=? order by proposed_at desc,resolution_id limit "
                +page.size()+" offset "+offset(page),(r,i)->proposal(r),incident.toString());
        return new PageResult<>(rows,total,page.page(),page.size());
    }
    public void save(UUID deliverable, Deliverable.CorrectionExtension extension) {
        jdbc.update("""
                insert into collaboration_correction_extension(extension_id,deliverable_id,submission_id,resolution_id,previous_deadline,
                deadline,proposed_by,accepted_by,accepted_at) values(?,?,?,?,?,?,?,?,?)
                """,extension.id().toString(),deliverable.toString(),extension.submissionId().toString(),extension.resolutionId().toString(),
                time(extension.previousDeadline()),time(extension.deadline()),extension.proposedBy().toString(),extension.acceptedBy().toString(),time(extension.acceptedAt()));
    }
    private Incident incident(ResultSet r,int index)throws SQLException {
        return new Incident(uuid(r,"incident_id"),uuid(r,"collaboration_id"),uuid(r,"opened_by"),Incident.Category.valueOf(r.getString("category")),
                r.getString("description"),uuid(r,"deliverable_id"),json.readValue(r.getString("evidence"),
                json.getTypeFactory().constructCollectionType(List.class,DeliveryContent.Reference.class)),instant(r,"opened_at"),
                Incident.Status.valueOf(r.getString("status")),instant(r,"resolved_at"),proposal(r),r.getLong("version"));
    }
    private Incident.Proposal proposal(ResultSet r)throws SQLException {
        if(r.getString("resolution_id")==null)return null;
        return new Incident.Proposal(uuid(r,"resolution_id"),json.readValue(r.getString("terms"),Incident.Terms.class),uuid(r,"proposed_by"),
                instant(r,"proposed_at"),Incident.ProposalStatus.valueOf(r.getString("resolution_status")),uuid(r,"decided_by"),
                instant(r,"decided_at"),r.getString("decision_reason"));
    }
    private static UUID uuid(ResultSet r,String field)throws SQLException{var value=r.getString(field);return value==null?null:UUID.fromString(value);}
    private static Instant instant(ResultSet r,String field)throws SQLException{var value=r.getTimestamp(field);return value==null?null:value.toInstant();}
    private static Timestamp time(Instant value){return value==null?null:Timestamp.from(value);}
    private static String string(UUID value){return value==null?null:value.toString();}
    private static int offset(PageRequest p){long value=(long)p.page()*p.size();if(value>Integer.MAX_VALUE)throw new IllegalArgumentException("Invalid page");return(int)value;}
}
