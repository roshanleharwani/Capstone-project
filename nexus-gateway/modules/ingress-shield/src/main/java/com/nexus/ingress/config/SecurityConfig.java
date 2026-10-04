package com.nexus.ingress.config;

import com.nexus.ingress.auth.DualLayerAuthFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security Configuration for Zero-Trust Ingress Enforcement Point (ZTEP).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final DualLayerAuthFilter dualLayerAuthFilter;

    public SecurityConfig(DualLayerAuthFilter dualLayerAuthFilter) {
        this.dualLayerAuthFilter = dualLayerAuthFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/**", "/health", "/v3/api-docs/**", "/api/v1/dashboard/**", "/api/v1/settings").permitAll()
                        .requestMatchers("/v1/chat/completions").authenticated()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(dualLayerAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
