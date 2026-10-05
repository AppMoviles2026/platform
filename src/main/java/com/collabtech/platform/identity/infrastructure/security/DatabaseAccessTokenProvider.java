package com.collabtech.platform.identity.infrastructure.security;

import com.collabtech.platform.identity.application.ports.AccessTokenProvider;
import com.collabtech.platform.identity.application.projections.IdentityViews;
import java.time.Clock;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

public final class DatabaseAccessTokenProvider implements AccessTokenProvider {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    public DatabaseAccessTokenProvider(JdbcTemplate jdbc, Clock clock) { this.jdbc = jdbc; this.clock = clock; }
    public IdentityViews.SessionView issue(IdentityViews.AccountView account) {
        String token = SecureTokens.random();
        Instant expires = clock.instant().plusSeconds(3600);
        jdbc.update("insert into identity_access_session(token_hash,account_id,expires_at) values(?,?,?)",
                SecureTokens.digest(token), account.accountId().toString(), Timestamp.from(expires));
        return new IdentityViews.SessionView(account, token, "Bearer", expires);
    }
    public Optional<AuthenticatedAccount> authenticate(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) return Optional.empty();
        return jdbc.query("select a.account_id,a.account_type from identity_access_session s join identity_account a "
                + "on a.account_id=s.account_id where s.token_hash=? and s.expires_at>? and a.status='ACTIVE'",
                (row, index) -> new AuthenticatedAccount(UUID.fromString(row.getString(1)), row.getString(2)),
                SecureTokens.digest(token), Timestamp.from(clock.instant())).stream().findFirst();
    }
    public record AuthenticatedAccount(UUID accountId, String role) {}
}
