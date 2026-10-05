package com.collabtech.platform.identity.application.commands;

import com.collabtech.platform.identity.application.projections.IdentityViews;
import com.collabtech.platform.shared.application.cqrs.Command;
import com.collabtech.platform.identity.domain.model.valueobjects.SocialPlatform;

public record CompleteSocialAuthorizationCommand(SocialPlatform platform, String state, String authorizationCode, String providerError) implements Command<IdentityViews.SocialAccountView> {
    @Override public String toString() { return "CompleteSocialAuthorizationCommand[sensitive fields redacted]"; }
}
