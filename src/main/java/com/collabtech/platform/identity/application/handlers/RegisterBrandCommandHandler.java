package com.collabtech.platform.identity.application.handlers;

import com.collabtech.platform.identity.application.commands.RegisterBrandCommand;
import com.collabtech.platform.identity.application.ports.PasswordHasher;
import com.collabtech.platform.identity.application.projections.IdentityViews;
import com.collabtech.platform.identity.domain.exceptions.DuplicateEmailException;
import com.collabtech.platform.identity.domain.model.aggregates.Account;
import com.collabtech.platform.identity.domain.repositories.AccountRepository;
import com.collabtech.platform.shared.application.cqrs.CommandHandler;
import java.time.Clock;
import java.util.Objects;

public final class RegisterBrandCommandHandler implements CommandHandler<RegisterBrandCommand, IdentityViews.AccountView> {
    private final AccountRepository accounts;
    private final PasswordHasher passwords;
    private final Clock clock;

    public RegisterBrandCommandHandler(AccountRepository accounts, PasswordHasher passwords, Clock clock) {
        this.accounts = Objects.requireNonNull(accounts);
        this.passwords = Objects.requireNonNull(passwords);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public IdentityViews.AccountView handle(RegisterBrandCommand command) {
        Objects.requireNonNull(command);
        if (accounts.existsByEmail(command.email())) throw new DuplicateEmailException();
        var account = Account.registerBrand(command.email(), passwords.hash(command.password()),
                command.businessName(), clock.instant());
        var saved = accounts.save(account);
        return new IdentityViews.AccountView(saved.id().value(), saved.brandProfile().id().value(),
                saved.brandProfile().businessName(), saved.accountType().name(), saved.status().name());
    }
}
