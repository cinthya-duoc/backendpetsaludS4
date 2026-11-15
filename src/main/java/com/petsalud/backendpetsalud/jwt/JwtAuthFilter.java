package com.petsalud.backendpetsalud.jwt;

import java.io.IOException;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.petsalud.backendpetsalud.security.JwtAuthenticationEntryPoint;
import com.petsalud.backendpetsalud.security.UsuarioDetailsService;
import com.petsalud.backendpetsalud.security.monitor.ActividadSospechosaService;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    public static final String VALIDATED_CLAIMS = "petsalud.jwt.validatedClaims";

    private final JwtUtil jwtUtil;
    private final JwtRevocations revocations;
    private final UsuarioDetailsService usuarioDetailsService;
    private final JwtAuthenticationEntryPoint authenticationEntryPoint;
    private final ActividadSospechosaService actividadSospechosa;

    public JwtAuthFilter(JwtUtil jwtUtil, JwtRevocations revocations,
                         UsuarioDetailsService usuarioDetailsService,
                         JwtAuthenticationEntryPoint authenticationEntryPoint,
                         ActividadSospechosaService actividadSospechosa) {
        this.jwtUtil = jwtUtil;
        this.revocations = revocations;
        this.usuarioDetailsService = usuarioDetailsService;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.actividadSospechosa = actividadSospechosa;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return "POST".equals(request.getMethod()) && "/auth/login".equals(request.getServletPath());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            try {
                Claims claims = jwtUtil.validate(authorization.substring(7));
                if (revocations.isRevoked(claims.getId())) {
                    throw new JwtException("Token revocado");
                }
                UserDetails user = usuarioDetailsService.loadUserByUsername(claims.getSubject());
                if (!jwtUtil.matchesCurrentCredentials(claims, user)) {
                    throw new JwtException("Credenciales actualizadas");
                }

                SecurityContextHolder.getContext().setAuthentication(
                        UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities()));
                request.setAttribute(VALIDATED_CLAIMS, claims);
            } catch (JwtException invalidToken) {
                reject(request, response, invalidToken.getClass().getSimpleName(), "Token inválido o expirado");
                return;
            } catch (UsernameNotFoundException unknownUser) {
                reject(request, response, "usuario eliminado", "El usuario del token ya no existe");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private void reject(HttpServletRequest request, HttpServletResponse response, String reason,
                        String message) throws IOException {
        actividadSospechosa.registrarTokenInvalido(request, reason);
        SecurityContextHolder.clearContext();
        authenticationEntryPoint.commence(request, response, new BadCredentialsException(message));
    }
}
