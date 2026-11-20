package com.petsalud.backendpetsalud.controller;

import com.petsalud.backendpetsalud.entity.Cita;
import com.petsalud.backendpetsalud.service.CitaService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Comprueba las respuestas y los datos delegados al servicio.
 * El servicio se simula para aislar la lógica del controlador.
 */
@ExtendWith(MockitoExtension.class)
class CitaControllerTest {

    @Mock
    private CitaService citaService;

    @InjectMocks
    private CitaController controlador;

    @Test
    void devuelveLaCitaCreadaConRespuestaOk() {
        // Preparar la respuesta que entregaría el servicio.
        LocalDateTime fecha = LocalDateTime.of(2027, 1, 20, 10, 0);
        Cita cita = Cita.builder()
                .id(1L)
                .fechaHora(fecha)
                .estado("Agendada")
                .motivo("Control general")
                .build();

        when(citaService.agendarCita(2L, 4L, fecha, "Control general"))
                .thenReturn(cita);

        // Ejecutar el controlador y comprobar su respuesta.
        var respuesta = controlador.agendarCita(
                2L, 4L, fecha, "Control general"
        );

        assertEquals(HttpStatus.OK, respuesta.getStatusCode());
        assertEquals(cita, respuesta.getBody());
        verify(citaService).agendarCita(2L, 4L, fecha, "Control general");
    }

    @Test
    void devuelveOkCuandoLaCitaExiste() {
        // Simular una consulta que encuentra el registro.
        Cita cita = Cita.builder().id(1L).estado("Agendada").build();
        when(citaService.obtenerCitaPorId(1L))
                .thenReturn(Optional.of(cita));

        var respuesta = controlador.obtenerCitaPorId(1L);

        assertEquals(HttpStatus.OK, respuesta.getStatusCode());
        assertEquals(cita, respuesta.getBody());
    }

    @Test
    void devuelveNotFoundCuandoLaCitaNoExiste() {
        // Simular una consulta sin coincidencias.
        when(citaService.obtenerCitaPorId(99L))
                .thenReturn(Optional.empty());

        var respuesta = controlador.obtenerCitaPorId(99L);

        assertEquals(HttpStatus.NOT_FOUND, respuesta.getStatusCode());
        assertNull(respuesta.getBody());
    }

    @Test
    void devuelveLaCitaConElEstadoActualizado() {
        // Simular la cita que devuelve el servicio después del cambio.
        Cita actualizada = Cita.builder()
                .id(1L)
                .estado("Completada")
                .build();

        when(citaService.actualizarEstadoCita(1L, "Completada"))
                .thenReturn(actualizada);

        var respuesta = controlador.actualizarEstadoCita(1L, "Completada");

        // Comprobar la respuesta y el estado recibido del servicio.
        assertEquals(HttpStatus.OK, respuesta.getStatusCode());
        assertEquals(actualizada, respuesta.getBody());
        assertEquals("Completada", respuesta.getBody().getEstado());
        verify(citaService).actualizarEstadoCita(1L, "Completada");
    }

    @Test
    void devuelveNoContentAlCancelarUnaCita() {
        // Ejecutar la operación sobre el servicio simulado.
        var respuesta = controlador.cancelarCita(1L);

        // Comprobar la respuesta sin contenido y el ID delegado.
        assertEquals(HttpStatus.NO_CONTENT, respuesta.getStatusCode());
        assertNull(respuesta.getBody());
        verify(citaService).cancelarCita(1L);
    }
}