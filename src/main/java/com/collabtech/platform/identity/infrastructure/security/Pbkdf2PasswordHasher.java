package com.collabtech.platform.identity.infrastructure.security;

import com.collabtech.platform.identity.application.ports.PasswordHasher;
import com.collabtech.platform.identity.domain.model.valueobjects.RegistrationPassword;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;

/** Salted, adaptive hashing; cryptography stays outside the domain model. */
public final class Pbkdf2PasswordHasher implements PasswordHasher {
    private static final String PREFIX = "{pbkdf2-sha256}";
    private final Pbkdf2PasswordEncoder encoder = new Pbkdf2PasswordEncoder("", 16, 600_000,
            Pbkdf2PasswordEncoder.SecretKeyFactoryAlgorithm.PBKDF2WithHmacSHA256);

    @Override
    public String hash(String plaintext) {
        new RegistrationPassword(plaintext);
        return PREFIX + encoder.encode(plaintext);
    }

    @Override
    public boolean matches(String plaintext, String hash) {
        if (plaintext == null || plaintext.length() > 128 || hash == null || !hash.startsWith(PREFIX)) return false;
        try {
            return encoder.matches(plaintext, hash.substring(PREFIX.length()));
        } catch (IllegalArgumentException invalidHash) {
            return false;
        }
    }
}
