package com.collabtech.platform.identity;

import static org.junit.jupiter.api.Assertions.*;
import com.collabtech.platform.identity.application.commands.RegisterBrandCommand;
import com.collabtech.platform.identity.application.commands.RegisterCreatorCommand;
import com.collabtech.platform.identity.domain.events.AccountRegistered;
import com.collabtech.platform.identity.domain.model.aggregates.Account;
import com.collabtech.platform.identity.domain.model.valueobjects.*;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class RegistrationDomainTests {
    @Test void brandRegistrationOwnsOnlyBrandProfileAndRecordsEvent() {
        Instant now = Instant.parse("2026-10-05T12:00:00Z");
        var account = Account.registerBrand(new EmailAddress(" BRAND@EXAMPLE.COM "), "encoded-hash", " Empresa ", now);
        assertEquals("brand@example.com", account.email().value());
        assertEquals(AccountType.BRAND, account.accountType());
        assertEquals(AccountStatus.ACTIVE, account.status());
        assertEquals("Empresa", account.brandProfile().businessName());
        assertNull(account.creatorProfile());
        var event = assertInstanceOf(AccountRegistered.class, account.pullDomainEvents().get(0));
        assertEquals(account.id(), event.accountId());
        assertEquals(now, event.occurredAt());
        assertTrue(account.pullDomainEvents().isEmpty());
    }

    @Test void creatorRegistrationOwnsEmptyInitialCreatorProfile() {
        var account = Account.registerCreator(new EmailAddress("creator@example.com"), "encoded-hash", " Creador ", Instant.now());
        assertNull(account.brandProfile());
        assertEquals(AccountType.CREATOR, account.accountType());
        assertEquals("Creador", account.creatorProfile().displayName());
        assertTrue(account.creatorProfile().socialMediaAccounts().isEmpty());
        assertNull(account.creatorProfile().biography());
    }

    @Test void commandsRejectMissingNamesOrWeakPasswordBeforeHashing() {
        var email = new EmailAddress("test@example.com");
        assertThrows(IllegalArgumentException.class, () -> new RegisterBrandCommand(" ", email, "ValidPassword1"));
        assertThrows(IllegalArgumentException.class, () -> new RegisterCreatorCommand("Nombre", email, "short"));
        assertThrows(IllegalArgumentException.class, () -> new RegistrationPassword("x".repeat(129)));
        assertThrows(IllegalArgumentException.class, () -> Account.registerCreator(email, "", "Nombre", Instant.now()));
    }

    @Test void sensitiveRegistrationInputsHaveRedactedStringRepresentations() {
        String password = "SecretPassword1";
        var email = new EmailAddress("test@example.com");
        assertFalse(new RegisterBrandCommand("Nombre", email, password).toString().contains(password));
        assertFalse(new RegisterCreatorCommand("Nombre", email, password).toString().contains(password));
        assertFalse(new RegistrationPassword(password).toString().contains(password));
    }
}
