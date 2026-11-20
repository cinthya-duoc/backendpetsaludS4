package com.petsalud.backendpetsalud.entity;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Comprueba la relación Usuario-Mascota en memoria y las restricciones
 * de nombre y especie de la mascota con el validador real.
 */
class UsuarioMascotaTest {

    private static ValidatorFactory fabrica;
    private static Validator validador;

    @BeforeAll
    static void prepararValidador() {
        fabrica = Validation.buildDefaultValidatorFactory();
        validador = fabrica.getValidator();
    }

    @AfterAll
    static void cerrarValidador() {
        fabrica.close();
    }

    @Test
    void clientePuedeTenerVariasMascotasAsociadas() {
        // Preparar al cliente y ambas direcciones de la relación.
        Usuario cliente = Usuario.builder()
                .id(3L)
                .rol(Rol.CLIENTE)
                .build();

        Mascota luna = mascotaValida();
        luna.setId(2L);
        luna.setPropietario(cliente);

        Mascota max = Mascota.builder()
                .id(5L)
                .nombre("Max")
                .especie("Gato")
                .propietario(cliente)
                .build();

        cliente.setMascotas(List.of(luna, max));

        // Comprobar los artículos de la colección y sus propietarios.
        assertEquals(Rol.CLIENTE, cliente.getRol());
        assertEquals(2, cliente.getMascotas().size());
        assertSame(luna, cliente.getMascotas().get(0));
        assertSame(max, cliente.getMascotas().get(1));
        assertSame(cliente, luna.getPropietario());
        assertSame(cliente, max.getPropietario());

        // Ambas mascotas tienen datos válidos según el modelo.
        assertTrue(validador.validate(luna).isEmpty());
        assertTrue(validador.validate(max).isEmpty());
    }

    @ParameterizedTest(name = "Rechaza nombre de mascota [{0}]")
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "<Luna>"})
    void rechazaNombreDeMascotaInvalido(String nombre) {
        Mascota mascota = mascotaValida();
        mascota.setNombre(nombre);

        // Identificar la infracción del campo evaluado.
        boolean nombreInvalido = validador.validate(mascota).stream()
                .anyMatch(error -> error.getPropertyPath()
                        .toString().equals("nombre"));

        assertTrue(nombreInvalido);
    }

    @ParameterizedTest(name = "Rechaza especie de mascota [{0}]")
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "<Perro>"})
    void rechazaEspecieDeMascotaInvalida(String especie) {
        Mascota mascota = mascotaValida();
        mascota.setEspecie(especie);

        boolean especieInvalida = validador.validate(mascota).stream()
                .anyMatch(error -> error.getPropertyPath()
                        .toString().equals("especie"));

        assertTrue(especieInvalida);
    }

    private Mascota mascotaValida() {
        // Preparar datos válidos antes de alterar el campo de cada caso.
        return Mascota.builder()
                .nombre("Luna")
                .especie("Perro")
                .build();
    }
}