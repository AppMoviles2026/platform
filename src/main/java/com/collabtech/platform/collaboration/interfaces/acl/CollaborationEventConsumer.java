package com.collabtech.platform.collaboration.interfaces.acl;
import java.time.Instant;
import java.util.UUID;

/** Internal durable event contract. Consumers implement an infrastructure adapter and join the transaction. */
public interface CollaborationEventConsumer {
    String consumerName();
    void handle(Event event);
    /** payload is the versioned frozen terms JSON; external effects must use eventId as an idempotency key. */
    record Event(UUID eventId,UUID collaborationId,String type,int schemaVersion,String payload,Instant occurredAt){}
}
