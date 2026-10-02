package com.collabtech.platform.identity.application.services;

import com.collabtech.platform.identity.application.commands.RegisterBrandCommand;
import com.collabtech.platform.identity.application.commands.RegisterCreatorCommand;
import com.collabtech.platform.identity.application.commands.AuthenticateAccountCommand;
import com.collabtech.platform.identity.application.commands.RecoverAccountCommand;
import com.collabtech.platform.identity.application.commands.UpdateCreatorProfileCommand;
import com.collabtech.platform.identity.application.commands.StartSocialAuthorizationCommand;
import com.collabtech.platform.identity.application.commands.CompleteSocialAuthorizationCommand;
import com.collabtech.platform.identity.application.projections.IdentityViews;

/** Inbound use-case port only. Future handlers implement this contract. */
public interface IdentityCommandService {
    IdentityViews.AccountView handle(RegisterBrandCommand command);
    IdentityViews.AccountView handle(RegisterCreatorCommand command);
    IdentityViews.SessionView handle(AuthenticateAccountCommand command);
    Void handle(RecoverAccountCommand command);
    IdentityViews.CreatorProfileView handle(UpdateCreatorProfileCommand command);
    IdentityViews.AuthorizationView handle(StartSocialAuthorizationCommand command);
    IdentityViews.SocialAccountView handle(CompleteSocialAuthorizationCommand command);
}
