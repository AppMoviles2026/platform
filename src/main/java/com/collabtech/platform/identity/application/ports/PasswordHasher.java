package com.collabtech.platform.identity.application.ports;

public interface PasswordHasher {
    String hash(String plaintext);
    boolean matches(String plaintext, String hash);
}
