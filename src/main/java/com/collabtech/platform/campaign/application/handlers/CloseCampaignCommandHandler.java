package com.collabtech.platform.campaign.application.handlers;

import com.collabtech.platform.campaign.application.commands.CloseCampaignCommand;
import com.collabtech.platform.campaign.domain.model.valueobjects.CampaignId;
import com.collabtech.platform.campaign.domain.model.valueobjects.CampaignStatus;
import com.collabtech.platform.campaign.domain.repositories.CampaignRepository;
import com.collabtech.platform.campaign.domain.exceptions.CampaignFailure;
import com.collabtech.platform.shared.application.cqrs.CommandHandler;
import java.time.Clock;

public final class CloseCampaignCommandHandler implements CommandHandler<CloseCampaignCommand, CampaignId> {
    private final CampaignRepository campaigns;
    private final Clock clock;
    public CloseCampaignCommandHandler(CampaignRepository campaigns, Clock clock) { this.campaigns=campaigns; this.clock=clock; }
    public CampaignId handle(CloseCampaignCommand command) {
        var campaign=campaigns.findByIdForUpdate(command.campaignId())
                .orElseThrow(() -> new CampaignFailure(CampaignFailure.Code.CAMPAIGN_NOT_FOUND));
        if (!campaign.brandId().equals(command.brandId())) throw new CampaignFailure(CampaignFailure.Code.FORBIDDEN);
        if (campaign.status() != CampaignStatus.CLOSED) { campaign.close(clock.instant()); campaigns.save(campaign); }
        return campaign.id();
    }
}
