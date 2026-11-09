package com.collabtech.platform.identity.infrastructure.oauth;

import com.collabtech.platform.identity.application.exceptions.IdentityFailure;
import com.collabtech.platform.identity.application.ports.SocialAuthorizationAccess;
import com.collabtech.platform.identity.application.projections.IdentityViews;
import com.collabtech.platform.identity.domain.model.valueobjects.CreatorProfileId;
import com.collabtech.platform.identity.domain.model.valueobjects.SocialMediaAccountId;
import com.collabtech.platform.identity.infrastructure.security.SecretCipher;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Locks a credential during renewal so concurrent consumers cannot rotate the same refresh token twice. */
public final class DatabaseSocialAuthorizationAccess implements SocialAuthorizationAccess {
    private final JdbcTemplate jdbc;
    private final SecretCipher cipher;
    private final Clock clock;
    private final HttpSocialTokenRefresher refresher;
    private final TransactionTemplate renewal;
    private final TransactionTemplate reads;
    public DatabaseSocialAuthorizationAccess(JdbcTemplate jdbc, SecretCipher cipher, Clock clock,
            HttpSocialTokenRefresher refresher, PlatformTransactionManager manager) {
        this.jdbc = jdbc; this.cipher = cipher; this.clock = clock; this.refresher = refresher;
        renewal = new TransactionTemplate(manager);
        renewal.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        renewal.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        renewal.setTimeout(20);
        reads = new TransactionTemplate(manager);
        reads.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        reads.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        reads.setReadOnly(true);
    }
    @Override
    public IdentityViews.SocialAccessView inspect(CreatorProfileId owner, SocialMediaAccountId social, Set<String> scopes) {
        return reads.execute(tx -> view(load(owner, social, false), Set.copyOf(scopes)));
    }
    @Override
    public IdentityViews.SocialAccessView ensureUsable(CreatorProfileId owner, SocialMediaAccountId social, Set<String> scopes) {
        Set<String> required = Set.copyOf(scopes);
        // Return the failure from the transaction so a reauthorization marker survives the caller's rollback.
        IdentityFailure failure = renewal.execute(tx -> {
            var row = load(owner, social, true);
            var state = view(row, required);
            if (state.status().equals("READY")) return null;
            if (!state.status().equals("EXPIRED")) return failure(state.status());
            try {
                var updated = refresher.refresh(row.platform(), cipher.decrypt(row.refresh()), row.externalId());
                jdbc.update("update identity_provider_credentials set encrypted_access_token=?,encrypted_refresh_token=?,scopes=?,expires_at=?,"
                        + "refresh_expires_at=?,reauthorization_required=false,version=version+1 where credential_id=?",
                        cipher.encrypt(updated.accessToken()), cipher.encrypt(updated.refreshToken()), SocialScopes.normalized(updated.scopes()),
                        Timestamp.from(updated.expiresAt()), Timestamp.from(updated.refreshExpiresAt()), row.credentialId());
                var after = view(load(owner, social, false), required);
                return after.status().equals("READY") ? null : failure(after.status());
            } catch (IdentityFailure error) {
                if (error.code() == IdentityFailure.Code.SOCIAL_REAUTHORIZATION_REQUIRED)
                    jdbc.update("update identity_provider_credentials set reauthorization_required=true,version=version+1 where credential_id=?", row.credentialId());
                return error;
            }
        });
        if (failure != null) throw failure;
        return inspect(owner, social, required);
    }
    /** Only called by the infrastructure facade after the lifecycle check. Never exposed as a REST resource. */
    public String accessToken(CreatorProfileId owner, SocialMediaAccountId social, Set<String> scopes) {
        return reads.execute(tx -> {
            var row = load(owner, social, false);
            var state = view(row, Set.copyOf(scopes));
            if (!state.status().equals("READY")) throw failure(state.status());
            return cipher.decrypt(row.access());
        });
    }
    private Credential load(CreatorProfileId owner, SocialMediaAccountId social, boolean lock) {
        if (lock) {
            // Lock the stable owner first, then read credentials in a second statement. A joined locking
            // projection may have been materialized before waiting for another renewal to commit.
            jdbc.queryForList("select account_id from identity_account where account_id=(select account_id from identity_creator_profile where profile_id=?) for update",
                    String.class, owner.value().toString());
        }
        var rows = jdbc.query("select s.social_id,s.platform,s.external_account_id,s.status as social_status,c.credential_id,"
                + "c.encrypted_access_token,c.encrypted_refresh_token,c.scopes,c.expires_at,c.refresh_expires_at,c.reauthorization_required "
                + "from identity_social_account s join identity_creator_profile p on p.profile_id=s.profile_id "
                + "join identity_account a on a.account_id=p.account_id left join identity_provider_credentials c on c.social_id=s.social_id "
                + "where s.social_id=? and p.profile_id=? and a.status='ACTIVE' and a.account_type='CREATOR'",
                (r, i) -> new Credential(r.getString("social_id"), r.getString("platform"), r.getString("external_account_id"),
                        r.getString("social_status"), r.getString("credential_id"), r.getString("encrypted_access_token"),
                        r.getString("encrypted_refresh_token"), r.getString("scopes"), instant(r.getTimestamp("expires_at")),
                        instant(r.getTimestamp("refresh_expires_at")), r.getBoolean("reauthorization_required")),
                social.value().toString(), owner.value().toString());
        if (rows.isEmpty()) throw new IdentityFailure(IdentityFailure.Code.SOCIAL_ACCOUNT_NOT_FOUND);
        return rows.getFirst();
    }
    private IdentityViews.SocialAccessView view(Credential row, Set<String> required) {
        var granted = SocialScopes.parse(row.scopes());
        var missing = new HashSet<>(required); missing.removeAll(granted);
        boolean renewable = row.refresh() != null && !row.refresh().isBlank() && refresher.supports(row.platform())
                && (row.refreshExpiresAt() == null || row.refreshExpiresAt().isAfter(clock.instant())) && !row.reauthorize();
        String status;
        boolean reauthorize = false;
        if (!row.socialStatus().equals("ACTIVE")) { status = "REVOKED"; reauthorize = true; }
        else if (row.credentialId() == null) { status = "MISSING_CREDENTIALS"; reauthorize = true; }
        else if (row.reauthorize()) { status = "REAUTHORIZATION_REQUIRED"; reauthorize = true; }
        else if (!refresher.configured(row.platform())) status = "PROVIDER_NOT_CONFIGURED";
        else if (!missing.isEmpty()) { status = "INSUFFICIENT_SCOPE"; reauthorize = true; }
        else if (row.expiresAt() == null || !row.expiresAt().isAfter(clock.instant())) {
            status = renewable ? "EXPIRED" : "REAUTHORIZATION_REQUIRED"; reauthorize = !renewable;
        } else status = "READY";
        return new IdentityViews.SocialAccessView(java.util.UUID.fromString(row.socialId()), row.platform(), status,
                granted, missing, row.expiresAt(), renewable && !reauthorize, reauthorize);
    }
    private static IdentityFailure failure(String status) {
        return new IdentityFailure(switch (status) {
            case "PROVIDER_NOT_CONFIGURED" -> IdentityFailure.Code.PROVIDER_NOT_CONFIGURED;
            case "INSUFFICIENT_SCOPE" -> IdentityFailure.Code.SOCIAL_SCOPE_REQUIRED;
            default -> IdentityFailure.Code.SOCIAL_REAUTHORIZATION_REQUIRED;
        });
    }
    private static Instant instant(Timestamp value) { return value == null ? null : value.toInstant(); }
    private record Credential(String socialId, String platform, String externalId, String socialStatus, String credentialId,
            String access, String refresh, String scopes, Instant expiresAt, Instant refreshExpiresAt, boolean reauthorize) {
        @Override public String toString() { return "Credential[redacted]"; }
    }
}
