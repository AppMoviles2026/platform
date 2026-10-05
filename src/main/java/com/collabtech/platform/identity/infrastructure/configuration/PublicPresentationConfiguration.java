package com.collabtech.platform.identity.infrastructure.configuration;

import com.collabtech.platform.identity.application.handlers.GetPresentationQueryHandler;
import com.collabtech.platform.identity.application.ports.PresentationContentProvider;
import com.collabtech.platform.identity.infrastructure.content.PropertiesPresentationContentProvider;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

@Configuration(proxyBeanMethods = false)
public class PublicPresentationConfiguration {
    @Bean
    PresentationContentProvider presentationContentProvider() throws IOException {
        var resource = new ClassPathResource("identity/presentations/presentations_es.properties");
        try (var reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
            return new PropertiesPresentationContentProvider(reader);
        }
    }

    @Bean
    GetPresentationQueryHandler getPresentationQueryHandler(PresentationContentProvider contentProvider) {
        return new GetPresentationQueryHandler(contentProvider);
    }
}
