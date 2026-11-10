package com.collabtech.platform.shared.infrastructure.configuration;
import com.collabtech.platform.performance.application.services.AttributionService;
import com.collabtech.platform.performance.application.ports.PerformancePorts.UnitOfWork;
import com.collabtech.platform.performance.application.ports.AttributionPorts.Destinations;
import com.collabtech.platform.performance.domain.model.AttributionModels.*;
import com.collabtech.platform.performance.domain.model.PerformanceEvidence;
import com.collabtech.platform.performance.domain.repositories.PerformanceRepository;
import com.collabtech.platform.performance.application.ports.PerformancePorts.Queries;
import com.collabtech.platform.collaboration.domain.repositories.*;
import com.collabtech.platform.identity.domain.repositories.AccountRepository;
import com.collabtech.platform.identity.domain.model.valueobjects.*;
import com.collabtech.platform.shared.application.pagination.PageRequest;
import java.util.*;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Demo interactions are explicitly labelled and excluded from normal attribution totals. */
@Component @Profile("!skeleton") @Order(50)
@ConditionalOnProperty(name="collabpro.demo-seed.enabled",havingValue="true")
public class AttributionDemoSeed implements ApplicationRunner {
    private final AttributionService service;private final AccountRepository accounts;private final AgreementRepository agreements;
    private final CollaborationRepository collaborations;private final PerformanceRepository reports;private final Queries evidence;
    private final UnitOfWork work;private final Destinations destinations;
    public AttributionDemoSeed(AttributionService service,AccountRepository accounts,AgreementRepository agreements,
            CollaborationRepository collaborations,PerformanceRepository reports,Queries evidence,UnitOfWork work,Destinations destinations){
        this.service=service;this.accounts=accounts;this.agreements=agreements;this.collaborations=collaborations;this.reports=reports;
        this.evidence=evidence;this.work=work;this.destinations=destinations;
    }
    public void run(ApplicationArguments args){work.execute(()->{
        var agreement=agreements.byApplication(UUID.fromString("30000000-0000-4000-8000-000000000004"),false);if(agreement.isEmpty())return null;
        var existing=collaborations.byAgreement(agreement.get().id());if(existing.isEmpty())return null;
        var c=collaborations.byId(existing.get().id(),true).orElseThrow();if(c.cancelledAt()!=null||c.status().name().equals("CANCELLED"))return null;
        var brand=accounts.findByEmail(new EmailAddress("demo.brand@collabpro.app"));
        if(brand.isEmpty()||brand.get().status()!=AccountStatus.ACTIVE||brand.get().brandProfile()==null
                ||!brand.get().brandProfile().id().value().equals(c.snapshot().brandId()))return null;
        var account=brand.get().id().value();
        var code=service.createDemonstration(account,c.id(),new AttributionService.Create(
                UUID.fromString("a0000000-0000-4000-8000-000000000001"),Kind.CODE,"Demostración: código de colaboración",null)).mechanism();
        if(destinations.resolve("demo").isPresent())service.createDemonstration(account,c.id(),new AttributionService.Create(
                UUID.fromString("a0000000-0000-4000-8000-000000000002"),Kind.LINK,"Demostración: enlace de campaña","demo"));
        var report=reports.report(c.id(),false).orElseThrow();
        var proof=evidence.evidence(report.id(),null,new PageRequest(0,100)).items().stream()
                .filter(e->e.demonstration()&&e.status()!=PerformanceEvidence.Status.REJECTED).findFirst();
        if(proof.isPresent()&&code.status()==Status.ACTIVE)service.record(account,code.id(),new AttributionService.RecordInteraction(
                UUID.fromString("a0000000-0000-4000-8000-000000000003"),UUID.fromString("a0000000-0000-4000-8000-000000000004"),
                InteractionType.CODE_REDEMPTION,code.createdAt(),proof.get().id()));
        return null;
    });}
}
