package com.collabtech.platform.shared.infrastructure.configuration;
import com.collabtech.platform.campaign.application.ports.CampaignCatalog;
import com.collabtech.platform.campaign.domain.model.aggregates.*;
import com.collabtech.platform.campaign.domain.model.entities.*;
import com.collabtech.platform.campaign.domain.model.valueobjects.*;
import com.collabtech.platform.campaign.domain.repositories.*;
import com.collabtech.platform.collaboration.application.services.*;
import com.collabtech.platform.collaboration.application.ports.CollaborationPorts.UnitOfWork;
import com.collabtech.platform.collaboration.domain.model.*;
import com.collabtech.platform.collaboration.domain.repositories.*;
import com.collabtech.platform.identity.domain.repositories.AccountRepository;
import com.collabtech.platform.identity.domain.model.valueobjects.EmailAddress;
import com.collabtech.platform.identity.domain.model.valueobjects.AccountStatus;
import java.time.Clock;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Separate USD demonstration; existing PEN agreements and financial authorizations are never rewritten. */
@Component @Profile("!skeleton") @Order(50)
@ConditionalOnProperty(name="collabpro.demo-seed.enabled",havingValue="true")
public class CompensationDemoSeed implements ApplicationRunner {
    public static final UUID APPLICATION=UUID.fromString("31000000-0000-4000-8000-000000000008");
    public static final UUID CAMPAIGN=UUID.fromString("41000000-0000-4000-8000-000000000008");
    private final AccountRepository accounts;private final CampaignRepository campaigns;private final ApplicationRepository applications;
    private final CampaignCatalog catalog;private final CollaborationService collaboration;private final DeliveryService delivery;
    private final AgreementRepository agreements;private final CollaborationRepository collaborations;private final DeliverableRepository deliveries;
    private final UnitOfWork work;private final Clock clock;
    public CompensationDemoSeed(AccountRepository accounts,CampaignRepository campaigns,ApplicationRepository applications,CampaignCatalog catalog,
        CollaborationService collaboration,DeliveryService delivery,AgreementRepository agreements,CollaborationRepository collaborations,
        DeliverableRepository deliveries,UnitOfWork work,Clock clock){
        this.accounts=accounts;this.campaigns=campaigns;this.applications=applications;this.catalog=catalog;this.collaboration=collaboration;
        this.delivery=delivery;this.agreements=agreements;this.collaborations=collaborations;this.deliveries=deliveries;this.work=work;this.clock=clock;
    }
    public void run(ApplicationArguments args){
        work.execute(()->{
            var b=accounts.findByEmail(new EmailAddress("demo.brand@collabpro.app"));
            var u=accounts.findByEmail(new EmailAddress("demo.creator@collabpro.app"));
            if(b.isEmpty()||u.isEmpty()||b.get().status()!=AccountStatus.ACTIVE||u.get().status()!=AccountStatus.ACTIVE)return null;
            var brand=b.get();var creator=u.get();var now=clock.instant();
            if(agreements.byApplication(APPLICATION,false).isPresent())return null;
            var campaignId=new CampaignId(CAMPAIGN);
            var existing=campaigns.findById(campaignId);
            if(existing.isPresent())return null; // Never override user modifications or partially prepared demonstrations.
            var campaign=new Campaign(campaignId,new BrandId(brand.brandProfile().id().value()),"Demo PayPal Sandbox USD",
                "Demostrar compensación sin dinero real","Ejemplo independiente de los acuerdos en PEN","Food & Beverage",
                "Audiencia de demostración","Lima, Perú",CampaignStatus.OPEN,now,now.plusSeconds(7*86400),
                List.of(new CampaignRequirement(new RequirementId(UUID.randomUUID()),"Contenido original",false,RequirementRule.manual())),
                List.of(new DeliverableSpecification(new SpecificationId(UUID.randomUUID()),"VIDEO","Video demostrativo",1,now.plusSeconds(14*86400))),
                new CompensationTerms(CompensationType.CASH,new BigDecimal("5.00"),"USD","Solo PayPal Sandbox; no dinero real"));
            catalog.saveNew(campaign,brand.brandProfile().businessName());
            var application=new Application(new ApplicationId(APPLICATION),campaignId,new CreatorId(creator.creatorProfile().id().value()),
                "Participación de demostración Sandbox",ApplicationStatus.PENDING,now,Set.of());
            application.decide(ApplicationStatus.SELECTED,brand.id().value(),null,now,0);applications.save(application);
            var proposed=collaboration.propose(brand.id().value(),APPLICATION);
            collaboration.decide(brand.id().value(),proposed.id(),proposed.snapshot().termsVersion(),Agreement.Decision.ACCEPT);
            collaboration.decide(creator.id().value(),proposed.id(),proposed.snapshot().termsVersion(),Agreement.Decision.ACCEPT);
            var c=collaborations.byAgreement(proposed.id()).orElseThrow();var obligation=c.obligations().getFirst();
            var submitted=delivery.submit(creator.id().value(),c.id(),new DeliveryService.Submit(UUID.randomUUID(),obligation.id(),
                new DeliveryContent(new DeliveryContent.Reference("https://example.com/collabpro-demo/sandbox-usd",null),List.of(new DeliveryContent.Reference("https://example.com/collabpro-demo/sandbox-usd-evidence",null)),
                    "Evidencia ilustrativa del seed; no publicación verificada.")));
            delivery.validate(brand.id().value(),submitted.deliverable().id(),new DeliveryService.Validation(UUID.randomUUID(),
                submitted.affectedSubmissionId(),submitted.deliverable().version(),null,null),Deliverable.Decision.APPROVED);
            return null;
        });
    }
}
