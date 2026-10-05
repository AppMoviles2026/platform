package com.collabtech.platform.campaign.application.handlers;

import com.collabtech.platform.campaign.application.commands.PublishCampaignCommand;
import com.collabtech.platform.campaign.domain.model.aggregates.Campaign;
import com.collabtech.platform.campaign.domain.repositories.CampaignRepository;
import com.collabtech.platform.campaign.domain.exceptions.CampaignFailure;
import java.time.Clock;

public final class PublishCampaignCommandHandler implements com.collabtech.platform.shared.application.cqrs.CommandHandler<PublishCampaignCommand, com.collabtech.platform.campaign.domain.model.valueobjects.CampaignId> {
    private final CampaignRepository campaigns; private final Clock clock;
    public PublishCampaignCommandHandler(CampaignRepository campaigns, Clock clock) { this.campaigns = campaigns; this.clock = clock; }
    public com.collabtech.platform.campaign.domain.model.valueobjects.CampaignId handle(PublishCampaignCommand command) {
        var campaign = campaigns.findById(command.campaignId()).orElseThrow(() -> new CampaignFailure(CampaignFailure.Code.CAMPAIGN_NOT_FOUND));
        if (!campaign.brandId().equals(command.brandId())) throw new CampaignFailure(CampaignFailure.Code.FORBIDDEN);
        campaign.publish(clock.instant()); return campaigns.save(campaign).id();
    }
}
