package com.collabtech.platform.identity.application.commands;

import com.collabtech.platform.identity.domain.model.valueobjects.AccountId;
import com.collabtech.platform.identity.application.projections.IdentityViews;
import com.collabtech.platform.shared.application.cqrs.Command;

public record UpdateCreatorProfileCommand(AccountId accountId, String displayName, String biography, String niche, String audienceDescription, String location) implements Command<IdentityViews.CreatorProfileView> {}
