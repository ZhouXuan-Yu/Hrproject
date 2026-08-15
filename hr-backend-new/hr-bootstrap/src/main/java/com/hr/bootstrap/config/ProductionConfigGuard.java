package com.hr.bootstrap.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * Prevents a production process from starting with known development secrets or open CORS.
 */
@Component
@Profile("prod")
public class ProductionConfigGuard {

    private static final String DEV_JWT_SECRET = "dev-jwt-secret-b4e8f1a3d6c9-change-in-production";
    private static final String DEV_PASSWORD_SALT = "default-salt-change-me";
    private static final String DEV_CRYPTO_SECRET = "dev-secret-key-a7f3b9c2e1d4-change-in-production";

    @Value("${jwt.secret:}")
    private String jwtSecret;

    @Value("${password.salt:}")
    private String passwordSalt;

    @Value("${crypto.secret-key:}")
    private String cryptoSecret;

    @Value("${spring.datasource.url:}")
    private String databaseUrl;

    @Value("${spring.datasource.password:}")
    private String databasePassword;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    @Value("${app.security.allowed-origins:}")
    private String allowedOrigins;

    @Value("${app.security.cookie-secure:false}")
    private boolean cookieSecure;

    @Value("${ai-service.internal-token:}")
    private String aiInternalToken;

    @PostConstruct
    void validate() {
        requireSecret("JWT_SECRET_KEY", jwtSecret, DEV_JWT_SECRET, 32);
        requireSecret("PASSWORD_SALT", passwordSalt, DEV_PASSWORD_SALT, 16);
        requireSecret("SECRET_KEY", cryptoSecret, DEV_CRYPTO_SECRET, 16);
        requireNonBlank("DATABASE_URL", databaseUrl);
        requireNonBlank("DATABASE_PASSWORD", databasePassword);
        requireNonBlank("REDIS_PASSWORD", redisPassword);
        requireNonBlank("APP_ALLOWED_ORIGINS", allowedOrigins);
        requireNonBlank("AI_INTERNAL_TOKEN", aiInternalToken);
        if (allowedOrigins.contains("*") || !cookieSecure) {
            throw new IllegalStateException("Production security requires explicit CORS origins and Secure cookies");
        }
    }

    private void requireSecret(String name, String value, String forbiddenDefault, int minLength) {
        if (value == null || value.isBlank() || value.equals(forbiddenDefault) || value.length() < minLength) {
            throw new IllegalStateException(name + " must be a strong production secret");
        }
    }

    private void requireNonBlank(String name, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be configured before starting the prod profile");
        }
    }
}
