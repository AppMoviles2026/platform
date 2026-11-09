package com.collabtech.platform.identity.application.ports;

import com.collabtech.platform.identity.domain.model.valueobjects.SocialPlatform;
import java.net.URI;
import java.util.UUID;
import com.collabtech.platform.identity.domain.model.valueobjects.SocialMediaAccountId;

/** External protocol port; credentials and provider token persistence remain in infrastructure. */
public interface SocialOAuthClient {
    URI authorizationUri(SocialPlatform platform, String state);
    AuthorizedAccount exchangeCode(SocialPlatform platform, String authorizationCode);
    void attachCredentials(UUID receipt, SocialMediaAccountId socialId);
    /** Replace credentials of the same verified identity only when renewal/extra consent is needed. */
    boolean replaceUnavailableCredentials(UUID receipt, SocialMediaAccountId socialId);
    record AuthorizedAccount(String externalAccountId, String username, UUID credentialReceipt) {}
}
