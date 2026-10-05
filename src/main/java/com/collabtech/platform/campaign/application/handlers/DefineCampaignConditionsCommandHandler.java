package com.collabtech.platform.campaign.application.handlers;

import com.collabtech.platform.campaign.application.commands.DefineCampaignConditionsCommand;
import com.collabtech.platform.campaign.domain.model.aggregates.Campaign;
import com.collabtech.platform.campaign.domain.model.entities.CampaignRequirement;
import com.collabtech.platform.campaign.domain.model.entities.DeliverableSpecification;
import com.collabtech.platform.campaign.domain.model.valueobjects.RequirementId;
import com.collabtech.platform.campaign.domain.model.valueobjects.SpecificationId;
import com.collabtech.platform.campaign.domain.repositories.CampaignRepository;
import com.collabtech.platform.campaign.domain.exceptions.CampaignFailure;
import java.time.Clock;

public final class DefineCampaignConditionsCommandHandler implements com.collabtech.platform.shared.application.cqrs.CommandHandler<DefineCampaignConditionsCommand, com.collabtech.platform.campaign.domain.model.valueobjects.CampaignId> {
    private final CampaignRepository campaigns; private final Clock clock;
    public DefineCampaignConditionsCommandHandler(CampaignRepository campaigns, Clock clock) { this.campaigns = campaigns; this.clock = clock; }
    public com.collabtech.platform.campaign.domain.model.valueobjects.CampaignId handle(DefineCampaignConditionsCommand command) {
        var campaign = campaigns.findById(command.campaignId()).orElseThrow(() -> new CampaignFailure(CampaignFailure.Code.CAMPAIGN_NOT_FOUND));
        if (!campaign.brandId().equals(command.brandId())) throw new CampaignFailure(CampaignFailure.Code.FORBIDDEN);
        var requirements = command.requirements().stream().map(row -> new CampaignRequirement(new RequirementId(row.id()), row.description(), row.mandatory())).toList();
        var deliverables = command.deliverables().stream().map(row -> new DeliverableSpecification(new SpecificationId(row.id()), row.contentType(), row.description(), row.quantity(), row.deadline())).toList();
        campaign.defineConditions(requirements, deliverables, command.applicationDeadline(), command.compensation(), clock.instant());
        return campaigns.save(campaign).id();
    }
}
