package com.petsalud.backendpetsalud.service;

import com.petsalud.backendpetsalud.entity.Producto;
import com.petsalud.backendpetsalud.entity.Usuario;
import com.petsalud.backendpetsalud.entity.Venta;
import com.petsalud.backendpetsalud.repository.ProductoRepository;
import com.petsalud.backendpetsalud.repository.UsuarioRepository;
import com.petsalud.backendpetsalud.repository.VentaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Prueba las reglas de venta con repositorios simulados, incluidos los rechazos,
 * el total, el descuento de stock y las asociaciones del objeto que se guarda.
 */
@ExtendWith(MockitoExtension.class)
class VentaServiceImplTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private ProductoRepository productoRepository;

    @Mock
    private VentaRepository ventaRepository;

    @InjectMocks
    private VentaServiceImpl ventaService;

    @Test
    void rechazaLaVentaCuandoElProductoNoTieneStock() {
        Usuario usuario = usuarioValido();
        Producto alimento = producto(1L, "Alimento", 0, "1200.00");

        when(usuarioRepository.findById(3L)).thenReturn(Optional.of(usuario));
        when(productoRepository.findAllById(List.of(1L))).thenReturn(List.of(alimento));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> ventaService.realizarVenta(3L, List.of(1L))
        );

        assertTrue(error.getMessage().contains("no tiene stock disponible"));
        verifyNoInteractions(ventaRepository);
    }

    @Test
    void rechazaLaVentaCuandoElProductoNoExiste() {
        when(usuarioRepository.findById(3L)).thenReturn(Optional.of(usuarioValido()));
        when(productoRepository.findAllById(List.of(99L))).thenReturn(List.of());

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> ventaService.realizarVenta(3L, List.of(99L))
        );

        assertTrue(error.getMessage().contains("productos no fueron encontrados"));
        verifyNoInteractions(ventaRepository);
    }

    @Test
    void rechazaLaVentaCuandoElUsuarioNoExiste() {
        when(usuarioRepository.findById(99L)).thenReturn(Optional.empty());

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> ventaService.realizarVenta(99L, List.of(1L))
        );

        assertTrue(error.getMessage().contains("Usuario no encontrado"));
        verifyNoInteractions(productoRepository, ventaRepository);
    }

    @Test
    void calculaElTotalSumandoLosPreciosDeLosProductos() {
        Producto alimento = producto(1L, "Alimento", 2, "1200.00");
        Producto juguete = producto(2L, "Juguete", 3, "2500.00");

        when(usuarioRepository.findById(3L)).thenReturn(Optional.of(usuarioValido()));
        when(productoRepository.findAllById(List.of(1L, 2L)))
                .thenReturn(List.of(alimento, juguete));
        when(ventaRepository.save(any(Venta.class)))
                .thenAnswer(llamada -> llamada.getArgument(0));

        Venta venta = ventaService.realizarVenta(3L, List.of(1L, 2L));

        assertEquals(new BigDecimal("3700.00"), venta.getTotal());
    }

    @Test
    void descuentaUnaUnidadDeStockPorCadaProductoVendido() {
        Producto alimento = producto(1L, "Alimento", 2, "1200.00");
        Producto juguete = producto(2L, "Juguete", 3, "2500.00");

        when(usuarioRepository.findById(3L)).thenReturn(Optional.of(usuarioValido()));
        when(productoRepository.findAllById(List.of(1L, 2L)))
                .thenReturn(List.of(alimento, juguete));

        ventaService.realizarVenta(3L, List.of(1L, 2L));

        assertEquals(1, alimento.getStock());
        assertEquals(2, juguete.getStock());
        verify(productoRepository).save(alimento);
        verify(productoRepository).save(juguete);
    }

    @Test
    void vinculaLaVentaConElUsuarioCorrecto() {
        Producto alimento = producto(1L, "Alimento", 2, "1200.00");

        when(usuarioRepository.findById(3L)).thenReturn(Optional.of(usuarioValido()));
        when(productoRepository.findAllById(List.of(1L))).thenReturn(List.of(alimento));

        ventaService.realizarVenta(3L, List.of(1L));

        ArgumentCaptor<Venta> captor = ArgumentCaptor.forClass(Venta.class);
        verify(ventaRepository).save(captor.capture());

        assertEquals(3L, captor.getValue().getUsuario().getId());
    }

    @Test
    void vinculaLaVentaConLosProductosSolicitados() {
        Producto alimento = producto(1L, "Alimento", 2, "1200.00");
        Producto juguete = producto(2L, "Juguete", 3, "2500.00");

        when(usuarioRepository.findById(3L)).thenReturn(Optional.of(usuarioValido()));
        when(productoRepository.findAllById(List.of(1L, 2L)))
                .thenReturn(List.of(juguete, alimento));

        ventaService.realizarVenta(3L, List.of(1L, 2L));

        ArgumentCaptor<Venta> captor = ArgumentCaptor.forClass(Venta.class);
        verify(ventaRepository).save(captor.capture());

        // La relación depende de los productos asociados, independientemente del orden del repositorio.
        List<Long> idsAsociados = captor.getValue().getProductos().stream()
                .map(Producto::getId)
                .sorted()
                .toList();

        assertEquals(List.of(1L, 2L), idsAsociados);
    }

    @Test
    void rechazaLaVentaCuandoElUsuarioNoTieneDireccion() {
        Usuario usuarioSinDireccion = Usuario.builder()
                .id(3L)
                .nombre("Ana")
                .apellido("Perez")
                .email("cliente@example.test")
                .build();

        when(usuarioRepository.findById(3L)).thenReturn(Optional.of(usuarioSinDireccion));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> ventaService.realizarVenta(3L, List.of(1L))
        );

        assertTrue(error.getMessage().contains("datos incompletos"));
        verifyNoInteractions(productoRepository, ventaRepository);
    }

    // Las pruebas de productos y stock usan una ficha completa para llegar a esas reglas.
    private Usuario usuarioValido() {
        return Usuario.builder()
                .id(3L)
                .nombre("Ana")
                .apellido("Perez")
                .email("cliente@example.test")
                .direccion("Avenida de prueba 123")
                .build();
    }

    private Producto producto(Long id, String nombre, int stock, String precio) {
        return Producto.builder()
                .id(id)
                .nombre(nombre)
                .stock(stock)
                .precio(new BigDecimal(precio))
                .build();
    }
}