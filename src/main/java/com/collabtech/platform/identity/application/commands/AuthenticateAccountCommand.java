package com.collabtech.platform.identity.application.commands;

import com.collabtech.platform.identity.domain.model.valueobjects.EmailAddress;
import com.collabtech.platform.identity.application.projections.IdentityViews;
import com.collabtech.platform.shared.application.cqrs.Command;

public record AuthenticateAccountCommand(EmailAddress email, String password) implements Command<IdentityViews.SessionView> {
    @Override public String toString() { return "AuthenticateAccountCommand[sensitive fields redacted]"; }
}
