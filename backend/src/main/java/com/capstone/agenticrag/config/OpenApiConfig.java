package com.capstone.agenticrag.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** OpenAPI description served at /v3/api-docs and rendered by Swagger UI at /swagger-ui.html. */
@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI apiDescription() {
        return new OpenAPI()
                .info(new Info()
                        .title("Agentic RAG Assistant API")
                        .version("1.0.0")
                        .description("""
                                Session-cookie API for the Agentic RAG Assistant. Sign in with POST /api/auth/login; \
                                the ARA_SESSION cookie then authenticates requests. State-changing requests must send \
                                the XSRF-TOKEN cookie value in the X-XSRF-TOKEN header (fetch it from GET /api/auth/csrf)."""))
                .components(new Components()
                        .addSecuritySchemes("session", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.COOKIE).name("ARA_SESSION"))
                        .addSecuritySchemes("csrf", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.HEADER).name("X-XSRF-TOKEN")))
                .addSecurityItem(new SecurityRequirement().addList("session").addList("csrf"));
    }
}
