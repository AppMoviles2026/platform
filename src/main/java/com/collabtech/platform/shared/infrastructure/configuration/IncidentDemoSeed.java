package com.collabtech.platform.shared.infrastructure.configuration;

import com.collabtech.platform.collaboration.application.ports.CollaborationPorts.UnitOfWork;
import com.collabtech.platform.collaboration.application.services.IncidentService;
import com.collabtech.platform.collaboration.domain.model.*;
import com.collabtech.platform.collaboration.domain.repositories.*;
import com.collabtech.platform.collaboration.application.ports.IncidentPorts.Queries;
import com.collabtech.platform.identity.domain.repositories.AccountRepository;
import com.collabtech.platform.identity.domain.model.valueobjects.*;
import java.time.Clock;
import java.util.*;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component @Profile("!skeleton") @Order(30)
@ConditionalOnProperty(name="collabpro.demo-seed.enabled",havingValue="true")
public class IncidentDemoSeed implements ApplicationRunner {
    private final IncidentService service;
    private final AccountRepository accounts;
    private final AgreementRepository agreements;
    private final CollaborationRepository collaborations;
    private final Queries queries;
    private final UnitOfWork work;
    private final Clock clock;
    public IncidentDemoSeed(IncidentService service,AccountRepository accounts,AgreementRepository agreements,
                            CollaborationRepository collaborations,Queries queries,UnitOfWork work,Clock clock) {
        this.service=service;this.accounts=accounts;this.agreements=agreements;this.collaborations=collaborations;
        this.queries=queries;this.work=work;this.clock=clock;
    }
    public void run(ApplicationArguments args) {
        work.execute(()->{
            var a=agreements.byApplication(UUID.fromString("30000000-0000-4000-8000-000000000004"),false);
            if(a.isEmpty())return null;
            var existing=collaborations.byAgreement(a.get().id());if(existing.isEmpty())return null;
            var c=collaborations.byId(existing.get().id(),true).orElseThrow();
            if(queries.list(c.id(),null,new com.collabtech.platform.shared.application.pagination.PageRequest(0,1)).total()!=0
                    || !new IncidentPolicy().canOpen(c,clock.instant()))return null;
            var brand=accounts.findByEmail(new EmailAddress("demo.brand@collabpro.app"));
            var creator=accounts.findByEmail(new EmailAddress("demo.creator2@collabpro.app"));
            if(brand.isEmpty()||creator.isEmpty()||brand.get().status()!=AccountStatus.ACTIVE||creator.get().status()!=AccountStatus.ACTIVE
                    ||brand.get().brandProfile()==null||creator.get().creatorProfile()==null
                    ||!brand.get().brandProfile().id().value().equals(c.snapshot().brandId())
                    ||!creator.get().creatorProfile().id().value().equals(c.snapshot().creatorId()))return null;
            var opened=service.open(creator.get().id().value(),c.id(),new IncidentService.Open(
                    UUID.fromString("80000000-0000-4000-8000-000000000001"),Incident.Category.CONDITIONS,
                    "Demostración: aclarar el mensaje del producto antes de revisar la entrega.",null,List.of())).incident();
            var proposal=service.propose(brand.get().id().value(),opened.id(),new IncidentService.Propose(
                    UUID.fromString("80000000-0000-4000-8000-000000000002"),opened.version(),
                    new Incident.Terms(Incident.Outcome.KEEP_AGREEMENT,"Ambas partes mantienen las condiciones y continúan la revisión.",
                            List.of(),null,null,null,null))).incident();
            service.decide(creator.get().id().value(),opened.id(),proposal.currentResolution().id(),new IncidentService.Decide(
                    UUID.fromString("80000000-0000-4000-8000-000000000003"),proposal.version(),Incident.Decision.ACCEPT,null));
            return null;
        });
    }
}
