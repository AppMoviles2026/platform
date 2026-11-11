package com.collabtech.platform.billing.infrastructure.acl;
import com.collabtech.platform.billing.interfaces.acl.PaymentReadinessFacade;
import com.collabtech.platform.billing.application.services.BillingService;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;
@Component @Profile("!skeleton")
public class PaymentReadinessAdapter implements PaymentReadinessFacade {
    private final BillingService service;
    public PaymentReadinessAdapter(BillingService service){this.service=service;}
    public Ready require(UUID accountId,UUID methodId){var value=service.requireUsable(accountId,accountId,methodId);
        return new Ready(value.methodId(),value.purpose().name(),value.provider(),value.environment());}
}
