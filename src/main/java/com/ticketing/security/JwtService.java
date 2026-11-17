package com.ticketing.security;

import com.ticketing.entity.Role;
import com.ticketing.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;

@Service
public class JwtService {

    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_ROLES = "roles";
    private static final String CLAIM_TYPE = "type";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";

    private final SecretKey key;
    private final Duration accessTokenTtl;
    private final Duration refreshTokenTtl;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.access-token-minutes}") long accessTokenMinutes,
                      @Value("${app.jwt.refresh-token-days}") long refreshTokenDays) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenTtl = Duration.ofMinutes(accessTokenMinutes);
        this.refreshTokenTtl = Duration.ofDays(refreshTokenDays);
    }

    public String generateAccessToken(User user) {
        List<String> roles = user.getRoles().stream().map(Role::name).toList();
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim(CLAIM_EMAIL, user.getEmail())
                .claim(CLAIM_ROLES, roles)
                .claim(CLAIM_TYPE, TYPE_ACCESS)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTokenTtl)))
                .signWith(key)
                .compact();
    }

    public String generateRefreshToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim(CLAIM_TYPE, TYPE_REFRESH)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(refreshTokenTtl)))
                .signWith(key)
                .compact();
    }

    // throws JwtException if the token is expired or tampered with
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isAccessToken(Claims claims) {
        return TYPE_ACCESS.equals(claims.get(CLAIM_TYPE, String.class));
    }

    public boolean isRefreshToken(Claims claims) {
        return TYPE_REFRESH.equals(claims.get(CLAIM_TYPE, String.class));
    }

    public long getAccessTokenSeconds() {
        return accessTokenTtl.toSeconds();
    }
}
