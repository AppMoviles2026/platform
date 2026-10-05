package com.collabtech.platform.identity.application.ports;

import com.collabtech.platform.identity.domain.model.valueobjects.AccountId;
import com.collabtech.platform.identity.domain.model.valueobjects.SocialPlatform;

public interface AuthorizationStateStore {
    String create(AccountId owner, SocialPlatform platform);
    Authorization consume(String state, SocialPlatform expectedPlatform);
    record Authorization(AccountId owner, SocialPlatform platform) {}
}
