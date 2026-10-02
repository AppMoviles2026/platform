package com.collabtech.platform.identity.application.queries;

import com.collabtech.platform.identity.domain.model.valueobjects.AccountId;
import com.collabtech.platform.identity.application.projections.IdentityViews;
import com.collabtech.platform.shared.application.cqrs.Query;

public record GetCurrentAccountQuery(AccountId accountId) implements Query<IdentityViews.AccountView> {}
