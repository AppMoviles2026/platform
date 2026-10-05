package com.collabtech.platform.identity.domain.exceptions;

/** Global uniqueness across company and creator accounts. Does not retain the email or database cause. */
public final class DuplicateEmailException extends RuntimeException {
    public DuplicateEmailException() { super("An account already uses this email"); }
}
