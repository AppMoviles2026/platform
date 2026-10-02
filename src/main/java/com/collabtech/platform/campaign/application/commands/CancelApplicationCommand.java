package com.collabtech.platform.campaign.application.commands;

import com.collabtech.platform.campaign.domain.model.valueobjects.ApplicationId;
import com.collabtech.platform.campaign.domain.model.valueobjects.CreatorId;

import com.collabtech.platform.shared.application.cqrs.Command;

public record CancelApplicationCommand(CreatorId creatorId, ApplicationId applicationId) implements Command<ApplicationId> {}
