# Revisión de pruebas a partir de la retroalimentación docente

## Correspondencia de las relaciones

El formato menciona una reserva y el proveedor de un producto reservado. El modelo implementado contiene ventas asociadas a usuarios y productos. Las pruebas verifican que el objeto Venta enviado al repositorio conserva su titular y los artículos seleccionados.

Venta–Usuario cubre la identidad del titular. Venta–Producto cubre la vinculación de la operación con sus artículos. Estas asociaciones aportan evidencia para el criterio de dos relaciones distintas del modelo. El backend carece de una entidad Proveedor y de una relación Reserva–Proveedor. Esa correspondencia específica queda fuera de lo demostrado y requiere una definición adicional del modelo.

## Regla evaluada, capa y evidencia

| Escenario | Regla | Capa | Evidencia |
|---|---|---|---|
| Registro sin nombre, apellido, correo o dirección | Campos obligatorios | Web con UsuarioService simulado | Cuatro casos parametrizados devuelven 400 sin llamar al servicio |
| Ficha persistida sin uno de los cuatro campos | Venta con usuario completo | Servicio con UsuarioRepository simulado | Cuatro casos parametrizados rechazan antes de consultar productos o guardar |
| Cliente propietario o ajeno en el controlador | Aplicar la respuesta de propiedad | Autorización con Propiedad simulado | Compra propia permitida y compra ajena rechazada |
| Solicitante real coincide con titular o es otra persona | Comparar IDs de UsuarioDetails | Componente real Propiedad | esUsuario devuelve true para titular y false para ajeno |
| Solicitud sin identidad | La identidad ausente no acredita propiedad | Componente real Propiedad | Devuelve false |
| Un producto con precio 12.345 | Sumar una sola unidad | Servicio de ventas | Total exacto 12.345 y stock de 2 a 1 |
| Precios 12.3 y 0.45 | Sumar escalas distintas | Servicio de ventas | Total exacto 12.75 |
| Precios 0.10 y 0.20 | Suma decimal exacta | Servicio de ventas | Total exacto 0.30 |
| Precios 12.345 y 0.005 | Conservar escala de tres decimales | Servicio de ventas | Total exacto 12.350 |
| Lista vacía o nula | Venta con al menos un producto | Servicio de ventas | Excepción antes de consultar o guardar |

## Cantidades y escala monetaria

La operación recibe identificadores y descuenta una unidad por producto. Los casos de uno y dos productos representan artículos distintos. El modelo no recibe una cantidad por línea. Probar varias unidades de un artículo requiere definir ese contrato y su regla de stock.

BigDecimal.equals comprueba el valor y la escala. Estos casos evalúan el cálculo en memoria. La precisión de columnas y el redondeo al persistir requieren una política monetaria y pruebas de integración.

## Preparación y reproducción

UsuarioRegistroTest construye MockMvc en BeforeEach y agrupa campos con pruebas parametrizadas cuyos nombres identifican el campo omitido. VentaServiceImplTest reutiliza usuarioValido y producto. PropiedadTest ejecuta esUsuario con el componente real y la identidad UsuarioDetails.

Con Java 17, ejecutar Maven con la selección Usuario*Test,Venta*Test,PropiedadTest y la fase verify. El HTML queda en target/site/jacoco-usuarios-ventas/index.html. El reporte mantiene el alcance declarado de dos controladores y dos servicios. PropiedadTest evalúa una clase adicional situada fuera de ese alcance de cobertura.

Conservar las capturas anteriores como evidencia de la primera entrega. Agregar una nueva captura del resumen y los nombres parametrizados para documentar esta revisión.
