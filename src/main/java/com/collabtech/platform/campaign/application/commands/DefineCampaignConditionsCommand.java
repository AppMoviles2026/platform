package com.collabtech.platform.campaign.application.commands;

import com.collabtech.platform.campaign.domain.model.valueobjects.CampaignId;
import com.collabtech.platform.campaign.domain.model.valueobjects.BrandId;
import com.collabtech.platform.campaign.domain.model.valueobjects.CompensationTerms;
import com.collabtech.platform.campaign.application.projections.CampaignViews;
import com.collabtech.platform.shared.application.cqrs.Command;
import java.time.Instant;
import java.util.List;

public record DefineCampaignConditionsCommand(BrandId brandId, CampaignId campaignId, List<CampaignViews.Requirement> requirements, List<CampaignViews.Deliverable> deliverables, Instant applicationDeadline, CompensationTerms compensation) implements Command<CampaignId> {
    public DefineCampaignConditionsCommand { requirements = List.copyOf(requirements); deliverables = List.copyOf(deliverables); }
}
