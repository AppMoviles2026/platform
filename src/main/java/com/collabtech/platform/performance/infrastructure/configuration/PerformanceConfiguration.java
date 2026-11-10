package com.collabtech.platform.performance.infrastructure.configuration;
import com.collabtech.platform.performance.application.ports.PerformancePorts.*;
import com.collabtech.platform.performance.application.services.PerformanceService;
import com.collabtech.platform.performance.infrastructure.persistence.PerformancePersistence;
import com.collabtech.platform.performance.infrastructure.providers.AuthorizedSocialMetricsProvider;
import com.collabtech.platform.identity.interfaces.acl.SocialAuthorizationFacade;
import java.time.*;
import java.net.http.HttpClient;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;
@Configuration(proxyBeanMethods=false) @Profile("!skeleton")
public class PerformanceConfiguration {
    @Bean UnitOfWork performanceUnitOfWork(PlatformTransactionManager manager) {
        var tx=new TransactionTemplate(manager);tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        return new UnitOfWork(){public <T>T execute(Supplier<T> task){return tx.execute(s->task.get());}};
    }
    @Bean PerformancePersistence performancePersistence(JdbcTemplate jdbc,ObjectMapper json,@Value("${performance.files.max-bytes:10485760}")long max) {
        return new PerformancePersistence(jdbc,json,max);
    }
    @Bean SocialMetricsProvider socialMetricsProvider(SocialAuthorizationFacade authorization,Clock clock) {
        var factory=new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build());
        factory.setReadTimeout(Duration.ofSeconds(5));
        return new AuthorizedSocialMetricsProvider(authorization,RestClient.builder().requestFactory(factory).build(),clock);
    }
    @Bean PerformanceService performanceService(Access access,UnitOfWork work,PerformancePersistence data,SocialMetricsProvider provider,Clock clock) {
        return new PerformanceService(access,work,data,data,data,data,provider,clock);
    }
}
