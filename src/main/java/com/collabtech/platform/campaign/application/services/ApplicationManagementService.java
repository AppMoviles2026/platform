package com.collabtech.platform.campaign.application.services;
import com.collabtech.platform.campaign.application.commands.*;
import com.collabtech.platform.campaign.application.handlers.*;
import com.collabtech.platform.campaign.application.queries.*;
import com.collabtech.platform.campaign.application.ports.*;
import com.collabtech.platform.campaign.application.projections.CampaignViews;
import com.collabtech.platform.campaign.domain.model.valueobjects.*;
import com.collabtech.platform.campaign.domain.repositories.*;
import com.collabtech.platform.campaign.domain.services.ApplicationEligibilityService;
import com.collabtech.platform.shared.application.pagination.*;
import java.time.Clock;
import java.util.Set;
import java.util.UUID;
public final class ApplicationManagementService {
    private final CampaignActorGateway actors; private final CampaignUnitOfWork transactions;
    private final ApplicationRepository applications; private final ApplicationReadRepository reads;
    private final SubmitApplicationCommandHandler submit;
    public ApplicationManagementService(CampaignActorGateway actors,CampaignUnitOfWork transactions,CampaignRepository campaigns,
            ApplicationRepository applications,ApplicationReadRepository reads,ApplicationEligibilityService eligibility,Clock clock) {
        this.actors=actors; this.transactions=transactions; this.applications=applications; this.reads=reads;
        this.submit=new SubmitApplicationCommandHandler(campaigns,applications,eligibility,clock);
    }
    public CampaignViews.ApplicationView submit(UUID account,CampaignId campaign,String message,Set<UUID> confirmations) {
        return transactions.execute(() -> {
            var creator=actors.getActiveCreator(account);
            var id=submit.handle(new SubmitApplicationCommand(creator.id(),campaign,message,confirmations.stream().map(RequirementId::new).collect(java.util.stream.Collectors.toSet()),creator.facts()));
            return detail(creator.id(),id);
        });
    }
    public CampaignViews.ApplicationView update(UUID account,ApplicationId id,String message,Long version) {
        return transactions.execute(() -> {
            var creator=actors.getActiveCreator(account).id();
            new UpdateApplicationCommandHandler(applications).handle(new UpdateApplicationCommand(creator,id,message,version)); return detail(creator,id);
        });
    }
    public CampaignViews.ApplicationView cancel(UUID account,ApplicationId id,Long version) {
        return transactions.execute(() -> {
            var creator=actors.getActiveCreator(account).id();
            new CancelApplicationCommandHandler(applications).handle(new CancelApplicationCommand(creator,id,version)); return detail(creator,id);
        });
    }
    public CampaignViews.ApplicationView get(UUID account,ApplicationId id) { return transactions.execute(() -> detail(actors.getActiveCreator(account).id(),id)); }
    public PageResult<CampaignViews.ApplicationView> mine(UUID account,PageRequest page) {
        return transactions.execute(() -> new GetCreatorApplicationsQueryHandler(reads).handle(new GetCreatorApplicationsQuery(actors.getActiveCreator(account).id(),page)));
    }
    private CampaignViews.ApplicationView detail(CreatorId creator,ApplicationId id) { return new GetApplicationDetailsQueryHandler(reads).handle(new GetApplicationDetailsQuery(creator,id)); }
}
