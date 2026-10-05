package com.collabtech.platform.campaign.domain.exceptions;

/** Business failures, independent of REST and persistence technology. */
public final class CampaignFailure extends RuntimeException {
    public enum Code { INVALID_CONDITIONS, INCOMPLETE_CAMPAIGN, CAMPAIGN_NOT_DRAFT, CAMPAIGN_NOT_FOUND, FORBIDDEN, BRAND_REQUIRED, CREATOR_REQUIRED, ACCOUNT_NOT_ACTIVE,
        CAMPAIGN_NOT_ACCEPTING_APPLICATIONS, APPLICATION_ALREADY_EXISTS, APPLICATION_NOT_FOUND, APPLICATION_NOT_PENDING, INVALID_CONFIRMATION, CONCURRENT_UPDATE }
    private final Code code;
    public CampaignFailure(Code code) { super(code.name()); this.code = code; }
    public Code code() { return code; }
}
