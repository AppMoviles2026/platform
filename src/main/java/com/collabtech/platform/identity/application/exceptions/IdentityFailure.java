package com.collabtech.platform.identity.application.exceptions;

/** Use-case failure codes, independent of HTTP or provider exception types. */
public final class IdentityFailure extends RuntimeException {
    public enum Code { INVALID_CREDENTIALS, CREATOR_REQUIRED, ACCOUNT_NOT_ACTIVE, INVALID_RECOVERY_TOKEN,
        INVALID_OAUTH_STATE, AUTHORIZATION_DENIED, PROVIDER_NOT_CONFIGURED, PROVIDER_FAILED }
    private final Code code;
    public IdentityFailure(Code code) { super(code.name()); this.code = code; }
    public Code code() { return code; }
}
