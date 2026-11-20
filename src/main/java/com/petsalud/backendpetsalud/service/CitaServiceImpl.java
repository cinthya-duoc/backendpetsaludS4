package com.petsalud.backendpetsalud.service;

import com.petsalud.backendpetsalud.entity.Cita;
import com.petsalud.backendpetsalud.entity.Mascota;
import com.petsalud.backendpetsalud.entity.Usuario;
import com.petsalud.backendpetsalud.repository.CitaRepository;
import com.petsalud.backendpetsalud.repository.MascotaRepository;
import com.petsalud.backendpetsalud.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.petsalud.backendpetsalud.entity.Rol;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class CitaServiceImpl implements CitaService {

    @Autowired
    private CitaRepository citaRepository;

    @Autowired
    private MascotaRepository mascotaRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Override
    public Cita agendarCita(Long mascotaId, Long veterinarioId, LocalDateTime fechaHora, String motivo) {
        Mascota mascota = mascotaRepository.findById(mascotaId)
                .orElseThrow(() -> new IllegalArgumentException("Mascota no encontrada"));

        // La cita necesita una mascota vinculada a un propietario con identificador.
        if (mascota.getPropietario() == null
                || mascota.getPropietario().getId() == null) {
            throw new IllegalArgumentException(
                    "La mascota debe tener un propietario registrado"
            );
        }

        // Confirmar que el propietario asociado está registrado en el sistema.
        usuarioRepository.findById(mascota.getPropietario().getId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Propietario no encontrado"
                ));

        Usuario veterinario = usuarioRepository.findById(veterinarioId)
                .orElseThrow(() -> new IllegalArgumentException("Veterinario no encontrado"));

        // La cuenta asignada a la atención clínica debe tener rol EMPLEADO.
        if (veterinario.getRol() != Rol.EMPLEADO) {
            throw new IllegalArgumentException(
                    "El usuario asignado debe tener rol EMPLEADO"
            );
        }

        // Comprobar la disponibilidad antes de guardar la nueva cita.
        // Las citas canceladas liberan su horario.
        boolean horarioOcupado = citaRepository.findByVeterinarioId(veterinarioId)
                .stream()
                .anyMatch(cita -> fechaHora.equals(cita.getFechaHora())
                        && !"Cancelada".equals(cita.getEstado()));

        if (horarioOcupado) {
            throw new IllegalArgumentException(
                    "El empleado ya tiene una cita en ese horario"
            );
        }

        Cita cita = Cita.builder()
                .mascota(mascota)
                .veterinario(veterinario)
                .fechaHora(fechaHora)
                .motivo(motivo)
                .estado("Agendada")
                .build();

        return citaRepository.save(cita);
    }

    @Override
    public Optional<Cita> obtenerCitaPorId(Long id) {
        return citaRepository.findById(id);
    }

    @Override
    public Cita actualizarEstadoCita(Long id, String nuevoEstado) {
        // Validar el estado antes de consultar o modificar la cita.
        boolean estadoPermitido = "Agendada".equals(nuevoEstado)
                || "Cancelada".equals(nuevoEstado)
                || "Completada".equals(nuevoEstado);

        if (!estadoPermitido) {
            throw new IllegalArgumentException("Estado de cita no permitido");
        }

        // Buscar la cita y guardar el cambio únicamente con un estado válido.
        return citaRepository.findById(id)
                .map(cita -> {
                    cita.setEstado(nuevoEstado);
                    return citaRepository.save(cita);
                })
                .orElseThrow(() -> new IllegalArgumentException("Cita no encontrada"));
    }

    @Override
    public void cancelarCita(Long id) {
        citaRepository.deleteById(id);
    }

    @Override
    public List<Cita> listarCitasPorMascota(Long mascotaId) {
        return citaRepository.findByMascotaId(mascotaId);
    }

    @Override
    public List<Cita> listarCitasPorVeterinario(Long veterinarioId) {
        return citaRepository.findByVeterinarioId(veterinarioId);
    }

    @Override
    public List<Cita> listarCitasEntreFechas(LocalDateTime inicio, LocalDateTime fin) {
        return citaRepository.findByFechaHoraBetween(inicio, fin);
    }
}

