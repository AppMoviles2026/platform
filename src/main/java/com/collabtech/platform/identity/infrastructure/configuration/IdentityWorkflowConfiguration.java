package com.collabtech.platform.identity.infrastructure.configuration;

import com.collabtech.platform.identity.application.services.IdentityApplicationService;
import com.collabtech.platform.identity.application.handlers.RegisterBrandCommandHandler;
import com.collabtech.platform.identity.application.handlers.RegisterCreatorCommandHandler;
import com.collabtech.platform.identity.application.ports.*;
import com.collabtech.platform.identity.domain.repositories.AccountRepository;
import com.collabtech.platform.identity.infrastructure.security.*;
import com.collabtech.platform.identity.infrastructure.oauth.*;
import com.collabtech.platform.shared.application.security.CurrentActor;
import java.time.Clock;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import java.net.http.HttpClient;
import java.time.Duration;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@Profile("!skeleton")
public class IdentityWorkflowConfiguration {
    @Bean IdentityUnitOfWork identityUnitOfWork(PlatformTransactionManager manager) {
        var template = new TransactionTemplate(manager);
        return new IdentityUnitOfWork() {
            public <T> T execute(Supplier<T> work) { return template.execute(status -> work.get()); }
        };
    }
    @Bean SecretCipher identitySecretCipher(Environment env) {
        return new SecretCipher(env.getRequiredProperty("identity.encryption-key"));
    }
    @Bean DatabaseAccessTokenProvider accessTokenProvider(JdbcTemplate jdbc, Clock clock) {
        return new DatabaseAccessTokenProvider(jdbc, clock);
    }
    @Bean CurrentActor currentActor() { return new SecurityContextActor(); }
    @Bean AuthorizationStateStore authorizationStateStore(JdbcTemplate jdbc, Clock clock) {
        return new DatabaseAuthorizationStateStore(jdbc, clock);
    }
    @Bean AccountRecoveryService accountRecoveryService(JdbcTemplate jdbc, Clock clock, SecretCipher cipher,
            AccountRepository accounts, PlatformTransactionManager manager) {
        return new DatabaseRecoveryService(jdbc, clock, cipher, accounts, manager);
    }
    @Bean RecoveryMailDispatcher recoveryMailDispatcher(JdbcTemplate jdbc, SecretCipher cipher, JavaMailSender mail,
            Clock clock, PlatformTransactionManager manager, Environment env) {
        return new RecoveryMailDispatcher(jdbc, cipher, mail, clock, manager,
                env.getRequiredProperty("identity.recovery.reset-url"), env.getRequiredProperty("identity.recovery.from"),
                env.getProperty("identity.recovery.delivery-enabled", Boolean.class, true));
    }
    @Bean SocialOAuthClient socialOAuthClient(Environment env, JdbcTemplate jdbc, SecretCipher cipher, Clock clock) {
        var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        var factory = new JdkClientHttpRequestFactory(client); factory.setReadTimeout(Duration.ofSeconds(10));
        var http = RestClient.builder().requestFactory(factory).build();
        return new HttpSocialOAuthClient(Map.of(
                "tiktok", provider(env, "tiktok", "https://www.tiktok.com/v2/auth/authorize/",
                        "https://open.tiktokapis.com/v2/oauth/token/",
                        "https://open.tiktokapis.com/v2/user/info/?fields=open_id,display_name", "user.info.basic"),
                "instagram", provider(env, "instagram", "https://www.instagram.com/oauth/authorize",
                        "https://api.instagram.com/oauth/access_token",
                        "https://graph.instagram.com/me?fields=user_id,username", "instagram_business_basic")), http, jdbc, cipher, clock);
    }
    private static HttpSocialOAuthClient.Provider provider(Environment env, String name, String authorize, String token, String userInfo, String scopes) {
        String prefix = "identity.oauth." + name + ".";
        return new HttpSocialOAuthClient.Provider(env.getProperty(prefix + "client-id", ""),
                env.getProperty(prefix + "client-secret", ""), env.getProperty(prefix + "redirect-uri", ""),
                authorize, token, userInfo, scopes);
    }
    @Bean IdentityApplicationService identityApplicationService(AccountRepository accounts, PasswordHasher passwords,
            AccessTokenProvider tokens, AccountRecoveryService recovery, AuthorizationStateStore states,
            SocialOAuthClient social, IdentityUnitOfWork unit, RegisterBrandCommandHandler brands, RegisterCreatorCommandHandler creators) {
        return new IdentityApplicationService(accounts, passwords, tokens, recovery, states, social, unit, brands, creators);
    }
}
