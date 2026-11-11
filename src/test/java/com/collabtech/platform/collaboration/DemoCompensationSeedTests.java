package com.collabtech.platform.collaboration;
import static org.junit.jupiter.api.Assertions.*;
import com.collabtech.platform.shared.infrastructure.configuration.*;
import com.collabtech.platform.campaign.application.ports.CampaignCatalog;
import com.collabtech.platform.campaign.domain.repositories.*;
import com.collabtech.platform.collaboration.application.services.*;
import com.collabtech.platform.collaboration.application.ports.CollaborationPorts.UnitOfWork;
import com.collabtech.platform.collaboration.domain.repositories.*;
import com.collabtech.platform.identity.domain.repositories.AccountRepository;
import com.collabtech.platform.identity.application.ports.PasswordHasher;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
@SpringBootTest @ActiveProfiles("test")
class DemoCompensationSeedTests {
    @Autowired AccountRepository accounts;@Autowired CampaignRepository campaigns;@Autowired ApplicationRepository applications;
    @Autowired CampaignCatalog catalog;@Autowired PasswordHasher passwords;@Autowired Clock clock;@Autowired PlatformTransactionManager manager;
    @Autowired CollaborationService collaboration;@Autowired DeliveryService delivery;@Autowired AgreementRepository agreements;
    @Autowired CollaborationRepository collaborations;@Autowired DeliverableRepository deliveries;@Autowired UnitOfWork work;@Autowired JdbcTemplate jdbc;
    @Test void addsIndependentUsdExampleWithoutRewritingPenOrFabricatingPayment(){
        var args=new DefaultApplicationArguments();
        new DemoDataSeed(accounts,campaigns,catalog,applications,passwords,clock,manager,"SeedTestPassword123!").run(args);
        var pen=jdbc.queryForMap("select * from campaign_campaign where campaign_id='40000000-0000-4000-8000-000000000001'");
        var seed=new CompensationDemoSeed(accounts,campaigns,applications,catalog,collaboration,delivery,agreements,collaborations,deliveries,work,clock);
        seed.run(args);
        var agreement=jdbc.queryForMap("select * from collaboration_agreement where application_id=?",CompensationDemoSeed.APPLICATION.toString());
        assertEquals("ACCEPTED",agreement.get("status"));assertTrue(agreement.get("snapshot").toString().contains("USD"));
        var result=jdbc.queryForMap("select * from collaboration_collaboration where agreement_id=?",agreement.get("agreement_id"));
        assertEquals("COMPLETED",result.get("status"));
        seed.run(args);
        assertEquals(agreement,jdbc.queryForMap("select * from collaboration_agreement where application_id=?",CompensationDemoSeed.APPLICATION.toString()));
        assertEquals(result,jdbc.queryForMap("select * from collaboration_collaboration where agreement_id=?",agreement.get("agreement_id")));
        assertEquals(pen,jdbc.queryForMap("select * from campaign_campaign where campaign_id='40000000-0000-4000-8000-000000000001'"));
        assertEquals(0,jdbc.queryForObject("select count(*) from billing_compensation where collaboration_id=? and state_json like '%\"wasPaid\":true%'",Integer.class,result.get("collaboration_id")));
    }
}
