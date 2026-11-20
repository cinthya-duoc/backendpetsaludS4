# Backend Clínica PetSalud – Semana 5 (Pruebas Unitarias)

Backend de la Clínica Veterinaria **PetSalud** desarrollado para la asignatura **Desarrollo Backend II (PBY2202)** de Duoc UC Online.

Sobre la base de la semana 2 (Spring Security + JWT), en esta etapa se integró la seguridad completa del backend:

- **Autorización a nivel de objeto:** cada cliente solo puede ver y modificar *sus* datos (usuario, mascotas, citas, historial médico, compras, notificaciones y recompensas), aunque cambie los ids de la URL.
- **JWT reforzado:** el token incluye id único (`jti`), emisor (`iss`) y audiencia (`aud`), y se validan en cada petición.
- **Validación de entradas** con listas blancas (`@Pattern`, `@Size`, `@Min`, `@Max`...) contra inyección SQL y XSS.
- **Errores uniformes en JSON** (400, 401, 403, 404, 409, 429, 500) sin exponer detalles internos.
- **Secretos fuera del código** en un archivo `.env` que no se sube al repositorio.
- **Monitoreo de actividad sospechosa** en un log y **bloqueo del login** tras 5 intentos fallidos.
- **Pruebas automatizadas en Postman:** 43 de 43 aprobadas.

---

## Tecnologías

| Tecnología | Versión |
|---|---|
| Java | 17 o superior |
| Spring Boot | 3.3.6 |
| Spring Security | 6.3 |
| Spring Data JPA / Hibernate | 6.5 |
| Bean Validation (`spring-boot-starter-validation`) | 3.x |
| jjwt (Java JWT) | 0.12.6 |
| MySQL | 8 |
| Lombok | — |
| Maven (wrapper incluido) | — |

---

## Requisitos previos

- Java 17 o superior (`java -version`)
- MySQL 8 en ejecución (instalado localmente o en Docker)
- Postman, para ejecutar la colección de pruebas

---

## Instalación y ejecución

### 1. Clonar el repositorio

```bash
git clone https://github.com/matisuarezm/backendpetsaludS3.git
cd backendpetsaludS3
```

### 2. Crear la base de datos y el usuario en MySQL

```sql
CREATE DATABASE petsalud_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'petsalud_user'@'%' IDENTIFIED BY 'tu_clave';
GRANT ALL PRIVILEGES ON petsalud_db.* TO 'petsalud_user'@'%';
FLUSH PRIVILEGES;
```

Las tablas se crean automáticamente al iniciar la aplicación (`spring.jpa.hibernate.ddl-auto=update`).

### 3. Crear el archivo `.env` con los secretos

Copiar `.env.example` como `.env` (en la raíz del proyecto, junto al `pom.xml`) y completar los valores:

```properties
DB_USER=petsalud_user
DB_PASSWORD=tu_clave
JWT_SECRET=clave_en_base64_de_al_menos_32_bytes
```

Para generar una `JWT_SECRET` segura:

```powershell
# Windows (PowerShell)
$b = New-Object byte[] 32; [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b); [Convert]::ToBase64String($b)
```

```bash
# Mac / Linux
openssl rand -base64 32
```

> **Importante:** `.env` está en `.gitignore` y **nunca** debe subirse a GitHub. `application.properties` solo contiene referencias a las variables (`${DB_USER}`, `${DB_PASSWORD}`, `${JWT_SECRET}`).

### 4. Ejecutar

Antes de iniciar esta versión, aplica las migraciones de [revocación de JWT](src/main/resources/db/jwt-revocations.sql) y [bloqueo de login](src/main/resources/db/login-attempts.sql). En la consola MySQL, selecciona la base que usa la API con una cuenta que pueda crear tablas y ejecuta:

```sql
SOURCE /ruta/al/proyecto/src/main/resources/db/jwt-revocations.sql;
SOURCE /ruta/al/proyecto/src/main/resources/db/login-attempts.sql;
```

La cuenta de la aplicación necesita permisos de lectura, inserción, actualización y borrado sobre `jwt_revocations` y `auth_login_attempts`. Todas las instancias deben apuntar a la misma base para compartir estos controles.

```bash
# Windows
.\mvnw clean spring-boot:run

# Mac / Linux
chmod +x mvnw
./mvnw clean spring-boot:run
```

La API queda disponible en `http://localhost:8080` y el log de la aplicación se guarda en `logs/petsalud.log`.

### 5. Crear los usuarios de prueba

El registro público siempre asigna el rol `CLIENTE`. Para tener un gerente y un empleado:

1. Registrar ambos usuarios con `POST /api/usuarios/registro` (ver ejemplo más abajo):
   - `gerente@petsalud.cl` / `Gerente123`
   - `empleado@petsalud.cl` / `Empleado123`
2. Cambiar sus roles directamente en la base de datos:

```sql
UPDATE petsalud_db.usuarios SET rol = 'GERENTE'  WHERE email = 'gerente@petsalud.cl';
UPDATE petsalud_db.usuarios SET rol = 'EMPLEADO' WHERE email = 'empleado@petsalud.cl';
```

---

## Cómo funciona la seguridad

1. **Login:** el usuario envía email y contraseña a `POST /auth/login`. Spring Security busca la cuenta en MySQL (`UsuarioDetailsService`) y compara la contraseña con **BCrypt**.
2. **Token:** si es correcta, `JwtUtil` entrega un **token JWT** firmado con HS256, válido por **15 minutos**, con los claims `jti`, `iss` (`petsalud-api`), `aud` (`petsalud-clientes`), `sub` (email), `iat`, `exp` y una versión derivada de las credenciales.
3. **Cada petición:** el cliente envía `Authorization: Bearer <token>`. `JwtAuthFilter` valida firma, emisor, audiencia, vencimiento y revocación por `jti`. Verifica también que la contraseña no haya cambiado y carga al usuario con su rol.
4. **Autorización por rol:** `SecurityConfig` decide qué rol puede usar cada ruta.
5. **Autorización por propiedad:** `@PreAuthorize` junto con el componente `Propiedad` verifica en la base de datos que el recurso pedido pertenezca al usuario logueado.

```java
@PreAuthorize("hasAnyAuthority('EMPLEADO','GERENTE') or @propiedad.esDuenoDeMascota(#id, authentication)")
@GetMapping("/{id}")
public ResponseEntity<Mascota> obtenerMascotaPorId(@PathVariable Long id) { ... }
```

### Respuestas de error

Todos los errores usan el mismo formato JSON:

```json
{
  "timestamp": "2026-11-06T12:30:24.220161200Z",
  "status": 401,
  "error": "Unauthorized",
  "message": "Token inválido o expirado",
  "path": "/api/usuarios/1"
}
```

| Código | Cuándo ocurre |
|---|---|
| `400 Bad Request` | Datos inválidos (falla una validación), JSON mal formado o parámetro faltante |
| `401 Unauthorized` | Sin token, token alterado o vencido, o email/contraseña incorrectos en el login |
| `403 Forbidden` | El rol no tiene permiso o el recurso pertenece a otra persona |
| `404 Not Found` | El recurso no existe (solo se informa al personal) |
| `409 Conflict` | El email ya está registrado |
| `429 Too Many Requests` | Login bloqueado por 15 minutos tras 5 intentos fallidos |
| `500 Internal Server Error` | Error inesperado (mensaje genérico; el detalle queda solo en el log) |

> Si un **cliente** pide un recurso que no existe, recibe `403` (igual que si fuera de otra persona). Así no puede descubrir qué ids existen probando números.

---

## Roles y permisos

| Rol | Descripción |
|---|---|
| *Visitante* (sin login) | Solo puede registrarse e iniciar sesión |
| `CLIENTE` | Dueño de mascotas. Accede únicamente a sus propios datos y al catálogo de productos |
| `EMPLEADO` | Personal clínico. Consulta clientes y mascotas, gestiona expedientes médicos y el estado de las citas |
| `GERENTE` | Administración. Acceso total: usuarios, productos e inventario, ventas, notificaciones, recompensas y eliminaciones |

---

## Endpoints

**Propio** = el cliente solo puede usarlo con sus propios datos (verificado con `@PreAuthorize` y `Propiedad`).

### Públicos

| Método | Ruta | Descripción |
|---|---|---|
| POST | `/api/usuarios/registro` | Registrar un usuario (siempre rol CLIENTE) |
| POST | `/auth/login` | Iniciar sesión y obtener el token |

### Sesión autenticada

| Método | Ruta | Descripción |
|---|---|---|
| POST | `/auth/logout` | Revocar el JWT presentado en `Authorization` y responder `204` |

### Usuarios y mascotas

| Método | Ruta | CLIENTE | EMPLEADO | GERENTE |
|---|---|---|---|---|
| GET | `/api/usuarios` | — | — | ✅ |
| GET | `/api/usuarios/{id}` | Propio | ✅ | ✅ |
| PUT | `/api/usuarios/{id}` | Propio | — | ✅ |
| DELETE | `/api/usuarios/{id}` | — | — | ✅ |
| POST | `/api/mascotas/propietario/{propietarioId}` | Propio | ✅ | ✅ |
| GET | `/api/mascotas/propietario/{propietarioId}` | Propio | ✅ | ✅ |
| GET / PUT | `/api/mascotas/{id}` | Propio | ✅ | ✅ |
| DELETE | `/api/mascotas/{id}` | — | — | ✅ |

`PUT /api/usuarios/{id}` solo modifica nombre, apellido, teléfono y dirección (DTO `ActualizarUsuarioRequest`). No es posible cambiar el rol, el email ni la contraseña por esta vía.

### Citas y expedientes médicos

| Método | Ruta | CLIENTE | EMPLEADO | GERENTE |
|---|---|---|---|---|
| POST | `/api/citas?mascotaId=&veterinarioId=&fechaHora=&motivo=` | Propio | ✅ | ✅ |
| GET | `/api/citas/{id}` | Propio | ✅ | ✅ |
| GET | `/api/citas/mascota/{mascotaId}` | Propio | ✅ | ✅ |
| PUT | `/api/citas/{id}/estado` | — | ✅ | ✅ |
| GET | `/api/citas/veterinario/{veterinarioId}` | — | ✅ | ✅ |
| GET | `/api/citas/entre-fechas?inicio=&fin=` | — | ✅ | ✅ |
| DELETE | `/api/citas/{id}` | — | — | ✅ |
| GET | `/api/expedientes/mascota/{mascotaId}` | Propio | ✅ | ✅ |
| POST | `/api/expedientes?mascotaId=&veterinarioId=` | — | ✅ | ✅ |
| GET | `/api/expedientes/{id}` | — | ✅ | ✅ |
| DELETE | `/api/expedientes/{id}` | — | — | ✅ |

### Productos y ventas

| Método | Ruta | CLIENTE | EMPLEADO | GERENTE |
|---|---|---|---|---|
| GET | `/api/productos`, `/api/productos/{id}`, `/api/productos/categoria?categoria=` | ✅ | ✅ | ✅ |
| POST / PUT | `/api/productos`, `/api/productos/{id}` | — | — | ✅ |
| GET | `/api/productos/stock-bajo?cantidad=` | — | — | ✅ |
| DELETE | `/api/productos/{id}` | — | — | ✅ |
| POST | `/api/ventas?usuarioId=` | Propio | ✅ | ✅ |
| GET | `/api/ventas/{id}` | Propio | ✅ | ✅ |
| GET | `/api/ventas/usuario/{usuarioId}` | Propio | ✅ | ✅ |
| GET | `/api/ventas` | — | — | ✅ |

### Notificaciones y recompensas

| Método | Ruta | CLIENTE | EMPLEADO | GERENTE |
|---|---|---|---|---|
| POST | `/api/notificaciones/usuario/{usuarioId}` | — | — | ✅ |
| GET | `/api/notificaciones/{id}` | Propio | — | ✅ |
| GET | `/api/notificaciones/usuario/{usuarioId}` y `/no-leidas` | Propio | — | ✅ |
| PUT | `/api/notificaciones/{id}/leida` | Propio | — | ✅ |
| POST | `/api/recompensas/usuario/{usuarioId}` | — | — | ✅ |
| GET | `/api/recompensas/{id}` | Propio | — | ✅ |
| GET | `/api/recompensas/usuario/{usuarioId}` | Propio | — | ✅ |
| DELETE | `/api/notificaciones/{id}`, `/api/recompensas/{id}` | — | — | ✅ |

---

## Validación de entradas

Las reglas están en las entidades, en los DTO y en los parámetros de los controladores. Las expresiones reutilizables están en `validation/Patrones.java` y funcionan como **listas blancas**: definen qué caracteres se aceptan y rechazan el resto con `400`.

| Patrón | Acepta | Se usa en |
|---|---|---|
| `NOMBRE` | Solo letras (con tildes y ñ), separadas por espacio o guion | Nombres, apellidos, especie, raza, categoría |
| `TEXTO_SEGURO` | Texto libre **sin** `` < > " ' ` ; \ `` ni `--` | Dirección, descripciones, diagnósticos, mensajes, motivo de la cita |
| `TELEFONO` | `+` opcional y 8 a 12 dígitos | Teléfono |
| `PASSWORD` | 8 a 72 caracteres, con letras y números, sin espacios | Contraseña |

Además: `@Email`, `@Size`, `@Min`/`@Max` (stock, puntos), `@DecimalMin`/`@Digits` (precio), `@PastOrPresent` (fecha de nacimiento) y `@FutureOrPresent` (fecha de la cita).

**Ejemplo (XSS):** registrarse con `"nombre": "<script>alert(1)</script>"` responde `400` con `"nombre: Solo puede contener letras"`.

---

## Monitoreo de actividad sospechosa

MySQL guarda los intentos en `auth_login_attempts` bajo una clave derivada del correo canónico y la IP de la conexión. Se cuentan los fallos de los últimos 15 minutos. El quinto fallo responde `401` y activa un bloqueo de 15 minutos desde ese momento. Los siguientes intentos reciben `429`, incluso con la contraseña correcta. Un login exitoso antes del bloqueo elimina el contador. La hora y el bloqueo de fila de MySQL mantienen un conteo común entre instancias. Un proceso programado limpia registros vencidos.

`ActividadSospechosaService` escribe en el log con el nombre `SEGURIDAD` (en la consola y en `logs/petsalud.log`):

| Evento | Significado |
|---|---|
| `LOGIN_FALLIDO` | Email o contraseña incorrectos (con contador `n/5`) |
| `CUENTA_BLOQUEADA` | Se alcanzaron 5 fallos en 15 minutos |
| `LOGIN_RECHAZADO_POR_BLOQUEO` | Intento de login mientras la cuenta está bloqueada (responde `429`) |
| `LOGIN_EXITOSO` | Login correcto; reinicia el contador |
| `TOKEN_INVALIDO` | Token alterado, vencido o de un usuario eliminado |
| `ACCESO_SIN_TOKEN` | Petición a una ruta protegida sin token |
| `ACCESO_DENEGADO` | El rol no alcanza o el recurso pertenece a otra persona |

Ejemplo:

```
WARN ... SEGURIDAD : LOGIN_FALLIDO email=ge***@petsalud.cl ip=0:0:0:0:0:0:0:1 intentos=5/5
WARN ... SEGURIDAD : CUENTA_BLOQUEADA email=ge***@petsalud.cl ip=0:0:0:0:0:0:0:1 minutos=15
WARN ... SEGURIDAD : ACCESO_DENEGADO usuario=cl***@petsalud.cl ip=0:0:0:0:0:0:0:1 metodo=GET ruta=/api/usuarios
```

Cuidados del registro:

- Nunca se guardan contraseñas ni tokens.
- El email se oculta parcialmente.
- Se eliminan los saltos de línea de los datos recibidos, para evitar *log injection*.
- La IP se toma de la conexión real, no del encabezado `X-Forwarded-For`, que el cliente puede falsificar.

---

## Pruebas con Postman

La colección `postman/PetSalud_S3_Pruebas_Propiedad.postman_collection.json` tiene 41 peticiones ordenadas en 6 carpetas:

| Carpeta | Qué prueba |
|---|---|
| 0. Preparación | Login del gerente y del empleado; registro y login de dos clientes de prueba (A y B) y sus mascotas |
| 1. Usuarios | A ve y edita solo su usuario; intento de subirse a GERENTE (el rol se mantiene CLIENTE) |
| 2. Mascotas | A no ve ni edita la mascota de B; mascota inexistente: `403` para el cliente y `404` para el empleado |
| 3. Citas | A agenda citas solo para sus mascotas |
| 4. Historial médico | A ve el historial de su mascota; B no puede; un cliente no crea expedientes |
| 5. Compras, notificaciones y recompensas | Un cliente no compra a nombre de otro ni ve datos ajenos |

**Cómo ejecutarla:**

1. Postman → **Import** → seleccionar el archivo `.json`.
2. Revisar las variables de la colección `emailGerente`/`claveGerente` y `emailEmpleado`/`claveEmpleado` (deben ser usuarios existentes, ver paso 5 de la instalación).
3. Clic derecho en la colección → **Run collection** → **Run**.

Los tokens e ids se guardan automáticamente en variables. Resultado esperado: **43 pruebas aprobadas, 0 fallidas**.

---

## Estructura del proyecto

```
src/main/java/com/petsalud/backendpetsalud/
├── controller/          # Endpoints REST con @PreAuthorize y @Valid (incluye AuthController)
├── dto/                 # LoginRequest, AuthResponse, ActualizarUsuarioRequest, ErrorResponse
├── entity/              # Entidades JPA con validaciones y enum Rol
├── exception/           # GlobalExceptionHandler, EmailDuplicadoException, CuentaBloqueadaException
├── jwt/                 # JwtUtil (crear/validar tokens) y JwtAuthFilter
├── repository/          # Repositorios Spring Data JPA (consultas parametrizadas)
├── security/            # SecurityConfig, Propiedad, UsuarioDetails(Service), manejadores 401/403
│   └── monitor/         # ActividadSospechosaService (log de seguridad y bloqueo)
├── service/             # Lógica de negocio
└── validation/          # Patrones (expresiones regulares reutilizables)
postman/                 # Colección de pruebas de Postman
.env.example             # Plantilla de variables secretas (el .env real no se sube)
```

---

## CORS y encabezados de seguridad

Para un frontend que llama a la API desde otro origen, agrega `APP_CORS_ALLOWED_ORIGINS` al archivo `.env`. El valor es una lista de orígenes separados por comas, con esquema, host y puerto cuando corresponde. Por ejemplo, `APP_CORS_ALLOWED_ORIGINS=http://localhost:5173,https://app.example.com`. Una lista vacía bloquea las peticiones de navegador desde otros orígenes.

La política aplica a `/auth/**` y `/api/**`. Permite GET, POST, PUT y DELETE con los encabezados de solicitud `Authorization` y `Content-Type`. El navegador puede enviar un preflight sin token para comprobar la política. La solicitud protegida posterior requiere su Bearer.

La API JSON usa una CSP que bloquea la carga de recursos, la URL base, los formularios y el uso en marcos. HSTS se envía por HTTPS durante un año y aplica al host de la API. `Referrer-Policy: no-referrer` evita enviar la URL de origen.

| Escenario | Resultado esperado |
|---|---|
| Preflight desde un origen configurado, con método PUT y encabezados permitidos | `200` y `Access-Control-Allow-Origin` con el origen solicitado |
| Preflight desde un origen ajeno o con un método fuera de la política | `403` |
| Respuesta HTTPS de la API | `Content-Security-Policy`, `Strict-Transport-Security` y `Referrer-Policy` |

## Revocación y renovación del JWT

`POST /auth/logout` guarda en MySQL el `jti` y el vencimiento del token presentado. Todas las instancias que comparten esa base rechazan el token con `401`, incluso después de reiniciar. Los registros vencidos se limpian cada cinco minutos.

Para renovar la sesión, vuelve a autenticarte en `POST /auth/login`. La vigencia corta limita la exposición de un token y volver a pedir credenciales evita mantener otro token de larga duración. `JWT_EXPIRATION_MS` permite reducir la vigencia a partir de un segundo. Todas las instancias deben compartir la clave de firma, el emisor y la audiencia.

Ante una contraseña comprometida, actualízala para invalidar los JWT anteriores de esa cuenta. Si se compromete la clave de firma, rótala en todas las instancias para invalidar todos los JWT previos.

| Escenario | Resultado esperado |
|---|---|
| Cerrar sesión con un JWT vigente | `204`; la tabla conserva su `jti` hasta la expiración |
| Reutilizar el token revocado en otra instancia o tras un reinicio | `401` |
| Usar otro JWT vigente de la misma cuenta después del cierre de sesión | La solicitud sigue autorizada según el rol |
| Reutilizar un JWT después de cambiar la contraseña de su cuenta | `401` |

En la verificación local, dos instancias conectadas al mismo MySQL rechazaron con `401` el JWT revocado. El rechazo persistió tras reiniciar una instancia. También se comprobó que un segundo JWT siguió activo tras cerrar la primera sesión y dejó de funcionar después de cambiar la contraseña.

## Medidas de seguridad implementadas

| Amenaza | Medida |
|---|---|
| Acceso a datos de otros clientes (OWASP API1 – BOLA) | `@PreAuthorize` + `Propiedad`; `403` también para ids inexistentes |
| Escalamiento de privilegios | Rol `CLIENTE` forzado en el registro; DTO sin campo `rol` en la actualización; reglas por rol en ruta y método |
| Inyección SQL | Spring Data JPA con consultas parametrizadas + validación de entradas |
| XSS | Listas blancas que rechazan `< > " ' ;`; respuestas JSON; CSP explícita, `X-Content-Type-Options` y `X-Frame-Options` |
| CSRF | API sin estado (`STATELESS`) con token en el encabezado `Authorization`, sin cookies; por eso CSRF está deshabilitado |
| Fuerza bruta | Bloqueo de 15 minutos tras 5 intentos fallidos (`429`) |
| Token alterado o falso | Firma HS256 + verificación de emisor, audiencia, vigencia de 15 minutos y revocación por `jti` |
| Exposición de datos | Contraseña `WRITE_ONLY` (nunca se devuelve); `@JsonIgnore` en relaciones; errores genéricos; emails ocultos en el log |
| Exposición de secretos | Clave JWT y credenciales en `.env` (excluido de Git) |

## Matriz de verificación del feedback

| Control | Escenario probado | Resultado observado |
|---|---|---|
| Ventas y propiedad | Lista vacía con el ID propio. Cliente con una lista válida intenta comprar para otro usuario. | `400` y `403`, respectivamente. |
| Citas y expedientes | Cliente y empleado cambian el estado de una cita. Cliente consulta historiales propios y ajenos. Empleado y gerente intentan borrar un expediente. | Cita `403` para cliente y `200` para empleado, con estado persistido. Historial propio `200` y ajeno `403`. Borrado `403` para empleado y `204` para gerente. |
| CORS y encabezados | Preflight permitido y preflights con origen, método o encabezado ajeno. Solicitud HTTPS. | `200` para el permitido y `403` para los demás. CSP, HSTS y `Referrer-Policy` presentes por HTTPS. |
| Revocación de JWT | Logout y reutilización del mismo token en dos instancias, incluida una reiniciada. Cambio de contraseña. | Logout `204`; token revocado `401` en ambas instancias. Tokens anteriores al cambio de contraseña recibieron `401`. |
| Bloqueo de login | Ocho intentos simultáneos repartidos entre dos instancias sobre la misma cuenta e IP. Reinicio con bloqueo vigente y simulación del vencimiento. | Cinco `401` y tres `429`. MySQL conservó cinco fallos y el bloqueo sobrevivió al reinicio. Al simular el vencimiento, la contraseña correcta volvió a dar `200`. |

## Limitaciones y mejoras futuras

- HTTPS obligatorio en producción.
- Rotación periódica de la clave JWT.

---

**Equipo:** Cinthya Guzmán, Mercedes Malandrino y Matías Suárez – Duoc UC Online, Analista Programador
