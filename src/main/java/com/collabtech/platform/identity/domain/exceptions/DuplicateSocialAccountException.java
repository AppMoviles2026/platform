package com.collabtech.platform.identity.domain.exceptions;

public final class DuplicateSocialAccountException extends RuntimeException {
    public DuplicateSocialAccountException() { super("Social account is already linked to this profile"); }
}
