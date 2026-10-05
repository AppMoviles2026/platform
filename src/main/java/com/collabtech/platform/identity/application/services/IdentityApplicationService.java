package com.collabtech.platform.identity.application.services;

import com.collabtech.platform.identity.application.commands.*;
import com.collabtech.platform.identity.application.handlers.RegisterBrandCommandHandler;
import com.collabtech.platform.identity.application.handlers.RegisterCreatorCommandHandler;
import com.collabtech.platform.identity.application.ports.*;
import com.collabtech.platform.identity.application.projections.IdentityViews;
import com.collabtech.platform.identity.application.queries.*;
import com.collabtech.platform.identity.application.exceptions.IdentityFailure;
import com.collabtech.platform.identity.domain.model.aggregates.Account;
import com.collabtech.platform.identity.domain.model.entities.SocialMediaAccount;
import com.collabtech.platform.identity.domain.model.valueobjects.*;
import com.collabtech.platform.identity.domain.repositories.AccountRepository;
import java.util.List;
import java.util.UUID;

/** CQRS entry-port implementation; transactions are supplied through an infrastructure port. */
public final class IdentityApplicationService implements IdentityCommandService, IdentityQueryService {
    private final AccountRepository accounts;
    private final PasswordHasher passwords;
    private final AccessTokenProvider sessions;
    private final AccountRecoveryService recovery;
    private final AuthorizationStateStore states;
    private final SocialOAuthClient social;
    private final IdentityUnitOfWork unitOfWork;
    private final RegisterBrandCommandHandler brands;
    private final RegisterCreatorCommandHandler creators;
    private final String dummyHash;

    public IdentityApplicationService(AccountRepository accounts, PasswordHasher passwords, AccessTokenProvider sessions,
            AccountRecoveryService recovery, AuthorizationStateStore states, SocialOAuthClient social,
            IdentityUnitOfWork unitOfWork, RegisterBrandCommandHandler brands, RegisterCreatorCommandHandler creators) {
        this.accounts = accounts; this.passwords = passwords; this.sessions = sessions; this.recovery = recovery;
        this.states = states; this.social = social; this.unitOfWork = unitOfWork;
        this.brands = brands; this.creators = creators;
        this.dummyHash = passwords.hash("UnrelatedTimingComparisonPassword");
    }

    public IdentityViews.AccountView handle(RegisterBrandCommand command) { return brands.handle(command); }
    public IdentityViews.AccountView handle(RegisterCreatorCommand command) { return creators.handle(command); }

    public IdentityViews.SessionView handle(AuthenticateAccountCommand command) {
        return unitOfWork.execute(() -> {
            var found = accounts.findByEmailForUpdate(command.email());
            boolean valid = passwords.matches(command.password(), found.map(Account::passwordHash).orElse(dummyHash));
            if (!valid || found.isEmpty() || found.get().status() != AccountStatus.ACTIVE) {
                throw new IdentityFailure(IdentityFailure.Code.INVALID_CREDENTIALS);
            }
            return sessions.issue(accountView(found.get()));
        });
    }

    public Void handle(RecoverAccountCommand command) { recovery.requestRecovery(command.email()); return null; }
    public Void handle(ResetPasswordCommand command) {
        recovery.resetPassword(command.token(), passwords.hash(command.newPassword())); return null;
    }

    public IdentityViews.CreatorProfileView handle(UpdateCreatorProfileCommand command) {
        return unitOfWork.execute(() -> {
            var account = creator(command.accountId());
            account.updateCreatorProfile(command.displayName(), command.biography(), command.niche(),
                    command.audienceDescription(), command.location());
            accounts.save(account);
            return profileView(account);
        });
    }

    public IdentityViews.AuthorizationView handle(StartSocialAuthorizationCommand command) {
        return unitOfWork.execute(() -> {
            creator(command.accountId());
            String state = states.create(command.accountId(), command.platform());
            return new IdentityViews.AuthorizationView(social.authorizationUri(command.platform(), state));
        });
    }

    public IdentityViews.SocialAccountView handle(CompleteSocialAuthorizationCommand command) {
        // Consume independently: denial, duplicate or failed provider calls cannot make a state replayable.
        var authorization = states.consume(command.state(), command.platform());
        if (command.providerError() != null) throw new IdentityFailure(IdentityFailure.Code.AUTHORIZATION_DENIED);
        if (command.authorizationCode() == null || command.authorizationCode().isBlank()) {
            throw new IdentityFailure(IdentityFailure.Code.AUTHORIZATION_DENIED);
        }
        return unitOfWork.execute(() -> {
            var account = creator(authorization.owner());
            var approved = social.exchangeCode(authorization.platform(), command.authorizationCode());
            var linked = new SocialMediaAccount(new SocialMediaAccountId(UUID.randomUUID()), authorization.platform(),
                    approved.externalAccountId(), approved.username(), SocialAccountStatus.ACTIVE);
            account.linkSocialAccount(linked);
            accounts.save(account);
            social.attachCredentials(approved.credentialReceipt(), linked.id());
            return socialView(linked);
        });
    }

    public IdentityViews.AccountView handle(GetCurrentAccountQuery query) {
        return unitOfWork.execute(() -> accountView(active(query.accountId())));
    }
    public IdentityViews.CreatorProfileView handle(GetUserProfileQuery query) {
        return unitOfWork.execute(() -> profileView(creator(query.accountId())));
    }
    public List<IdentityViews.SocialAccountView> handle(GetLinkedSocialMediaQuery query) {
        return unitOfWork.execute(() -> creator(query.accountId()).creatorProfile().socialMediaAccounts()
                .stream().map(IdentityApplicationService::socialView).toList());
    }

    private Account active(AccountId id) {
        var account = accounts.findById(id).orElseThrow(() -> new IdentityFailure(IdentityFailure.Code.ACCOUNT_NOT_ACTIVE));
        if (account.status() != AccountStatus.ACTIVE) throw new IdentityFailure(IdentityFailure.Code.ACCOUNT_NOT_ACTIVE);
        return account;
    }
    private Account creator(AccountId id) {
        var account = active(id);
        if (account.accountType() != AccountType.CREATOR) throw new IdentityFailure(IdentityFailure.Code.CREATOR_REQUIRED);
        return account;
    }
    private static IdentityViews.AccountView accountView(Account account) {
        boolean brand = account.accountType() == AccountType.BRAND;
        return new IdentityViews.AccountView(account.id().value(),
                brand ? account.brandProfile().id().value() : account.creatorProfile().id().value(),
                brand ? account.brandProfile().businessName() : account.creatorProfile().displayName(),
                account.accountType().name(), account.status().name());
    }
    private static IdentityViews.CreatorProfileView profileView(Account account) {
        var profile = account.creatorProfile();
        return new IdentityViews.CreatorProfileView(profile.id().value(), profile.displayName(), profile.biography(),
                profile.niche(), profile.audienceDescription(), profile.location());
    }
    private static IdentityViews.SocialAccountView socialView(SocialMediaAccount account) {
        return new IdentityViews.SocialAccountView(account.id().value(), account.platform().code(),
                account.username(), account.status().name());
    }
}
