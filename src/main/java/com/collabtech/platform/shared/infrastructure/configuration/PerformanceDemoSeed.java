package com.collabtech.platform.shared.infrastructure.configuration;

import com.collabtech.platform.performance.application.services.PerformanceService;
import com.collabtech.platform.performance.application.ports.PerformancePorts.*;
import com.collabtech.platform.performance.domain.model.PerformanceModels.*;
import com.collabtech.platform.performance.domain.repositories.PerformanceRepository;
import com.collabtech.platform.collaboration.domain.repositories.*;
import com.collabtech.platform.identity.domain.repositories.AccountRepository;
import com.collabtech.platform.identity.domain.model.valueobjects.*;
import java.time.Clock;
import java.util.*;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Explicit manual demonstration only; never fabricates automated provider data or credentials. */
@Component @Profile("!skeleton") @Order(40)
@ConditionalOnProperty(name="collabpro.demo-seed.enabled",havingValue="true")
public class PerformanceDemoSeed implements ApplicationRunner {
    private final PerformanceService service;private final AccountRepository accounts;private final AgreementRepository agreements;
    private final CollaborationRepository collaborations;private final PerformanceRepository reports;private final Queries queries;
    private final UnitOfWork work;private final Clock clock;
    public PerformanceDemoSeed(PerformanceService service,AccountRepository accounts,AgreementRepository agreements,
            CollaborationRepository collaborations,PerformanceRepository reports,Queries queries,UnitOfWork work,Clock clock){
        this.service=service;this.accounts=accounts;this.agreements=agreements;this.collaborations=collaborations;
        this.reports=reports;this.queries=queries;this.work=work;this.clock=clock;
    }
    public void run(ApplicationArguments args){
        work.execute(()->{
            var agreement=agreements.byApplication(UUID.fromString("30000000-0000-4000-8000-000000000004"),false);
            if(agreement.isEmpty())return null;
            var existing=collaborations.byAgreement(agreement.get().id());if(existing.isEmpty())return null;
            var c=collaborations.byId(existing.get().id(),true).orElseThrow();
            if(c.cancelledAt()!=null||c.status().name().equals("CANCELLED"))return null;
            var report=reports.report(c.id(),true);
            if(report.isPresent()&&queries.evidenceCount(report.get().id())>0)return null;
            var creator=accounts.findByEmail(new EmailAddress("demo.creator2@collabpro.app"));
            if(creator.isEmpty()||creator.get().status()!=AccountStatus.ACTIVE||creator.get().creatorProfile()==null
                    ||!creator.get().creatorProfile().id().value().equals(c.snapshot().creatorId()))return null;
            var observed=clock.instant().minusSeconds(60);
            service.registerDemonstration(creator.get().id().value(),c.id(),new PerformanceService.EvidenceInput(
                    UUID.fromString("90000000-0000-4000-8000-000000000001"),null,
                    "Demostración: valores declarados de ejemplo, no obtenidos ni verificados por una red social.",
                    List.of(new Reference("https://example.com/collabpro-demo/estadisticas-manuales",null)),
                    Map.of(MetricType.VIEWS,1200L,MetricType.LIKES,85L),observed,new Period(observed.minusSeconds(86400),observed)));
            return null;
        });
    }
}
