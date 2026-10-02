package com.collabtech.platform.identity.application.commands;

import com.collabtech.platform.identity.domain.model.valueobjects.EmailAddress;
import com.collabtech.platform.identity.application.projections.IdentityViews;
import com.collabtech.platform.shared.application.cqrs.Command;

public record RegisterBrandCommand(String businessName, EmailAddress email, String password) implements Command<IdentityViews.AccountView> {
    @Override public String toString() { return "RegisterBrandCommand[sensitive fields redacted]"; }
}
