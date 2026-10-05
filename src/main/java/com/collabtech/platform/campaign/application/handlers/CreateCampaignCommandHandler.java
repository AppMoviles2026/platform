package com.collabtech.platform.campaign.application.handlers;

import com.collabtech.platform.campaign.application.commands.CreateCampaignCommand;
import com.collabtech.platform.campaign.application.ports.CampaignCatalog;
import com.collabtech.platform.campaign.domain.model.aggregates.Campaign;
import com.collabtech.platform.campaign.domain.model.valueobjects.CampaignId;

/** Authorization and transaction surround this pure command handler in the application service. */
public final class CreateCampaignCommandHandler implements com.collabtech.platform.shared.application.cqrs.CommandHandler<CreateCampaignCommand, CampaignId> {
    private final CampaignCatalog catalog;
    public CreateCampaignCommandHandler(CampaignCatalog catalog) { this.catalog = catalog; }
    public CampaignId handle(CreateCampaignCommand command) {
        var campaign = Campaign.draft(command.brandId(), command.title(), command.objective(), command.description(),
                command.category(), command.targetAudience(), command.location());
        catalog.saveNew(campaign, command.brandName()); return campaign.id();
    }
}
