package com.collabtech.platform.collaboration.domain.exceptions;

public final class CollaborationFailure extends RuntimeException {
    public enum Code { NOT_FOUND, FORBIDDEN, ACCOUNT_NOT_ACTIVE, SOURCE_NOT_SELECTED, TERMS_EXPIRED, TERMS_CHANGED, AGREEMENT_CLOSED,
        DECISION_ALREADY_RECORDED, AGREEMENT_NOT_ACCEPTED, CONCURRENT_UPDATE, INCOMPLETE_COLLABORATION }
    private final Code code;
    public CollaborationFailure(Code code){super(code.name());this.code=code;}
    public Code code(){return code;}
}
