package com.ccapp.config;

import com.ccapp.admin.security.AdminJwtAuthenticationFilter;
import com.ccapp.config.security.ApiKeyAuthenticationFilter;
import com.ccapp.config.security.FirebaseAuthenticationFilter;
import com.ccapp.config.security.RestAuthenticationEntryPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final AdminJwtAuthenticationFilter adminJwtAuthenticationFilter;
    private final ApiKeyAuthenticationFilter apiKeyAuthenticationFilter;
    private final FirebaseAuthenticationFilter firebaseAuthenticationFilter;
    private final RestAuthenticationEntryPoint restAuthenticationEntryPoint;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(restAuthenticationEntryPoint))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/info", "/error").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/api/admin/auth/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/public/countries").permitAll()
                        .requestMatchers("/api/admin/**").hasAnyAuthority(
                                "ROLE_SUPER_ADMIN",
                                "ROLE_COUNTRY_ADMIN",
                                "ROLE_IMF_ADMIN",
                                "ROLE_OPERATIONS",
                                "ROLE_SUPPORT"
                        )
                        .requestMatchers(HttpMethod.POST, "/psp/jawupay/callback").permitAll()
                        .requestMatchers("/internal/**").hasAuthority("INTERNAL")
                        .anyRequest().authenticated())
                .addFilterBefore(apiKeyAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(adminJwtAuthenticationFilter, ApiKeyAuthenticationFilter.class)
                .addFilterAfter(firebaseAuthenticationFilter, AdminJwtAuthenticationFilter.class);

        return http.build();
    }
}
