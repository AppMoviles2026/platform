package com.collabtech.platform.identity.infrastructure.security;

import com.collabtech.platform.identity.application.ports.AccountRecoveryService;
import com.collabtech.platform.identity.application.exceptions.IdentityFailure;
import com.collabtech.platform.identity.domain.repositories.AccountRepository;
import com.collabtech.platform.identity.domain.model.valueobjects.AccountId;
import com.collabtech.platform.identity.domain.model.valueobjects.AccountStatus;
import com.collabtech.platform.identity.domain.model.valueobjects.EmailAddress;
import java.time.Clock;
import java.sql.Timestamp;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;

public final class DatabaseRecoveryService implements AccountRecoveryService {
    private final JdbcTemplate jdbc; private final Clock clock; private final SecretCipher cipher;
    private final AccountRepository accounts; private final TransactionTemplate transactions;
    public DatabaseRecoveryService(JdbcTemplate jdbc, Clock clock, SecretCipher cipher,
            AccountRepository accounts, PlatformTransactionManager manager) {
        this.jdbc = jdbc; this.clock = clock; this.cipher = cipher; this.accounts = accounts;
        transactions = new TransactionTemplate(manager);
    }
    public void requestRecovery(EmailAddress email) {
        transactions.executeWithoutResult(status -> {
            var owner = accounts.findByEmail(email);
            if (owner.isEmpty() || owner.get().status() != AccountStatus.ACTIVE) return;
            // Serialize requests for this account: only the newest recovery token remains valid.
            jdbc.queryForObject("select account_id from identity_account where account_id=? for update",
                    String.class, owner.get().id().value().toString());
            String token = SecureTokens.random();
            var expires = Timestamp.from(clock.instant().plusSeconds(1800));
            jdbc.update("delete from identity_recovery_token where account_id=?", owner.get().id().value().toString());
            jdbc.update("update identity_recovery_mail set encrypted_token=null,sent_at=? where email=? and sent_at is null",
                    Timestamp.from(clock.instant()), email.value());
            jdbc.update("insert into identity_recovery_token(token_hash,account_id,expires_at) values(?,?,?)",
                    SecureTokens.digest(token), owner.get().id().value().toString(), expires);
            jdbc.update("insert into identity_recovery_mail(mail_id,email,encrypted_token,expires_at) values(?,?,?,?)",
                    UUID.randomUUID().toString(), email.value(), cipher.encrypt(token), expires);
        });
    }
    public void resetPassword(String token, String encodedPassword) {
        transactions.executeWithoutResult(status -> {
            var pending = jdbc.query("select account_id,expires_at from identity_recovery_token where token_hash=?",
                    (row, index) -> new Pending(row.getString(1), row.getTimestamp(2)), SecureTokens.digest(token));
            if (pending.isEmpty() || !pending.get(0).expires.toInstant().isAfter(clock.instant())) invalid();
            // Same lock order as requestRecovery/login, then recheck the token under lock.
            jdbc.queryForObject("select account_id from identity_account where account_id=? for update", String.class, pending.get(0).owner);
            pending = jdbc.query("select account_id,expires_at from identity_recovery_token where token_hash=? for update",
                    (row, index) -> new Pending(row.getString(1), row.getTimestamp(2)), SecureTokens.digest(token));
            if (pending.isEmpty() || !pending.get(0).expires.toInstant().isAfter(clock.instant())) invalid();
            var id = new AccountId(UUID.fromString(pending.get(0).owner));
            var account = accounts.findById(id).orElseThrow(() -> new IdentityFailure(IdentityFailure.Code.INVALID_RECOVERY_TOKEN));
            if (account.status() != AccountStatus.ACTIVE) invalid();
            account.changePassword(encodedPassword); accounts.save(account);
            jdbc.update("delete from identity_access_session where account_id=?", id.value().toString());
            jdbc.update("delete from identity_recovery_token where account_id=?", id.value().toString());
            jdbc.update("update identity_recovery_mail set encrypted_token=null,sent_at=? where email=? and sent_at is null",
                    Timestamp.from(clock.instant()), account.email().value());
        });
    }
    private static void invalid() { throw new IdentityFailure(IdentityFailure.Code.INVALID_RECOVERY_TOKEN); }
    private record Pending(String owner, Timestamp expires) {}
}
