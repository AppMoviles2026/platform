package com.collabtech.platform.identity.infrastructure.oauth;

import com.collabtech.platform.identity.application.ports.AuthorizationStateStore;
import com.collabtech.platform.identity.application.ports.AuthorizationClient;
import com.collabtech.platform.identity.application.projections.IdentityViews;
import com.collabtech.platform.identity.application.exceptions.IdentityFailure;
import com.collabtech.platform.identity.domain.model.valueobjects.AccountId;
import com.collabtech.platform.identity.domain.model.valueobjects.SocialPlatform;
import com.collabtech.platform.identity.infrastructure.security.SecureTokens;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

public class DatabaseAuthorizationStateStore implements AuthorizationStateStore {
    private final JdbcTemplate jdbc; private final Clock clock;
    public DatabaseAuthorizationStateStore(JdbcTemplate jdbc, Clock clock) { this.jdbc = jdbc; this.clock = clock; }
    public PendingAuthorization create(AccountId owner, SocialPlatform platform, AuthorizationClient client) {
        String state = SecureTokens.random();
        UUID id = UUID.randomUUID(); var expires = Timestamp.from(clock.instant().plusSeconds(600));
        jdbc.update("insert into identity_oauth_authorization(authorization_id,account_id,platform,client,status,expires_at) values(?,?,?,?,'PENDING',?)",
                id.toString(), owner.value().toString(), platform.code(), client.name(), expires);
        jdbc.update("insert into identity_oauth_state(state_hash,account_id,platform,expires_at,authorization_id) values(?,?,?,?,?)",
                SecureTokens.digest(state), owner.value().toString(), platform.code(), expires, id.toString());
        return new PendingAuthorization(state, id);
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Authorization consume(String state, SocialPlatform platform) {
        if (state == null || !state.matches("[A-Za-z0-9_-]{43}")) invalid();
        var rows = jdbc.query("select account_id,platform,expires_at,authorization_id from identity_oauth_state where state_hash=? for update",
                (row, index) -> new Pending(row.getString(1), row.getString(2), row.getTimestamp(3), row.getString(4)), SecureTokens.digest(state));
        if (rows.isEmpty() || !rows.get(0).platform.equals(platform.code())
                || !rows.get(0).expires.toInstant().isAfter(clock.instant())) invalid();
        jdbc.update("delete from identity_oauth_state where state_hash=?", SecureTokens.digest(state));
        String id = rows.get(0).authorizationId;
        var client = id == null ? AuthorizationClient.API : AuthorizationClient.valueOf(jdbc.queryForObject(
                "select client from identity_oauth_authorization where authorization_id=?", String.class, id));
        return new Authorization(new AccountId(UUID.fromString(rows.get(0).owner)), platform, id == null ? null : UUID.fromString(id), client);
    }
    public void finish(UUID id, String status, String errorCode) {
        if (id == null) return; // Existing authorization states issued before V6 keep the API callback contract.
        if (!status.equals("SUCCEEDED") && !status.equals("FAILED")) throw new IllegalArgumentException("Invalid authorization result");
        jdbc.update("update identity_oauth_authorization set status=?,error_code=? where authorization_id=? and status='PENDING'",
                status, errorCode, id.toString());
    }
    public IdentityViews.AuthorizationStatusView find(UUID id, AccountId owner) {
        return jdbc.query("select platform,status,error_code,expires_at from identity_oauth_authorization where authorization_id=? and account_id=?",
                (row,index) -> {
                    var expires = row.getTimestamp(4).toInstant();
                    String status = row.getString(2);
                    if (status.equals("PENDING") && !expires.isAfter(clock.instant())) status="EXPIRED";
                    return new IdentityViews.AuthorizationStatusView(id,row.getString(1),status,row.getString(3),expires);
                },id.toString(),owner.value().toString()).stream().findFirst()
                .orElseThrow(() -> new IdentityFailure(IdentityFailure.Code.AUTHORIZATION_NOT_FOUND));
    }
    private static void invalid() { throw new IdentityFailure(IdentityFailure.Code.INVALID_OAUTH_STATE); }
    private record Pending(String owner, String platform, Timestamp expires, String authorizationId) {}
}
