package com.collabtech.platform.identity.infrastructure.oauth;

import com.collabtech.platform.identity.application.ports.AuthorizationStateStore;
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
    public String create(AccountId owner, SocialPlatform platform) {
        String state = SecureTokens.random();
        jdbc.update("insert into identity_oauth_state(state_hash,account_id,platform,expires_at) values(?,?,?,?)",
                SecureTokens.digest(state), owner.value().toString(), platform.code(), Timestamp.from(clock.instant().plusSeconds(600)));
        return state;
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Authorization consume(String state, SocialPlatform platform) {
        if (state == null || !state.matches("[A-Za-z0-9_-]{43}")) invalid();
        var rows = jdbc.query("select account_id,platform,expires_at from identity_oauth_state where state_hash=? for update",
                (row, index) -> new Pending(row.getString(1), row.getString(2), row.getTimestamp(3)), SecureTokens.digest(state));
        if (rows.isEmpty() || !rows.get(0).platform.equals(platform.code())
                || !rows.get(0).expires.toInstant().isAfter(clock.instant())) invalid();
        jdbc.update("delete from identity_oauth_state where state_hash=?", SecureTokens.digest(state));
        return new Authorization(new AccountId(UUID.fromString(rows.get(0).owner)), platform);
    }
    private static void invalid() { throw new IdentityFailure(IdentityFailure.Code.INVALID_OAUTH_STATE); }
    private record Pending(String owner, String platform, Timestamp expires) {}
}
