package com.collabtech.platform.identity.application.services;

import com.collabtech.platform.identity.application.commands.*;
import com.collabtech.platform.identity.application.handlers.RegisterBrandCommandHandler;
import com.collabtech.platform.identity.application.handlers.RegisterCreatorCommandHandler;
import com.collabtech.platform.identity.application.ports.*;
import com.collabtech.platform.identity.application.projections.IdentityViews;
import com.collabtech.platform.identity.application.projections.SocialAuthorizationCompletion;
import com.collabtech.platform.identity.domain.exceptions.DuplicateSocialAccountException;
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
    private final SocialAuthorizationAccess socialAccess;
    private final IdentityUnitOfWork unitOfWork;
    private final RegisterBrandCommandHandler brands;
    private final RegisterCreatorCommandHandler creators;
    private final String dummyHash;

    public IdentityApplicationService(AccountRepository accounts, PasswordHasher passwords, AccessTokenProvider sessions,
            AccountRecoveryService recovery, AuthorizationStateStore states, SocialOAuthClient social,
            IdentityUnitOfWork unitOfWork, RegisterBrandCommandHandler brands, RegisterCreatorCommandHandler creators,
            SocialAuthorizationAccess socialAccess) {
        this.accounts = accounts; this.passwords = passwords; this.sessions = sessions; this.recovery = recovery;
        this.states = states; this.social = social; this.unitOfWork = unitOfWork;
        this.brands = brands; this.creators = creators;
        this.socialAccess = socialAccess;
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

    public IdentityViews.BrandProfileView handle(UpdateBrandProfileCommand command) {
        return unitOfWork.execute(() -> {
            var account = activeForUpdate(command.accountId());
            if (account.accountType() != AccountType.BRAND) throw new IdentityFailure(IdentityFailure.Code.BRAND_REQUIRED);
            if (account.brandProfile().version() != command.expectedVersion())
                throw new IdentityFailure(IdentityFailure.Code.CONCURRENT_UPDATE);
            account.updateBrandProfile(command.businessName(), command.description(), command.category(), command.location());
            accounts.save(account);
            return brandView(account);
        });
    }
    public IdentityViews.BrandProfileView handle(GetBrandProfileQuery query) {
        return unitOfWork.execute(() -> brandView(brand(query.accountId())));
    }

    public IdentityViews.CreatorProfileView handle(UpdateCreatorProfileCommand command) {
        return unitOfWork.execute(() -> {
            var account = activeForUpdate(command.accountId());
            if (account.accountType() != AccountType.CREATOR) throw new IdentityFailure(IdentityFailure.Code.CREATOR_REQUIRED);
            account.updateCreatorProfile(command.displayName(), command.biography(), command.niche(),
                    command.audienceDescription(), command.location());
            accounts.save(account);
            return profileView(account);
        });
    }

    public IdentityViews.AuthorizationView handle(StartSocialAuthorizationCommand command) {
        return unitOfWork.execute(() -> {
            creator(command.accountId());
            var pending = states.create(command.accountId(), command.platform(), command.client());
            return new IdentityViews.AuthorizationView(social.authorizationUri(command.platform(), pending.state()), pending.authorizationId());
        });
    }

    public IdentityViews.SocialAccountView handle(CompleteSocialAuthorizationCommand command) {
        var result = completeSocialAuthorization(command);
        if (result.failure() != null) throw result.failure();
        return result.socialAccount();
    }

    public SocialAuthorizationCompletion completeSocialAuthorization(CompleteSocialAuthorizationCommand command) {
        // Consume independently: denial, duplicate or failed provider calls cannot make a state replayable.
        var authorization = states.consume(command.state(), command.platform());
        try {
            if (command.providerError() != null || command.authorizationCode() == null || command.authorizationCode().isBlank()) {
                throw new IdentityFailure(IdentityFailure.Code.AUTHORIZATION_DENIED);
            }
            var result = unitOfWork.execute(() -> {
                var account = activeForUpdate(authorization.owner());
                if (account.accountType() != AccountType.CREATOR) throw new IdentityFailure(IdentityFailure.Code.CREATOR_REQUIRED);
                var approved = social.exchangeCode(authorization.platform(), command.authorizationCode());
                var existing = account.creatorProfile().socialMediaAccounts().stream()
                        .filter(s -> s.platform().equals(authorization.platform()) && s.externalAccountId().equals(approved.externalAccountId()))
                        .findFirst();
                if (existing.isPresent()) {
                    if (!social.replaceUnavailableCredentials(approved.credentialReceipt(), existing.get().id()))
                        throw new DuplicateSocialAccountException();
                    account.renewSocialAccount(existing.get().id(), approved.username());
                    accounts.save(account);
                    states.finish(authorization.authorizationId(), "SUCCEEDED", null);
                    return socialView(existing.get());
                }
                var linked = new SocialMediaAccount(new SocialMediaAccountId(UUID.randomUUID()), authorization.platform(),
                        approved.externalAccountId(), approved.username(), SocialAccountStatus.ACTIVE);
                account.linkSocialAccount(linked);
                accounts.save(account);
                social.attachCredentials(approved.credentialReceipt(), linked.id());
                states.finish(authorization.authorizationId(), "SUCCEEDED", null);
                return socialView(linked);
            });
            return new SocialAuthorizationCompletion(authorization, result, null);
        } catch (RuntimeException failure) {
            String code = failure instanceof IdentityFailure identityFailure ? identityFailure.code().name()
                    : failure instanceof DuplicateSocialAccountException ? "SOCIAL_ACCOUNT_ALREADY_LINKED" : "PROVIDER_FAILED";
            states.finish(authorization.authorizationId(), "FAILED", code);
            RuntimeException sanitized = failure instanceof IdentityFailure || failure instanceof DuplicateSocialAccountException
                    ? failure : new IdentityFailure(IdentityFailure.Code.PROVIDER_FAILED);
            return new SocialAuthorizationCompletion(authorization, null, sanitized);
        }
    }

    public IdentityViews.AuthorizationStatusView authorizationStatus(AccountId owner, UUID id) {
        return unitOfWork.execute(() -> { creator(owner); return states.find(id, owner); });
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

    public IdentityViews.SocialAccessView socialAccessStatus(AccountId owner, SocialMediaAccountId socialId) {
        return unitOfWork.execute(() -> socialAccess.inspect(creator(owner).creatorProfile().id(), socialId, java.util.Set.of()));
    }

    private Account activeForUpdate(AccountId id) {
        var account = accounts.findByIdForUpdate(id).orElseThrow(() -> new IdentityFailure(IdentityFailure.Code.ACCOUNT_NOT_ACTIVE));
        if (account.status() != AccountStatus.ACTIVE) throw new IdentityFailure(IdentityFailure.Code.ACCOUNT_NOT_ACTIVE);
        return account;
    }
    private Account active(AccountId id) {
        var account = accounts.findById(id).orElseThrow(() -> new IdentityFailure(IdentityFailure.Code.ACCOUNT_NOT_ACTIVE));
        if (account.status() != AccountStatus.ACTIVE) throw new IdentityFailure(IdentityFailure.Code.ACCOUNT_NOT_ACTIVE);
        return account;
    }
    private Account brand(AccountId id) {
        var account = active(id);
        if (account.accountType() != AccountType.BRAND) throw new IdentityFailure(IdentityFailure.Code.BRAND_REQUIRED);
        return account;
    }
    private static IdentityViews.BrandProfileView brandView(Account account) {
        var profile = account.brandProfile();
        return new IdentityViews.BrandProfileView(profile.id().value(), profile.businessName(), profile.description(),
                profile.category(), profile.location(), profile.version());
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
