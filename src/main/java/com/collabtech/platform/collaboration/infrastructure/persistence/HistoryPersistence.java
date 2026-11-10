package com.collabtech.platform.collaboration.infrastructure.persistence;

import com.collabtech.platform.collaboration.application.ports.HistoryPorts.*;
import com.collabtech.platform.shared.application.pagination.*;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;

/** Own-context audit. Writes share the business transaction and the parent row lock. */
public final class HistoryPersistence implements History {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private record Fact(String type,UUID source,UUID actor,Instant at,Map<String,Object> details) {}
    public HistoryPersistence(JdbcTemplate jdbc,ObjectMapper json){this.jdbc=jdbc;this.json=json;}

    public void record(UUID collaboration,String type,UUID source,UUID actor,Instant now,Map<String,Object> details) {
        requireTransaction();
        UUID key=key(collaboration,type,source);
        initialize(collaboration,key);
        append(collaboration,new Fact(type,source,actor,now,details),false);
    }
    public void initialize(UUID collaboration){initialize(collaboration,null);}
    private void initialize(UUID collaboration,UUID excluded) {
        requireTransaction();
        if(Boolean.TRUE.equals(jdbc.queryForObject("select history_initialized from collaboration_collaboration where collaboration_id=?",Boolean.class,collaboration.toString())))return;
        if(Boolean.TRUE.equals(jdbc.queryForObject("select history_initialized from collaboration_collaboration where collaboration_id=? for update",Boolean.class,collaboration.toString())))return;
        var root=jdbc.queryForMap("""
                select c.*,a.created_at as agreement_created_at,a.brand_accepted_at,a.brand_accepted_by,a.creator_accepted_at,a.creator_accepted_by
                from collaboration_collaboration c join collaboration_agreement a on c.agreement_id=a.agreement_id
                where c.collaboration_id=?
                """,collaboration.toString());
        if(Boolean.TRUE.equals(root.get("history_initialized")))return;
        var facts=new ArrayList<Fact>();
        UUID agreement=UUID.fromString(root.get("agreement_id").toString());
        facts.add(new Fact("AgreementGenerated",agreement,null,instant(root.get("agreement_created_at")),Map.of("agreementId",agreement)));
        if(root.get("brand_accepted_at")!=null)facts.add(new Fact("BrandTermsAccepted",agreement,uuid(root.get("brand_accepted_by")),
                instant(root.get("brand_accepted_at")),Map.of("agreementId",agreement)));
        if(root.get("creator_accepted_at")!=null)facts.add(new Fact("CreatorTermsAccepted",agreement,uuid(root.get("creator_accepted_by")),
                instant(root.get("creator_accepted_at")),Map.of("agreementId",agreement)));
        var outbox=jdbc.queryForList("select event_id,event_type,payload,occurred_at from collaboration_outbox where aggregate_id=? order by occurred_at,event_id",collaboration.toString());
        var types=new HashSet<String>();
        for(var row:outbox) {
            String type=row.get("event_type").toString();types.add(type);
            var payload=json.readTree(row.get("payload").toString());
            UUID actor=payload.hasNonNull("performedBy")?UUID.fromString(payload.path("performedBy").asText()):null;
            facts.add(new Fact(type,uuid(row.get("event_id")),actor,instant(row.get("occurred_at")),eventDetails(row.get("payload").toString(),agreement)));
        }
        if(!types.contains("CollaborationCreated"))facts.add(new Fact("CollaborationCreated",collaboration,null,instant(root.get("started_at")),Map.of("agreementId",agreement)));
        if(root.get("completed_at")!=null&&!types.contains("CollaborationCompleted"))facts.add(new Fact("CollaborationCompleted",collaboration,null,instant(root.get("completed_at")),Map.of("agreementId",agreement)));
        if(root.get("cancelled_at")!=null&&!types.contains("CollaborationCancelled"))facts.add(new Fact("CollaborationCancelled",collaboration,null,instant(root.get("cancelled_at")),Map.of("agreementId",agreement)));
        for(var row:jdbc.queryForList("select asset_id,owner_account_id,filename,media_type,byte_size,created_at from collaboration_asset where collaboration_id=?",collaboration.toString())) {
            facts.add(new Fact("AssetUploaded",uuid(row.get("asset_id")),uuid(row.get("owner_account_id")),instant(row.get("created_at")),
                    Map.of("assetId",row.get("asset_id"))));
        }
        facts.sort(Comparator.comparing(Fact::at).thenComparingInt(f->priority(f.type())).thenComparing(f->f.source().toString()));
        for(var fact:facts)if(!key(collaboration,fact.type(),fact.source()).equals(excluded))append(collaboration,fact,true);
        jdbc.update("update collaboration_collaboration set history_initialized=true where collaboration_id=?",collaboration.toString());
    }
    private void append(UUID collaboration,Fact fact,boolean reconstructed) {
        UUID id=key(collaboration,fact.type(),fact.source());
        if(jdbc.queryForObject("select count(*) from collaboration_history where history_id=?",Integer.class,id.toString())!=0)return;
        jdbc.update("update collaboration_collaboration set history_sequence=history_sequence+1 where collaboration_id=?",collaboration.toString());
        long sequence=jdbc.queryForObject("select history_sequence from collaboration_collaboration where collaboration_id=?",Long.class,collaboration.toString());
        jdbc.update("""
                insert into collaboration_history(history_id,collaboration_id,sequence_number,event_type,actor_account_id,occurred_at,source_id,reconstructed,details)
                values(?,?,?,?,?,?,?,?,?)
                """,id.toString(),collaboration.toString(),sequence,fact.type(),fact.actor()==null?null:fact.actor().toString(),
                Timestamp.from(fact.at().truncatedTo(ChronoUnit.MICROS)),fact.source().toString(),reconstructed,json.writeValueAsString(fact.details()));
    }
    public Map<String,Object> eventDetails(String payload,UUID agreement) {
        var tree=json.readTree(payload);
        if(tree.has("termsVersion"))return Map.of("agreementId",agreement);
        if(tree instanceof tools.jackson.databind.node.ObjectNode object) {
            for(String field:List.of("eventId","collaborationId","performedBy","occurredAt","snapshot"))object.remove(field);
        }
        return json.readValue(tree.toString(),json.getTypeFactory().constructMapType(LinkedHashMap.class,String.class,Object.class));
    }
    public PageResult<Entry> list(UUID collaboration,PageRequest page) {
        initialize(collaboration);
        long total=jdbc.queryForObject("select count(*) from collaboration_history where collaboration_id=?",Long.class,collaboration.toString());
        long offset=(long)page.page()*page.size();
        if(offset>Integer.MAX_VALUE)throw new IllegalArgumentException("Invalid page");
        var rows=jdbc.query("select * from collaboration_history where collaboration_id=? order by sequence_number limit "+page.size()+" offset "+offset,
                (r,i)->new Entry(UUID.fromString(r.getString("history_id")),collaboration,r.getLong("sequence_number"),r.getString("event_type"),
                        uuid(r.getString("actor_account_id")),r.getTimestamp("occurred_at").toInstant(),UUID.fromString(r.getString("source_id")),
                        r.getBoolean("reconstructed"),json.readValue(r.getString("details"),json.getTypeFactory().constructMapType(LinkedHashMap.class,String.class,Object.class))),
                collaboration.toString());
        return new PageResult<>(rows,total,page.page(),page.size());
    }
    public List<UUID> uninitialized(int limit) {
        if(limit<1||limit>1000)throw new IllegalArgumentException("Invalid batch size");
        return jdbc.query("select collaboration_id from collaboration_collaboration where history_initialized=false order by collaboration_id limit "+limit,
                (r,i)->UUID.fromString(r.getString(1)));
    }
    private static UUID key(UUID c,String type,UUID source){return UUID.nameUUIDFromBytes((c+":"+type+":"+source).getBytes(StandardCharsets.UTF_8));}
    private static UUID uuid(Object value){return value==null?null:UUID.fromString(value.toString());}
    private static Instant instant(Object value) {
        if(value instanceof Timestamp timestamp)return timestamp.toInstant();
        if(value instanceof java.time.LocalDateTime date)return date.toInstant(java.time.ZoneOffset.UTC);
        if(value instanceof java.time.OffsetDateTime date)return date.toInstant();
        return (Instant)value;
    }
    private static int priority(String type){return switch(type){case "AgreementGenerated"->0;case "BrandTermsAccepted","CreatorTermsAccepted"->1;case "CollaborationCreated"->2;case "AssetUploaded"->3;default->4;};}
    private static void requireTransaction(){if(!TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("History requires a business transaction");}
}
