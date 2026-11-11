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
    @Test void upgradesV16WithoutChangingExistingFinancialDataOrInventingPaidCompensations(){
        try(var data=new HikariDataSource()){
            data.setJdbcUrl("jdbc:h2:mem:upgrade-v16-"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");data.setUsername("sa");
            String[] locations={"classpath:db/migration/identity","classpath:db/migration/campaign","classpath:db/migration/collaboration","classpath:db/migration/performance","classpath:db/migration/billing"};
            Flyway.configure().dataSource(data).locations(locations).target("16").load().migrate();var jdbc=new JdbcTemplate(data);
            String root=UUID.randomUUID().toString(),plan=UUID.randomUUID().toString();var created=Timestamp.from(Instant.now());
            jdbc.update("INSERT INTO billing_account(payment_account_id,account_id,role,created_at) VALUES(?,?,'BRAND',?)",root,UUID.randomUUID().toString(),created);
            jdbc.update("INSERT INTO billing_plan(plan_id,code,definition_hash,available,demonstration,definition_json) VALUES(?,'ESSENTIAL',?,true,true,'{}')",plan,"a".repeat(64));
            var original=jdbc.queryForMap("SELECT * FROM billing_account WHERE payment_account_id=?",root);
            var originalPlan=jdbc.queryForMap("SELECT * FROM billing_plan WHERE plan_id=?",plan);
            var flyway=Flyway.configure().dataSource(data).locations(locations).load();assertEquals(1,flyway.migrate().migrationsExecuted);flyway.validate();
            assertEquals(original,jdbc.queryForMap("SELECT * FROM billing_account WHERE payment_account_id=?",root));
            assertEquals(originalPlan,jdbc.queryForMap("SELECT * FROM billing_plan WHERE plan_id=?",plan));
            for(String table:java.util.List.of("billing_compensation","billing_compensation_request","billing_compensation_reference","billing_compensation_event","billing_compensation_audit"))
                assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM "+table,Integer.class));
            assertEquals(0,flyway.migrate().migrationsExecuted);
        }
    }
    @Test void upgradesV15PreservingLinkedPaymentAccounts(){
        try(var data=new HikariDataSource()){
            data.setJdbcUrl("jdbc:h2:mem:upgrade-v15-"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");data.setUsername("sa");
            String[] locations={"classpath:db/migration/identity","classpath:db/migration/campaign","classpath:db/migration/collaboration","classpath:db/migration/performance","classpath:db/migration/billing"};
            Flyway.configure().dataSource(data).locations(locations).target("15").load().migrate();var jdbc=new JdbcTemplate(data);
            String root=UUID.randomUUID().toString();var created=Timestamp.from(Instant.now());
            jdbc.update("INSERT INTO billing_account(payment_account_id,account_id,role,created_at) VALUES(?,?,'BRAND',?)",root,UUID.randomUUID().toString(),created);
            var original=jdbc.queryForMap("SELECT * FROM billing_account WHERE payment_account_id=?",root);
            var flyway=Flyway.configure().dataSource(data).locations(locations).load();assertEquals(2,flyway.migrate().migrationsExecuted);flyway.validate();
            assertEquals(original,jdbc.queryForMap("SELECT * FROM billing_account WHERE payment_account_id=?",root));
            for(String table:java.util.List.of("billing_plan","billing_subscription","billing_subscription_payment","billing_subscription_event"))
                assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM "+table,Integer.class));
            assertEquals(0,flyway.migrate().migrationsExecuted);
        }
    }
    @Test void upgradesV14PreservingPriorDomainsWithoutInventingLinkedAccounts(){
        try(var data=new HikariDataSource()){
            data.setJdbcUrl("jdbc:h2:mem:upgrade-v14-"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");data.setUsername("sa");
            String[] locations={"classpath:db/migration/identity","classpath:db/migration/campaign","classpath:db/migration/collaboration","classpath:db/migration/performance","classpath:db/migration/billing"};
            Flyway.configure().dataSource(data).locations(locations).target("14").load().migrate();
            var jdbc=new JdbcTemplate(data);var account=UUID.randomUUID().toString();var created=Timestamp.from(Instant.now());
            jdbc.update("insert into identity_account(account_id,email,password_hash,account_type,status,created_at) values(?,?,?,'CREATOR','ACTIVE',?)",
                account,"existing-billing@example.com","existing-hash",created);
            var original=jdbc.queryForMap("select * from identity_account where account_id=?",account);
            var flyway=Flyway.configure().dataSource(data).locations(locations).load();assertEquals(3,flyway.migrate().migrationsExecuted);flyway.validate();
            assertEquals(original,jdbc.queryForMap("select * from identity_account where account_id=?",account));
            for(String table:java.util.List.of("billing_account","billing_authorization","billing_method","billing_webhook_event"))
                assertEquals(0,jdbc.queryForObject("select count(*) from "+table,Integer.class),table);
            assertEquals(0,flyway.migrate().migrationsExecuted);
        }
    }
    @Test void upgradesV13WithoutChangingMeasuredOrManualDataAndWithoutInventingInteractions(){
        try(var data=new HikariDataSource()){
            data.setJdbcUrl("jdbc:h2:mem:upgrade-v13-"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");data.setUsername("sa");
            String[] locations={"classpath:db/migration/identity","classpath:db/migration/campaign","classpath:db/migration/collaboration","classpath:db/migration/performance","classpath:db/migration/billing"};
            Flyway.configure().dataSource(data).locations(locations).target("13").load().migrate();
            var jdbc=new JdbcTemplate(data);var now=Timestamp.from(Instant.now());String report=UUID.randomUUID().toString(),publication=UUID.randomUUID().toString(),evidence=UUID.randomUUID().toString();
            jdbc.update("insert into performance_report(report_id,collaboration_id,brand_id,creator_id,created_at,updated_at) values(?,?,?,?,?,?)",
                    report,UUID.randomUUID().toString(),UUID.randomUUID().toString(),UUID.randomUUID().toString(),now,now);
            jdbc.update("insert into performance_publication(publication_id,report_id,social_id,platform,post_id,created_at) values(?,?,?,'tiktok','123',?)",
                    publication,report,UUID.randomUUID().toString(),now);
            jdbc.update("insert into performance_snapshot(snapshot_id,publication_id,submission_id,captured_at,period_start,period_end,metrics,recorded_sequence) values(?,?,?,?,?,?,?,1)",
                    UUID.randomUUID().toString(),publication,UUID.randomUUID().toString(),now,now,now,"{\"VIEWS\":42}");
            jdbc.update("insert into performance_evidence(evidence_id,report_id,submitted_by,description,references_json,claimed_metrics,observed_at,period_start,period_end,submitted_at,status) values(?,?,?,'Existing proof','[]','{}',?,?,?,?,'UNVERIFIED')",
                    evidence,report,UUID.randomUUID().toString(),now,now,now,now);
            var measured=jdbc.queryForMap("select * from performance_snapshot where publication_id=?",publication);
            var manual=jdbc.queryForMap("select * from performance_evidence where evidence_id=?",evidence);
            var upgrade=Flyway.configure().dataSource(data).locations(locations).load();assertEquals(4,upgrade.migrate().migrationsExecuted);upgrade.validate();
            assertEquals(measured,jdbc.queryForMap("select * from performance_snapshot where publication_id=?",publication));
            assertEquals(manual,jdbc.queryForMap("select * from performance_evidence where evidence_id=?",evidence));
            assertEquals(0,jdbc.queryForObject("select count(*) from performance_attribution",Integer.class));
            assertEquals(0,jdbc.queryForObject("select count(*) from performance_interaction",Integer.class));
            assertEquals(0,upgrade.migrate().migrationsExecuted);
        }
    }

    @Test void upgradesV12WithoutFabricatingMetricsOrChangingPrivateAssets() {
        try(var data=new HikariDataSource()){
            data.setJdbcUrl("jdbc:h2:mem:upgrade-v12-"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");data.setUsername("sa");
            String[] locations={"classpath:db/migration/identity","classpath:db/migration/campaign","classpath:db/migration/collaboration","classpath:db/migration/performance","classpath:db/migration/billing"};
            Flyway.configure().dataSource(data).locations(locations).target("12").load().migrate();
            var jdbc=new JdbcTemplate(data);var now=Timestamp.from(Instant.now());
            String a=UUID.randomUUID().toString(),c=UUID.randomUUID().toString(),brand=UUID.randomUUID().toString(),creator=UUID.randomUUID().toString();
            String snapshot="{\"historical\":true}";
            jdbc.update("insert into collaboration_agreement(agreement_id,application_id,brand_id,creator_id,snapshot,status,acceptance_deadline,created_at) values(?,?,?,?,?,'ACCEPTED',?,?)",
                    a,UUID.randomUUID().toString(),brand,creator,snapshot,now,now);
            jdbc.update("insert into collaboration_collaboration(collaboration_id,agreement_id,brand_id,creator_id,snapshot,status,started_at,obligations) values(?,?,?,?,?,'ACTIVE',?,?)",
                    c,a,brand,creator,snapshot,now,"[]");
            String asset=UUID.randomUUID().toString();byte[] bytes="%PDF-1.7 private".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
            jdbc.update("insert into collaboration_asset(asset_id,collaboration_id,owner_account_id,filename,media_type,byte_size,sha256,created_at,bytes) values(?,?,?,'evidence.pdf','application/pdf',?,?,?,?)",
                    asset,c,creator,bytes.length,"a".repeat(64),now,bytes);
            var before=jdbc.queryForMap("select * from collaboration_collaboration where collaboration_id=?",c);
            var upgrade=Flyway.configure().dataSource(data).locations(locations).load();
            assertEquals(5,upgrade.migrate().migrationsExecuted);upgrade.validate();
            assertEquals(before,jdbc.queryForMap("select * from collaboration_collaboration where collaboration_id=?",c));
            assertArrayEquals(bytes,jdbc.queryForObject("select bytes from collaboration_asset where asset_id=?",byte[].class,asset));
            assertEquals(creator,jdbc.queryForObject("select owner_account_id from collaboration_asset where asset_id=?",String.class,asset));
            for(String table:java.util.List.of("performance_report","performance_snapshot","performance_evidence","performance_asset","performance_command"))
                assertEquals(0,jdbc.queryForObject("select count(*) from "+table,Integer.class),table);
            assertEquals(0,upgrade.migrate().migrationsExecuted);
        }
    }

    @Test void upgradesV11WithoutChangingSnapshotsReviewsOrExistingEvents() {
        try(var data=new HikariDataSource()) {
            data.setJdbcUrl("jdbc:h2:mem:upgrade-v11-"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
            data.setUsername("sa");
            String[] locations={"classpath:db/migration/identity","classpath:db/migration/campaign","classpath:db/migration/collaboration","classpath:db/migration/performance","classpath:db/migration/billing"};
            Flyway.configure().dataSource(data).locations(locations).target("11").load().migrate();
            var jdbc=new JdbcTemplate(data);var now=Timestamp.from(Instant.now());
            String a=UUID.randomUUID().toString(),c=UUID.randomUUID().toString(),brand=UUID.randomUUID().toString(),creator=UUID.randomUUID().toString();
            String snapshot="{\"accepted\":true}";
            jdbc.update("insert into collaboration_agreement(agreement_id,application_id,brand_id,creator_id,snapshot,status,acceptance_deadline,created_at) values(?,?,?,?,?,'ACCEPTED',?,?)",
                    a,UUID.randomUUID().toString(),brand,creator,snapshot,now,now);
            jdbc.update("insert into collaboration_collaboration(collaboration_id,agreement_id,brand_id,creator_id,snapshot,status,started_at,obligations) values(?,?,?,?,?,'UNDER_REVIEW',?,?)",
                    c,a,brand,creator,snapshot,now,"[]");
            String d=UUID.randomUUID().toString(),submission=UUID.randomUUID().toString();
            jdbc.update("insert into collaboration_deliverable(deliverable_id,collaboration_id,obligation_id,piece_number,current_revision,status,created_at,updated_at) values(?,?,?,1,1,'REJECTED',?,?)",
                    d,c,UUID.randomUUID().toString(),now,now);
            jdbc.update("insert into collaboration_submission(submission_id,deliverable_id,revision,submitted_by,submitted_at,late,content) values(?,?,1,?,?,false,?)",
                    submission,d,creator,now,"{\"evidence\":[]}");
            jdbc.update("insert into collaboration_review(review_id,submission_id,reviewed_by,reviewed_at,decision,observations,correction_deadline) values(?,?,?,?,'REJECTED','Original observations',?)",
                    UUID.randomUUID().toString(),submission,brand,now,now);
            var before=jdbc.queryForMap("select * from collaboration_review where submission_id=?",submission);
            var upgrade=Flyway.configure().dataSource(data).locations(locations).load();
            assertEquals(6,upgrade.migrate().migrationsExecuted);upgrade.validate();
            assertEquals(before,jdbc.queryForMap("select * from collaboration_review where submission_id=?",submission));
            assertEquals(snapshot,jdbc.queryForObject("select snapshot from collaboration_collaboration where collaboration_id=?",String.class,c));
            assertEquals(0,jdbc.queryForObject("select open_incident_count from collaboration_collaboration where collaboration_id=?",Integer.class,c));
            assertEquals(false,jdbc.queryForObject("select history_initialized from collaboration_collaboration where collaboration_id=?",Boolean.class,c));
            assertEquals(0,jdbc.queryForObject("select count(*) from collaboration_incident",Integer.class));
            assertNull(jdbc.queryForObject("select correction_deadline_override from collaboration_deliverable where deliverable_id=?",Timestamp.class,d));
            assertEquals(0,upgrade.migrate().migrationsExecuted);
        }
    }

    @Test void upgradesV10WithoutChangingAcceptedTermsOrEventReceipts() {
        try (var data=new HikariDataSource()) {
            data.setJdbcUrl("jdbc:h2:mem:upgrade-v10-"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
            data.setUsername("sa");
            String[] locations={"classpath:db/migration/identity","classpath:db/migration/campaign","classpath:db/migration/collaboration","classpath:db/migration/performance","classpath:db/migration/billing"};
            Flyway.configure().dataSource(data).locations(locations).target("10").load().migrate();
            var jdbc=new JdbcTemplate(data);var now=Timestamp.from(Instant.now());
            String agreement=UUID.randomUUID().toString(), collaboration=UUID.randomUUID().toString();
            String brand=UUID.randomUUID().toString(),creator=UUID.randomUUID().toString(),event=UUID.randomUUID().toString();
            String snapshot="{\"historical\":\"accepted terms\"}",obligations="[{\"quantity\":2,\"approvedQuantity\":0}]";
            jdbc.update("insert into collaboration_agreement(agreement_id,application_id,brand_id,creator_id,snapshot,status,acceptance_deadline,created_at) values(?,?,?,?,?,'ACCEPTED',?,?)",
                    agreement,UUID.randomUUID().toString(),brand,creator,snapshot,now,now);
            jdbc.update("insert into collaboration_collaboration(collaboration_id,agreement_id,brand_id,creator_id,snapshot,status,started_at,next_deadline,obligations) values(?,?,?,?,?,'ACTIVE',?,?,?)",
                    collaboration,agreement,brand,creator,snapshot,now,now,obligations);
            jdbc.update("insert into collaboration_outbox(event_id,aggregate_id,event_type,payload,occurred_at) values(?,?,'CollaborationExpired',?,?)",event,collaboration,snapshot,now);
            jdbc.update("insert into collaboration_event_receipt(event_id,consumer,delivered,attempts,delivered_at) values(?,'existing.consumer',true,1,?)",event,now);
            var before=jdbc.queryForMap("select * from collaboration_agreement where agreement_id=?",agreement);
            var upgrade=Flyway.configure().dataSource(data).locations(locations).load();
            assertEquals(7,upgrade.migrate().migrationsExecuted);upgrade.validate();
            assertEquals(before,jdbc.queryForMap("select * from collaboration_agreement where agreement_id=?",agreement));
            assertEquals(obligations,jdbc.queryForObject("select obligations from collaboration_collaboration where collaboration_id=?",String.class,collaboration));
            assertNull(jdbc.queryForObject("select completed_at from collaboration_collaboration where collaboration_id=?",Timestamp.class,collaboration));
            assertEquals(true,jdbc.queryForObject("select delivered from collaboration_event_receipt where event_id=?",Boolean.class,event));
            jdbc.update("insert into collaboration_outbox(event_id,aggregate_id,event_type,payload,occurred_at) values(?,?,'CollaborationExpired',?,?)",UUID.randomUUID().toString(),collaboration,snapshot,now);
            assertEquals(2,jdbc.queryForObject("select count(*) from collaboration_outbox where aggregate_id=?",Integer.class,collaboration));
            assertEquals(0,jdbc.queryForObject("select count(*) from collaboration_deliverable",Integer.class));
            assertEquals(0,upgrade.migrate().migrationsExecuted);
        }
    }

    @Test void upgradesV9SelectedApplicationsWithoutImplicitlyAcceptingAgreements() {
        try (var data = new HikariDataSource()) {
            data.setJdbcUrl("jdbc:h2:mem:upgrade-v9-"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
            data.setUsername("sa");
            String[] locations={"classpath:db/migration/identity","classpath:db/migration/campaign","classpath:db/migration/collaboration","classpath:db/migration/performance","classpath:db/migration/billing"};
            Flyway.configure().dataSource(data).locations(locations).target("9").load().migrate();
            var jdbc = new JdbcTemplate(data);
            String campaign=UUID.randomUUID().toString(), application=UUID.randomUUID().toString();
            var decided=Timestamp.from(Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS));
            jdbc.update("insert into campaign_campaign(campaign_id,brand_id,brand_name,title,objective,category,target_audience,status) values(?,?,?,?,?,?,?,'OPEN')",
                    campaign,UUID.randomUUID().toString(),"Existing brand","Preserved campaign","Objective","Category","Audience");
            jdbc.update("insert into campaign_application(application_id,campaign_id,creator_id,message,status,submitted_at,decided_at,decided_by,decision_reason) values(?,?,?,?,'SELECTED',?,?,?,?)",
                    application,campaign,UUID.randomUUID().toString(),"Agreed proposal",decided,decided,UUID.randomUUID().toString(),"Selected by owner");
            var before=jdbc.queryForMap("select * from campaign_application where application_id=?",application);
            var upgrade=Flyway.configure().dataSource(data).locations(locations).load();
            assertEquals(8,upgrade.migrate().migrationsExecuted);
            upgrade.validate();
            assertEquals(before,jdbc.queryForMap("select * from campaign_application where application_id=?",application));
            assertEquals(0,jdbc.queryForObject("select count(*) from collaboration_agreement",Integer.class));
            assertEquals(0,jdbc.queryForObject("select count(*) from collaboration_collaboration",Integer.class));
            assertEquals(0,jdbc.queryForObject("select count(*) from collaboration_outbox",Integer.class));
            assertEquals(0,upgrade.migrate().migrationsExecuted);
        }
    }

    @Test void upgradesV8ApplicationsAndOnlyRepairsKnownLegacyDemoIds() {
        try(var data=new HikariDataSource()){
            data.setJdbcUrl("jdbc:h2:mem:upgrade-v8-"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");data.setUsername("sa");
            String[] locations={"classpath:db/migration/identity","classpath:db/migration/campaign","classpath:db/migration/collaboration","classpath:db/migration/performance","classpath:db/migration/billing"};
            Flyway.configure().dataSource(data).locations(locations).target("8").load().migrate();
            var jdbc=new JdbcTemplate(data);String campaign=UUID.randomUUID().toString();
            jdbc.update("insert into campaign_campaign(campaign_id,brand_id,brand_name,title,objective,category,target_audience,status) values(?,?,?,?,?,?,?,'OPEN')",
                    campaign,UUID.randomUUID().toString(),"Brand","Title","Objective","Category","Audience");
            String demo="30000000-0000-4000-8000-000000000001",other=UUID.randomUUID().toString(),old="20000000-0000-4000-8000-000000000001";
            for(String id:java.util.List.of(demo,other)){
                jdbc.update("insert into campaign_application(application_id,campaign_id,creator_id,message,status,submitted_at) values(?,?,?,?,'PENDING',?)",
                    id,campaign,id.equals(demo)?old:UUID.randomUUID().toString(),"Preserved proposal",Timestamp.from(Instant.now()));
            }
            var upgrade=Flyway.configure().dataSource(data).locations(locations).load();assertEquals(9,upgrade.migrate().migrationsExecuted);
            assertEquals("20000000-0000-4000-8000-000000000002",jdbc.queryForObject("select creator_id from campaign_application where application_id=?",String.class,demo));
            assertEquals(2,jdbc.queryForObject("select count(*) from campaign_application where message='Preserved proposal' and status='PENDING' and decided_at is null",Integer.class));
            assertEquals(0L,jdbc.queryForObject("select change_sequence from campaign_campaign where campaign_id=?",Long.class,campaign));
            assertEquals(0,upgrade.migrate().migrationsExecuted);
        }
    }

    @Test void upgradesV7BrandAndSocialCredentialsWithoutLosingExistingData() {
        try (var data = new HikariDataSource()) {
            data.setJdbcUrl("jdbc:h2:mem:upgrade-v7-"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
            data.setUsername("sa"); data.setPassword("");
            String[] locations={"classpath:db/migration/identity","classpath:db/migration/campaign","classpath:db/migration/collaboration","classpath:db/migration/performance","classpath:db/migration/billing"};
            Flyway.configure().dataSource(data).locations(locations).target("7").load().migrate();
            var jdbc=new JdbcTemplate(data); String account=UUID.randomUUID().toString(); String profile=UUID.randomUUID().toString();
            jdbc.update("insert into identity_account(account_id,email,password_hash,account_type,status,created_at) values(?,?,?,'BRAND','ACTIVE',?)",
                    account,"brand-v7@example.com","existing-hash",Timestamp.from(Instant.now()));
            jdbc.update("insert into identity_brand_profile(profile_id,account_id,business_name,description) values(?,?,?,?)",profile,account,"Existing brand","Existing description");
            jdbc.update("insert into identity_provider_credentials(credential_id,encrypted_access_token,scopes,expires_at) values(?,?,?,?)",
                    UUID.randomUUID().toString(),"existing-ciphertext","basic",Timestamp.from(Instant.now().plusSeconds(100)));
            var upgrade=Flyway.configure().dataSource(data).locations(locations).load();
            assertEquals(10,upgrade.migrate().migrationsExecuted); upgrade.validate();
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
            String[] locations={"classpath:db/migration/identity","classpath:db/migration/campaign","classpath:db/migration/collaboration","classpath:db/migration/performance","classpath:db/migration/billing"};
            Flyway.configure().dataSource(data).locations(locations).target("5").load().migrate();
            var jdbc=new JdbcTemplate(data); var now=Instant.now(); var id=UUID.randomUUID().toString();
            jdbc.update("insert into identity_account(account_id,email,password_hash,account_type,status,created_at) values(?,?,?,'CREATOR','ACTIVE',?)",
                    id,"existing@example.com","existing-hash",Timestamp.from(now));
            String state=SecureTokens.random();
            jdbc.update("insert into identity_oauth_state(state_hash,account_id,platform,expires_at) values(?,?,'tiktok',?)",
                    SecureTokens.digest(state),id,Timestamp.from(now.plusSeconds(600)));
            jdbc.update("insert into identity_access_session values(?,?,?)",SecureTokens.digest("old-session"),id,Timestamp.from(now.plusSeconds(3600)));
            var upgrade=Flyway.configure().dataSource(data).locations(locations).load();
            assertEquals(12,upgrade.migrate().migrationsExecuted); upgrade.validate();
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
