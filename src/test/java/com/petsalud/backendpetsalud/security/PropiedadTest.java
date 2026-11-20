package com.petsalud.backendpetsalud.security;

import com.petsalud.backendpetsalud.entity.Rol;
import com.petsalud.backendpetsalud.entity.Usuario;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Ejecuta el componente real de propiedad con la identidad que emplea el filtro JWT. */
class PropiedadTest {

    private final Propiedad propiedad = new Propiedad();

    @Test
    void permiteCompraCuandoElSolicitanteEsElTitular() {
        var usuario = new UsuarioDetails(Usuario.builder().id(3L).rol(Rol.CLIENTE).build());
        var autenticacion = UsernamePasswordAuthenticationToken.authenticated(
                usuario, null, usuario.getAuthorities());

        assertTrue(propiedad.esUsuario(3L, autenticacion));
    }

    @Test
    void rechazaCompraCuandoElSolicitanteEsOtraPersona() {
        var usuario = new UsuarioDetails(Usuario.builder().id(3L).rol(Rol.CLIENTE).build());
        var autenticacion = UsernamePasswordAuthenticationToken.authenticated(
                usuario, null, usuario.getAuthorities());

        assertFalse(propiedad.esUsuario(4L, autenticacion));
    }

    @Test
    void rechazaSolicitudSinIdentidad() {
        assertFalse(propiedad.esUsuario(3L, null));
    }
}
