package com.collabtech.platform.campaign.application.commands;

import com.collabtech.platform.campaign.domain.model.valueobjects.ApplicationId;
import com.collabtech.platform.campaign.domain.model.valueobjects.CreatorId;

import com.collabtech.platform.shared.application.cqrs.Command;

public record UpdateApplicationCommand(CreatorId creatorId, ApplicationId applicationId, String message, Long expectedVersion) implements Command<ApplicationId> {
    public UpdateApplicationCommand(CreatorId creatorId, ApplicationId applicationId, String message) { this(creatorId, applicationId, message, null); }
}
