package com.collabtech.platform.identity.application.commands;

import com.collabtech.platform.identity.domain.model.valueobjects.EmailAddress;

import com.collabtech.platform.shared.application.cqrs.Command;

public record RecoverAccountCommand(EmailAddress email) implements Command<Void> {}
