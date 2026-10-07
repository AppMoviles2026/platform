package com.collabtech.platform.identity.application.ports;

import com.collabtech.platform.identity.domain.model.valueobjects.AccountId;
import com.collabtech.platform.identity.domain.model.valueobjects.SocialPlatform;
import com.collabtech.platform.identity.application.projections.IdentityViews;
import java.util.UUID;

public interface AuthorizationStateStore {
    PendingAuthorization create(AccountId owner, SocialPlatform platform, AuthorizationClient client);
    Authorization consume(String state, SocialPlatform expectedPlatform);
    void finish(UUID authorizationId, String status, String errorCode);
    IdentityViews.AuthorizationStatusView find(UUID authorizationId, AccountId owner);
    record PendingAuthorization(String state, UUID authorizationId) {
        @Override public String toString() { return "PendingAuthorization[state=<redacted>]"; }
    }
    record Authorization(AccountId owner, SocialPlatform platform, UUID authorizationId, AuthorizationClient client) {}
}
