package com.petsalud.backendpetsalud.controller;

import com.petsalud.backendpetsalud.security.Propiedad;
import com.petsalud.backendpetsalud.service.CitaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import com.petsalud.backendpetsalud.entity.Cita;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Activa la seguridad por método del controlador con usuarios simulados.
 * El servicio y la consulta de propiedad se reemplazan por mocks.
 */
@SpringJUnitConfig(CitaPermisosTest.Config.class)
class CitaPermisosTest {

    @Configuration
    @EnableMethodSecurity
    @Import(CitaController.class)
    static class Config {
    }

    @Autowired
    private CitaController controlador;

    @MockBean
    private CitaService citaService;

    @MockBean(name = "propiedad")
    private Propiedad propiedad;

    @Test
    @WithMockUser(authorities = "CLIENTE")
    void clienteNoPuedeCambiarElEstadoDeUnaCita() {
        // El cliente debe ser rechazado antes de ejecutar el servicio.
        assertThrows(AccessDeniedException.class,
                () -> controlador.actualizarEstadoCita(1L, "Completada"));

        verifyNoInteractions(citaService);
    }

    @Test
    @WithMockUser(authorities = "EMPLEADO")
    void empleadoPuedeCambiarElEstadoDeUnaCita() {
        // Preparar la cita que devuelve el servicio simulado.
        Cita cita = Cita.builder()
                .id(1L)
                .estado("Completada")
                .build();

        when(citaService.actualizarEstadoCita(1L, "Completada"))
                .thenReturn(cita);

        var respuesta = controlador.actualizarEstadoCita(1L, "Completada");

        // La autorización permite delegar el cambio al servicio.
        assertEquals(HttpStatus.OK, respuesta.getStatusCode());
        assertEquals(cita, respuesta.getBody());
        verify(citaService).actualizarEstadoCita(1L, "Completada");
    }

    @Test
    @WithMockUser(authorities = "GERENTE")
    void gerentePuedeCambiarElEstadoDeUnaCita() {
        Cita cita = Cita.builder()
                .id(1L)
                .estado("Cancelada")
                .build();

        when(citaService.actualizarEstadoCita(1L, "Cancelada"))
                .thenReturn(cita);

        var respuesta = controlador.actualizarEstadoCita(1L, "Cancelada");

        // El rol administrativo también puede gestionar el estado.
        assertEquals(HttpStatus.OK, respuesta.getStatusCode());
        assertEquals(cita, respuesta.getBody());
        verify(citaService).actualizarEstadoCita(1L, "Cancelada");
    }

}