package com.skytech.instaloc.InstLoc.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.client.RestTemplate;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

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
                        // All other API endpoints require authentication
                        .requestMatchers("/api/v1/**").authenticated()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.decoder(jwtDecoder())));

        return http.build();
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        // Use Supabase's userinfo endpoint to validate tokens
        // This is more reliable than JWKS when running in environments with limited network access
        String supabaseUrl = "https://ltsklagfqeqphqttrahy.supabase.co";
        return new SupabaseUserinfoJwtDecoder(supabaseUrl);
    }

    /**
     * Custom JWT decoder that validates tokens via Supabase's userinfo endpoint
     */
    static class SupabaseUserinfoJwtDecoder implements JwtDecoder {
        private final String supabaseUrl;
        private final RestTemplate restTemplate;

        public SupabaseUserinfoJwtDecoder(String supabaseUrl) {
            this.supabaseUrl = supabaseUrl;
            this.restTemplate = new RestTemplate();
        }

        @Override
        public Jwt decode(String token) {
            try {
                // Validate token by calling Supabase's userinfo endpoint
                String userinfoUrl = supabaseUrl + "/auth/v1/userinfo";
                org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
                headers.setBearerAuth(token);

                org.springframework.http.HttpEntity<String> entity = new org.springframework.http.HttpEntity<>(headers);
                org.springframework.http.ResponseEntity<String> response = restTemplate.exchange(
                        userinfoUrl,
                        org.springframework.http.HttpMethod.GET,
                        entity,
                        String.class
                );

                if (response.getStatusCode() == org.springframework.http.HttpStatus.OK) {
                    // Token is valid, parse the userinfo response
                    com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                    com.fasterxml.jackson.databind.JsonNode userInfo = mapper.readTree(response.getBody());

                    // Extract claims from userinfo
                    String userId = userInfo.has("id") ? userInfo.get("id").asText() : "";
                    String email = userInfo.has("email") ? userInfo.get("email").asText() : "";
                    String role = userInfo.has("role") ? userInfo.get("role").asText() : "";

                    // Build JWT with claims from validated token
                    Jwt.Builder builder = Jwt.withTokenValue(token)
                            .header("alg", "ES256")
                            .subject(userId)
                            .claim("email", email)
                            .claim("role", role)
                            .issuedAt(java.time.Instant.now())
                            .expiresAt(java.time.Instant.now().plusSeconds(3600));

                    // Add any additional claims from userinfo
                    userInfo.fields().forEachRemaining(entry -> {
                        if (!builder.getClaims().containsKey(entry.getKey())) {
                            builder.claim(entry.getKey(), entry.getValue().asText());
                        }
                    });

                    return builder.build();
                } else {
                    throw new org.springframework.security.oauth2.jwt.JwtValidationException(
                            "Token validation failed: " + response.getStatusCode(),
                            java.util.Collections.emptyList()
                    );
                }
            } catch (Exception e) {
                throw new org.springframework.security.oauth2.jwt.JwtValidationException(
                        "Token validation failed: " + e.getMessage(),
                        java.util.Collections.emptyList()
                );
            }
        }
    }
}
