package com.tfzhang.linuxback.security;

import com.tfzhang.linuxback.common.AuthException;
import com.tfzhang.linuxback.config.JwtProperties;
import com.tfzhang.linuxback.entity.User;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {

    private JwtService jwtService;
    private JwtProperties properties;

    @BeforeEach
    void setUp() {
        properties = new JwtProperties();
        properties.setSecret("test-secret-at-least-32-bytes-long!!");
        properties.setTtlHours(8);
        jwtService = new JwtService(properties);
    }

    @Test
    void createAndParse_roundTripsClaimsIncludingTv() {
        User user = new User();
        user.setId(42L);
        user.setUserType("teacher");
        user.setUserRole("user");
        user.setTokenVersion(3);

        String token = jwtService.createToken(user);
        JwtPrincipal principal = jwtService.parse(token);

        assertEquals(42L, principal.id());
        assertEquals("teacher", principal.userType());
        assertEquals("user", principal.userRole());
        assertEquals(3, principal.tv());
    }

    @Test
    void createAndParse_nullTokenVersion_isZero() {
        User user = new User();
        user.setId(42L);
        user.setUserType("teacher");
        user.setUserRole("user");

        JwtPrincipal principal = jwtService.parse(jwtService.createToken(user));
        assertEquals(0, principal.tv());
    }

    @Test
    void parse_missingTvClaim_isZero() {
        SecretKey key = Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
        Instant now = Instant.now();
        String token = Jwts.builder()
                .subject("7")
                .claim("userType", "student")
                .claim("userRole", "user")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(8, ChronoUnit.HOURS)))
                .signWith(key)
                .compact();

        JwtPrincipal principal = jwtService.parse(token);
        assertEquals(7L, principal.id());
        assertEquals(0, principal.tv());
    }

    @Test
    void parse_tamperedToken_throwsUnauthorized() {
        User user = new User();
        user.setId(1L);
        user.setUserType("teacher");
        user.setUserRole("user");

        String token = jwtService.createToken(user);
        String tampered = token.substring(0, token.length() - 2) + "xx";

        AuthException ex = assertThrows(AuthException.class, () -> jwtService.parse(tampered));
        assertEquals("UNAUTHORIZED", ex.getCode());
    }

    @Test
    void parse_wrongSecret_throwsUnauthorized() {
        User user = new User();
        user.setId(1L);
        user.setUserType("teacher");
        user.setUserRole("user");

        String token = jwtService.createToken(user);

        JwtProperties other = new JwtProperties();
        other.setSecret("other-secret-at-least-32-bytes-long!");
        other.setTtlHours(8);
        JwtService otherService = new JwtService(other);

        AuthException ex = assertThrows(AuthException.class, () -> otherService.parse(token));
        assertEquals("UNAUTHORIZED", ex.getCode());
    }

    @Test
    void parse_expiredToken_throwsUnauthorized() {
        properties.setTtlHours(-1);
        jwtService = new JwtService(properties);

        User user = new User();
        user.setId(1L);
        user.setUserType("teacher");
        user.setUserRole("user");

        String token = jwtService.createToken(user);

        AuthException ex = assertThrows(AuthException.class, () -> jwtService.parse(token));
        assertEquals("UNAUTHORIZED", ex.getCode());
    }

    @Test
    void constructor_rejectsInsecureDefaultSecret() {
        JwtProperties bad = new JwtProperties();
        bad.setSecret("dev-only-hmac-secret-change-me-32b!");
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> new JwtService(bad));
        assertTrue(ex.getMessage().contains("app.jwt.secret"));
        assertFalse(ex.getMessage().contains("dev-only-hmac-secret-change-me-32b!"));
    }

    @Test
    void constructor_rejectsShortSecret() {
        JwtProperties bad = new JwtProperties();
        bad.setSecret("too-short");
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> new JwtService(bad));
        assertTrue(ex.getMessage().contains("app.jwt.secret"));
        assertFalse(ex.getMessage().contains("too-short"));
    }
}
