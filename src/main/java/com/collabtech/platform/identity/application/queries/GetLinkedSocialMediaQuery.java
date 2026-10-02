package com.collabtech.platform.identity.application.queries;

import com.collabtech.platform.identity.domain.model.valueobjects.AccountId;
import com.collabtech.platform.identity.application.projections.IdentityViews;
import com.collabtech.platform.shared.application.cqrs.Query;

import java.util.List;

public record GetLinkedSocialMediaQuery(AccountId accountId) implements Query<List<IdentityViews.SocialAccountView>> {}
