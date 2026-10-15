package com.tfzhang.linuxback.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    static final String INSECURE_DEFAULT_SECRET = "dev-only-hmac-secret-change-me-32b!";

    private String secret;
    private int ttlHours = 8;

    @PostConstruct
    public void validateSecret() {
        if (secret == null || secret.isBlank() || secret.length() < 32
                || INSECURE_DEFAULT_SECRET.equals(secret)) {
            throw new IllegalStateException(
                    "app.jwt.secret is missing, shorter than 32 characters, or still the insecure default");
        }
    }
}
