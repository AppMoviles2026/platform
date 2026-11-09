package com.collabtech.platform.collaboration.domain.exceptions;

public final class CollaborationFailure extends RuntimeException {
    public enum Code { NOT_FOUND, FORBIDDEN, ACCOUNT_NOT_ACTIVE, SOURCE_NOT_SELECTED, TERMS_EXPIRED, TERMS_CHANGED, AGREEMENT_CLOSED,
        DECISION_ALREADY_RECORDED, AGREEMENT_NOT_ACCEPTED, CONCURRENT_UPDATE, INCOMPLETE_COLLABORATION, COLLABORATION_CLOSED, QUANTITY_EXCEEDED,
        STALE_SUBMISSION, DELIVERABLE_ALREADY_REVIEWED, REVISION_NOT_ALLOWED, CORRECTION_WINDOW_CLOSED,
        IDEMPOTENCY_CONFLICT, ASSET_NOT_AVAILABLE, UNSUPPORTED_FILE, FILE_TOO_LARGE }
    private final Code code;
    public CollaborationFailure(Code code){super(code.name());this.code=code;}
    public Code code(){return code;}
}
