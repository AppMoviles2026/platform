package com.collabtech.platform.campaign.application.handlers;
import com.collabtech.platform.campaign.application.commands.CancelApplicationCommand;
import com.collabtech.platform.campaign.domain.repositories.ApplicationRepository;
import com.collabtech.platform.campaign.domain.model.valueobjects.ApplicationId;
import com.collabtech.platform.campaign.domain.exceptions.CampaignFailure;
import com.collabtech.platform.shared.application.cqrs.CommandHandler;
public final class CancelApplicationCommandHandler implements CommandHandler<CancelApplicationCommand,ApplicationId> {
    private final ApplicationRepository repository;
    public CancelApplicationCommandHandler(ApplicationRepository repository) { this.repository=repository; }
    public ApplicationId handle(CancelApplicationCommand command) {
        var application=repository.findById(command.applicationId()).orElseThrow(() -> new CampaignFailure(CampaignFailure.Code.APPLICATION_NOT_FOUND));
        if (!application.creatorId().equals(command.creatorId())) throw new CampaignFailure(CampaignFailure.Code.FORBIDDEN);
        if (command.expectedVersion()!=null && application.version()!=command.expectedVersion()) throw new CampaignFailure(CampaignFailure.Code.CONCURRENT_UPDATE);
        application.cancel(); return repository.save(application).id();
    }
}
