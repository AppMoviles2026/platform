package com.collabtech.platform.identity.application.projections;

import com.collabtech.platform.identity.application.ports.AuthorizationStateStore.Authorization;

/** Delivery decision after consuming a valid state; errors contain no provider payload. */
public record SocialAuthorizationCompletion(Authorization authorization, IdentityViews.SocialAccountView socialAccount, RuntimeException failure) {}
