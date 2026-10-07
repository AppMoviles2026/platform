package com.collabtech.platform.identity.application.commands;

import com.collabtech.platform.identity.domain.model.valueobjects.AccountId;
import com.collabtech.platform.identity.domain.model.valueobjects.SocialPlatform;
import com.collabtech.platform.identity.application.projections.IdentityViews;
import com.collabtech.platform.shared.application.cqrs.Command;
import com.collabtech.platform.identity.application.ports.AuthorizationClient;

public record StartSocialAuthorizationCommand(AccountId accountId, SocialPlatform platform, AuthorizationClient client) implements Command<IdentityViews.AuthorizationView> {
    public StartSocialAuthorizationCommand(AccountId accountId, SocialPlatform platform) { this(accountId, platform, AuthorizationClient.API); }
    public StartSocialAuthorizationCommand { java.util.Objects.requireNonNull(client); }
}
