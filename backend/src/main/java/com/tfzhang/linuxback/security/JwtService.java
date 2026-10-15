package com.tfzhang.linuxback.security;

import com.tfzhang.linuxback.common.AuthException;
import com.tfzhang.linuxback.config.JwtProperties;
import com.tfzhang.linuxback.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

@Service
public class JwtService {

    private final JwtProperties properties;
    private final SecretKey secretKey;

    public JwtService(JwtProperties properties) {
        properties.validateSecret();
        this.properties = properties;
        this.secretKey = Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String createToken(User user) {
        Instant now = Instant.now();
        Instant expiry = now.plus(properties.getTtlHours(), ChronoUnit.HOURS);
        int tv = user.getTokenVersion() == null ? 0 : user.getTokenVersion();
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("userType", user.getUserType())
                .claim("userRole", user.getUserRole())
                .claim("tv", tv)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(secretKey)
                .compact();
    }

    public JwtPrincipal parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            long id = Long.parseLong(claims.getSubject());
            String userType = claims.get("userType", String.class);
            String userRole = claims.get("userRole", String.class);
            Integer tv = claims.get("tv", Integer.class);
            return new JwtPrincipal(id, userType, userRole, tv == null ? 0 : tv);
        } catch (ExpiredJwtException ex) {
            throw AuthException.invalidToken();
        } catch (JwtException | NumberFormatException ex) {
            throw AuthException.invalidToken();
        }
    }
}
