package com.collabtech.platform.billing.infrastructure.acl;
import com.collabtech.platform.billing.application.ports.BillingPorts.*;
import com.collabtech.platform.billing.domain.exceptions.BillingFailure;
import com.collabtech.platform.billing.domain.exceptions.BillingFailure.Code;
import com.collabtech.platform.identity.interfaces.acl.IdentityProfileFacade;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;
@Component @Profile("!skeleton")
public class BillingAccessAdapter implements Access {
    private final IdentityProfileFacade profiles;
    public BillingAccessAdapter(IdentityProfileFacade profiles){this.profiles=profiles;}
    public Actor actor(UUID account){var profile=profiles.byAccount(account).filter(IdentityProfileFacade.Profile::active)
            .orElseThrow(()->new BillingFailure(Code.ACCOUNT_NOT_ACTIVE));return new Actor(account,profile.type());}
}
