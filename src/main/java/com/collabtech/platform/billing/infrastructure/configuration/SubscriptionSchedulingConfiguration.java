package com.collabtech.platform.billing.infrastructure.configuration;
import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
@org.springframework.context.annotation.Configuration @Profile("!skeleton")
@ConditionalOnProperty(name="billing.subscriptions.scheduling-enabled",havingValue="true",matchIfMissing=true)
public class SubscriptionSchedulingConfiguration {
    @Bean(name="taskScheduler") @ConditionalOnMissingBean(name="taskScheduler")
    ThreadPoolTaskScheduler defaultScheduler(@Value("${spring.task.scheduling.pool.size:1}")int size){
        var scheduler=new ThreadPoolTaskScheduler();scheduler.setPoolSize(Math.max(1,size));scheduler.setThreadNamePrefix("collabpro-scheduled-");return scheduler;
    }
    @Bean(name="billingSubscriptionScheduler") ThreadPoolTaskScheduler billingSubscriptionScheduler(){
        var scheduler=new ThreadPoolTaskScheduler();scheduler.setPoolSize(1);scheduler.setThreadNamePrefix("billing-subscription-");return scheduler;
    }
}
