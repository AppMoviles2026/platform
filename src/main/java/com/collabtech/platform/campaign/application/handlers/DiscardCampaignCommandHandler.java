package com.collabtech.platform.campaign.application.handlers;

import com.collabtech.platform.campaign.application.commands.DiscardCampaignCommand;
import com.collabtech.platform.campaign.domain.repositories.CampaignRepository;
import com.collabtech.platform.campaign.domain.exceptions.CampaignFailure;
import com.collabtech.platform.shared.application.cqrs.CommandHandler;

public final class DiscardCampaignCommandHandler implements CommandHandler<DiscardCampaignCommand, Void> {
    private final CampaignRepository campaigns;
    public DiscardCampaignCommandHandler(CampaignRepository campaigns) { this.campaigns=campaigns; }
    public Void handle(DiscardCampaignCommand command) {
        var campaign=campaigns.findByIdForUpdate(command.campaignId())
                .orElseThrow(() -> new CampaignFailure(CampaignFailure.Code.CAMPAIGN_NOT_FOUND));
        if (!campaign.brandId().equals(command.brandId())) throw new CampaignFailure(CampaignFailure.Code.FORBIDDEN);
        campaign.requireDiscardable(); campaigns.discard(campaign); return null;
    }
}
