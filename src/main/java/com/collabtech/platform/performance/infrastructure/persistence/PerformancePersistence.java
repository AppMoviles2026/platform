package com.collabtech.platform.performance.infrastructure.persistence;

import com.collabtech.platform.performance.application.ports.PerformancePorts.*;
import com.collabtech.platform.performance.domain.model.*;
import com.collabtech.platform.performance.domain.model.PerformanceModels.*;
import com.collabtech.platform.performance.domain.repositories.PerformanceRepository;
import com.collabtech.platform.performance.domain.exceptions.PerformanceFailure;
import com.collabtech.platform.performance.domain.exceptions.PerformanceFailure.Code;
import com.collabtech.platform.shared.application.pagination.*;
import com.collabtech.platform.shared.infrastructure.files.EvidenceFileInspection;
import java.sql.*;
import java.time.Instant;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

public final class PerformancePersistence implements PerformanceRepository,Queries,Commands,Files {
    private final JdbcTemplate jdbc;private final ObjectMapper json;private final EvidenceFileInspection inspection;
    public PerformancePersistence(JdbcTemplate jdbc,ObjectMapper json,long maxBytes){this.jdbc=jdbc;this.json=json;inspection=new EvidenceFileInspection(maxBytes);}
    public Optional<PerformanceReport> report(UUID id,boolean lock){return report("collaboration_id",id,lock);}
    public Optional<PerformanceReport> reportById(UUID id,boolean lock){return report("report_id",id,lock);}
    private Optional<PerformanceReport> report(String key,UUID id,boolean lock) {
        return jdbc.query("select * from performance_report where "+key+"=?"+(lock?" for update":""),
                (r,i)->new PerformanceReport(uuid(r,"report_id"),uuid(r,"collaboration_id"),uuid(r,"brand_id"),uuid(r,"creator_id"),
                        instant(r,"created_at"),instant(r,"updated_at"),r.getLong("version")),id.toString()).stream().findFirst();
    }
    public void create(PerformanceReport r){jdbc.update("insert into performance_report(report_id,collaboration_id,brand_id,creator_id,created_at,updated_at,version) values(?,?,?,?,?,?,0)",
            r.id().toString(),r.collaborationId().toString(),r.brandId().toString(),r.creatorId().toString(),time(r.createdAt()),time(r.updatedAt()));}
    public void touch(UUID id,Instant now){jdbc.update("update performance_report set updated_at=?,version=version+1 where report_id=?",time(now),id.toString());}
    public Optional<Publication> publication(UUID report,UUID social,String post) {
        return jdbc.query("select * from performance_publication where report_id=? and social_id=? and post_id=?",this::mapPublication,
                report.toString(),social.toString(),post).stream().findFirst();
    }
    public void save(Publication p,Binding b) {
        if(jdbc.queryForObject("select count(*) from performance_publication where publication_id=?",Integer.class,p.id().toString())==0)
            jdbc.update("insert into performance_publication(publication_id,report_id,social_id,platform,post_id,created_at) values(?,?,?,?,?,?)",
                    p.id().toString(),p.reportId().toString(),p.socialId().toString(),p.platform(),p.postId(),time(p.createdAt()));
        if(jdbc.queryForObject("select count(*) from performance_binding where publication_id=? and submission_id=?",Integer.class,p.id().toString(),b.submissionId().toString())==0)
            jdbc.update("insert into performance_binding(publication_id,submission_id,deliverable_id,publication_url,bound_at) values(?,?,?,?,?)",
                    p.id().toString(),b.submissionId().toString(),b.deliverableId().toString(),b.publicationUrl(),time(b.boundAt()));
    }
    public Optional<Publication> publicationById(UUID id){return jdbc.query("select * from performance_publication where publication_id=?",this::mapPublication,id.toString()).stream().findFirst();}
    public List<Binding> bindings(UUID id) {
        return jdbc.query("select * from performance_binding where publication_id=? order by bound_at,submission_id",
                (r,i)->new Binding(uuid(r,"publication_id"),uuid(r,"submission_id"),uuid(r,"deliverable_id"),r.getString("publication_url"),instant(r,"bound_at")),id.toString());
    }
    public void save(Snapshot s){jdbc.update("insert into performance_snapshot(snapshot_id,publication_id,submission_id,captured_at,period_start,period_end,metrics,recorded_sequence) values(?,?,?,?,?,?,?,?)",
            s.id().toString(),s.publicationId().toString(),s.submissionId().toString(),time(s.capturedAt()),time(s.period().start()),time(s.period().end()),json.writeValueAsString(s.metrics()),s.recordedSequence());}
    public void save(Refresh r){jdbc.update("insert into performance_refresh(refresh_id,publication_id,requested_by,checked_at,status,snapshot_id,recorded_sequence) values(?,?,?,?,?,?,?)",
            r.id().toString(),r.publicationId().toString(),r.requestedBy().toString(),time(r.checkedAt()),r.status().name(),string(r.snapshotId()),r.recordedSequence());}
    public Optional<Refresh> refresh(UUID id){return jdbc.query("select * from performance_refresh where refresh_id=?",this::mapRefresh,id.toString()).stream().findFirst();}
    public Optional<Snapshot> snapshot(UUID id){return jdbc.query("select * from performance_snapshot where snapshot_id=?",this::mapSnapshot,id.toString()).stream().findFirst();}
    public Optional<Refresh> latestRefresh(UUID id){return jdbc.query("select * from performance_refresh where publication_id=? order by recorded_sequence desc limit 1",this::mapRefresh,id.toString()).stream().findFirst();}
    public Optional<Snapshot> latestSnapshot(UUID id){return jdbc.query("select * from performance_snapshot where publication_id=? order by recorded_sequence desc limit 1",this::mapSnapshot,id.toString()).stream().findFirst();}
    public PageResult<Publication> publications(UUID report,PageRequest page) {
        long count=jdbc.queryForObject("select count(*) from performance_publication where report_id=?",Long.class,report.toString());
        var rows=jdbc.query("select * from performance_publication where report_id=? order by created_at,publication_id limit "+page.size()+" offset "+offset(page),this::mapPublication,report.toString());
        return new PageResult<>(rows,count,page.page(),page.size());
    }
    public PageResult<Snapshot> snapshots(UUID report,PageRequest page) {
        long count=jdbc.queryForObject("select count(*) from performance_snapshot s join performance_publication p on s.publication_id=p.publication_id where p.report_id=?",Long.class,report.toString());
        var rows=jdbc.query("select s.* from performance_snapshot s join performance_publication p on s.publication_id=p.publication_id where p.report_id=? order by s.recorded_sequence desc,s.snapshot_id limit "+page.size()+" offset "+offset(page),this::mapSnapshot,report.toString());
        return new PageResult<>(rows,count,page.page(),page.size());
    }
    public Optional<PerformanceEvidence> evidence(UUID id){return jdbc.query("select * from performance_evidence where evidence_id=?",this::mapEvidence,id.toString()).stream().findFirst();}
    public void save(PerformanceEvidence e) {
        var rows=jdbc.queryForList("select version from performance_evidence where evidence_id=?",e.id().toString());
        if(rows.isEmpty()) {
            jdbc.update("""
                    insert into performance_evidence(evidence_id,report_id,submitted_by,submission_id,description,references_json,claimed_metrics,
                    observed_at,period_start,period_end,submitted_at,status,is_demonstration,version) values(?,?,?,?,?,?,?,?,?,?,?,?,?,0)
                    """,e.id().toString(),e.reportId().toString(),e.submittedBy().toString(),string(e.submissionId()),e.description(),
                    json.writeValueAsString(e.references()),json.writeValueAsString(e.claimedMetrics()),time(e.observedAt()),time(e.period().start()),
                    time(e.period().end()),time(e.submittedAt()),e.status().name(),e.demonstration());
            for(var id:e.references().stream().map(Reference::assetId).filter(Objects::nonNull).distinct().toList())
                jdbc.update("insert into performance_evidence_asset(evidence_id,asset_id) values(?,?)",e.id().toString(),id.toString());
        }else {
            int changed=jdbc.update("update performance_evidence set status=?,reviewed_by=?,reviewed_at=?,observations=?,version=version+1 where evidence_id=? and version=?",
                    e.status().name(),string(e.reviewedBy()),time(e.reviewedAt()),e.observations(),e.id().toString(),e.version());
            if(changed!=1)throw new PerformanceFailure(Code.CONCURRENT_UPDATE);e.restoreVersion(e.version()+1);
        }
    }
    public PageResult<PerformanceEvidence> evidence(UUID report,PerformanceEvidence.Status status,PageRequest page) {
        String where=" where report_id=?"+(status==null?"":" and status=?");
        Object[] params=status==null?new Object[]{report.toString()}:new Object[]{report.toString(),status.name()};
        long count=jdbc.queryForObject("select count(*) from performance_evidence"+where,Long.class,params);
        var rows=jdbc.query("select * from performance_evidence"+where+" order by submitted_at desc,evidence_id limit "+page.size()+" offset "+offset(page),this::mapEvidence,params);
        return new PageResult<>(rows,count,page.page(),page.size());
    }
    public long availablePublications(UUID report,Set<UUID> current) {
        if(current.isEmpty())return 0;
        var ids=new ArrayList<>(current);var found=new HashSet<String>();
        for(int start=0;start<ids.size();start+=500){
            var part=ids.subList(start,Math.min(start+500,ids.size()));var params=new ArrayList<Object>();params.add(report.toString());
            part.forEach(id->params.add(id.toString()));
            found.addAll(jdbc.query("select distinct p.publication_id from performance_publication p join performance_binding b on b.publication_id=p.publication_id "
                    +"where p.report_id=? and exists(select 1 from performance_snapshot s where s.publication_id=p.publication_id) and b.submission_id in ("
                    +String.join(",",Collections.nCopies(part.size(),"?"))+")",(r,i)->r.getString(1),params.toArray()));
        }
        return found.size();
    }
    public long evidenceCount(UUID report){return jdbc.queryForObject("select count(*) from performance_evidence where report_id=?",Long.class,report.toString());}
    public Optional<Command> command(UUID report,UUID actor,UUID request) {
        return jdbc.query("select operation,fingerprint,result_id from performance_command where report_id=? and actor_id=? and request_id=?",
                (r,i)->new Command(r.getString(1),r.getString(2),UUID.fromString(r.getString(3))),report.toString(),actor.toString(),request.toString()).stream().findFirst();
    }
    public void command(UUID report,UUID actor,UUID request,Command c,Instant now){jdbc.update("insert into performance_command(report_id,actor_id,request_id,operation,fingerprint,result_id,created_at) values(?,?,?,?,?,?,?)",
            report.toString(),actor.toString(),request.toString(),c.operation(),c.fingerprint(),c.resultId().toString(),time(now));}
    public String fingerprint(Object input) {
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsString(input).getBytes(StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    public FileInfo inspect(String name,String media,byte[] bytes) {
        try {var f=inspection.inspect(name,media,bytes);return new FileInfo(f.filename(),f.mediaType(),f.byteSize(),f.sha256());}
        catch(EvidenceFileInspection.Failure e){throw new PerformanceFailure(e.reason()==EvidenceFileInspection.Reason.TOO_LARGE?Code.FILE_TOO_LARGE:Code.UNSUPPORTED_FILE);}
    }
    public void save(Asset a,byte[] bytes){jdbc.update("insert into performance_asset(asset_id,report_id,owner_account_id,filename,media_type,byte_size,sha256,created_at,bytes) values(?,?,?,?,?,?,?,?,?)",
            a.id().toString(),a.reportId().toString(),a.owner().toString(),a.filename(),a.mediaType(),a.byteSize(),a.sha256(),time(a.createdAt()),bytes);}
    public Optional<Asset> asset(UUID id) {
        return jdbc.query("select asset_id,report_id,owner_account_id,filename,media_type,byte_size,sha256,created_at from performance_asset where asset_id=?",
                (r,i)->new Asset(uuid(r,"asset_id"),uuid(r,"report_id"),uuid(r,"owner_account_id"),r.getString("filename"),r.getString("media_type"),r.getLong("byte_size"),r.getString("sha256"),instant(r,"created_at")),id.toString()).stream().findFirst();
    }
    public byte[] bytes(UUID id){return jdbc.queryForObject("select bytes from performance_asset where asset_id=?",byte[].class,id.toString());}
    public boolean shared(UUID id){return jdbc.queryForObject("select count(*) from performance_evidence_asset where asset_id=?",Integer.class,id.toString())>0;}
    private Publication mapPublication(ResultSet r,int i)throws SQLException{return new Publication(uuid(r,"publication_id"),uuid(r,"report_id"),uuid(r,"social_id"),r.getString("platform"),r.getString("post_id"),instant(r,"created_at"));}
    private Snapshot mapSnapshot(ResultSet r,int i)throws SQLException{return new Snapshot(uuid(r,"snapshot_id"),uuid(r,"publication_id"),uuid(r,"submission_id"),instant(r,"captured_at"),
            new Period(instant(r,"period_start"),instant(r,"period_end")),metrics(r.getString("metrics")),r.getLong("recorded_sequence"));}
    private Refresh mapRefresh(ResultSet r,int i)throws SQLException{return new Refresh(uuid(r,"refresh_id"),uuid(r,"publication_id"),uuid(r,"requested_by"),instant(r,"checked_at"),FetchStatus.valueOf(r.getString("status")),uuid(r,"snapshot_id"),r.getLong("recorded_sequence"));}
    private PerformanceEvidence mapEvidence(ResultSet r,int i)throws SQLException {
        return new PerformanceEvidence(uuid(r,"evidence_id"),uuid(r,"report_id"),uuid(r,"submitted_by"),uuid(r,"submission_id"),r.getString("description"),
                json.readValue(r.getString("references_json"),json.getTypeFactory().constructCollectionType(List.class,Reference.class)),
                metrics(r.getString("claimed_metrics")),instant(r,"observed_at"),new Period(instant(r,"period_start"),instant(r,"period_end")),
                instant(r,"submitted_at"),r.getBoolean("is_demonstration"),PerformanceEvidence.Status.valueOf(r.getString("status")),r.getLong("version"),
                uuid(r,"reviewed_by"),instant(r,"reviewed_at"),r.getString("observations"));
    }
    private Map<MetricType,Long> metrics(String payload){return json.readValue(payload,json.getTypeFactory().constructMapType(EnumMap.class,MetricType.class,Long.class));}
    private static UUID uuid(ResultSet r,String key)throws SQLException{var s=r.getString(key);return s==null?null:UUID.fromString(s);}
    private static Instant instant(ResultSet r,String key)throws SQLException{var t=r.getTimestamp(key);return t==null?null:t.toInstant();}
    private static Timestamp time(Instant value){return value==null?null:Timestamp.from(value);}
    private static String string(UUID value){return value==null?null:value.toString();}
    private static int offset(PageRequest p){long n=(long)p.page()*p.size();if(n>Integer.MAX_VALUE)throw new IllegalArgumentException("Invalid page");return(int)n;}
}
