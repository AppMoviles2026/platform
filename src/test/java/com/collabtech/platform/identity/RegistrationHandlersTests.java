package com.collabtech.platform.identity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import com.collabtech.platform.identity.application.commands.RegisterBrandCommand;
import com.collabtech.platform.identity.application.commands.RegisterCreatorCommand;
import com.collabtech.platform.identity.application.handlers.RegisterBrandCommandHandler;
import com.collabtech.platform.identity.application.handlers.RegisterCreatorCommandHandler;
import com.collabtech.platform.identity.application.ports.PasswordHasher;
import com.collabtech.platform.identity.domain.exceptions.DuplicateEmailException;
import com.collabtech.platform.identity.domain.model.aggregates.Account;
import com.collabtech.platform.identity.domain.model.valueobjects.EmailAddress;
import com.collabtech.platform.identity.domain.repositories.AccountRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RegistrationHandlersTests {
    private final AccountRepository accounts = mock(AccountRepository.class);
    private final PasswordHasher passwords = mock(PasswordHasher.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-05T12:00:00Z"), ZoneOffset.UTC);
    private final EmailAddress email = new EmailAddress("test@example.com");

    @Test void companyHandlerHashesPasswordAndSavesAggregateWithClockTime() {
        when(passwords.hash("ValidPassword1")).thenReturn("encoded-hash");
        when(accounts.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var result = new RegisterBrandCommandHandler(accounts, passwords, clock)
                .handle(new RegisterBrandCommand("Empresa", email, "ValidPassword1"));
        var saved = ArgumentCaptor.forClass(Account.class);
        verify(accounts).save(saved.capture());
        assertEquals(clock.instant(), saved.getValue().createdAt());
        assertEquals("encoded-hash", saved.getValue().passwordHash());
        assertEquals("BRAND", result.accountType());
    }

    @Test void creatorHandlerDoesNotHashOrWriteWhenEmailExists() {
        when(accounts.existsByEmail(email)).thenReturn(true);
        assertThrows(DuplicateEmailException.class, () -> new RegisterCreatorCommandHandler(accounts, passwords, clock)
                .handle(new RegisterCreatorCommand("Creador", email, "ValidPassword1")));
        verifyNoInteractions(passwords);
        verify(accounts, never()).save(any());
    }

    @Test void companyHandlerDoesNotHashOrWriteWhenEmailExists() {
        when(accounts.existsByEmail(email)).thenReturn(true);
        assertThrows(DuplicateEmailException.class, () -> new RegisterBrandCommandHandler(accounts, passwords, clock)
                .handle(new RegisterBrandCommand("Empresa", email, "ValidPassword1")));
        verifyNoInteractions(passwords);
        verify(accounts, never()).save(any());
    }
}
