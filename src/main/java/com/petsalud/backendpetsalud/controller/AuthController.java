package com.petsalud.backendpetsalud.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import com.petsalud.backendpetsalud.dto.AuthResponse;
import com.petsalud.backendpetsalud.dto.LoginRequest;
import com.petsalud.backendpetsalud.jwt.JwtAuthFilter;
import com.petsalud.backendpetsalud.jwt.JwtRevocations;
import com.petsalud.backendpetsalud.jwt.JwtUtil;

import com.petsalud.backendpetsalud.security.monitor.LoginAttemptService;

import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * Endpoint público de login (/auth/login): recibe email y contraseña y, si son correctos,
 * entrega el token JWT. Registra cada intento y bloquea tras 5 fallos en 15 minutos.
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private LoginAttemptService loginAttempts;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private JwtRevocations revocations;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request,
                                              HttpServletRequest httpRequest) {

        UserDetails user = loginAttempts.authenticate(request.getEmail(), request.getPassword(), httpRequest);
        String token = jwtUtil.getToken(user);
        return ResponseEntity.ok(new AuthResponse(token));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestAttribute(JwtAuthFilter.VALIDATED_CLAIMS) Claims claims) {
        revocations.revoke(claims);
    }
}
