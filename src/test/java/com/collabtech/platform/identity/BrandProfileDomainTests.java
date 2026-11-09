package com.collabtech.platform.identity;

import static org.junit.jupiter.api.Assertions.*;
import com.collabtech.platform.identity.domain.model.aggregates.Account;
import com.collabtech.platform.identity.domain.model.entities.BrandProfile;
import com.collabtech.platform.identity.domain.model.valueobjects.*;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BrandProfileDomainTests {
    @Test void validatesAllFieldsBeforeChangingTheProfileAndPreservesIdentity() {
        var profile = new BrandProfile(new BrandProfileId(UUID.randomUUID()), " Original ", null, null, null);
        profile.restoreVersion(5);
        assertThrows(IllegalArgumentException.class, () -> profile.updateInformation("Nuevo", "x".repeat(2001), "Food", "Lima"));
        assertEquals("Original", profile.businessName()); assertNull(profile.category()); assertEquals(5, profile.version());
        var id = profile.id();
        profile.updateInformation(" Nuevo ", " Descripción ", " Food ", " Lima ");
        assertEquals(id, profile.id()); assertEquals("Nuevo", profile.businessName()); assertEquals("Descripción", profile.description());
        assertEquals("Food", profile.category()); assertEquals("Lima", profile.location());
        profile.updateInformation("Nuevo", " ", null, " "); assertNull(profile.description()); assertNull(profile.location());
        assertThrows(IllegalArgumentException.class, () -> profile.restoreVersion(-1));
    }
    @Test void onlyAnActiveBrandCanUpdateItsBusinessProfile() {
        var account = Account.registerBrand(new EmailAddress("brand@example.com"), "hash", "Marca", Instant.now());
        account.updateBrandProfile("Marca actualizada", "Bio", "Food", "Lima");
        assertEquals("Marca actualizada", account.brandProfile().businessName());
        assertThrows(IllegalStateException.class, () -> Account.registerCreator(new EmailAddress("creator@example.com"), "hash", "Creador", Instant.now())
                .updateBrandProfile("Marca", null, null, null));
        var inactive = new Account(account.id(), account.email(), "hash", AccountType.BRAND, AccountStatus.SUSPENDED,
                account.createdAt(), account.brandProfile(), null);
        assertThrows(IllegalStateException.class, () -> inactive.updateBrandProfile("Otra", null, null, null));
    }
}
