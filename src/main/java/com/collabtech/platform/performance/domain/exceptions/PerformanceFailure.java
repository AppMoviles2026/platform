package com.collabtech.platform.performance.domain.exceptions;
public final class PerformanceFailure extends RuntimeException {
    public enum Code { NOT_FOUND,FORBIDDEN,ACCOUNT_NOT_ACTIVE,COLLABORATION_CLOSED,SOURCE_NOT_CURRENT,
        SOURCE_NOT_PUBLISHED,POST_URL_MISMATCH,IDEMPOTENCY_CONFLICT,CONCURRENT_UPDATE,EVIDENCE_ALREADY_REVIEWED,
        ASSET_NOT_AVAILABLE,UNSUPPORTED_FILE,FILE_TOO_LARGE }
    private final Code code;
    public PerformanceFailure(Code code){super(code.name());this.code=code;}
    public Code code(){return code;}
}
