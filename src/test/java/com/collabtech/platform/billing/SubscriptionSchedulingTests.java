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
@SpringBootTest(properties="billing.subscriptions.scheduling-enabled=true") @ActiveProfiles("test") @DirtiesContext
class SubscriptionSchedulingTests {
    @Autowired @Qualifier("taskScheduler") ThreadPoolTaskScheduler defaults;
    @Autowired @Qualifier("billingSubscriptionScheduler") ThreadPoolTaskScheduler billing;
    @Test void blockedBillingWorkerDoesNotBlockOtherScheduledTasks()throws Exception{
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        billing.execute(()->{entered.countDown();try{release.await(5,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}});
        try{assertTrue(entered.await(2,TimeUnit.SECONDS));var other=new CompletableFuture<Boolean>();defaults.execute(()->other.complete(true));assertTrue(other.get(2,TimeUnit.SECONDS));}
        finally{release.countDown();}
    }
}
