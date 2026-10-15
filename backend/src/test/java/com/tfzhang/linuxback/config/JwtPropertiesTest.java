package com.tfzhang.linuxback.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtPropertiesTest {

    @Test
    void validateSecret_rejectsInsecureDefault() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("dev-only-hmac-secret-change-me-32b!");
        IllegalStateException ex = assertThrows(IllegalStateException.class, properties::validateSecret);
        assertTrue(ex.getMessage().contains("app.jwt.secret"));
        assertFalse(ex.getMessage().contains("dev-only-hmac-secret-change-me-32b!"));
    }

    @Test
    void validateSecret_rejectsBlankAndShort() {
        JwtProperties blank = new JwtProperties();
        blank.setSecret("");
        assertTrue(assertThrows(IllegalStateException.class, blank::validateSecret)
                .getMessage().contains("app.jwt.secret"));

        JwtProperties shortSecret = new JwtProperties();
        shortSecret.setSecret("only-31-characters-not-enough!!");
        assertEquals32(shortSecret.getSecret());
        assertTrue(assertThrows(IllegalStateException.class, shortSecret::validateSecret)
                .getMessage().contains("app.jwt.secret"));
    }

    private static void assertEquals32(String secret) {
        assertTrue(secret.length() < 32);
    }
}
