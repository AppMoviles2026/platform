package com.collabtech.platform.identity.application.queries;

import com.collabtech.platform.identity.domain.model.valueobjects.AccountId;
import com.collabtech.platform.identity.application.projections.IdentityViews;
import com.collabtech.platform.shared.application.cqrs.Query;

public record GetUserProfileQuery(AccountId accountId) implements Query<IdentityViews.CreatorProfileView> {}
