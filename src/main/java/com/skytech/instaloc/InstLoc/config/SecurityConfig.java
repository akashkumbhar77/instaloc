package com.skytech.instaloc.InstLoc.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    @Value("${app.security.api-key:}")
    private String apiKey;

    @jakarta.annotation.PostConstruct
    public void init() {
        log.info("API Key loaded: present={}, length={}", !apiKey.isEmpty(), apiKey.length());
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints
                        .requestMatchers("/api/v1/health", "/api/v1/status").permitAll()
                        .requestMatchers("/actuator/**").permitAll()
                        // All other API endpoints require API key authentication
                        .requestMatchers("/api/v1/**").authenticated()
                        .anyRequest().authenticated())
                .addFilterBefore(apiKeyFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public OncePerRequestFilter apiKeyFilter() {
        return new ApiKeyFilter(apiKey);
    }

    /**
     * Custom filter that validates API key from X-API-KEY header
     */
    static class ApiKeyFilter extends OncePerRequestFilter {

        private static final String API_KEY_HEADER = "X-API-KEY";
        private final String validApiKey;

        public ApiKeyFilter(String validApiKey) {
            this.validApiKey = validApiKey;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                        FilterChain filterChain) throws ServletException, IOException {

            String path = request.getRequestURI();

            // Skip authentication for public endpoints
            if (path.startsWith("/api/v1/health") || path.startsWith("/api/v1/status")
                    || path.startsWith("/actuator")) {
                filterChain.doFilter(request, response);
                return;
            }

            // Check API key for protected endpoints
            String apiKey = request.getHeader(API_KEY_HEADER);
            log.debug("API Key from request: present={}, expected length={}", apiKey != null, validApiKey.length());

            if (apiKey == null || apiKey.isEmpty()) {
                log.warn("Missing API key in request to {}", path);
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\": \"Missing X-API-KEY header\"}");
                return;
            }

            if (!apiKey.equals(validApiKey)) {
                log.warn("Invalid API key in request to {} - key length: {}", path, apiKey.length());
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\": \"Invalid API key\"}");
                return;
            }

            // API key is valid - allow the request
            filterChain.doFilter(request, response);
        }
    }
}
