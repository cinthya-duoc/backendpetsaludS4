package com.petsalud.backendpetsalud.security.monitor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.petsalud.backendpetsalud.exception.CuentaBloqueadaException;

import jakarta.servlet.http.HttpServletRequest;

@Service
public class LoginAttemptService {

    static final int MAX_INTENTOS = 5;
    static final int MINUTOS_BLOQUEO = 15;
    private static final long VENTANA_MS = MINUTOS_BLOQUEO * 60_000L;
    private static final TypeReference<List<Long>> FECHAS = new TypeReference<>() {};

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final AuthenticationManager authenticationManager;
    private final ActividadSospechosaService monitor;

    public LoginAttemptService(JdbcTemplate jdbc, ObjectMapper json, AuthenticationManager authenticationManager,
                               ActividadSospechosaService monitor) {
        this.jdbc = jdbc;
        this.json = json;
        this.authenticationManager = authenticationManager;
        this.monitor = monitor;
    }

    // La comprobación, el login y el incremento comparten el bloqueo de una fila.
    // Un fallo de credenciales se devuelve al cliente después de confirmar el contador.
    @Transactional(isolation = Isolation.READ_COMMITTED, noRollbackFor = BadCredentialsException.class)
    public UserDetails authenticate(String email, String password, HttpServletRequest request) {
        String account = jdbc.query("SELECT email FROM usuarios WHERE email = ?",
                rows -> rows.next() ? rows.getString(1) : email, email);
        String key = attemptKey(account, request.getRemoteAddr());
        jdbc.update("""
                INSERT INTO auth_login_attempts (attempt_key, failures, expires_at_ms)
                VALUES (?, JSON_ARRAY(), 0)
                ON DUPLICATE KEY UPDATE attempt_key = attempt_key
                """, key);

        AttemptState state = jdbc.queryForObject("""
                SELECT failures, expires_at_ms, CAST(UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3)) * 1000 AS UNSIGNED) AS now_ms
                FROM auth_login_attempts
                WHERE attempt_key = ?
                FOR UPDATE
                """, (row, index) -> new AttemptState(readFailures(row.getString("failures")),
                row.getLong("expires_at_ms"), row.getLong("now_ms")), key);

        if (state.expiresAt() > state.now() && state.failures().size() >= MAX_INTENTOS) {
            monitor.registrarLoginBloqueado(account, request);
            throw new CuentaBloqueadaException();
        }

        UserDetails user;
        try {
            var authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(account, password));
            user = (UserDetails) authentication.getPrincipal();
        } catch (BadCredentialsException invalid) {
            // Usa la hora de MySQL tras BCrypt para conservar la ventana entre instancias.
            long now = databaseTime();
            List<Long> recent = new ArrayList<>(state.failures());
            recent.removeIf(attempt -> attempt <= now - VENTANA_MS);
            recent.add(now);
            jdbc.update("UPDATE auth_login_attempts SET failures = ?, expires_at_ms = ? WHERE attempt_key = ?",
                    writeFailures(recent), now + VENTANA_MS, key);
            monitor.registrarLoginFallido(account, request, recent.size());
            throw invalid;
        }

        jdbc.update("DELETE FROM auth_login_attempts WHERE attempt_key = ?", key);
        monitor.registrarLoginExitoso(account, request);
        return user;
    }

    @Scheduled(initialDelay = 300000, fixedDelay = 300000)
    public void deleteExpiredAttempts() {
        long now = databaseTime();
        List<String> keys = jdbc.queryForList("""
                SELECT attempt_key FROM auth_login_attempts
                WHERE expires_at_ms <= ?
                ORDER BY expires_at_ms
                LIMIT 1000
                """, String.class, now);
        for (String key : keys) {
            jdbc.update("DELETE FROM auth_login_attempts WHERE attempt_key = ? AND expires_at_ms <= ?", key, now);
        }
    }

    private long databaseTime() {
        return jdbc.queryForObject("SELECT CAST(UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3)) * 1000 AS UNSIGNED)", Long.class);
    }

    private static String attemptKey(String email, String address) {
        byte[] input = (email.trim().toLowerCase(Locale.ROOT) + "|" + address).getBytes(StandardCharsets.UTF_8);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input));
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 no disponible", unavailable);
        }
    }

    private List<Long> readFailures(String value) {
        try {
            return json.readValue(value, FECHAS);
        } catch (JsonProcessingException invalid) {
            throw new IllegalStateException("No se pudo leer el contador de intentos", invalid);
        }
    }

    private String writeFailures(List<Long> values) {
        try {
            return json.writeValueAsString(values);
        } catch (JsonProcessingException invalid) {
            throw new IllegalStateException("No se pudo guardar el contador de intentos", invalid);
        }
    }

    private record AttemptState(List<Long> failures, long expiresAt, long now) {
    }
}
