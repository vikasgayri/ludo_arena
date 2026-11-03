package com.ludoarena.security;

import com.ludoarena.config.AppProperties;
import com.ludoarena.model.Enums.Role;
import com.ludoarena.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private final SecretKey key;
    private final long accessTtlSeconds;

    public JwtService(AppProperties props) {
        String secret = props.jwt().secret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT_SECRET must be set to at least 32 bytes (generate one with: openssl rand -base64 48)");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTtlSeconds = props.jwt().accessTokenMinutes() * 60L;
    }

    public long accessTtlSeconds() {
        return accessTtlSeconds;
    }

    public String createAccessToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.id)
                .claim("role", user.role.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(accessTtlSeconds)))
                .signWith(key)
                .compact();
    }

    /** @return the user id when the token is valid and unexpired, otherwise empty */
    public Optional<String> validateAndGetUserId(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            return Optional.ofNullable(claims.getSubject());
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public static Role roleOf(Claims claims) {
        return Role.valueOf(claims.get("role", String.class));
    }
}
