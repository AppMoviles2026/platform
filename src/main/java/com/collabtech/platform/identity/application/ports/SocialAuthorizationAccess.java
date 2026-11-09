package com.collabtech.platform.identity.application.ports;

import com.collabtech.platform.identity.application.projections.IdentityViews;
import com.collabtech.platform.identity.domain.model.valueobjects.CreatorProfileId;
import com.collabtech.platform.identity.domain.model.valueobjects.SocialMediaAccountId;
import java.util.Set;

/** Credential-free lifecycle port. Provider secrets remain in infrastructure. */
public interface SocialAuthorizationAccess {
    IdentityViews.SocialAccessView inspect(CreatorProfileId owner, SocialMediaAccountId social, Set<String> requiredScopes);
    IdentityViews.SocialAccessView ensureUsable(CreatorProfileId owner, SocialMediaAccountId social, Set<String> requiredScopes);
}
