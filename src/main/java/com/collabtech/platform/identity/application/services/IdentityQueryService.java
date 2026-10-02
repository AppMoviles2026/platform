package com.collabtech.platform.identity.application.services;

import com.collabtech.platform.identity.application.queries.GetCurrentAccountQuery;
import com.collabtech.platform.identity.application.queries.GetUserProfileQuery;
import com.collabtech.platform.identity.application.queries.GetLinkedSocialMediaQuery;
import com.collabtech.platform.identity.application.projections.IdentityViews;

import java.util.List;

/** Inbound read port only; no persistence or endpoint implementation yet. */
public interface IdentityQueryService {
    IdentityViews.AccountView handle(GetCurrentAccountQuery query);
    IdentityViews.CreatorProfileView handle(GetUserProfileQuery query);
    List<IdentityViews.SocialAccountView> handle(GetLinkedSocialMediaQuery query);
}
