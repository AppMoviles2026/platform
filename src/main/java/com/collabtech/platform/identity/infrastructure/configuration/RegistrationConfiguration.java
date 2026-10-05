package com.collabtech.platform.identity.infrastructure.configuration;

import com.collabtech.platform.identity.application.handlers.RegisterBrandCommandHandler;
import com.collabtech.platform.identity.application.handlers.RegisterCreatorCommandHandler;
import com.collabtech.platform.identity.application.ports.PasswordHasher;
import com.collabtech.platform.identity.domain.repositories.AccountRepository;
import com.collabtech.platform.identity.infrastructure.security.Pbkdf2PasswordHasher;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("!skeleton")
public class RegistrationConfiguration {
    @Bean PasswordHasher passwordHasher() { return new Pbkdf2PasswordHasher(); }
    @Bean Clock identityClock() { return Clock.systemUTC(); }
    @Bean RegisterBrandCommandHandler registerBrandCommandHandler(AccountRepository accounts, PasswordHasher passwords, Clock clock) {
        return new RegisterBrandCommandHandler(accounts, passwords, clock);
    }
    @Bean RegisterCreatorCommandHandler registerCreatorCommandHandler(AccountRepository accounts, PasswordHasher passwords, Clock clock) {
        return new RegisterCreatorCommandHandler(accounts, passwords, clock);
    }
}
