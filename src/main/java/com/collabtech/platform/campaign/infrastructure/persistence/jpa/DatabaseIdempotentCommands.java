package com.collabtech.platform.campaign.infrastructure.persistence.jpa;

import com.collabtech.platform.campaign.application.ports.IdempotentCommands;
import com.collabtech.platform.campaign.application.services.RequestFingerprint;
import com.collabtech.platform.campaign.domain.exceptions.CampaignFailure;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

@Component @Profile("!skeleton")
public class DatabaseIdempotentCommands implements IdempotentCommands {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final Clock clock;
    private final TransactionTemplate transactions;
    public DatabaseIdempotentCommands(JdbcTemplate jdbc, ObjectMapper json, Clock clock, PlatformTransactionManager manager) {
        this.jdbc=jdbc; this.json=json; this.clock=clock; this.transactions=new TransactionTemplate(manager);
    }
    public <T> T execute(UUID actor, String operation, String key, String fingerprint, Class<T> resultType, Supplier<T> work) {
        if (key == null) return work.get(); // Existing clients retain their contract.
        if (!key.matches("[A-Za-z0-9._:-]{8,128}")) throw new IllegalArgumentException("Invalid Idempotency-Key");
        String hash = RequestFingerprint.of(key);
        return transactions.execute(transaction -> {
            var now = clock.instant(); var expires = Timestamp.from(now.plusSeconds(86400));
            // MySQL/H2-MySQL upsert serializes contenders without poisoning the transaction with a duplicate insert.
            jdbc.update("insert into campaign_idempotent_command(actor_id,operation,key_hash,request_fingerprint,expires_at) values(?,?,?,?,?) "
                    + "on duplicate key update key_hash=key_hash", actor.toString(),operation,hash,fingerprint,expires);
            var saved = jdbc.queryForObject("select request_fingerprint,response_json,expires_at from campaign_idempotent_command "
                    + "where actor_id=? and operation=? and key_hash=? for update",
                    (row,index) -> new Stored(row.getString(1),row.getString(2),row.getTimestamp(3)),actor.toString(),operation,hash);
            if (!saved.expires.toInstant().isAfter(now)) {
                jdbc.update("update campaign_idempotent_command set request_fingerprint=?,response_json=null,expires_at=? "
                        + "where actor_id=? and operation=? and key_hash=?",fingerprint,expires,actor.toString(),operation,hash);
            } else {
                if (!saved.fingerprint.equals(fingerprint)) throw new CampaignFailure(CampaignFailure.Code.IDEMPOTENCY_KEY_REUSED);
                if (saved.response != null) return json.readValue(saved.response,resultType);
            }
            T result = work.get();
            jdbc.update("update campaign_idempotent_command set response_json=? where actor_id=? and operation=? and key_hash=?",
                    json.writeValueAsString(result),actor.toString(),operation,hash);
            return result;
        });
    }
    private record Stored(String fingerprint,String response,Timestamp expires) {}
}
