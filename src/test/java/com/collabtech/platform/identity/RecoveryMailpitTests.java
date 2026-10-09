package com.collabtech.platform.identity;

import static org.junit.jupiter.api.Assertions.*;
import com.collabtech.platform.identity.application.commands.RegisterCreatorCommand;
import com.collabtech.platform.identity.application.handlers.RegisterCreatorCommandHandler;
import com.collabtech.platform.identity.application.ports.AccountRecoveryService;
import com.collabtech.platform.identity.domain.model.valueobjects.EmailAddress;
import com.collabtech.platform.identity.infrastructure.security.RecoveryMailDispatcher;
import com.collabtech.platform.identity.infrastructure.security.SecretCipher;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.ObjectMapper;

/** Real SMTP delivery into local Docker Mailpit, without deleting any existing messages. */
@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named="COLLABPRO_TEST_MAILPIT", matches="true")
class RecoveryMailpitTests {
    @Autowired RegisterCreatorCommandHandler creators;
    @Autowired AccountRecoveryService recovery;
    @Autowired RecoveryMailDispatcher dispatcher;
    @Autowired JdbcTemplate jdbc;
    @Autowired SecretCipher cipher;
    @Autowired ObjectMapper json;
    @Test void recoveryEmailIsActuallyDeliveredAndOutboxSecretIsCleared() throws Exception {
        String email = "smtp-" + UUID.randomUUID() + "@example.com";
        creators.handle(new RegisterCreatorCommand("SMTP prueba", new EmailAddress(email), "PasswordForMail123!"));
        recovery.requestRecovery(new EmailAddress(email));
        String raw = cipher.decrypt(jdbc.queryForObject("select encrypted_token from identity_recovery_mail where email=? and sent_at is null", String.class, email));
        for (int i = 0; i < 30 && jdbc.queryForObject("select count(*) from identity_recovery_mail where email=? and sent_at is null", Integer.class, email) > 0; i++) {
            dispatcher.dispatchPending();
        }
        assertEquals(1, jdbc.queryForObject("select count(*) from identity_recovery_mail where email=? and sent_at is not null and encrypted_token is null", Integer.class, email));
        var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        String configured = System.getenv("COLLABPRO_TEST_MAILPIT_URL");
        URI mailpit = URI.create(configured == null ? "http://localhost:8025" : configured);
        assertEquals("http", mailpit.getScheme());
        assertTrue(java.util.Set.of("localhost", "127.0.0.1", "[::1]").contains(mailpit.getHost()), "Mailpit test must remain on loopback");
        assertNull(mailpit.getUserInfo()); assertNull(mailpit.getQuery()); assertNull(mailpit.getFragment());
        assertTrue(mailpit.getPath().isEmpty() || mailpit.getPath().equals("/"));
        String base = mailpit.toString().replaceAll("/$", "") + "/api/v1/";
        var response = client.send(HttpRequest.newBuilder(URI.create(base + "messages?limit=200")).timeout(Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        String id = null;
        for (var message : json.readTree(response.body()).path("messages")) {
            if (message.path("To").toString().contains(email)) id = message.path("ID").asText();
        }
        assertNotNull(id, "Expected uniquely addressed SMTP message in Mailpit");
        var delivered = client.send(HttpRequest.newBuilder(URI.create(base + "message/" + id)).timeout(Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, delivered.statusCode());
        assertTrue(json.readTree(delivered.body()).path("Text").asText().contains("token=" + raw));
    }
}
