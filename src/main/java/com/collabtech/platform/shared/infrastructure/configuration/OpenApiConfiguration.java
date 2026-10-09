package com.collabtech.platform.shared.infrastructure.configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfiguration {
    @Bean
    OpenAPI collabProOpenApi() {
        return new OpenAPI()
                .info(new Info().title("CollabPro API").version("v1")
                        .description("Inicia sesión en /api/v1/auth/sessions y pega accessToken en Authorize para probar las rutas protegidas."))
                .components(new Components().addSecuritySchemes("bearerAuth", new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }

    @Bean
    OpenApiCustomizer publicOperations() {
        return api -> api.getPaths().forEach((path, item) -> {
            if (path.startsWith("/api/v1/auth/")) {
                var operation = item.getPost();
                if (operation != null) operation.setSecurity(List.of());
            }
            if (path.matches("/api/v1/social-accounts/[^/]+/callback")
                    || path.startsWith("/api/v1/public/presentations/")) {
                item.readOperations().forEach(operation -> operation.setSecurity(List.of()));
            }
        });
    }
}
