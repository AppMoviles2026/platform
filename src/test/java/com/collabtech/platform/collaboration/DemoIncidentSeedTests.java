package com.collabtech.platform.collaboration;

import static org.junit.jupiter.api.Assertions.*;
import com.collabtech.platform.shared.infrastructure.configuration.*;
import com.collabtech.platform.collaboration.application.ports.CollaborationPorts.UnitOfWork;
import com.collabtech.platform.collaboration.application.services.*;
import com.collabtech.platform.collaboration.domain.repositories.*;
import com.collabtech.platform.collaboration.infrastructure.persistence.IncidentPersistence;
import com.collabtech.platform.identity.domain.repositories.AccountRepository;
import com.collabtech.platform.identity.application.ports.PasswordHasher;
import com.collabtech.platform.campaign.application.ports.CampaignCatalog;
import com.collabtech.platform.campaign.domain.repositories.CampaignRepository;
import com.collabtech.platform.campaign.domain.repositories.ApplicationRepository;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest @ActiveProfiles("test") @Transactional
class DemoIncidentSeedTests {
    @Autowired AccountRepository accounts;@Autowired CampaignRepository campaigns;@Autowired CampaignCatalog catalog;
    @Autowired ApplicationRepository applications;@Autowired PasswordHasher passwords;@Autowired Clock clock;@Autowired PlatformTransactionManager manager;
    @Autowired CollaborationService collaborationsService;@Autowired DeliveryService delivery;@Autowired IncidentService service;
    @Autowired AgreementRepository agreements;@Autowired CollaborationRepository collaborations;@Autowired DeliverableRepository deliveries;
    @Autowired IncidentPersistence incidents;@Autowired UnitOfWork work;@Autowired JdbcTemplate jdbc;
    @Test void explicitDemoHasAnAcceptedResolutionAndRerunsPreserveEveryDecisionAndHistoryEntry() {
        var args=new DefaultApplicationArguments();
        new DemoDataSeed(accounts,campaigns,catalog,applications,passwords,clock,manager,"SeedTestPassword123!").run(args);
        new CollaborationDemoSeed(collaborationsService,accounts,agreements,work).run(args);
        new DeliveryDemoSeed(delivery,accounts,agreements,collaborations,deliveries,work,clock).run(args);
        var seed=new IncidentDemoSeed(service,accounts,agreements,collaborations,incidents,work,clock);
        seed.run(args);
        String c=jdbc.queryForObject("select c.collaboration_id from collaboration_collaboration c join collaboration_agreement a on c.agreement_id=a.agreement_id where a.application_id='30000000-0000-4000-8000-000000000004'",String.class);
        var before=jdbc.queryForMap("select * from collaboration_incident where collaboration_id=?",c);
        assertEquals("RESOLVED",before.get("status"));
        assertEquals(0,jdbc.queryForObject("select open_incident_count from collaboration_collaboration where collaboration_id=?",Integer.class,c));
        assertEquals("UNDER_REVIEW",jdbc.queryForObject("select status from collaboration_collaboration where collaboration_id=?",String.class,c));
        long history=jdbc.queryForObject("select count(*) from collaboration_history where collaboration_id=?",Long.class,c);
        seed.run(args);
        assertEquals(before,jdbc.queryForMap("select * from collaboration_incident where collaboration_id=?",c));
        assertEquals(history,jdbc.queryForObject("select count(*) from collaboration_history where collaboration_id=?",Long.class,c));
    }
}
