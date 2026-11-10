package com.collabtech.platform.collaboration;
import static org.junit.jupiter.api.Assertions.*;
import com.collabtech.platform.shared.infrastructure.configuration.*;
import com.collabtech.platform.collaboration.application.ports.CollaborationPorts.UnitOfWork;
import com.collabtech.platform.collaboration.application.services.*;
import com.collabtech.platform.collaboration.domain.repositories.*;
import com.collabtech.platform.performance.application.services.PerformanceService;
import com.collabtech.platform.performance.infrastructure.persistence.PerformancePersistence;
import com.collabtech.platform.performance.application.ports.PerformancePorts;
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
class DemoPerformanceSeedTests {
    @Autowired AccountRepository accounts;@Autowired CampaignRepository campaigns;@Autowired CampaignCatalog catalog;
    @Autowired ApplicationRepository applications;@Autowired PasswordHasher passwords;@Autowired Clock clock;@Autowired PlatformTransactionManager manager;
    @Autowired CollaborationService collaborationService;@Autowired DeliveryService delivery;@Autowired PerformanceService service;
    @Autowired AgreementRepository agreements;@Autowired CollaborationRepository collaborations;@Autowired DeliverableRepository deliveries;
    @Autowired PerformancePersistence data;@Autowired UnitOfWork work;@Autowired PerformancePorts.UnitOfWork performanceWork;@Autowired JdbcTemplate jdbc;
    @Autowired com.collabtech.platform.performance.application.services.AttributionService attribution;
    @Autowired com.collabtech.platform.performance.infrastructure.persistence.AttributionPersistence attributionData;
    @Autowired com.collabtech.platform.performance.application.ports.AttributionPorts.Destinations destinations;
    @Test void attributionDemoHasSeparateTotalsAndRerunsPreserveDeactivation(){
        var args=new DefaultApplicationArguments();
        new DemoDataSeed(accounts,campaigns,catalog,applications,passwords,clock,manager,"SeedTestPassword123!").run(args);
        new CollaborationDemoSeed(collaborationService,accounts,agreements,work).run(args);
        new DeliveryDemoSeed(delivery,accounts,agreements,collaborations,deliveries,work,clock).run(args);
        new PerformanceDemoSeed(service,accounts,agreements,collaborations,data,data,performanceWork,clock).run(args);
        var seed=new AttributionDemoSeed(attribution,accounts,agreements,collaborations,data,data,performanceWork,destinations);
        seed.run(args);
        String c=jdbc.queryForObject("select c.collaboration_id from collaboration_collaboration c join collaboration_agreement a on c.agreement_id=a.agreement_id where a.application_id='30000000-0000-4000-8000-000000000004'",String.class);
        var brand=accounts.findByEmail(new com.collabtech.platform.identity.domain.model.valueobjects.EmailAddress("demo.brand@collabpro.app")).orElseThrow();
        var report=attribution.results(brand.id().value(),java.util.UUID.fromString(c),null,null,new com.collabtech.platform.shared.application.pagination.PageRequest(0,20));
        assertEquals(2,report.mechanisms().total());assertEquals(0,report.totals().total());assertEquals(1,report.demonstrationTotals().codeRedemptions());
        var code=report.mechanisms().items().stream().filter(m->m.mechanism().kind()==com.collabtech.platform.performance.domain.model.AttributionModels.Kind.CODE).findFirst().orElseThrow().mechanism();
        attribution.status(brand.id().value(),code.id(),new com.collabtech.platform.performance.application.services.AttributionService.ChangeStatus(java.util.UUID.randomUUID(),0,
                com.collabtech.platform.performance.domain.model.AttributionModels.Status.INACTIVE));
        seed.run(args);
        assertEquals(com.collabtech.platform.performance.domain.model.AttributionModels.Status.INACTIVE,attributionData.mechanism(code.id()).orElseThrow().status());
        var after=attribution.results(brand.id().value(),java.util.UUID.fromString(c),null,null,new com.collabtech.platform.shared.application.pagination.PageRequest(0,20));
        assertEquals(2,after.mechanisms().total());assertEquals(1,after.demonstrationTotals().codeRedemptions());assertEquals(0,after.totals().total());
    }
    @Test void manualDemonstrationIsMarkedAndNeverCreatesAutomaticMetricsOrOverwritesReview(){
        var args=new DefaultApplicationArguments();
        new DemoDataSeed(accounts,campaigns,catalog,applications,passwords,clock,manager,"SeedTestPassword123!").run(args);
        new CollaborationDemoSeed(collaborationService,accounts,agreements,work).run(args);
        new DeliveryDemoSeed(delivery,accounts,agreements,collaborations,deliveries,work,clock).run(args);
        var seed=new PerformanceDemoSeed(service,accounts,agreements,collaborations,data,data,performanceWork,clock);
        String c=jdbc.queryForObject("select c.collaboration_id from collaboration_collaboration c join collaboration_agreement a on c.agreement_id=a.agreement_id where a.application_id='30000000-0000-4000-8000-000000000004'",String.class);
        int snapshots=jdbc.queryForObject("select count(*) from performance_snapshot",Integer.class);
        int credentials=jdbc.queryForObject("select count(*) from identity_provider_credentials",Integer.class);
        seed.run(args);
        var before=jdbc.queryForMap("select e.* from performance_evidence e join performance_report r on e.report_id=r.report_id where r.collaboration_id=?",c);
        assertEquals(true,before.get("is_demonstration"));assertEquals("UNVERIFIED",before.get("status"));
        assertTrue(before.get("description").toString().contains("Demostración"));
        var brand=accounts.findByEmail(new com.collabtech.platform.identity.domain.model.valueobjects.EmailAddress("demo.brand@collabpro.app")).orElseThrow();
        service.review(brand.id().value(),java.util.UUID.fromString(before.get("evidence_id").toString()),
                new PerformanceService.ReviewInput(java.util.UUID.randomUUID(),0,com.collabtech.platform.performance.domain.model.PerformanceEvidence.Status.VERIFIED,"Revisión demo"));
        before=jdbc.queryForMap("select e.* from performance_evidence e join performance_report r on e.report_id=r.report_id where r.collaboration_id=?",c);
        assertEquals("VERIFIED",before.get("status"));
        seed.run(args);
        assertEquals(before,jdbc.queryForMap("select e.* from performance_evidence e join performance_report r on e.report_id=r.report_id where r.collaboration_id=?",c));
        assertEquals(snapshots,jdbc.queryForObject("select count(*) from performance_snapshot",Integer.class));
        assertEquals(credentials,jdbc.queryForObject("select count(*) from identity_provider_credentials",Integer.class));
    }
}
