package com.ccapp.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("dev")
public class OpenApiConfig {

    @Bean
    public OpenAPI ccAppOpenApi() {
        return new OpenAPI().info(new Info()
                .title("cc-app-backend API")
                .version("v1")
                .description("Modular monolithic fintech backend"));
    }
}
