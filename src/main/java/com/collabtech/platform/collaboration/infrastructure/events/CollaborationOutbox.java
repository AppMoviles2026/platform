package com.collabtech.platform.collaboration.infrastructure.events;

import com.collabtech.platform.collaboration.application.ports.CollaborationPorts.Events;
import com.collabtech.platform.collaboration.domain.model.AgreementSnapshot;
import com.collabtech.platform.collaboration.interfaces.acl.CollaborationEventConsumer;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

public final class CollaborationOutbox implements Events {
    private final JdbcTemplate jdbc;private final ObjectMapper json;private final Clock clock;private final TransactionTemplate tx;
    public CollaborationOutbox(JdbcTemplate jdbc,ObjectMapper json,Clock clock,PlatformTransactionManager manager){
        this.jdbc=jdbc;this.json=json;this.clock=clock;tx=new TransactionTemplate(manager);tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }
    public void append(UUID id,String type,AgreementSnapshot terms,Instant now){
        jdbc.update("insert into collaboration_outbox(event_id,aggregate_id,event_type,payload,occurred_at) values(?,?,?,?,?)",UUID.randomUUID().toString(),id.toString(),type,json.writeValueAsString(terms),Timestamp.from(now));
    }
    public void dispatch(List<CollaborationEventConsumer> consumers){
        var names=new HashSet<String>();for(var c:consumers){if(c.consumerName()==null||!c.consumerName().matches("[a-z][a-z0-9.-]{1,63}")||!names.add(c.consumerName()))throw new IllegalArgumentException("Invalid/duplicate consumer name");}
        for(var consumer:consumers){
            var ids=jdbc.query("select e.event_id from collaboration_outbox e left join collaboration_event_receipt r on r.event_id=e.event_id and r.consumer=? "
                    +"where r.event_id is null or (r.delivered=false and (r.next_attempt_at is null or r.next_attempt_at<=?)) order by e.occurred_at,e.event_id limit 50",
                    (r,i)->r.getString(1),consumer.consumerName(),Timestamp.from(clock.instant()));
            for(var id:ids){try{tx.executeWithoutResult(status->deliver(id,consumer));}catch(RuntimeException failure){
                tx.executeWithoutResult(status->recordFailure(id,consumer.consumerName()));
            }}
        }
    }
    private void deliver(String id,CollaborationEventConsumer consumer){
        var event=lock(id);var receipts=jdbc.queryForList("select delivered,next_attempt_at from collaboration_event_receipt where event_id=? and consumer=?",id,consumer.consumerName());
        if(!receipts.isEmpty()){
            if(Boolean.TRUE.equals(receipts.getFirst().get("delivered")))return;
            var next=jdbc.queryForObject("select next_attempt_at from collaboration_event_receipt where event_id=? and consumer=?",Timestamp.class,id,consumer.consumerName());
            if(next!=null&&next.toInstant().isAfter(clock.instant()))return;
        }
        consumer.handle(event);
        if(receipts.isEmpty())jdbc.update("insert into collaboration_event_receipt(event_id,consumer,delivered,attempts,delivered_at) values(?,?,true,1,?)",id,consumer.consumerName(),Timestamp.from(clock.instant()));
        else jdbc.update("update collaboration_event_receipt set delivered=true,attempts=attempts+1,delivered_at=?,next_attempt_at=null,last_error=null where event_id=? and consumer=?",Timestamp.from(clock.instant()),id,consumer.consumerName());
    }
    private void recordFailure(String id,String consumer){
        lock(id);var rows=jdbc.queryForList("select delivered,attempts from collaboration_event_receipt where event_id=? and consumer=?",id,consumer);
        if(!rows.isEmpty()&&Boolean.TRUE.equals(rows.getFirst().get("delivered")))return;
        int attempts=rows.isEmpty()?1:((Number)rows.getFirst().get("attempts")).intValue()+1;
        var next=Timestamp.from(clock.instant().plusSeconds(Math.min(300,5L*(1L<<Math.min(attempts,6)))));
        if(rows.isEmpty())jdbc.update("insert into collaboration_event_receipt(event_id,consumer,delivered,attempts,next_attempt_at,last_error) values(?,?,false,?,?,'CONSUMER_FAILED')",id,consumer,attempts,next);
        else jdbc.update("update collaboration_event_receipt set attempts=?,next_attempt_at=?,last_error='CONSUMER_FAILED' where event_id=? and consumer=?",attempts,next,id,consumer);
    }
    private CollaborationEventConsumer.Event lock(String id){return jdbc.queryForObject("select * from collaboration_outbox where event_id=? for update",(r,i)->
            new CollaborationEventConsumer.Event(UUID.fromString(r.getString("event_id")),UUID.fromString(r.getString("aggregate_id")),r.getString("event_type"),1,r.getString("payload"),r.getTimestamp("occurred_at").toInstant()),id);}
}
