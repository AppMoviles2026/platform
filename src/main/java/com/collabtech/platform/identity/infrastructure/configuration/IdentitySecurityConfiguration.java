package com.collabtech.platform.identity.infrastructure.configuration;

import com.collabtech.platform.identity.application.ports.AccessTokenVerifier;
import com.collabtech.platform.identity.infrastructure.security.BearerSessionFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

@Configuration(proxyBeanMethods = false)
@Profile("!skeleton")
public class IdentitySecurityConfiguration {
    @Bean org.springframework.security.core.userdetails.UserDetailsService disabledDefaultUsers() {
        return username -> { throw new org.springframework.security.core.userdetails.UsernameNotFoundException("No default users"); };
    }
    @Bean SecurityFilterChain identitySecurity(HttpSecurity http, AccessTokenVerifier tokens) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/brands", "/api/v1/auth/creators",
                                "/api/v1/auth/sessions", "/api/v1/auth/recovery-requests", "/api/v1/auth/password-resets").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/social-accounts/*/callback").permitAll()
                        .requestMatchers("/error", "/api/v1/public/presentations/**").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, failure) -> {
                            response.setStatus(401); response.setHeader("WWW-Authenticate", "Bearer");
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write("{\"code\":\"UNAUTHORIZED\",\"message\":\"Se requiere una sesión válida.\",\"fieldErrors\":{}}");
                        })
                        .accessDeniedHandler((request, response, failure) -> {
                            response.setStatus(403); response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write("{\"code\":\"FORBIDDEN\",\"message\":\"Acceso no permitido.\",\"fieldErrors\":{}}");
                        }))
                .addFilterBefore(new BearerSessionFilter(tokens), AnonymousAuthenticationFilter.class).build();
    }
}
