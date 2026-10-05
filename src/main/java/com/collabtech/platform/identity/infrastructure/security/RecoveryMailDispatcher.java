package com.collabtech.platform.identity.infrastructure.security;

import java.time.Clock;
import java.sql.Timestamp;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.scheduling.annotation.Scheduled;

/** Durable encrypted outbox; delivery failures stay retryable and do not enumerate accounts publicly. */
public final class RecoveryMailDispatcher {
    private final JdbcTemplate jdbc; private final SecretCipher cipher; private final JavaMailSender mail;
    private final Clock clock; private final String resetUrl; private final String from;
    private final TransactionTemplate transactions; private final boolean enabled;
    public RecoveryMailDispatcher(JdbcTemplate jdbc, SecretCipher cipher, JavaMailSender mail, Clock clock,
            PlatformTransactionManager manager, String resetUrl, String from, boolean enabled) {
        this.jdbc = jdbc; this.cipher = cipher; this.mail = mail; this.clock = clock; this.resetUrl = resetUrl;
        this.from = from; this.enabled = enabled; transactions = new TransactionTemplate(manager);
    }
    @Scheduled(fixedDelayString = "${identity.recovery.delivery-delay-ms:5000}")
    public void scheduledDispatch() { if (enabled) dispatchPending(); }
    public void dispatchPending() {
        transactions.executeWithoutResult(status -> {
            var pending = jdbc.query("select mail_id,email,encrypted_token from identity_recovery_mail "
                    + "where sent_at is null and expires_at>? and attempts<5 order by mail_id limit 10 for update",
                    (row, index) -> new Pending(row.getString(1), row.getString(2), row.getString(3)), Timestamp.from(clock.instant()));
            for (var row : pending) {
                try {
                    var message = new SimpleMailMessage(); message.setFrom(from); message.setTo(row.email);
                    message.setSubject("CollabPro: recupera tu acceso");
                    String link = resetUrl + (resetUrl.contains("?") ? "&" : "?") + "token="
                            + URLEncoder.encode(cipher.decrypt(row.encrypted), StandardCharsets.UTF_8);
                    message.setText("Solicitaste recuperar el acceso a CollabPro. Este enlace vence en 30 minutos y puede usarse una sola vez:\n"
                            + link + "\nSi no lo solicitaste, ignora este mensaje.");
                    mail.send(message);
                    jdbc.update("update identity_recovery_mail set sent_at=?,encrypted_token=null where mail_id=?",
                            Timestamp.from(clock.instant()), row.id);
                } catch (org.springframework.mail.MailException deliveryFailure) {
                    jdbc.update("update identity_recovery_mail set attempts=attempts+1 where mail_id=?", row.id);
                }
            }
        });
    }
    private record Pending(String id, String email, String encrypted) {}
}
