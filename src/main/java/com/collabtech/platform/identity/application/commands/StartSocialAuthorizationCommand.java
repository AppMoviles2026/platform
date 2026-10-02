package com.collabtech.platform.identity.application.commands;

import com.collabtech.platform.identity.domain.model.valueobjects.AccountId;
import com.collabtech.platform.identity.domain.model.valueobjects.SocialPlatform;
import com.collabtech.platform.identity.application.projections.IdentityViews;
import com.collabtech.platform.shared.application.cqrs.Command;

public record StartSocialAuthorizationCommand(AccountId accountId, SocialPlatform platform) implements Command<IdentityViews.AuthorizationView> {}
