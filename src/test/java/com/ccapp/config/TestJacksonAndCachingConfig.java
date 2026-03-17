package com.ccapp.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
public class TestJacksonAndCachingConfig {

    @Bean
    ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
