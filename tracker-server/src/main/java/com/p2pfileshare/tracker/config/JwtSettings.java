package com.p2pfileshare.tracker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.jwt")
public record JwtSettings(String secret, long expirationMinutes) {
    public JwtSettings {
        if (secret == null || secret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT_SECRET is required and must contain at least 32 bytes.");
        }
        if (expirationMinutes < 1) throw new IllegalStateException("app.jwt.expiration-minutes must be positive.");
    }
}
