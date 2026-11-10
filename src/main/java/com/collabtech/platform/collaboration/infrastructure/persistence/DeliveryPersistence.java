package com.collabtech.platform.collaboration.infrastructure.persistence;

import com.collabtech.platform.collaboration.application.ports.DeliveryPorts.*;
import com.collabtech.platform.collaboration.domain.model.*;
import com.collabtech.platform.collaboration.domain.repositories.DeliverableRepository;
import com.collabtech.platform.collaboration.domain.exceptions.CollaborationFailure;
import com.collabtech.platform.collaboration.domain.exceptions.CollaborationFailure.Code;
import com.collabtech.platform.shared.application.pagination.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

public final class DeliveryPersistence implements DeliverableRepository, Queries, Attachments, Commands {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final HistoryPersistence history;
    private static final String CURRENT = """
            select d.*,s.submission_id,s.revision,s.submitted_by,s.submitted_at,s.late,s.content,
            r.review_id,r.reviewed_by,r.reviewed_at,r.decision,r.observations,r.correction_deadline
            from collaboration_deliverable d
            join collaboration_submission s on s.deliverable_id=d.deliverable_id and s.revision=d.current_revision
            left join collaboration_review r on r.submission_id=s.submission_id
            """;

    public DeliveryPersistence(JdbcTemplate jdbc, ObjectMapper json) { this(jdbc,json,new HistoryPersistence(jdbc,json)); }
    public DeliveryPersistence(JdbcTemplate jdbc,ObjectMapper json,HistoryPersistence history) { this.jdbc=jdbc;this.json=json;this.history=history; }
    public Optional<Deliverable> byId(UUID id) {
        return jdbc.query(CURRENT+" where d.deliverable_id=?", this::piece, id.toString()).stream().findFirst();
    }
    public List<Deliverable> forCollaboration(UUID id) {
        return jdbc.query(CURRENT+" where d.collaboration_id=? order by d.obligation_id,d.piece_number", this::piece, id.toString());
    }

    public Deliverable save(Deliverable d) {
        var stored = jdbc.queryForList("select version from collaboration_deliverable where deliverable_id=?", d.id().toString());
        boolean create = stored.isEmpty();
        if (create) {
            jdbc.update("""
                    insert into collaboration_deliverable(deliverable_id,collaboration_id,obligation_id,piece_number,
                    current_revision,status,created_at,updated_at,version) values(?,?,?,?,?,?,?,?,0)
                    """, d.id().toString(), d.collaborationId().toString(), d.obligationId().toString(), d.pieceNumber(),
                    d.submission().revision(), d.status().name(), time(d.createdAt()), time(d.submission().submittedAt()));
        } else if (((Number)stored.getFirst().get("version")).longValue() != d.version()) {
            throw new CollaborationFailure(Code.CONCURRENT_UPDATE);
        }
        var s = d.submission();
        if (jdbc.queryForObject("select count(*) from collaboration_submission where submission_id=?", Integer.class, s.id().toString()) == 0) {
            jdbc.update("""
                    insert into collaboration_submission(submission_id,deliverable_id,revision,submitted_by,submitted_at,late,content)
                    values(?,?,?,?,?,?,?)
                    """, s.id().toString(), d.id().toString(), s.revision(), s.submittedBy().toString(),
                    time(s.submittedAt()), s.late(), json.writeValueAsString(s.content()));
            for (var asset : s.content().assetIds()) {
                jdbc.update("insert into collaboration_submission_asset(submission_id,asset_id) values(?,?)", s.id().toString(), asset.toString());
            }
        }
        var r = d.review();
        if (r != null && jdbc.queryForObject("select count(*) from collaboration_review where review_id=?", Integer.class, r.id().toString()) == 0) {
            jdbc.update("""
                    insert into collaboration_review(review_id,submission_id,reviewed_by,reviewed_at,decision,observations,correction_deadline)
                    values(?,?,?,?,?,?,?)
                    """, r.id().toString(), s.id().toString(), r.reviewedBy().toString(), time(r.reviewedAt()),
                    r.decision().name(), r.observations(), time(r.correctionDeadline()));
        }
        if (!create) {
            int changed = jdbc.update("""
                    update collaboration_deliverable set current_revision=?,status=?,updated_at=?,correction_deadline_override=?,version=version+1
                    where deliverable_id=? and version=?
                    """, s.revision(), d.status().name(), time(r == null ? s.submittedAt() : r.reviewedAt()), time(d.correctionDeadlineOverride()), d.id().toString(), d.version());
            if (changed != 1) throw new CollaborationFailure(Code.CONCURRENT_UPDATE);
            d.restoreVersion(d.version()+1);
        }
        return d;
    }

    public PageResult<Deliverable> list(UUID collaboration, Deliverable.Status status, PageRequest page) {
        String where = " where d.collaboration_id=?" + (status == null ? "" : " and d.status=?");
        Object[] params = status == null ? new Object[]{collaboration.toString()} : new Object[]{collaboration.toString(),status.name()};
        var count = jdbc.queryForObject("select count(*) from collaboration_deliverable d"+where, Long.class, params);
        var rows = jdbc.query(CURRENT+where+" order by d.created_at,d.deliverable_id limit "+page.size()+" offset "+offset(page), this::piece, params);
        return new PageResult<>(rows, count, page.page(), page.size());
    }
    public PageResult<Deliverable.Revision> revisions(UUID deliverable, PageRequest page) {
        var count = jdbc.queryForObject("select count(*) from collaboration_submission where deliverable_id=?", Long.class, deliverable.toString());
        var rows = jdbc.query("""
                select s.*,r.review_id,r.reviewed_by,r.reviewed_at,r.decision,r.observations,r.correction_deadline
                from collaboration_submission s left join collaboration_review r on r.submission_id=s.submission_id
                where s.deliverable_id=? order by s.revision desc
                """+" limit "+page.size()+" offset "+offset(page),
                (r,i)->new Deliverable.Revision(submission(r), review(r),extensions(uuid(r,"submission_id"))), deliverable.toString());
        return new PageResult<>(rows, count, page.page(), page.size());
    }
    public Optional<Deliverable> bySubmission(UUID id) {
        return jdbc.query(CURRENT+" where d.deliverable_id=(select deliverable_id from collaboration_submission where submission_id=?)",
                this::piece, id.toString()).stream().findFirst();
    }

    public Optional<Asset> metadata(UUID id) {
        return jdbc.query("""
                select asset_id,collaboration_id,owner_account_id,filename,media_type,byte_size,sha256,created_at
                from collaboration_asset where asset_id=?
                """, (r,i)->new Asset(uuid(r,"asset_id"),uuid(r,"collaboration_id"),uuid(r,"owner_account_id"),
                r.getString("filename"),r.getString("media_type"),r.getLong("byte_size"),r.getString("sha256"),instant(r,"created_at")),
                id.toString()).stream().findFirst();
    }
    public void save(Asset a, byte[] bytes) {
        jdbc.update("""
                insert into collaboration_asset(asset_id,collaboration_id,owner_account_id,filename,media_type,byte_size,sha256,created_at,bytes)
                values(?,?,?,?,?,?,?,?,?)
                """, a.id().toString(),a.collaborationId().toString(),a.ownerAccountId().toString(),a.filename(),a.mediaType(),
                a.byteSize(),a.sha256(),time(a.createdAt()),bytes);
        history.record(a.collaborationId(),"AssetUploaded",a.id(),a.ownerAccountId(),a.createdAt(),
                Map.of("assetId",a.id()));
    }
    public byte[] bytes(UUID id) {
        return jdbc.queryForObject("select bytes from collaboration_asset where asset_id=?", byte[].class, id.toString());
    }
    public boolean referenced(UUID id) {
        return jdbc.queryForObject("select (select count(*) from collaboration_submission_asset where asset_id=?)+(select count(*) from collaboration_incident_asset where asset_id=?)",
                Integer.class, id.toString(),id.toString()) > 0;
    }
    public Optional<Command> find(UUID collaboration, UUID actor, UUID request) {
        return jdbc.query("""
                select operation,fingerprint,result_id from collaboration_delivery_command
                where collaboration_id=? and actor_account_id=? and request_id=?
                """,(r,i)->new Command(r.getString(1),r.getString(2),UUID.fromString(r.getString(3))),
                collaboration.toString(),actor.toString(),request.toString()).stream().findFirst();
    }
    public void save(UUID collaboration, UUID actor, UUID request, Command command, Instant now) {
        jdbc.update("""
                insert into collaboration_delivery_command(collaboration_id,actor_account_id,request_id,operation,fingerprint,result_id,created_at)
                values(?,?,?,?,?,?,?)
                """, collaboration.toString(),actor.toString(),request.toString(),command.operation(),command.fingerprint(),command.resultId().toString(),time(now));
    }
    public String fingerprint(Object value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsString(value).getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private List<Deliverable.CorrectionExtension> extensions(UUID submission) {
        return jdbc.query("select * from collaboration_correction_extension where submission_id=? order by accepted_at,extension_id",
                (r,i)->new Deliverable.CorrectionExtension(uuid(r,"extension_id"),uuid(r,"submission_id"),uuid(r,"resolution_id"),
                        instant(r,"previous_deadline"),instant(r,"deadline"),uuid(r,"proposed_by"),uuid(r,"accepted_by"),instant(r,"accepted_at")),submission.toString());
    }
    private Deliverable piece(ResultSet r, int i) throws SQLException {
        return new Deliverable(uuid(r,"deliverable_id"),uuid(r,"collaboration_id"),uuid(r,"obligation_id"),r.getInt("piece_number"),
                instant(r,"created_at"),submission(r),review(r),instant(r,"correction_deadline_override"),r.getLong("version"));
    }
    private Deliverable.Submission submission(ResultSet r) throws SQLException {
        return new Deliverable.Submission(uuid(r,"submission_id"),r.getInt("revision"),uuid(r,"submitted_by"),
                instant(r,"submitted_at"),r.getBoolean("late"),json.readValue(r.getString("content"),DeliveryContent.class));
    }
    private Deliverable.Review review(ResultSet r) throws SQLException {
        if (r.getString("review_id") == null) return null;
        return new Deliverable.Review(uuid(r,"review_id"),uuid(r,"submission_id"),uuid(r,"reviewed_by"),instant(r,"reviewed_at"),
                Deliverable.Decision.valueOf(r.getString("decision")),r.getString("observations"),instant(r,"correction_deadline"));
    }
    private static UUID uuid(ResultSet r, String field) throws SQLException { return UUID.fromString(r.getString(field)); }
    private static Instant instant(ResultSet r, String field) throws SQLException { var t=r.getTimestamp(field); return t==null?null:t.toInstant(); }
    private static Timestamp time(Instant value) { return value==null?null:Timestamp.from(value); }
    private static int offset(PageRequest page) {
        long offset=(long)page.page()*page.size();
        if (offset>Integer.MAX_VALUE) throw new IllegalArgumentException("Invalid page");
        return (int)offset;
    }
}
