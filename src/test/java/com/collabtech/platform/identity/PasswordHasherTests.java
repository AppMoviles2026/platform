package com.collabtech.platform.identity;

import static org.junit.jupiter.api.Assertions.*;
import com.collabtech.platform.identity.infrastructure.security.Pbkdf2PasswordHasher;
import org.junit.jupiter.api.Test;

class PasswordHasherTests {
    @Test void hashingUsesDifferentSaltsAndNeverStoresPlaintext() {
        var passwords = new Pbkdf2PasswordHasher();
        String plaintext = "UnaClaveValida123!";
        String first = passwords.hash(plaintext);
        String second = passwords.hash(plaintext);
        assertNotEquals(first, second);
        assertFalse(first.contains(plaintext));
        assertTrue(passwords.matches(plaintext, first));
        assertFalse(passwords.matches("ClaveDiferente", first));
        assertFalse(passwords.matches(plaintext, "invalid-hash"));
    }
}
