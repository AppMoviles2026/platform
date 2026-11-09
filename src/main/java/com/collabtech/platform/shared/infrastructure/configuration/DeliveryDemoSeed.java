package com.collabtech.platform.shared.infrastructure.configuration;

import com.collabtech.platform.collaboration.application.ports.CollaborationPorts.UnitOfWork;
import com.collabtech.platform.collaboration.application.services.DeliveryService;
import com.collabtech.platform.collaboration.domain.model.*;
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

/** Explicit demo only: rejected content followed by a pending corrected version, without altering agreed quantities. */
@Component
@Profile("!skeleton")
@Order(20)
@ConditionalOnProperty(name="collabpro.demo-seed.enabled",havingValue="true")
public class DeliveryDemoSeed implements ApplicationRunner {
    private final DeliveryService service;
    private final AccountRepository accounts;
    private final AgreementRepository agreements;
    private final CollaborationRepository collaborations;
    private final DeliverableRepository deliveries;
    private final UnitOfWork work;
    private final Clock clock;

    public DeliveryDemoSeed(DeliveryService service, AccountRepository accounts, AgreementRepository agreements,
                            CollaborationRepository collaborations, DeliverableRepository deliveries, UnitOfWork work, Clock clock) {
        this.service=service;this.accounts=accounts;this.agreements=agreements;this.collaborations=collaborations;
        this.deliveries=deliveries;this.work=work;this.clock=clock;
    }
    public void run(ApplicationArguments args) {
        work.execute(() -> {
            var agreement=agreements.byApplication(UUID.fromString("30000000-0000-4000-8000-000000000004"),false);
            if (agreement.isEmpty()) return null;
            var existing=collaborations.byAgreement(agreement.get().id());
            if (existing.isEmpty()) return null;
            var c=collaborations.byId(existing.get().id(),true).orElseThrow();
            if (!deliveries.forCollaboration(c.id()).isEmpty() || c.status()==Collaboration.Status.COMPLETED
                    || c.status()==Collaboration.Status.CANCELLED || c.status()==Collaboration.Status.DISPUTED) return null;
            var brand=accounts.findByEmail(new EmailAddress("demo.brand@collabpro.app"));
            var creator=accounts.findByEmail(new EmailAddress("demo.creator2@collabpro.app"));
            if (brand.isEmpty() || creator.isEmpty() || brand.get().status()!=AccountStatus.ACTIVE || creator.get().status()!=AccountStatus.ACTIVE) return null;
            var obligation=c.obligations().stream().filter(o->o.quantity()>=2).findFirst().orElse(c.obligations().getFirst());
            var first=service.submit(creator.get().id().value(),c.id(),new DeliveryService.Submit(
                    UUID.fromString("50000000-0000-4000-8000-000000000001"),obligation.id(),content("primera-pieza")));
            var rejected=service.validate(brand.get().id().value(),first.deliverable().id(),new DeliveryService.Validation(
                    UUID.fromString("50000000-0000-4000-8000-000000000002"),first.affectedSubmissionId(),first.deliverable().version(),
                    "Demostración: incluir el origen del café en el mensaje de la publicación.",clock.instant().plusSeconds(3*86400)),Deliverable.Decision.REJECTED);
            if (obligation.quantity()>=2) {
                service.submit(creator.get().id().value(),c.id(),new DeliveryService.Submit(
                        UUID.fromString("50000000-0000-4000-8000-000000000003"),obligation.id(),content("segunda-pieza")));
            } else {
                service.correct(creator.get().id().value(),first.deliverable().id(),new DeliveryService.Correction(
                        UUID.fromString("50000000-0000-4000-8000-000000000003"),rejected.deliverable().version(),content("version-corregida")));
            }
            return null;
        });
    }
    private DeliveryContent content(String key) {
        return new DeliveryContent(new DeliveryContent.Reference("https://example.com/collabpro-demo/"+key,null),
                List.of(new DeliveryContent.Reference("https://example.com/collabpro-demo/evidencia-"+key,null)),
                "Datos de demostración. Los enlaces example.com son ilustrativos, no publicaciones verificadas.");
    }
}
