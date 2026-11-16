package com.petsalud.backendpetsalud.controller;

import com.petsalud.backendpetsalud.security.Propiedad;
import com.petsalud.backendpetsalud.service.VentaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Ejecuta las reglas de @PreAuthorize del controlador con roles simulados.
 * El servicio de ventas y la consulta de propiedad se reemplazan por mocks.
 */
@SpringJUnitConfig(VentaPermisosTest.Config.class)
class VentaPermisosTest {

    // Carga la seguridad por método y el controlador sin iniciar la aplicación completa.
    @Configuration
    @EnableMethodSecurity
    @Import(VentaController.class)
    static class Config {
    }

    @Autowired
    private VentaController controlador;

    @MockBean
    private VentaService ventaService;

    @MockBean(name = "propiedad")
    private Propiedad propiedad;

    @Test
    @WithMockUser(authorities = "CLIENTE")
    void clientePuedeComprarConSuPropioId() {
        var autenticacion = SecurityContextHolder.getContext().getAuthentication();
        when(propiedad.esUsuario(3L, autenticacion)).thenReturn(true);

        controlador.realizarVenta(3L, List.of(1L));

        verify(ventaService).realizarVenta(3L, List.of(1L));
    }

    @Test
    @WithMockUser(authorities = "CLIENTE")
    void clienteNoPuedeComprarConUnIdAjeno() {
        var autenticacion = SecurityContextHolder.getContext().getAuthentication();
        when(propiedad.esUsuario(4L, autenticacion)).thenReturn(false);

        assertThrows(AccessDeniedException.class,
                () -> controlador.realizarVenta(4L, List.of(1L)));

        verifyNoInteractions(ventaService);
    }

    @Test
    @WithMockUser(authorities = "EMPLEADO")
    void empleadoPuedeRegistrarUnaVentaParaOtroUsuario() {
        controlador.realizarVenta(4L, List.of(1L));

        verify(ventaService).realizarVenta(4L, List.of(1L));
    }

    @Test
    @WithMockUser(authorities = "GERENTE")
    void gerentePuedeRegistrarUnaVentaParaOtroUsuario() {
        controlador.realizarVenta(4L, List.of(1L));

        verify(ventaService).realizarVenta(4L, List.of(1L));
    }
}