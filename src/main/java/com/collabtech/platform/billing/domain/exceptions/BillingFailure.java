package com.collabtech.platform.billing.domain.exceptions;
public final class BillingFailure extends RuntimeException {
    public enum Code { AWAITING_APPROVAL,NOT_FOUND,FORBIDDEN,ACCOUNT_NOT_ACTIVE,PROVIDER_NOT_CONFIGURED,PROVIDER_UNAVAILABLE,
        PROVIDER_PERMISSION_REQUIRED,PROVIDER_REJECTED,INVALID_PROVIDER_RESPONSE,IDEMPOTENCY_CONFLICT,INVALID_STATE,
        AUTHORIZATION_EXPIRED,METHOD_REQUIRED,OWNERSHIP_CONFLICT,ACCOUNT_NOT_VERIFIED,SIGNATURE_INVALID,
        WEBHOOK_NOT_CONFIGURED,INVALID_WEBHOOK,CONCURRENT_UPDATE }
    private final Code code;
    private final boolean retryable;
    public BillingFailure(Code code){this(code,false);}
    public BillingFailure(Code code,boolean retryable){super(code.name());this.code=code;this.retryable=retryable;}
    public Code code(){return code;}public boolean retryable(){return retryable;}
}
