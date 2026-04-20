package com.phoneserver.controlplane.security;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private final String secret;
    private final long accessTokenExpirationMs;

    public JwtService(
            @Value("${app.security.jwt.secret}") String secret,
            @Value("${app.security.jwt.access-token-expiration-ms:86400000}") long accessTokenExpirationMs
    ) {
        this.secret = secret;
        this.accessTokenExpirationMs = accessTokenExpirationMs;
    }

    @PostConstruct
    public void validateSecretLength() {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT secret must be at least 32 bytes long.");
        }
    }

    public String generateToken(AppUserPrincipal principal) {
        Date issuedAt = new Date();
        Date expiration = new Date(issuedAt.getTime() + accessTokenExpirationMs);

        return Jwts.builder()
                .subject(principal.getUserId().toString())
                .claim("email", principal.getUsername())
                .issuedAt(issuedAt)
                .expiration(expiration)
                .signWith(getSigningKey())
                .compact();
    }

    public UUID extractUserId(String token) {
        return UUID.fromString(extractAllClaims(token).getSubject());
    }

    public boolean isTokenValid(String token, AppUserPrincipal principal) {
        UUID tokenUserId = extractUserId(token);
        return tokenUserId.equals(principal.getUserId()) && !isTokenExpired(token);
    }

    private boolean isTokenExpired(String token) {
        return extractAllClaims(token).getExpiration().before(new Date());
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
