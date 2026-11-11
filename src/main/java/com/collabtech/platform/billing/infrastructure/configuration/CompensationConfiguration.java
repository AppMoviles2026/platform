package com.collabtech.platform.billing.infrastructure.configuration;
import com.collabtech.platform.billing.application.ports.CompensationPorts.*;
import com.collabtech.platform.billing.application.ports.BillingPorts.*;
import com.collabtech.platform.billing.application.services.*;
import com.collabtech.platform.billing.domain.repositories.PaymentAccountRepository;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
@org.springframework.context.annotation.Configuration @Profile("!skeleton")
public class CompensationConfiguration {
    @Bean CompensationService compensationService(Context context,Store store,PaymentAccountRepository accounts,Queries methods,Provider provider,Secrets secrets,UnitOfWork work,Clock clock){
        return new CompensationService(context,store,accounts,methods,provider,secrets,work,clock);}
    @Bean CompensationEvents compensationEvents(Store store,CompensationService service,Secrets secrets,UnitOfWork work,Clock clock){
        return new CompensationEvents(store,service,secrets,work,clock);}
}
