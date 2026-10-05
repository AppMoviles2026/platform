package com.collabtech.platform.campaign.application.handlers;
import com.collabtech.platform.campaign.application.commands.UpdateApplicationCommand;
import com.collabtech.platform.campaign.domain.repositories.ApplicationRepository;
import com.collabtech.platform.campaign.domain.model.valueobjects.ApplicationId;
import com.collabtech.platform.campaign.domain.exceptions.CampaignFailure;
import com.collabtech.platform.shared.application.cqrs.CommandHandler;
public final class UpdateApplicationCommandHandler implements CommandHandler<UpdateApplicationCommand,ApplicationId> {
    private final ApplicationRepository repository;
    public UpdateApplicationCommandHandler(ApplicationRepository repository) { this.repository=repository; }
    public ApplicationId handle(UpdateApplicationCommand command) {
        var application=repository.findById(command.applicationId()).orElseThrow(() -> new CampaignFailure(CampaignFailure.Code.APPLICATION_NOT_FOUND));
        if (!application.creatorId().equals(command.creatorId())) throw new CampaignFailure(CampaignFailure.Code.FORBIDDEN);
        if (command.expectedVersion()!=null && application.version()!=command.expectedVersion()) throw new CampaignFailure(CampaignFailure.Code.CONCURRENT_UPDATE);
        application.updateMessage(command.message()); return repository.save(application).id();
    }
}
