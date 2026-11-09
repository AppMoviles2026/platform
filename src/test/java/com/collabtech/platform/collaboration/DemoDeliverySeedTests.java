package com.collabtech.platform.collaboration;

import static org.junit.jupiter.api.Assertions.*;
import com.collabtech.platform.shared.infrastructure.configuration.*;
import com.collabtech.platform.collaboration.application.ports.CollaborationPorts.UnitOfWork;
import com.collabtech.platform.collaboration.application.services.*;
import com.collabtech.platform.collaboration.domain.repositories.*;
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

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DemoDeliverySeedTests {
    @Autowired AccountRepository accounts; @Autowired CampaignRepository campaigns;
    @Autowired CampaignCatalog catalog; @Autowired ApplicationRepository applications;
    @Autowired PasswordHasher passwords; @Autowired Clock clock; @Autowired PlatformTransactionManager manager;
    @Autowired CollaborationService collaborationService; @Autowired DeliveryService service;
    @Autowired AgreementRepository agreements; @Autowired CollaborationRepository collaborations;
    @Autowired DeliverableRepository deliveries; @Autowired UnitOfWork work; @Autowired JdbcTemplate jdbc;

    @Test void createsExplicitDemoRevisionAndNeverReplacesExistingVersionsOrDecisions() {
        var args=new DefaultApplicationArguments();
        new DemoDataSeed(accounts,campaigns,catalog,applications,passwords,clock,manager,"SeedTestPassword123!").run(args);
        new CollaborationDemoSeed(collaborationService,accounts,agreements,work).run(args);
        var seed=new DeliveryDemoSeed(service,accounts,agreements,collaborations,deliveries,work,clock);
        seed.run(args);
        var id=jdbc.queryForObject("""
                select c.collaboration_id from collaboration_collaboration c join collaboration_agreement a on c.agreement_id=a.agreement_id
                where a.application_id='30000000-0000-4000-8000-000000000004'
                """,String.class);
        var original=jdbc.queryForList("select * from collaboration_deliverable where collaboration_id=? order by piece_number",id);
        assertEquals(1,original.size());
        assertEquals("PENDING_VALIDATION",original.getFirst().get("status"));
        assertEquals(2,((Number)original.getFirst().get("current_revision")).intValue());
        var reviews=jdbc.queryForList("""
                select r.* from collaboration_review r join collaboration_submission s on r.submission_id=s.submission_id
                join collaboration_deliverable d on s.deliverable_id=d.deliverable_id where d.collaboration_id=?
                """,id);
        assertEquals(1,reviews.size());assertNotNull(reviews.getFirst().get("correction_deadline"));
        seed.run(args);
        assertEquals(original,jdbc.queryForList("select * from collaboration_deliverable where collaboration_id=? order by piece_number",id));
        assertEquals(3,jdbc.queryForObject("select count(*) from collaboration_delivery_command where collaboration_id=?",Integer.class,id));
    }
}
