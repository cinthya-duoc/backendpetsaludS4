package com.petsalud.backendpetsalud.controller;

import com.petsalud.backendpetsalud.service.UsuarioService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Comprueba con MockMvc que el registro rechaza solicitudes sin nombre, apellido,
 * correo o dirección antes de llamar al servicio de usuarios simulado.
 */
@ExtendWith(MockitoExtension.class)
class UsuarioRegistroTest {

    @Mock
    private UsuarioService usuarioService;

    @InjectMocks
    private UsuarioController usuarioController;

    @Test
    void rechazaRegistroCuandoFaltaElNombre() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(usuarioController).build();

        mvc.perform(post("/api/usuarios/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                          "apellido": "Perez",
                          "email": "cliente@example.test",
                          "password": "Prueba123",
                          "direccion": "Avenida de prueba 123"
                        }
                        """))
                .andExpect(status().isBadRequest());

        // El 400 debe producirse en la validación de la solicitud, antes de delegar el registro.
        verifyNoInteractions(usuarioService);
    }

    @Test
    void rechazaRegistroCuandoFaltaElApellido() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(usuarioController).build();

        mvc.perform(post("/api/usuarios/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                    {
                      "nombre": "Ana",
                      "email": "cliente@example.test",
                      "password": "Prueba123",
                      "direccion": "Avenida de prueba 123"
                    }
                    """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(usuarioService);
    }

    @Test
    void rechazaRegistroCuandoFaltaElCorreo() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(usuarioController).build();

        mvc.perform(post("/api/usuarios/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                    {
                      "nombre": "Ana",
                      "apellido": "Perez",
                      "password": "Prueba123",
                      "direccion": "Avenida de prueba 123"
                    }
                    """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(usuarioService);
    }

    @Test
    void rechazaRegistroCuandoFaltaLaDireccion() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(usuarioController).build();

        mvc.perform(post("/api/usuarios/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                    {
                      "nombre": "Ana",
                      "apellido": "Perez",
                      "email": "cliente@example.test",
                      "password": "Prueba123"
                    }
                    """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(usuarioService);
    }

}