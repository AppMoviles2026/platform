package com.collabtech.platform.identity.infrastructure.security;

import com.collabtech.platform.identity.application.ports.AccessTokenProvider;
import com.collabtech.platform.identity.application.ports.AccessTokenVerifier;
import com.collabtech.platform.identity.application.projections.IdentityViews;
import java.time.Clock;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;
import java.util.Base64;
import java.util.List;
import javax.crypto.spec.SecretKeySpec;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.jdbc.core.JdbcTemplate;

/** Signed JWTs plus a revocable session registry; HTTP sessions are never used. */
public final class DatabaseAccessTokenProvider implements AccessTokenProvider, AccessTokenVerifier {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final String issuer;
    private final String audience;
    private final long ttlSeconds;

    public DatabaseAccessTokenProvider(JdbcTemplate jdbc, Clock clock, String base64Secret,
            String issuer, String audience, long ttlSeconds) {
        this.jdbc = jdbc; this.clock = clock;
        byte[] secret;
        try { secret = Base64.getDecoder().decode(base64Secret); }
        catch (IllegalArgumentException ex) { throw new IllegalArgumentException("JWT secret must be Base64."); }
        if (secret.length < 32) throw new IllegalArgumentException("JWT secret must contain at least 32 random bytes.");
        if (issuer == null || issuer.isBlank() || audience == null || audience.isBlank())
            throw new IllegalArgumentException("JWT issuer and audience are required.");
        if (ttlSeconds < 60 || ttlSeconds > 86400) throw new IllegalArgumentException("JWT TTL must be between 60 and 86400 seconds.");
        this.issuer = issuer; this.audience = audience; this.ttlSeconds = ttlSeconds;
        var key = new SecretKeySpec(secret, "HmacSHA256");
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        var verifier = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        verifier.setJwtValidator(jwt -> {
            Instant now = clock.instant();
            boolean valid = issuer.equals(jwt.getClaimAsString("iss")) && jwt.getAudience().contains(audience)
                    && jwt.getExpiresAt() != null && jwt.getExpiresAt().isAfter(now)
                    && jwt.getIssuedAt() != null && !jwt.getIssuedAt().isAfter(now)
                    && jwt.getNotBefore() != null && !jwt.getNotBefore().isAfter(now)
                    && jwt.getExpiresAt().isAfter(jwt.getIssuedAt())
                    && jwt.getId() != null && !jwt.getId().isBlank();
            return valid ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
        });
        this.decoder = verifier;
    }
    public IdentityViews.SessionView issue(IdentityViews.AccountView account) {
        Instant now = clock.instant().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        Instant expires = now.plusSeconds(ttlSeconds);
        var claims = JwtClaimsSet.builder().issuer(issuer).subject(account.accountId().toString())
                .audience(List.of(audience)).issuedAt(now).notBefore(now).expiresAt(expires)
                .id(UUID.randomUUID().toString()).claim("role", account.accountType()).build();
        String token = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).type("JWT").build(), claims)).getTokenValue();
        jdbc.update("insert into identity_access_session(token_hash,account_id,expires_at) values(?,?,?)",
                SecureTokens.digest(token), account.accountId().toString(), Timestamp.from(expires));
        return new IdentityViews.SessionView(account, token, "Bearer", expires);
    }
    public Optional<AuthenticatedAccount> authenticate(String token) {
        if (token == null || token.length() > 4096 || !token.matches("[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+"))
            return Optional.empty();
        Jwt jwt;
        UUID subject;
        try {
            jwt = decoder.decode(token);
            subject = UUID.fromString(jwt.getSubject());
            if (!List.of("BRAND", "CREATOR").contains(jwt.getClaimAsString("role"))) return Optional.empty();
        } catch (JwtException | IllegalArgumentException | NullPointerException ex) { return Optional.empty(); }
        return jdbc.query("select a.account_id,a.account_type from identity_access_session s join identity_account a "
                + "on a.account_id=s.account_id where s.token_hash=? and s.expires_at>? and a.status='ACTIVE'",
                (row, index) -> new AuthenticatedAccount(UUID.fromString(row.getString(1)), row.getString(2)),
                SecureTokens.digest(token), Timestamp.from(clock.instant())).stream()
                .filter(account -> account.accountId().equals(subject) && account.role().equals(jwt.getClaimAsString("role")))
                .findFirst();
    }
}
