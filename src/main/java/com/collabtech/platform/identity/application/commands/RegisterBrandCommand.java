package com.collabtech.platform.identity.application.commands;

import com.collabtech.platform.identity.domain.model.valueobjects.EmailAddress;
import com.collabtech.platform.identity.application.projections.IdentityViews;
import com.collabtech.platform.shared.application.cqrs.Command;
import com.collabtech.platform.identity.domain.model.valueobjects.RegistrationPassword;
import java.util.Objects;

public record RegisterBrandCommand(String businessName, EmailAddress email, String password) implements Command<IdentityViews.AccountView> {
    public RegisterBrandCommand {
        Objects.requireNonNull(email, "email");
        if (businessName == null || businessName.isBlank() || businessName.strip().length() > 150) {
            throw new IllegalArgumentException("Business name must contain 1 to 150 characters");
        }
        businessName = businessName.strip();
        new RegistrationPassword(password);
    }
    @Override public String toString() { return "RegisterBrandCommand[sensitive fields redacted]"; }
}
