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
    @Test void upgradesV7BrandAndSocialCredentialsWithoutLosingExistingData() {
        try (var data = new HikariDataSource()) {
            data.setJdbcUrl("jdbc:h2:mem:upgrade-v7-"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
            data.setUsername("sa"); data.setPassword("");
            String[] locations={"classpath:db/migration/identity","classpath:db/migration/campaign"};
            Flyway.configure().dataSource(data).locations(locations).target("7").load().migrate();
            var jdbc=new JdbcTemplate(data); String account=UUID.randomUUID().toString(); String profile=UUID.randomUUID().toString();
            jdbc.update("insert into identity_account(account_id,email,password_hash,account_type,status,created_at) values(?,?,?,'BRAND','ACTIVE',?)",
                    account,"brand-v7@example.com","existing-hash",Timestamp.from(Instant.now()));
            jdbc.update("insert into identity_brand_profile(profile_id,account_id,business_name,description) values(?,?,?,?)",profile,account,"Existing brand","Existing description");
            jdbc.update("insert into identity_provider_credentials(credential_id,encrypted_access_token,scopes,expires_at) values(?,?,?,?)",
                    UUID.randomUUID().toString(),"existing-ciphertext","basic",Timestamp.from(Instant.now().plusSeconds(100)));
            var upgrade=Flyway.configure().dataSource(data).locations(locations).load();
            assertEquals(1,upgrade.migrate().migrationsExecuted); upgrade.validate();
            assertEquals(0,jdbc.queryForObject("select version from identity_brand_profile where profile_id=?",Long.class,profile));
            assertEquals("Existing description",jdbc.queryForObject("select description from identity_brand_profile where profile_id=?",String.class,profile));
            var credential=jdbc.queryForMap("select encrypted_access_token,refresh_expires_at,reauthorization_required,version from identity_provider_credentials");
            assertEquals("existing-ciphertext",credential.get("encrypted_access_token")); assertNull(credential.get("refresh_expires_at"));
            assertEquals(false,credential.get("reauthorization_required")); assertEquals(0L,((Number)credential.get("version")).longValue());
        }
    }
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
            assertEquals(3,upgrade.migrate().migrationsExecuted); upgrade.validate();
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
