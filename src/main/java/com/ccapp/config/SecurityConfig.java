package com.ccapp.config;

import com.ccapp.admin.security.AdminJwtAuthenticationFilter;
import com.ccapp.config.security.ApiKeyAuthenticationFilter;
import com.ccapp.config.security.FirebaseAuthenticationFilter;
import com.ccapp.config.security.RestAccessDeniedHandler;
import com.ccapp.config.security.RestAuthenticationEntryPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    private final AdminJwtAuthenticationFilter adminJwtAuthenticationFilter;
    private final ApiKeyAuthenticationFilter apiKeyAuthenticationFilter;
    private final FirebaseAuthenticationFilter firebaseAuthenticationFilter;
    private final RestAuthenticationEntryPoint restAuthenticationEntryPoint;
    private final RestAccessDeniedHandler restAccessDeniedHandler;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(restAuthenticationEntryPoint)
                        .accessDeniedHandler(restAccessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info", "/error").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/ws/**").permitAll()
                        .requestMatchers("/api/admin/auth/**").permitAll()
                        .requestMatchers("/admin/auth/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/public/countries").permitAll()
                        .requestMatchers(HttpMethod.GET, "/public/activity-sectors").permitAll()
                        .requestMatchers(HttpMethod.GET, "/public/income-brackets").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/mobile/auth/social-login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/mobile/onboarding/plans").permitAll()
                        .requestMatchers("/api/admin/**").hasAnyAuthority(
                                "ROLE_SUPER_ADMIN",
                                "ROLE_COUNTRY_ADMIN",
                                "ROLE_IMF_ADMIN",
                                "ROLE_OPERATIONS",
                                "ROLE_SUPPORT"
                        )
                        .requestMatchers("/admin/**").hasAnyAuthority(
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

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "Origin", "X-Requested-With"));
        configuration.setExposedHeaders(List.of("Authorization"));
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
