package com.petsalud.backendpetsalud.security.monitor;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Monitoreo de actividad sospechosa.
 * Escribe en el log (con fecha y hora) los logins fallidos, bloqueos, tokens inválidos
 * y accesos denegados. El contador de intentos se almacena en MySQL.
 * Nunca registra contraseñas ni tokens, y oculta parte del email.
 */
@Component
public class ActividadSospechosaService {

    // Todas las líneas salen con el nombre "SEGURIDAD": así se filtran fácil en el log
    private static final Logger log = LoggerFactory.getLogger("SEGURIDAD");

    public void registrarLoginFallido(String email, HttpServletRequest request, int total) {
        log.warn("LOGIN_FALLIDO email={} ip={} intentos={}/{}", ocultarEmail(email), ip(request),
                total, LoginAttemptService.MAX_INTENTOS);
        if (total == LoginAttemptService.MAX_INTENTOS) {
            log.warn("CUENTA_BLOQUEADA email={} ip={} minutos={}", ocultarEmail(email), ip(request),
                    LoginAttemptService.MINUTOS_BLOQUEO);
        }
    }

    public void registrarLoginExitoso(String email, HttpServletRequest request) {
        log.info("LOGIN_EXITOSO email={} ip={}", ocultarEmail(email), ip(request));
    }

    /** Alguien intentó entrar mientras estaba bloqueado (aunque traiga la clave correcta). */
    public void registrarLoginBloqueado(String email, HttpServletRequest request) {
        log.warn("LOGIN_RECHAZADO_POR_BLOQUEO email={} ip={}", ocultarEmail(email), ip(request));
    }

    /** Token falso, alterado, vencido, o de un usuario eliminado. */
    public void registrarTokenInvalido(HttpServletRequest request, String motivo) {
        log.warn("TOKEN_INVALIDO ip={} metodo={} ruta={} motivo={}",
                ip(request), request.getMethod(), limpiar(request.getRequestURI()), limpiar(motivo));
    }

    /** Petición a una ruta protegida sin token. */
    public void registrarSinToken(HttpServletRequest request) {
        log.info("ACCESO_SIN_TOKEN ip={} metodo={} ruta={}",
                ip(request), request.getMethod(), limpiar(request.getRequestURI()));
    }

    /** Usuario identificado que intentó entrar donde su rol no puede, o a un recurso ajeno. */
    public void registrarAccesoDenegado(HttpServletRequest request) {
        String usuario = Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
                .map(Authentication::getName)
                .orElse("desconocido");
        log.warn("ACCESO_DENEGADO usuario={} ip={} metodo={} ruta={}",
                ocultarEmail(usuario), ip(request), request.getMethod(), limpiar(request.getRequestURI()));
    }

    // ---------- ayudantes ----------

    /** IP real de la conexión. No usamos X-Forwarded-For porque el cliente lo puede falsificar. */
    private String ip(HttpServletRequest request) {
        return request.getRemoteAddr();
    }

    /** Evita que alguien "escriba líneas falsas" en el log metiendo saltos de línea en un dato. */
    private String limpiar(String texto) {
        return texto == null ? "" : texto.replaceAll("[\\r\\n\\t]", "_");
    }

    /** "ana@petsalud.cl" → "an***@petsalud.cl" (minimización de datos personales). */
    private String ocultarEmail(String email) {
        String e = limpiar(email);
        int arroba = e.indexOf('@');
        if (arroba < 0) {
            return e.length() <= 2 ? "***" : e.substring(0, 2) + "***";
        }
        if (arroba <= 2) {
            return "***" + e.substring(arroba);
        }
        return e.substring(0, 2) + "***" + e.substring(arroba);
    }
}