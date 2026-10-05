package com.collabtech.platform.identity.domain.repositories;

import com.collabtech.platform.identity.domain.model.aggregates.Account;
import com.collabtech.platform.identity.domain.model.valueobjects.AccountId;
import com.collabtech.platform.identity.domain.model.valueobjects.EmailAddress;
import java.util.Optional;

public interface AccountRepository {
    Account save(Account account);
    Optional<Account> findById(AccountId id);
    Optional<Account> findByEmail(EmailAddress email);
    /** Serialize authentication with password changes inside the caller's unit of work. */
    Optional<Account> findByEmailForUpdate(EmailAddress email);
    boolean existsByEmail(EmailAddress email);
}
