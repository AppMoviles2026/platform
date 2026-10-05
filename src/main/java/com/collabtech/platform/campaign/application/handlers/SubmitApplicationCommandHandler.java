package com.collabtech.platform.campaign.application.handlers;
import com.collabtech.platform.campaign.application.commands.SubmitApplicationCommand;
import com.collabtech.platform.campaign.domain.repositories.*;
import com.collabtech.platform.campaign.domain.model.aggregates.Application;
import com.collabtech.platform.campaign.domain.model.valueobjects.*;
import com.collabtech.platform.campaign.domain.services.ApplicationEligibilityService;
import com.collabtech.platform.campaign.domain.services.ApplicationEligibilityService.EligibilityFacts;
import com.collabtech.platform.campaign.domain.exceptions.*;
import com.collabtech.platform.shared.application.cqrs.CommandHandler;
import java.time.Clock;
public final class SubmitApplicationCommandHandler implements CommandHandler<SubmitApplicationCommand, ApplicationId> {
    private final CampaignRepository campaigns; private final ApplicationRepository applications;
    private final ApplicationEligibilityService eligibility; private final Clock clock;
    public SubmitApplicationCommandHandler(CampaignRepository campaigns,ApplicationRepository applications,ApplicationEligibilityService eligibility,Clock clock) {
        this.campaigns=campaigns; this.applications=applications; this.eligibility=eligibility; this.clock=clock;
    }
    public ApplicationId handle(SubmitApplicationCommand command) {
        var campaign=campaigns.findByIdForUpdate(command.campaignId()).orElseThrow(() -> new CampaignFailure(CampaignFailure.Code.CAMPAIGN_NOT_FOUND));
        if (applications.existsByCampaignAndCreator(campaign.id(),command.creatorId())) throw new CampaignFailure(CampaignFailure.Code.APPLICATION_ALREADY_EXISTS);
        if (!campaign.acceptsApplications(clock.instant())) throw new CampaignFailure(CampaignFailure.Code.CAMPAIGN_NOT_ACCEPTING_APPLICATIONS);
        for (var id:command.confirmations()) {
            if (campaign.requirements().stream().noneMatch(r -> r.id().equals(id) && r.rule().type()==RequirementRule.Type.MANUAL_CONFIRMATION))
                throw new CampaignFailure(CampaignFailure.Code.INVALID_CONFIRMATION);
        }
        var f=command.facts();
        var result=eligibility.evaluate(campaign,new EligibilityFacts(f.niche(),f.location(),f.audienceDescription(),f.authorizedPlatforms(),command.confirmations()));
        if (!result.eligible()) throw new UnmetRequirementsException(campaign.requirements().stream().filter(r -> result.unmetRequirements().contains(r.id())).toList());
        return applications.save(Application.submit(campaign,command.creatorId(),command.message(),command.confirmations(),clock.instant())).id();
    }
}
