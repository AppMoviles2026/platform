package com.collabtech.platform.identity.application.commands;

import com.collabtech.platform.identity.domain.model.valueobjects.EmailAddress;
import com.collabtech.platform.identity.application.projections.IdentityViews;
import com.collabtech.platform.shared.application.cqrs.Command;
import com.collabtech.platform.identity.domain.model.valueobjects.RegistrationPassword;
import java.util.Objects;

public record RegisterCreatorCommand(String displayName, EmailAddress email, String password) implements Command<IdentityViews.AccountView> {
    public RegisterCreatorCommand {
        Objects.requireNonNull(email, "email");
        if (displayName == null || displayName.isBlank() || displayName.strip().length() > 150) {
            throw new IllegalArgumentException("Display name must contain 1 to 150 characters");
        }
        displayName = displayName.strip();
        new RegistrationPassword(password);
    }
    @Override public String toString() { return "RegisterCreatorCommand[sensitive fields redacted]"; }
}
