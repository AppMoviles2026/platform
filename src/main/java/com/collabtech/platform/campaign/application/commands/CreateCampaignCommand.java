package com.collabtech.platform.campaign.application.commands;

import com.collabtech.platform.campaign.domain.model.valueobjects.CampaignId;
import com.collabtech.platform.campaign.domain.model.valueobjects.BrandId;

import com.collabtech.platform.shared.application.cqrs.Command;

public record CreateCampaignCommand(BrandId brandId, String title, String objective, String description, String category, String targetAudience, String location) implements Command<CampaignId> {}
