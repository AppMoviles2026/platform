package com.collabtech.platform.campaign.application.services;

import com.collabtech.platform.campaign.application.commands.*;
import com.collabtech.platform.campaign.application.handlers.*;
import com.collabtech.platform.campaign.application.ports.*;
import com.collabtech.platform.campaign.application.projections.CampaignViews;
import com.collabtech.platform.campaign.application.queries.GetBrandCampaignsQuery;
import com.collabtech.platform.campaign.application.queries.GetPublishedCampaignsQuery;
import com.collabtech.platform.campaign.domain.model.aggregates.Campaign;
import com.collabtech.platform.campaign.domain.model.valueobjects.*;
import com.collabtech.platform.campaign.domain.repositories.CampaignRepository;
import com.collabtech.platform.campaign.domain.exceptions.CampaignFailure;
import com.collabtech.platform.shared.application.pagination.*;
import java.time.Clock;
import java.util.UUID;
import java.util.List;
import java.time.Instant;

/** US15/16 entry service; no placeholder handlers for unrequested stories. */
public final class CampaignPreparationService {
    private final CampaignActorGateway actors; private final CampaignUnitOfWork transactions;
    private final CampaignRepository campaigns; private final CampaignCatalog catalog; private final Clock clock;
    private final CreateCampaignCommandHandler create;
    private final DefineCampaignConditionsCommandHandler conditions;
    private final PublishCampaignCommandHandler publish;
    public CampaignPreparationService(CampaignActorGateway actors, CampaignUnitOfWork transactions,
            CampaignRepository campaigns, CampaignCatalog catalog, Clock clock) {
        this.actors = actors; this.transactions = transactions; this.campaigns = campaigns; this.catalog = catalog; this.clock = clock;
        create = new CreateCampaignCommandHandler(catalog);
        conditions = new DefineCampaignConditionsCommandHandler(campaigns, clock);
        publish = new PublishCampaignCommandHandler(campaigns, clock);
    }
    public CampaignViews.Details create(UUID accountId, String title, String objective, String description, String category, String audience, String location) {
        return transactions.execute(() -> {
            var brand = actors.getActiveBrand(accountId);
            var id = create.handle(new CreateCampaignCommand(brand.id(), brand.name(), title, objective, description, category, audience,
                    location == null ? brand.location() : location));
            return details(campaigns.findById(id).orElseThrow());
        });
    }
    public CampaignViews.Details define(UUID accountId, CampaignId id, List<CampaignViews.Requirement> requirements,
            List<CampaignViews.Deliverable> deliverables, Instant deadline, CompensationTerms compensation) {
        return transactions.execute(() -> {
            var saved = conditions.handle(new DefineCampaignConditionsCommand(actors.getActiveBrand(accountId).id(), id, requirements, deliverables, deadline, compensation));
            return details(campaigns.findById(saved).orElseThrow());
        });
    }
    public CampaignViews.Details publish(UUID accountId, CampaignId id) {
        return transactions.execute(() -> {
            var saved = publish.handle(new PublishCampaignCommand(actors.getActiveBrand(accountId).id(), id));
            return details(campaigns.findById(saved).orElseThrow());
        });
    }
    public CampaignViews.Details own(UUID accountId, CampaignId id) {
        return transactions.execute(() -> {
            var brand = actors.getActiveBrand(accountId);
            var campaign = campaigns.findById(id).orElseThrow(() -> new CampaignFailure(CampaignFailure.Code.CAMPAIGN_NOT_FOUND));
            if (!brand.id().equals(campaign.brandId())) throw new CampaignFailure(CampaignFailure.Code.FORBIDDEN);
            return details(campaign);
        });
    }
    public PageResult<CampaignViews.Summary> mine(UUID accountId, PageRequest page) {
        return transactions.execute(() -> new GetBrandCampaignsQueryHandler(catalog)
                .handle(new GetBrandCampaignsQuery(actors.getActiveBrand(accountId).id(), page)));
    }
    public PageResult<CampaignViews.Summary> published(UUID accountId, PageRequest page) {
        return transactions.execute(() -> { actors.requireActiveCreator(accountId);
            return new GetPublishedCampaignsQueryHandler(catalog).handle(new GetPublishedCampaignsQuery(page)); });
    }
    private CampaignViews.Summary summary(Campaign campaign) {
        return new CampaignViews.Summary(campaign.id().value(), campaign.brandId().value(), catalog.brandName(campaign.id()), campaign.title(),
                campaign.category(), campaign.location(), campaign.compensationTerms(), campaign.applicationDeadline(), campaign.status().name());
    }
    private CampaignViews.Details details(Campaign campaign) {
        return new CampaignViews.Details(summary(campaign), campaign.objective(), campaign.description(), campaign.targetAudience(),
                campaign.requirements().stream().map(row -> new CampaignViews.Requirement(row.id().value(), row.description(), row.mandatory())).toList(),
                campaign.deliverables().stream().map(row -> new CampaignViews.Deliverable(row.id().value(), row.contentType(), row.description(), row.quantity(), row.deadline())).toList(),
                campaign.status() == CampaignStatus.OPEN && campaign.applicationDeadline().isAfter(clock.instant()), campaign.publicationDate());
    }
}
