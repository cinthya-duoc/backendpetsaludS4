package com.petsalud.backendpetsalud.jwt;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

@Component
public class JwtUtil {

    private static final String CREDENTIAL_VERSION = "credential_version";
    private static final long MAX_EXPIRATION_MS = 900_000;

    private final SecretKey key;
    private final JwtParser parser;
    private final String issuer;
    private final String audience;
    private final long expirationMs;

    public JwtUtil(@Value("${jwt.secret}") String secret,
                   @Value("${jwt.issuer}") String issuer,
                   @Value("${jwt.audience}") String audience,
                   @Value("${jwt.expiration-ms}") long expirationMs) {
        if (expirationMs < 1_000 || expirationMs > MAX_EXPIRATION_MS) {
            throw new IllegalArgumentException("La duración del JWT debe estar entre 1 segundo y 15 minutos");
        }
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.issuer = issuer;
        this.audience = audience;
        this.expirationMs = expirationMs;
        this.parser = Jwts.parser()
                .verifyWith(key)
                .requireIssuer(issuer)
                .requireAudience(audience)
                .build();
    }

    public String getToken(UserDetails user) {
        Date now = new Date();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .issuer(issuer)
                .audience().add(audience).and()
                .subject(user.getUsername())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMs))
                .claim(CREDENTIAL_VERSION, credentialVersion(user))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    public Claims validate(String token) {
        if (token.isBlank()) {
            throw new JwtException("Token vacío");
        }
        var signed = parser.parseSignedClaims(token);
        if (!Jwts.SIG.HS256.getId().equals(signed.getHeader().getAlgorithm())) {
            throw new JwtException("Algoritmo de firma JWT no permitido");
        }
        Claims claims = signed.getPayload();
        Date issuedAt = claims.getIssuedAt();
        Date expiration = claims.getExpiration();
        Date now = new Date();
        if (claims.getId() == null || claims.getId().length() != 36
                || claims.getSubject() == null || claims.getSubject().isBlank()
                || claims.get(CREDENTIAL_VERSION, String.class) == null
                || issuedAt == null || issuedAt.after(now)
                || expiration == null || !expiration.after(now)
                || !expiration.after(issuedAt)
                || expiration.getTime() - issuedAt.getTime() > MAX_EXPIRATION_MS) {
            throw new JwtException("Claims del token inválidos");
        }
        return claims;
    }

    public boolean matchesCurrentCredentials(Claims claims, UserDetails user) {
        return MessageDigest.isEqual(
                claims.get(CREDENTIAL_VERSION, String.class).getBytes(StandardCharsets.US_ASCII),
                credentialVersion(user).getBytes(StandardCharsets.US_ASCII));
    }

    private String credentialVersion(UserDetails user) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            byte[] input = ("petsalud:credential:v1:" + user.getPassword()).getBytes(StandardCharsets.UTF_8);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(input));
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("No se pudo comprobar la versión de credenciales", error);
        }
    }
}
