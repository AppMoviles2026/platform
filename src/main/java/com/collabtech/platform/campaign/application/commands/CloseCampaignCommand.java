package com.collabtech.platform.campaign.application.commands;

import com.collabtech.platform.campaign.domain.model.valueobjects.BrandId;
import com.collabtech.platform.campaign.domain.model.valueobjects.CampaignId;
import com.collabtech.platform.shared.application.cqrs.Command;

public record CloseCampaignCommand(BrandId brandId, CampaignId campaignId) implements Command<CampaignId> {}
