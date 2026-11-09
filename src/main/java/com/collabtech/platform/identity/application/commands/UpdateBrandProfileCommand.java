package com.collabtech.platform.identity.application.commands;

import com.collabtech.platform.identity.domain.model.valueobjects.AccountId;
import com.collabtech.platform.identity.application.projections.IdentityViews;
import com.collabtech.platform.shared.application.cqrs.Command;
import java.util.Objects;

public record UpdateBrandProfileCommand(AccountId accountId, String businessName, String description,
        String category, String location, long expectedVersion) implements Command<IdentityViews.BrandProfileView> {
    public UpdateBrandProfileCommand {
        Objects.requireNonNull(accountId);
        if (expectedVersion < 0) throw new IllegalArgumentException("Invalid profile version");
    }
}
