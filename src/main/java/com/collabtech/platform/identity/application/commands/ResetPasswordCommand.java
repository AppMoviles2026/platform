package com.collabtech.platform.identity.application.commands;

import com.collabtech.platform.shared.application.cqrs.Command;
import com.collabtech.platform.identity.domain.model.valueobjects.RegistrationPassword;

public record ResetPasswordCommand(String token, String newPassword) implements Command<Void> {
    public ResetPasswordCommand {
        if (token == null || token.isBlank() || token.length() > 128) throw new IllegalArgumentException("Recovery token required");
        new RegistrationPassword(newPassword);
    }
    @Override public String toString() { return "ResetPasswordCommand[redacted]"; }
}
