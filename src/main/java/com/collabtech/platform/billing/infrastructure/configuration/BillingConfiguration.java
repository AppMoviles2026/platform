package com.collabtech.platform.billing.infrastructure.configuration;
import com.collabtech.platform.billing.application.ports.BillingPorts.*;
import com.collabtech.platform.billing.application.services.BillingService;
import com.collabtech.platform.billing.domain.repositories.PaymentAccountRepository;
import com.collabtech.platform.billing.infrastructure.security.BillingSecrets;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

@org.springframework.context.annotation.Configuration @Profile("!skeleton")
public class BillingConfiguration {
    @Bean Secrets billingSecrets(@Value("${billing.encryption-key}") String key,ObjectMapper json){return new BillingSecrets(key,json);}
    @Bean UnitOfWork billingUnitOfWork(PlatformTransactionManager manager){var tx=new TransactionTemplate(manager);tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        return new UnitOfWork(){public <T>T execute(java.util.function.Supplier<T> work){
            for(int attempt=0;;attempt++){
                try{return tx.execute(status->work.get());}
                catch(org.springframework.dao.PessimisticLockingFailureException conflict){
                    if(attempt>=3)throw conflict;
                    java.util.concurrent.locks.LockSupport.parkNanos(java.util.concurrent.ThreadLocalRandom.current().nextLong(5_000_000L,20_000_000L));
                }
            }
        }};}
    @Bean BillingService billingService(PaymentAccountRepository repo,Queries queries,Access access,Gateway gateway,Secrets secrets,UnitOfWork work,Clock clock,com.collabtech.platform.billing.application.ports.SubscriptionPorts.EventRouter subscriptionEvents){
        return new BillingService(repo,queries,access,gateway,secrets,work,clock,subscriptionEvents);
    }
}
