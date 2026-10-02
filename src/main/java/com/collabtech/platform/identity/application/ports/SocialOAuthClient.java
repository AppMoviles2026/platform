package com.collabtech.platform.identity.application.ports;

import com.collabtech.platform.identity.domain.model.valueobjects.SocialPlatform;
import java.net.URI;

/** External protocol port; credentials and provider token persistence remain in infrastructure. */
public interface SocialOAuthClient {
    URI authorizationUri(SocialPlatform platform, String state);
    AuthorizedAccount exchangeCode(SocialPlatform platform, String authorizationCode);
    record AuthorizedAccount(String externalAccountId, String username) {}
}
