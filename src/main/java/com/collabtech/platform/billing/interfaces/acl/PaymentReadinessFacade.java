package com.collabtech.platform.billing.interfaces.acl;
import java.util.UUID;
/** Future financial operations must call this prerequisite; no provider references leave Billing. */
public interface PaymentReadinessFacade {
    Ready require(UUID accountId,UUID methodId);
    record Ready(UUID methodId,String purpose,String provider,String environment){}
}
