package com.collabtech.platform.shared.infrastructure.configuration;

import com.collabtech.platform.collaboration.application.services.CollaborationService;
import com.collabtech.platform.collaboration.application.ports.CollaborationPorts.UnitOfWork;
import com.collabtech.platform.collaboration.domain.model.Agreement;
import com.collabtech.platform.collaboration.domain.repositories.AgreementRepository;
import com.collabtech.platform.collaboration.domain.exceptions.CollaborationFailure;
import com.collabtech.platform.identity.domain.repositories.AccountRepository;
import com.collabtech.platform.identity.domain.model.valueobjects.EmailAddress;
import com.collabtech.platform.identity.domain.model.valueobjects.AccountStatus;
import java.util.UUID;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Explicit demo data only: creates one acknowledged demo agreement and never changes an existing agreement. */
@Component @Profile("!skeleton") @Order(10)
@ConditionalOnProperty(name="collabpro.demo-seed.enabled",havingValue="true")
public class CollaborationDemoSeed implements ApplicationRunner {
    private final CollaborationService service;private final AccountRepository accounts;private final AgreementRepository agreements;private final UnitOfWork work;
    public CollaborationDemoSeed(CollaborationService service,AccountRepository accounts,AgreementRepository agreements,UnitOfWork work){this.service=service;this.accounts=accounts;this.agreements=agreements;this.work=work;}
    public void run(ApplicationArguments args){
        UUID application=UUID.fromString("30000000-0000-4000-8000-000000000004");
        try { work.execute(() -> {
        if(agreements.byApplication(application,false).isPresent())return null;
        var brand=accounts.findByEmail(new EmailAddress("demo.brand@collabpro.app"));var creator=accounts.findByEmail(new EmailAddress("demo.creator2@collabpro.app"));
        if(brand.isEmpty()||creator.isEmpty()||brand.get().status()!=AccountStatus.ACTIVE||creator.get().status()!=AccountStatus.ACTIVE)return null;
            var proposal=service.propose(brand.get().id().value(),application);
            if(proposal.status()!=Agreement.Status.PENDING)return null;
            service.decide(brand.get().id().value(),proposal.id(),proposal.snapshot().termsVersion(),Agreement.Decision.ACCEPT);
            service.decide(creator.get().id().value(),proposal.id(),proposal.snapshot().termsVersion(),Agreement.Decision.ACCEPT);
            return null;
        }); }catch(CollaborationFailure failure){
            if(failure.code()!=CollaborationFailure.Code.TERMS_EXPIRED&&failure.code()!=CollaborationFailure.Code.SOURCE_NOT_SELECTED)throw failure;
        }
    }
}
