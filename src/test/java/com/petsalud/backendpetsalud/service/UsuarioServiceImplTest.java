package com.petsalud.backendpetsalud.service;

import com.petsalud.backendpetsalud.entity.Rol;
import com.petsalud.backendpetsalud.entity.Usuario;
import com.petsalud.backendpetsalud.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.petsalud.backendpetsalud.exception.EmailDuplicadoException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import java.util.List;

/**
 * Comprueba registro, actualización, consultas y borrado con un repositorio
 * simulado. El codificador simulado permite verificar qué contraseña se guarda.
 */
@ExtendWith(MockitoExtension.class)
class UsuarioServiceImplTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UsuarioServiceImpl usuarioService;

    @Test
    void registraConRolClienteYContrasenaCodificada() {
        Usuario usuario = Usuario.builder()
                .nombre("Ana")
                .apellido("Perez")
                .email("cliente@example.test")
                .direccion("Avenida de prueba 123")
                .password("Prueba123")
                .rol(Rol.GERENTE)
                .build();

        when(usuarioRepository.findByEmail("cliente@example.test"))
                .thenReturn(Optional.empty());
        when(passwordEncoder.encode("Prueba123")).thenReturn("hash-de-prueba");
        when(usuarioRepository.save(any(Usuario.class)))
                .thenAnswer(llamada -> llamada.getArgument(0));

        Usuario registrado = usuarioService.registrarUsuario(usuario);

        assertEquals(Rol.CLIENTE, registrado.getRol());
        assertEquals("hash-de-prueba", registrado.getPassword());
    }

    @Test
    void rechazaElRegistroCuandoElCorreoYaExiste() {
        Usuario usuarioExistente = Usuario.builder()
                .id(3L)
                .email("cliente@example.test")
                .build();
        Usuario nuevoUsuario = Usuario.builder()
                .email("cliente@example.test")
                .password("Prueba123")
                .build();

        when(usuarioRepository.findByEmail("cliente@example.test"))
                .thenReturn(Optional.of(usuarioExistente));

        assertThrows(EmailDuplicadoException.class,
                () -> usuarioService.registrarUsuario(nuevoUsuario));

        verifyNoInteractions(passwordEncoder);
        verify(usuarioRepository, never()).save(any(Usuario.class));
    }

    @Test
    void actualizaDatosDeContactoYConservaCorreoContrasenaYRol() {
        Usuario existente = Usuario.builder()
                .id(3L)
                .nombre("Ana")
                .apellido("Perez")
                .telefono("912345678")
                .direccion("Avenida Antigua 123")
                .email("cliente@example.test")
                .password("hash-original")
                .rol(Rol.CLIENTE)
                .build();

        Usuario cambios = Usuario.builder()
                .nombre("Maria")
                .apellido("Rojas")
                .telefono("987654321")
                .direccion("Avenida Nueva 456")
                .email("otro@example.test")
                .password("otra-clave")
                .rol(Rol.GERENTE)
                .build();

        when(usuarioRepository.findById(3L)).thenReturn(Optional.of(existente));
        when(usuarioRepository.save(any(Usuario.class)))
                .thenAnswer(llamada -> llamada.getArgument(0));

        Usuario actualizado = usuarioService.actualizarUsuario(3L, cambios);

        assertEquals("Maria", actualizado.getNombre());
        assertEquals("Rojas", actualizado.getApellido());
        assertEquals("987654321", actualizado.getTelefono());
        assertEquals("Avenida Nueva 456", actualizado.getDireccion());
        assertEquals("cliente@example.test", actualizado.getEmail());
        assertEquals("hash-original", actualizado.getPassword());
        assertEquals(Rol.CLIENTE, actualizado.getRol());
    }

    @Test
    void rechazaLaActualizacionCuandoElUsuarioNoExiste() {
        when(usuarioRepository.findById(99L)).thenReturn(Optional.empty());

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> usuarioService.actualizarUsuario(99L, Usuario.builder().build())
        );

        assertEquals("Usuario no encontrado", error.getMessage());
        verify(usuarioRepository, never()).save(any(Usuario.class));
    }

    @Test
    void encuentraUnUsuarioPorId() {
        Usuario usuario = Usuario.builder().id(3L).build();
        when(usuarioRepository.findById(3L)).thenReturn(Optional.of(usuario));

        assertEquals(Optional.of(usuario), usuarioService.obtenerUsuarioPorId(3L));
    }

    @Test
    void encuentraUnUsuarioPorCorreo() {
        String correo = "cliente@example.test";
        Usuario usuario = Usuario.builder().email(correo).build();
        when(usuarioRepository.findByEmail(correo)).thenReturn(Optional.of(usuario));

        assertEquals(Optional.of(usuario), usuarioService.obtenerUsuarioPorEmail(correo));
    }

    @Test
    void listaLosUsuariosDelRepositorio() {
        List<Usuario> usuarios = List.of(
                Usuario.builder().id(3L).build(),
                Usuario.builder().id(4L).build()
        );
        when(usuarioRepository.findAll()).thenReturn(usuarios);

        assertEquals(usuarios, usuarioService.listarUsuarios());
    }

    @Test
    void eliminaAlUsuarioConElIdIndicado() {
        usuarioService.eliminarUsuario(3L);

        verify(usuarioRepository).deleteById(3L);
    }

}