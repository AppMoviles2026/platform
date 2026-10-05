package com.collabtech.platform.identity.domain.model.valueobjects;

/** Transient registration input only: never store this value in Account or persistence. */
public record RegistrationPassword(String value) {
    public RegistrationPassword {
        if (value == null || value.isBlank() || value.length() < 8 || value.length() > 128) {
            throw new IllegalArgumentException("Password must contain 8 to 128 characters");
        }
    }

    @Override public String toString() { return "RegistrationPassword[redacted]"; }
}
