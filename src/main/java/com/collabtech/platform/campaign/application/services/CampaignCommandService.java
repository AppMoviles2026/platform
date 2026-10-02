package com.collabtech.platform.campaign.application.services;

import com.collabtech.platform.campaign.application.commands.CreateCampaignCommand;
import com.collabtech.platform.campaign.application.commands.DefineCampaignConditionsCommand;
import com.collabtech.platform.campaign.application.commands.PublishCampaignCommand;
import com.collabtech.platform.campaign.application.commands.SubmitApplicationCommand;
import com.collabtech.platform.campaign.application.commands.UpdateApplicationCommand;
import com.collabtech.platform.campaign.application.commands.CancelApplicationCommand;

import com.collabtech.platform.campaign.domain.model.valueobjects.CampaignId;
import com.collabtech.platform.campaign.domain.model.valueobjects.ApplicationId;

/** Inbound use-case port only. Future handlers implement this contract. */
public interface CampaignCommandService {
    CampaignId handle(CreateCampaignCommand command);
    CampaignId handle(DefineCampaignConditionsCommand command);
    CampaignId handle(PublishCampaignCommand command);
    ApplicationId handle(SubmitApplicationCommand command);
    ApplicationId handle(UpdateApplicationCommand command);
    ApplicationId handle(CancelApplicationCommand command);
}
