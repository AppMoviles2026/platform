package com.collabtech.platform.identity.infrastructure.persistence.jpa;

import com.collabtech.platform.identity.domain.exceptions.DuplicateEmailException;
import com.collabtech.platform.identity.domain.model.aggregates.Account;
import com.collabtech.platform.identity.domain.model.valueobjects.AccountId;
import com.collabtech.platform.identity.domain.model.valueobjects.EmailAddress;
import com.collabtech.platform.identity.domain.repositories.AccountRepository;
import jakarta.persistence.EntityManager;
import java.util.Locale;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Profile("!skeleton")
public class AccountPersistenceAdapter implements AccountRepository {
    private final EntityManager entityManager;

    public AccountPersistenceAdapter(EntityManager entityManager) { this.entityManager = entityManager; }

    /** Registration insert. Account + profile commit atomically before returning to the handler. */
    @Override
    @Transactional
    public Account save(Account account) {
        try {
            entityManager.persist(AccountPersistenceMapper.toEntity(account));
            entityManager.flush();
            return account;
        } catch (RuntimeException failure) {
            for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
                if (cause instanceof org.hibernate.exception.ConstraintViolationException constraint
                        && constraint.getConstraintName() != null
                        && constraint.getConstraintName().toLowerCase(Locale.ROOT).contains("uk_account_email")) {
                    throw new DuplicateEmailException();
                }
            }
            throw failure;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Account> findById(AccountId id) {
        return Optional.ofNullable(entityManager.find(AccountJpaEntity.class, id.value().toString()))
                .map(AccountPersistenceMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Account> findByEmail(EmailAddress email) {
        return entityManager.createQuery("select a from AccountJpaEntity a where a.email = :email", AccountJpaEntity.class)
                .setParameter("email", email.value()).getResultStream().findFirst().map(AccountPersistenceMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByEmail(EmailAddress email) {
        return entityManager.createQuery("select count(a) from AccountJpaEntity a where a.email = :email", Long.class)
                .setParameter("email", email.value()).getSingleResult() > 0;
    }
}
