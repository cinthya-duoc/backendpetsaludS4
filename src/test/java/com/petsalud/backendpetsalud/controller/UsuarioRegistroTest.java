package com.petsalud.backendpetsalud.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.petsalud.backendpetsalud.service.UsuarioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.LinkedHashMap;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Valida campos obligatorios en la capa web antes de delegar al servicio simulado. */
@ExtendWith(MockitoExtension.class)
class UsuarioRegistroTest {

    @Mock
    private UsuarioService usuarioService;

    @InjectMocks
    private UsuarioController usuarioController;

    private MockMvc mvc;

    @BeforeEach
    void prepararControlador() {
        mvc = MockMvcBuilders.standaloneSetup(usuarioController).build();
    }

    @ParameterizedTest(name = "Registro sin {0} devuelve 400")
    @ValueSource(strings = {"nombre", "apellido", "email", "direccion"})
    void rechazaRegistroSinCampoObligatorio(String campo) throws Exception {
        var datos = new LinkedHashMap<String, String>();
        datos.put("nombre", "Ana");
        datos.put("apellido", "Perez");
        datos.put("email", "cliente@example.test");
        datos.put("password", "Prueba123");
        datos.put("direccion", "Avenida de prueba 123");
        datos.remove(campo);

        mvc.perform(post("/api/usuarios/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(datos)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(usuarioService);
    }
}
