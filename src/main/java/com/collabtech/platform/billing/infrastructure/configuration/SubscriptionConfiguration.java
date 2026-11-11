package com.collabtech.platform.billing.infrastructure.configuration;
import com.collabtech.platform.billing.application.ports.SubscriptionPorts.*;
import com.collabtech.platform.billing.application.ports.BillingPorts.*;
import com.collabtech.platform.billing.application.services.*;
import com.collabtech.platform.billing.domain.repositories.PaymentAccountRepository;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
@org.springframework.context.annotation.Configuration @Profile("!skeleton")
public class SubscriptionConfiguration {
    @Bean SubscriptionService subscriptionService(Store store,PaymentAccountRepository accounts,Access access,Provider provider,Secrets secrets,UnitOfWork work,Clock clock){
        return new SubscriptionService(store,accounts,access,provider,secrets,work,clock);}
    @Bean SubscriptionEvents subscriptionEvents(Store store,PaymentAccountRepository accounts,Secrets secrets,UnitOfWork work,SubscriptionService service,Clock clock){
        return new SubscriptionEvents(store,accounts,secrets,work,service,clock);}
}
