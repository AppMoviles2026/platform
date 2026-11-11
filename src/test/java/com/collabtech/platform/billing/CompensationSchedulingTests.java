package com.collabtech.platform.billing;
import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
@SpringBootTest(properties={"billing.compensation.scheduling-enabled=true","billing.subscriptions.scheduling-enabled=false"})
@ActiveProfiles("test") @DirtiesContext
class CompensationSchedulingTests {
    @Autowired @Qualifier("taskScheduler") ThreadPoolTaskScheduler defaults;
    @Autowired @Qualifier("billingCompensationScheduler") ThreadPoolTaskScheduler compensation;
    @Autowired @Qualifier("billingSubscriptionScheduler") ThreadPoolTaskScheduler subscription;
    @Test void compensationCanRunWithoutSubscriptionsAndCannotBlockOtherWorkers()throws Exception{
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        compensation.execute(()->{entered.countDown();try{release.await(5,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}});
        try{assertTrue(entered.await(2,TimeUnit.SECONDS));var other=new CompletableFuture<Boolean>();var billing=new CompletableFuture<Boolean>();
            defaults.execute(()->other.complete(true));subscription.execute(()->billing.complete(true));assertTrue(other.get(2,TimeUnit.SECONDS));assertTrue(billing.get(2,TimeUnit.SECONDS));}
        finally{release.countDown();}
    }
}
