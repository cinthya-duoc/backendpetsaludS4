package com.petsalud.backendpetsalud.jwt;

import java.time.Instant;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;

@Service
public class JwtRevocations {

    private final JdbcTemplate jdbc;

    public JwtRevocations(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean isRevoked(String id) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM jwt_revocations WHERE jti = ? AND expires_at > ?)
                """, Boolean.class, id, Instant.now().getEpochSecond()));
    }

    public void revoke(Claims claims) {
        jdbc.update("""
                INSERT INTO jwt_revocations (jti, expires_at) VALUES (?, ?)
                ON DUPLICATE KEY UPDATE jti = jti
                """, claims.getId(), claims.getExpiration().toInstant().getEpochSecond());
    }

    @Scheduled(initialDelay = 300000, fixedDelay = 300000)
    public void deleteExpiredRevocations() {
        jdbc.update("DELETE FROM jwt_revocations WHERE expires_at <= ?", Instant.now().getEpochSecond());
    }
}
