package com.petsalud.backendpetsalud.service;

import com.petsalud.backendpetsalud.entity.Cita;
import com.petsalud.backendpetsalud.entity.Mascota;
import com.petsalud.backendpetsalud.entity.Rol;
import com.petsalud.backendpetsalud.entity.Usuario;
import com.petsalud.backendpetsalud.repository.CitaRepository;
import com.petsalud.backendpetsalud.repository.MascotaRepository;
import com.petsalud.backendpetsalud.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Comprueba las reglas de citas mediante repositorios simulados.
 * Ejecuta el servicio real sin conectarse a MySQL.
 */
@ExtendWith(MockitoExtension.class)
class CitaServiceImplTest {

    @Mock
    private CitaRepository citaRepository;

    @Mock
    private MascotaRepository mascotaRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @InjectMocks
    private CitaServiceImpl citaService;

    @Test
    void agendaCitaConEstadoInicialYRelacionesCorrectas() {
        // Preparar una mascota con propietario y un empleado.
        Usuario propietario = propietarioValido();
        Usuario empleado = empleadoValido();
        Mascota mascota = mascotaConPropietario(propietario);
        LocalDateTime fecha = fechaDePrueba();

        // Simular que todos los registros existen y el horario está disponible.
        when(mascotaRepository.findById(2L))
                .thenReturn(Optional.of(mascota));
        when(usuarioRepository.findById(3L))
                .thenReturn(Optional.of(propietario));
        when(usuarioRepository.findById(4L))
                .thenReturn(Optional.of(empleado));
        when(citaRepository.findByVeterinarioId(4L))
                .thenReturn(List.of());
        when(citaRepository.save(any(Cita.class)))
                .thenAnswer(llamada -> llamada.getArgument(0));

        // Ejecutar la creación mediante el servicio real.
        Cita resultado = citaService.agendarCita(
                2L, 4L, fecha, "Control general"
        );

        // Inspeccionar la cita enviada al repositorio.
        ArgumentCaptor<Cita> captor = ArgumentCaptor.forClass(Cita.class);
        verify(citaRepository).save(captor.capture());

        Cita guardada = captor.getValue();

        assertSame(guardada, resultado);
        assertEquals("Agendada", guardada.getEstado());
        assertEquals(fecha, guardada.getFechaHora());
        assertEquals("Control general", guardada.getMotivo());
        assertEquals(2L, guardada.getMascota().getId());
        assertEquals(4L, guardada.getVeterinario().getId());
        assertEquals(Rol.EMPLEADO, guardada.getVeterinario().getRol());
        assertEquals(3L, guardada.getMascota().getPropietario().getId());
    }

    @Test
    void rechazaCitaCuandoElEmpleadoYaTieneEseHorarioOcupado() {
        Usuario propietario = propietarioValido();
        Usuario empleado = empleadoValido();
        Mascota mascota = mascotaConPropietario(propietario);
        LocalDateTime fecha = fechaDePrueba();

        // Preparar una cita activa del mismo empleado en el horario solicitado.
        Cita existente = Cita.builder()
                .id(1L)
                .veterinario(empleado)
                .fechaHora(fecha)
                .estado("Agendada")
                .build();

        when(mascotaRepository.findById(2L))
                .thenReturn(Optional.of(mascota));
        when(usuarioRepository.findById(3L))
                .thenReturn(Optional.of(propietario));
        when(usuarioRepository.findById(4L))
                .thenReturn(Optional.of(empleado));
        when(citaRepository.findByVeterinarioId(4L))
                .thenReturn(List.of(existente));

        // La segunda reserva debe rechazarse antes de guardar.
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> citaService.agendarCita(
                        2L, 4L, fecha, "Control general"
                )
        );

        assertEquals(
                "El empleado ya tiene una cita en ese horario",
                error.getMessage()
        );
        verify(citaRepository, never()).save(any(Cita.class));
    }

    @Test
    void permiteAgendarCuandoLaCitaDelHorarioEstaCancelada() {
        Usuario propietario = propietarioValido();
        Usuario empleado = empleadoValido();
        Mascota mascota = mascotaConPropietario(propietario);
        LocalDateTime fecha = fechaDePrueba();

        // La cita anterior fue cancelada y debe liberar el horario.
        Cita cancelada = Cita.builder()
                .id(1L)
                .veterinario(empleado)
                .fechaHora(fecha)
                .estado("Cancelada")
                .build();

        when(mascotaRepository.findById(2L))
                .thenReturn(Optional.of(mascota));
        when(usuarioRepository.findById(3L))
                .thenReturn(Optional.of(propietario));
        when(usuarioRepository.findById(4L))
                .thenReturn(Optional.of(empleado));
        when(citaRepository.findByVeterinarioId(4L))
                .thenReturn(List.of(cancelada));
        when(citaRepository.save(any(Cita.class)))
                .thenAnswer(llamada -> llamada.getArgument(0));

        Cita resultado = citaService.agendarCita(
                2L, 4L, fecha, "Control general"
        );

        // Comprobar que se pudo guardar una nueva cita en ese horario.
        assertEquals("Agendada", resultado.getEstado());
        assertEquals(fecha, resultado.getFechaHora());
        verify(citaRepository).save(any(Cita.class));
    }

    @Test
    void rechazaCitaCuandoLaMascotaNoTienePropietario() {
        // La mascota carece de la asociación con un propietario.
        Mascota mascota = Mascota.builder()
                .id(2L)
                .nombre("Luna")
                .especie("Perro")
                .build();

        when(mascotaRepository.findById(2L))
                .thenReturn(Optional.of(mascota));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> citaService.agendarCita(
                        2L, 4L, fechaDePrueba(), "Control general"
                )
        );

        assertEquals(
                "La mascota debe tener un propietario registrado",
                error.getMessage()
        );
        verify(citaRepository, never()).save(any(Cita.class));
    }

    @Test
    void rechazaCitaCuandoElPropietarioNoExisteEnElSistema() {
        // La mascota tiene una asociación, pero el propietario no está registrado.
        Usuario propietarioInexistente = Usuario.builder()
                .id(99L)
                .rol(Rol.CLIENTE)
                .build();

        Mascota mascota = mascotaConPropietario(propietarioInexistente);

        when(mascotaRepository.findById(2L))
                .thenReturn(Optional.of(mascota));
        when(usuarioRepository.findById(99L))
                .thenReturn(Optional.empty());

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> citaService.agendarCita(
                        2L, 4L, fechaDePrueba(), "Control general"
                )
        );

        assertEquals("Propietario no encontrado", error.getMessage());
        verify(citaRepository, never()).save(any(Cita.class));
    }

    @Test
    void rechazaCitaCuandoElUsuarioAsignadoNoEsEmpleado() {
        // Preparar una mascota y su propietario registrado.
        Usuario propietario = propietarioValido();
        Mascota mascota = mascotaConPropietario(propietario);

        // El usuario existe, pero tiene rol CLIENTE.
        Usuario clienteAsignado = Usuario.builder()
                .id(4L)
                .nombre("Pedro")
                .email("cliente.asignado@example.test")
                .rol(Rol.CLIENTE)
                .build();

        when(mascotaRepository.findById(2L))
                .thenReturn(Optional.of(mascota));
        when(usuarioRepository.findById(3L))
                .thenReturn(Optional.of(propietario));
        when(usuarioRepository.findById(4L))
                .thenReturn(Optional.of(clienteAsignado));

        // Exigir que el servicio rechace el rol antes de guardar.
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> citaService.agendarCita(
                        2L, 4L, fechaDePrueba(), "Control general"
                )
        );

        assertEquals(
                "El usuario asignado debe tener rol EMPLEADO",
                error.getMessage()
        );
        verify(citaRepository, never()).save(any(Cita.class));
    }

    @Test
    void rechazaElCambioDeEstadoCuandoLaCitaNoExiste() {
        // Simular que el repositorio no encuentra la cita solicitada.
        when(citaRepository.findById(99L))
                .thenReturn(Optional.empty());

        // Intentar cambiar el estado de una cita inexistente.
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> citaService.actualizarEstadoCita(99L, "Completada")
        );

        // Comprobar el motivo del rechazo y que no se intente guardar.
        assertEquals("Cita no encontrada", error.getMessage());
        verify(citaRepository, never()).save(any(Cita.class));
    }

    @Test
    void rechazaUnEstadoDeCitaNoPermitido() {
        // Preparar una cita existente para aislar el problema del estado.
        Cita existente = Cita.builder()
                .id(1L)
                .estado("Agendada")
                .build();

        // Exigir el rechazo de un estado ajeno a los valores permitidos.
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> citaService.actualizarEstadoCita(1L, "Desconocida")
        );

        assertEquals("Estado de cita no permitido", error.getMessage());
        assertEquals("Agendada", existente.getEstado());
        verify(citaRepository, never()).save(any(Cita.class));
    }

    @ParameterizedTest(name = "Permite cambiar el estado a {0}")
    @ValueSource(strings = {"Agendada", "Cancelada", "Completada"})
    void permiteActualizarConLosEstadosDefinidos(String estado) {
        // Preparar una cita existente para cada estado permitido.
        Cita existente = Cita.builder()
                .id(1L)
                .estado("Agendada")
                .build();

        when(citaRepository.findById(1L))
                .thenReturn(Optional.of(existente));
        when(citaRepository.save(any(Cita.class)))
                .thenAnswer(llamada -> llamada.getArgument(0));

        // Ejecutar el cambio con el valor recibido por la prueba parametrizada.
        Cita resultado = citaService.actualizarEstadoCita(1L, estado);

        // Comprobar el estado enviado a guardar.
        ArgumentCaptor<Cita> captor = ArgumentCaptor.forClass(Cita.class);
        verify(citaRepository).save(captor.capture());

        assertEquals(estado, resultado.getEstado());
        assertEquals(estado, captor.getValue().getEstado());
    }

    @Test
    void rechazaCitaCuandoLaMascotaNoExiste() {
        // Simular que el ID solicitado no corresponde a una mascota registrada.
        when(mascotaRepository.findById(99L))
                .thenReturn(Optional.empty());

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> citaService.agendarCita(
                        99L, 4L, fechaDePrueba(), "Control general"
                )
        );

        // La búsqueda debe detenerse antes de consultar usuarios o citas.
        assertEquals("Mascota no encontrada", error.getMessage());
        verifyNoInteractions(usuarioRepository, citaRepository);
    }

    @Test
    void rechazaCitaCuandoElEmpleadoNoExiste() {
        // Preparar una mascota con propietario registrado.
        Usuario propietario = propietarioValido();
        Mascota mascota = mascotaConPropietario(propietario);

        when(mascotaRepository.findById(2L))
                .thenReturn(Optional.of(mascota));
        when(usuarioRepository.findById(3L))
                .thenReturn(Optional.of(propietario));

        // Simular que la cuenta asignada a la atención no existe.
        when(usuarioRepository.findById(99L))
                .thenReturn(Optional.empty());

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> citaService.agendarCita(
                        2L, 99L, fechaDePrueba(), "Control general"
                )
        );

        assertEquals("Veterinario no encontrado", error.getMessage());
        verifyNoInteractions(citaRepository);
    }

    @Test
    void permiteAgendarCuandoElEmpleadoTieneUnaCitaEnOtroHorario() {
        // Preparar los registros necesarios para agendar.
        Usuario propietario = propietarioValido();
        Usuario empleado = empleadoValido();
        Mascota mascota = mascotaConPropietario(propietario);
        LocalDateTime fechaSolicitada = fechaDePrueba();

        // El empleado tiene una cita activa, pero una hora después.
        Cita otraCita = Cita.builder()
                .id(1L)
                .veterinario(empleado)
                .fechaHora(fechaSolicitada.plusHours(1))
                .estado("Agendada")
                .build();

        when(mascotaRepository.findById(2L))
                .thenReturn(Optional.of(mascota));
        when(usuarioRepository.findById(3L))
                .thenReturn(Optional.of(propietario));
        when(usuarioRepository.findById(4L))
                .thenReturn(Optional.of(empleado));
        when(citaRepository.findByVeterinarioId(4L))
                .thenReturn(List.of(otraCita));
        when(citaRepository.save(any(Cita.class)))
                .thenAnswer(llamada -> llamada.getArgument(0));

        // La cita de otro horario debe permitir la nueva reserva.
        Cita resultado = citaService.agendarCita(
                2L, 4L, fechaSolicitada, "Control general"
        );

        assertEquals(fechaSolicitada, resultado.getFechaHora());
        assertEquals("Agendada", resultado.getEstado());
        verify(citaRepository).save(any(Cita.class));
    }

    // Preparación común para que cada prueba cambie solo su condición específica.
    private Usuario propietarioValido() {
        return Usuario.builder()
                .id(3L)
                .nombre("Ana")
                .email("cliente@example.test")
                .rol(Rol.CLIENTE)
                .build();
    }

    private Usuario empleadoValido() {
        return Usuario.builder()
                .id(4L)
                .nombre("Pedro")
                .email("empleado@example.test")
                .rol(Rol.EMPLEADO)
                .build();
    }

    private Mascota mascotaConPropietario(Usuario propietario) {
        return Mascota.builder()
                .id(2L)
                .nombre("Luna")
                .especie("Perro")
                .propietario(propietario)
                .build();
    }

    private LocalDateTime fechaDePrueba() {
        return LocalDateTime.of(2027, 1, 20, 10, 0);
    }
}