package com.collabtech.platform.identity.application.handlers;

import com.collabtech.platform.identity.application.commands.RegisterCreatorCommand;
import com.collabtech.platform.identity.application.ports.PasswordHasher;
import com.collabtech.platform.identity.application.projections.IdentityViews;
import com.collabtech.platform.identity.domain.exceptions.DuplicateEmailException;
import com.collabtech.platform.identity.domain.model.aggregates.Account;
import com.collabtech.platform.identity.domain.repositories.AccountRepository;
import com.collabtech.platform.shared.application.cqrs.CommandHandler;
import java.time.Clock;
import java.util.Objects;

public final class RegisterCreatorCommandHandler implements CommandHandler<RegisterCreatorCommand, IdentityViews.AccountView> {
    private final AccountRepository accounts;
    private final PasswordHasher passwords;
    private final Clock clock;

    public RegisterCreatorCommandHandler(AccountRepository accounts, PasswordHasher passwords, Clock clock) {
        this.accounts = Objects.requireNonNull(accounts);
        this.passwords = Objects.requireNonNull(passwords);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public IdentityViews.AccountView handle(RegisterCreatorCommand command) {
        Objects.requireNonNull(command);
        if (accounts.existsByEmail(command.email())) throw new DuplicateEmailException();
        var account = Account.registerCreator(command.email(), passwords.hash(command.password()),
                command.displayName(), clock.instant());
        var saved = accounts.save(account);
        return new IdentityViews.AccountView(saved.id().value(), saved.creatorProfile().id().value(),
                saved.creatorProfile().displayName(), saved.accountType().name(), saved.status().name());
    }
}
