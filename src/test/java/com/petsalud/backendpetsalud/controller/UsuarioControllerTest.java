package com.petsalud.backendpetsalud.controller;

import com.petsalud.backendpetsalud.dto.ActualizarUsuarioRequest;
import com.petsalud.backendpetsalud.entity.Usuario;
import com.petsalud.backendpetsalud.service.UsuarioService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Prueba las respuestas del controlador de usuarios con un servicio simulado.
 * También verifica cómo se convierten los datos editables de la solicitud.
 */
@ExtendWith(MockitoExtension.class)
class UsuarioControllerTest {

    @Mock
    private UsuarioService usuarioService;

    @InjectMocks
    private UsuarioController controlador;

    @Test
    void devuelveOkConElUsuarioRegistrado() {
        Usuario solicitud = Usuario.builder()
                .nombre("Ana")
                .apellido("Perez")
                .email("cliente@example.test")
                .direccion("Avenida de prueba 123")
                .password("Prueba123")
                .build();
        Usuario guardado = Usuario.builder().id(3L).nombre("Ana").build();

        when(usuarioService.registrarUsuario(solicitud)).thenReturn(guardado);

        var respuesta = controlador.registrarUsuario(solicitud);

        assertEquals(HttpStatus.OK, respuesta.getStatusCode());
        assertEquals(guardado, respuesta.getBody());
    }

    @Test
    void devuelveOkAlConsultarUnUsuarioExistente() {
        Usuario usuario = Usuario.builder().id(3L).nombre("Ana").build();
        when(usuarioService.obtenerUsuarioPorId(3L)).thenReturn(Optional.of(usuario));

        var respuesta = controlador.obtenerUsuarioPorId(3L);

        assertEquals(HttpStatus.OK, respuesta.getStatusCode());
        assertEquals(usuario, respuesta.getBody());
    }

    @Test
    void enviaAlServicioSoloLosDatosEditables() {
        ActualizarUsuarioRequest solicitud = new ActualizarUsuarioRequest();
        solicitud.setNombre("Maria");
        solicitud.setApellido("Rojas");
        solicitud.setTelefono("987654321");
        solicitud.setDireccion("Avenida Nueva 456");

        Usuario actualizado = Usuario.builder().id(3L).nombre("Maria").build();
        when(usuarioService.actualizarUsuario(eq(3L), any(Usuario.class)))
                .thenReturn(actualizado);

        var respuesta = controlador.actualizarUsuario(3L, solicitud);

        // Inspecciona los datos que se delegan al servicio, además del código de respuesta.
        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioService).actualizarUsuario(eq(3L), captor.capture());

        Usuario datosEnviados = captor.getValue();
        assertEquals(HttpStatus.OK, respuesta.getStatusCode());
        assertEquals(actualizado, respuesta.getBody());
        assertEquals("Maria", datosEnviados.getNombre());
        assertEquals("Rojas", datosEnviados.getApellido());
        assertEquals("987654321", datosEnviados.getTelefono());
        assertEquals("Avenida Nueva 456", datosEnviados.getDireccion());
        assertNull(datosEnviados.getEmail());
        assertNull(datosEnviados.getPassword());
        assertNull(datosEnviados.getRol());
    }
}