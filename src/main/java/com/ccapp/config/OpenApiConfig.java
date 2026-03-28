package com.ccapp.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.LinkedHashMap;
import java.util.Map;

@Configuration
@Profile("dev")
public class OpenApiConfig {

    @Bean
    public OpenAPI ccAppOpenApi() {
        return new OpenAPI().info(new Info()
                .title("cc-backend-monolitic API")
                .version("v1")
                .description("Modular monolithic fintech backend"))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT"))
                        .addSecuritySchemes("apiKeyAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-API-KEY")));
    }

    @Bean
    public OpenApiCustomizer commonResponseExamplesCustomizer() {
        return openApi -> {
            if (openApi.getPaths() == null) {
                return;
            }
            openApi.getPaths().values().forEach(pathItem -> pathItem.readOperations().forEach(this::ensureOperationExamples));
        };
    }

    private void ensureOperationExamples(Operation operation) {
        if (operation.getResponses() == null) {
            return;
        }
        operation.getResponses().forEach((code, response) -> ensureResponseExample(code, response));
    }

    private void ensureResponseExample(String responseCode, ApiResponse response) {
        Content content = response.getContent();
        if (content == null) {
            content = new Content();
            response.setContent(content);
        }
        MediaType jsonMediaType = content.get("application/json");
        if (jsonMediaType == null) {
            jsonMediaType = new MediaType();
            content.addMediaType("application/json", jsonMediaType);
        }

        boolean hasSingleExample = jsonMediaType.getExample() != null;
        boolean hasNamedExamples = jsonMediaType.getExamples() != null && !jsonMediaType.getExamples().isEmpty();
        if (hasSingleExample || hasNamedExamples) {
            return;
        }

        jsonMediaType.setExample(defaultExampleForCode(responseCode));
    }

    private Map<String, Object> defaultExampleForCode(String responseCode) {
        Map<String, Object> example = new LinkedHashMap<>();
        boolean success = responseCode != null && responseCode.startsWith("2");
        example.put("success", success);
        example.put("message", success ? "Operation reussie" : "Operation echouee");
        example.put("data", success ? Map.of() : null);
        example.put("timestamp", "2026-03-10T12:00:00Z");
        return example;
    }
}
