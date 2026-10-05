package com.collabtech.platform.campaign.infrastructure.configuration;

import com.collabtech.platform.campaign.application.ports.*;
import com.collabtech.platform.campaign.application.services.CampaignPreparationService;
import com.collabtech.platform.campaign.domain.repositories.CampaignRepository;
import java.time.Clock;
import java.util.function.Supplier;
import org.springframework.context.annotation.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration(proxyBeanMethods=false) @Profile("!skeleton")
public class CampaignConfiguration {
    @Bean CampaignUnitOfWork campaignUnitOfWork(PlatformTransactionManager manager) {
        var template = new TransactionTemplate(manager);
        return new CampaignUnitOfWork() { public <T> T execute(Supplier<T> work) { return template.execute(status -> work.get()); } };
    }
    @Bean CampaignPreparationService campaignPreparationService(CampaignActorGateway actors, CampaignUnitOfWork transactions,
            CampaignRepository campaigns, CampaignCatalog catalog, Clock clock) {
        return new CampaignPreparationService(actors, transactions, campaigns, catalog, clock);
    }
}
