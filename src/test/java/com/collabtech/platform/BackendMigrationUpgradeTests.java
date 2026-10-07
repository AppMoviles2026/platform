package com.collabtech.platform;

import static org.junit.jupiter.api.Assertions.*;
import com.collabtech.platform.identity.application.ports.AuthorizationClient;
import com.collabtech.platform.identity.domain.model.valueobjects.SocialPlatform;
import com.collabtech.platform.identity.infrastructure.oauth.DatabaseAuthorizationStateStore;
import com.collabtech.platform.identity.infrastructure.security.SecureTokens;
import java.sql.Timestamp;
import java.time.*;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import com.zaxxer.hikari.HikariDataSource;

class BackendMigrationUpgradeTests {
    @Test void upgradesExistingV5DatabaseWithoutDeletingAccountsOrBreakingPendingApiStates() {
        // Match the pooled-connection lifecycle used by the running application.
        try (var data=new HikariDataSource()) {
            data.setJdbcUrl("jdbc:h2:mem:upgrade-"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
            data.setUsername("sa"); data.setPassword(""); data.setMaximumPoolSize(2);
            String[] locations={"classpath:db/migration/identity","classpath:db/migration/campaign"};
            Flyway.configure().dataSource(data).locations(locations).target("5").load().migrate();
            var jdbc=new JdbcTemplate(data); var now=Instant.now(); var id=UUID.randomUUID().toString();
            jdbc.update("insert into identity_account(account_id,email,password_hash,account_type,status,created_at) values(?,?,?,'CREATOR','ACTIVE',?)",
                    id,"existing@example.com","existing-hash",Timestamp.from(now));
            String state=SecureTokens.random();
            jdbc.update("insert into identity_oauth_state(state_hash,account_id,platform,expires_at) values(?,?,'tiktok',?)",
                    SecureTokens.digest(state),id,Timestamp.from(now.plusSeconds(600)));
            jdbc.update("insert into identity_access_session values(?,?,?)",SecureTokens.digest("old-session"),id,Timestamp.from(now.plusSeconds(3600)));
            var upgrade=Flyway.configure().dataSource(data).locations(locations).load();
            assertEquals(2,upgrade.migrate().migrationsExecuted); upgrade.validate();
            assertEquals(1,jdbc.queryForObject("select count(*) from identity_account",Integer.class));
            assertEquals(1,jdbc.queryForObject("select count(*) from identity_access_session",Integer.class));
            var authorization=new DatabaseAuthorizationStateStore(jdbc,Clock.fixed(now,ZoneOffset.UTC)).consume(state,new SocialPlatform("tiktok"));
            assertEquals(id,authorization.owner().value().toString()); assertEquals(AuthorizationClient.API,authorization.client());
            assertNull(authorization.authorizationId());
            assertEquals(0,jdbc.queryForObject("select count(*) from identity_oauth_authorization",Integer.class));
            assertEquals(0,jdbc.queryForObject("select count(*) from campaign_idempotent_command",Integer.class));
            assertEquals(0,upgrade.migrate().migrationsExecuted);
        }
    }
}
