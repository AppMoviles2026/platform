package com.collabtech.platform.collaboration;

import static org.junit.jupiter.api.Assertions.*;

import com.collabtech.platform.campaign.application.ports.CampaignCatalog;
import com.collabtech.platform.campaign.domain.repositories.ApplicationRepository;
import com.collabtech.platform.campaign.domain.repositories.CampaignRepository;
import com.collabtech.platform.collaboration.application.ports.CollaborationPorts.UnitOfWork;
import com.collabtech.platform.collaboration.application.services.CollaborationService;
import com.collabtech.platform.collaboration.domain.repositories.AgreementRepository;
import com.collabtech.platform.identity.application.ports.PasswordHasher;
import com.collabtech.platform.identity.domain.repositories.AccountRepository;
import com.collabtech.platform.shared.infrastructure.configuration.CollaborationDemoSeed;
import com.collabtech.platform.shared.infrastructure.configuration.DemoDataSeed;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;

@SpringBootTest
@ActiveProfiles("test")
class DemoCollaborationSeedTests {
    @Autowired AccountRepository accounts;
    @Autowired CampaignRepository campaigns;
    @Autowired CampaignCatalog catalog;
    @Autowired ApplicationRepository applications;
    @Autowired PasswordHasher passwords;
    @Autowired Clock clock;
    @Autowired PlatformTransactionManager manager;
    @Autowired JdbcTemplate jdbc;
    @Autowired CollaborationService service;
    @Autowired AgreementRepository agreements;
    @Autowired UnitOfWork work;

    @Test void createsOneBilateralDemoAndPreservesExistingAgreementOnRestart() {
        var data = new DemoDataSeed(accounts, campaigns, catalog, applications, passwords, clock, manager, "SeedTestPassword123!");
        var seed = new CollaborationDemoSeed(service, accounts, agreements, work);
        var args = new DefaultApplicationArguments();
        data.run(args);
        seed.run(args);
        String application = "30000000-0000-4000-8000-000000000004";
        var original = jdbc.queryForMap("select agreement_id,snapshot,brand_accepted_at,creator_accepted_at,version from collaboration_agreement where application_id=?", application);
        String agreement = original.get("agreement_id").toString();
        var collaboration = jdbc.queryForMap("select collaboration_id,status from collaboration_collaboration where agreement_id=?", agreement);
        assertEquals("ACTIVE", collaboration.get("status"));
        assertNotNull(original.get("brand_accepted_at"));
        assertNotNull(original.get("creator_accepted_at"));
        data.run(args);
        seed.run(args);
        assertEquals(original, jdbc.queryForMap("select agreement_id,snapshot,brand_accepted_at,creator_accepted_at,version from collaboration_agreement where application_id=?", application));
        assertEquals(collaboration, jdbc.queryForMap("select collaboration_id,status from collaboration_collaboration where agreement_id=?", agreement));
        assertEquals(1, jdbc.queryForObject("select count(*) from collaboration_outbox where aggregate_id=? and event_type='CollaborationCreated'", Integer.class, collaboration.get("collaboration_id")));
    }
}
