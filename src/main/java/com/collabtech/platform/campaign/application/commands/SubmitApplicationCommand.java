package com.collabtech.platform.campaign.application.commands;

import com.collabtech.platform.campaign.domain.model.valueobjects.CampaignId;
import com.collabtech.platform.campaign.domain.model.valueobjects.ApplicationId;
import com.collabtech.platform.campaign.domain.model.valueobjects.CreatorId;

import com.collabtech.platform.shared.application.cqrs.Command;

public record SubmitApplicationCommand(CreatorId creatorId, CampaignId campaignId, String message) implements Command<ApplicationId> {}
