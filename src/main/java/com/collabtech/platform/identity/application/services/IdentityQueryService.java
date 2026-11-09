package com.collabtech.platform.identity.application.services;

import com.collabtech.platform.identity.application.queries.GetCurrentAccountQuery;
import com.collabtech.platform.identity.application.queries.GetUserProfileQuery;
import com.collabtech.platform.identity.application.queries.GetLinkedSocialMediaQuery;
import com.collabtech.platform.identity.application.projections.IdentityViews;

import java.util.List;

/** Inbound read port implemented by IdentityApplicationService. */
public interface IdentityQueryService {
    IdentityViews.AccountView handle(GetCurrentAccountQuery query);
    IdentityViews.CreatorProfileView handle(GetUserProfileQuery query);
    IdentityViews.BrandProfileView handle(com.collabtech.platform.identity.application.queries.GetBrandProfileQuery query);
    List<IdentityViews.SocialAccountView> handle(GetLinkedSocialMediaQuery query);
}
