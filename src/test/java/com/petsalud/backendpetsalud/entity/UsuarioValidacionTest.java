package com.petsalud.backendpetsalud.entity;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Comprueba las restricciones de la entidad Usuario con el validador real.
 * Estas pruebas se ejecutan sin iniciar Spring ni conectar MySQL.
 */
class UsuarioValidacionTest {

    private static ValidatorFactory fabrica;
    private static Validator validador;

    @BeforeAll
    static void prepararValidador() {
        // Compartir el validador entre los casos de esta clase.
        fabrica = Validation.buildDefaultValidatorFactory();
        validador = fabrica.getValidator();
    }

    @AfterAll
    static void cerrarValidador() {
        // Liberar los recursos utilizados por el validador.
        fabrica.close();
    }

    @ParameterizedTest(name = "Rechaza nombre obligatorio con valor [{0}]")
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void rechazaNombreAusenteOVacio(String nombre) {
        Usuario usuario = usuarioValido();
        usuario.setNombre(nombre);

        // Comprobar que la infracción corresponde al campo evaluado.
        boolean nombreInvalido = validador.validate(usuario).stream()
                .anyMatch(error -> error.getPropertyPath()
                        .toString().equals("nombre"));

        assertTrue(nombreInvalido);
    }

    @ParameterizedTest(name = "Rechaza correo obligatorio con valor [{0}]")
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void rechazaCorreoAusenteOVacio(String correo) {
        Usuario usuario = usuarioValido();
        usuario.setEmail(correo);

        // Verificar que el correo ausente, vacío o con espacios sea inválido.
        boolean correoInvalido = validador.validate(usuario).stream()
                .anyMatch(error -> error.getPropertyPath()
                        .toString().equals("email"));

        assertTrue(correoInvalido);
    }

    @Test
    void rechazaUsuarioSinRol() {
        // Preparar un usuario válido y omitir únicamente su rol.
        Usuario usuario = usuarioValido();
        usuario.setRol(null);

        // Exigir que el validador detecte el campo obligatorio.
        boolean rolInvalido = validador.validate(usuario, Usuario.CuentaCompleta.class).stream()
                .anyMatch(error -> error.getPropertyPath()
                        .toString().equals("rol"));

        assertTrue(rolInvalido);
    }

    @ParameterizedTest(name = "Acepta el rol definido {0}")
    @EnumSource(Rol.class)
    void aceptaLosRolesDefinidosEnElProyecto(Rol rol) {
        // Evaluar CLIENTE, EMPLEADO y GERENTE por separado.
        Usuario usuario = usuarioValido();
        usuario.setRol(rol);

        boolean rolInvalido = validador.validate(usuario, Usuario.CuentaCompleta.class).stream()
                .anyMatch(error -> error.getPropertyPath()
                        .toString().equals("rol"));

        assertFalse(rolInvalido);
    }

    private Usuario usuarioValido() {
        // Preparar los demás campos para aislar la condición de cada prueba.
        return Usuario.builder()
                .nombre("Ana")
                .apellido("Perez")
                .email("cliente@example.test")
                .password("Prueba123")
                .direccion("Avenida de prueba 123")
                .rol(Rol.CLIENTE)
                .build();
    }
}