package com.collabtech.platform.identity.application.ports;

import com.collabtech.platform.identity.application.projections.IdentityViews;

public interface AccessTokenProvider {
    IdentityViews.SessionView issue(IdentityViews.AccountView account);
}
