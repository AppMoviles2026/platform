package com.collabtech.platform.campaign;
import static org.junit.jupiter.api.Assertions.*;
import com.collabtech.platform.shared.infrastructure.configuration.DemoDataSeed;
import com.collabtech.platform.identity.domain.repositories.AccountRepository;
import com.collabtech.platform.identity.application.ports.PasswordHasher;
import com.collabtech.platform.campaign.domain.repositories.*;
import com.collabtech.platform.campaign.application.ports.CampaignCatalog;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
@SpringBootTest @ActiveProfiles("test")
class DemoCampaignSeedTests {
    @Autowired AccountRepository accounts;@Autowired CampaignRepository campaigns;@Autowired CampaignCatalog catalog;
    @Autowired ApplicationRepository applications;@Autowired PasswordHasher passwords;@Autowired Clock clock;
    @Autowired PlatformTransactionManager manager;@Autowired JdbcTemplate jdbc;
    @Test void seededApplicantsHaveRealProfileIdsAndDecisionsAndRerunsPreserveChanges(){
        var seed=new DemoDataSeed(accounts,campaigns,catalog,applications,passwords,clock,manager,"SeedTestPassword123!");
        seed.run(new DefaultApplicationArguments());
        assertEquals(5,jdbc.queryForObject("select count(*) from campaign_campaign c join identity_brand_profile p on c.brand_id=p.profile_id where c.campaign_id like '40000000-%'",Integer.class));
        assertEquals(5,jdbc.queryForObject("select count(*) from campaign_application a join identity_creator_profile p on a.creator_id=p.profile_id where a.application_id like '30000000-%'",Integer.class));
        assertEquals(2,jdbc.queryForObject("select count(*) from campaign_application where application_id like '30000000-%' and decided_at is not null",Integer.class));
        String id="30000000-0000-4000-8000-000000000004";
        jdbc.update("update campaign_application set message='User modification' where application_id=?",id);
        seed.run(new DefaultApplicationArguments());
        assertEquals("User modification",jdbc.queryForObject("select message from campaign_application where application_id=?",String.class,id));
    }
}
