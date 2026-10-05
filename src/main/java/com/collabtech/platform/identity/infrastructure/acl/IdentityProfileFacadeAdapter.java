package com.collabtech.platform.identity.infrastructure.acl;

import com.collabtech.platform.identity.interfaces.acl.IdentityProfileFacade;
import com.collabtech.platform.identity.domain.repositories.AccountRepository;
import com.collabtech.platform.identity.domain.model.valueobjects.AccountId;
import com.collabtech.platform.identity.domain.model.valueobjects.AccountType;
import com.collabtech.platform.identity.domain.model.valueobjects.AccountStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("!skeleton")
public class IdentityProfileFacadeAdapter implements IdentityProfileFacade {
    private final AccountRepository accounts;
    public IdentityProfileFacadeAdapter(AccountRepository accounts) { this.accounts = accounts; }
    @Transactional(readOnly = true)
    public Optional<IdentityProfileFacade.Profile> byAccount(UUID accountId) {
        return accounts.findById(new AccountId(accountId)).map(account -> {
            boolean brand = account.accountType() == AccountType.BRAND;
            return new IdentityProfileFacade.Profile(brand ? account.brandProfile().id().value() : account.creatorProfile().id().value(),
                    brand ? account.brandProfile().businessName() : account.creatorProfile().displayName(),
                    brand ? account.brandProfile().location() : account.creatorProfile().location(), account.accountType().name(),
                    account.status() == AccountStatus.ACTIVE);
        });
    }
}
