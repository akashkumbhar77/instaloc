package com.skytech.instaloc.InstLoc.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

/**
 * Database configuration for production deployment
 * Converts Render DATABASE_URL to Spring JDBC format
 */
@Configuration
public class DatabaseConfig {

    @Value("${DB_URL:}")
    private String dbUrl;

    @Value("${DB_USER:}")
    private String dbUser;

    @Value("${DB_PASSWORD:}")
    private String dbPassword;

    @Value("${DB_DRIVER:org.h2.Driver}")
    private String dbDriver;

    @Bean
    @Primary
    public DataSource dataSource() {
        String jdbcUrl = convertToJdbcUrl(dbUrl);

        if (jdbcUrl == null) {
            // Fallback to H2 for development
            return DataSourceBuilder.create()
                    .url("jdbc:h2:mem:testdb")
                    .driverClassName("org.h2.Driver")
                    .build();
        }

        return DataSourceBuilder.create()
                .url(jdbcUrl)
                .username(dbUser)
                .password(dbPassword)
                .driverClassName(dbDriver)
                .build();
    }

    /**
     * Convert Render DATABASE_URL to JDBC URL
     * Render: postgres://user:pass@host:port/dbname
     * JDBC:  jdbc:postgresql://host:port/dbname
     */
    private String convertToJdbcUrl(String databaseUrl) {
        if (databaseUrl == null || databaseUrl.isBlank()) {
            return null;
        }

        // Already a JDBC URL
        if (databaseUrl.startsWith("jdbc:")) {
            return databaseUrl;
        }

        // Convert postgres:// to jdbc:postgresql://
        if (databaseUrl.startsWith("postgres://")) {
            return "jdbc:" + databaseUrl.substring("postgres".length());
        }

        return databaseUrl;
    }
}
