# CollabPro Platform — base DDD

Base de backend Spring Boot/Java para preparar las primeras 18 **posiciones del Product Backlog** del reporte.

## Ejecución y pruebas

Requiere Java 17 o superior. El perfil predeterminado `local` utiliza MySQL en `localhost:3307` y Mailpit para correo local.

```powershell
docker compose up -d
.\mvnw.cmd spring-boot:run
# Pruebas aisladas: H2 en modo MySQL, sin Docker ni credenciales de proveedores.
.\mvnw.cmd test
```

Flyway aplica las migraciones al iniciar. V1–V5 se conservan intactas; V6 añade resultados OAuth y V7 el registro de reintentos. No es necesario borrar la base de datos existente.

## Autenticación JWT

`POST /api/v1/auth/sessions` mantiene los campos `account`, `accessToken`, `tokenType="Bearer"` y `expiresAt`; ahora `accessToken` es un **JWT firmado con HS256**, no una cadena opaca. Los registros de empresa/creador siguen devolviendo la cuenta sin iniciar sesión automáticamente.

Enviar `Authorization: Bearer <accessToken>` en las rutas protegidas. El servidor valida firma, algoritmo, emisor, audiencia, vencimiento, fecha de emisión, inicio de validez, identificador de token y sujeto. También comprueba que la sesión siga registrada y que la cuenta esté activa y conserve el rol firmado. Los claims incluyen `iss`, `sub` (AccountId, no ProfileId), `aud`, `iat`, `nbf`, `exp`, `jti` y `role`; no incluyen correo, contraseña ni tokens de redes sociales.

El registro de sesiones guarda únicamente el hash SHA-256 del JWT. Restablecer la contraseña revoca las sesiones anteriores, incluso si sus JWT aún no han vencido. Los tokens opacos emitidos antes de este cambio ya no son válidos: hay que volver a iniciar sesión. No se añade refresh token ni un servidor de autorización propio. Logout remoto continúa pendiente; el cliente puede borrar su credencial localmente.

| Variable | Uso |
|---|---|
| `JWT_SECRET` | Clave aleatoria de al menos 32 bytes, codificada en Base64; obligatoria fuera de `local`/pruebas |
| `JWT_ISSUER` | Emisor esperado; predeterminado `collabpro-platform` |
| `JWT_AUDIENCE` | Audiencia esperada; predeterminado `collabpro-clients` |
| `JWT_TTL_SECONDS` | Duración; predeterminado 3600, permitido 60–86400 |

En `local` y pruebas hay una clave pública **solo de desarrollo**, distinta de la clave de cifrado de credenciales. Para despliegue, seleccionar explícitamente un perfil no local y configurar datasource, `IDENTITY_ENCRYPTION_KEY` y `JWT_SECRET` mediante secretos del entorno. Nunca copiar la clave de firma a Android/web ni registrarla en Git. Cambiar la clave invalida los JWT previos. Utilizar HTTPS fuera del desarrollo local.

La emisión y validación quedan detrás de `AccessTokenProvider` y `AccessTokenVerifier` en Application; Spring Security/Nimbus y el registro revocable pertenecen a Infrastructure. Se utiliza [el soporte JWT oficial de Spring Security](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html).

## Ajustes de campañas dentro de V1

`GET /api/v1/campaigns` y `GET /api/v1/campaigns/published` muestran solo campañas `OPEN` cuya fecha límite sea futura; el total paginado usa el mismo instante que la consulta. Los resúmenes incluyen `acceptsApplications`, al igual que el detalle. Las vencidas permanecen consultables por ID, sin cambiar su estado como efecto de una lectura.

| Operación nueva | Permisos y comportamiento |
|---|---|
| `DELETE /api/v1/campaigns/{id}` | Empresa propietaria, únicamente `DRAFT` sin postulaciones; elimina también requisitos/entregables del borrador; devuelve 204 |
| `POST /api/v1/campaigns/{id}/closure` | Empresa propietaria, `OPEN` → `CLOSED`; repetir sobre `CLOSED` devuelve el mismo detalle; borrador/cancelada producen 409 |

El cierre comparte bloqueo de campaña con la postulación, impide nuevas postulaciones y **no** selecciona, rechaza ni cancela las existentes. No hay reapertura, edición genérica de metadatos, cancelación en cascada ni eliminación de campañas publicadas. Las condiciones publicadas siguen congeladas y la política existente de no volver a postular tras cancelar se conserva.

## Reintentos seguros

Las operaciones `POST /api/v1/campaigns` y `POST /api/v1/campaigns/{id}/applications` aceptan la cabecera opcional `Idempotency-Key`. Se recomienda un UUID nuevo para cada intención del usuario; repetir exactamente la misma intención con la misma clave ante una respuesta perdida.

- Misma cuenta, operación, clave y contenido: devuelve la respuesta original (201, Location y cuerpo), sin crear otro recurso; también serializa solicitudes simultáneas.
- Misma clave con contenido distinto: 409 `IDEMPOTENCY_KEY_REUSED`.
- La clave está aislada por cuenta y operación; para postulaciones, también por campaña. El orden de confirmaciones no altera su huella.
- Persistencia de cambios y resultado en una sola transacción: los fallos no consumen la clave. La respuesta guardada es una instantánea del resultado original, no una nueva consulta del recurso.
- Protección de 24 horas; al vencer puede reutilizarse la clave. El vencimiento no elimina la restricción de postulación duplicada. Las claves aceptan 8–128 caracteres alfanuméricos o `._:-`; cabecera inválida devuelve 400. Clientes sin cabecera mantienen el comportamiento previo.

## Vinculación OAuth y retorno a Android

La configuración de Instagram/TikTok sigue requiriendo credenciales reales y callback HTTPS registrado en el proveedor (`INSTAGRAM_CLIENT_ID`, `INSTAGRAM_CLIENT_SECRET`, `INSTAGRAM_REDIRECT_URI`; `TIKTOK_CLIENT_KEY`, `TIKTOK_CLIENT_SECRET`, `TIKTOK_REDIRECT_URI`). Sin configuración, iniciar una autorización devuelve 503 `PROVIDER_NOT_CONFIGURED`, no una vinculación simulada.

1. `POST /api/v1/social-accounts/{platform}/authorizations?client=ANDROID`, con JWT de creador, devuelve `authorizationUrl` y `authorizationId`. Sin `client` se conserva el canal `API` y su callback JSON.
2. El cliente abre la URL del proveedor; este responde al callback HTTPS del backend. El estado aleatorio expira a los 10 minutos y es de un solo uso incluso con rechazo, duplicado o fallo.
3. Un callback Android reconocido termina con 303 hacia `collabpro://social-authorization-completed?authorizationId=<UUID>`, tanto en éxito como en fallo reconocido. No contiene códigos, estado, JWT ni tokens del proveedor. No se acepta una URL de retorno proporcionada por el usuario; un estado inválido/vencido/reutilizado no redirige.
4. `GET /api/v1/social-accounts/authorizations/{authorizationId}`, con JWT del creador propietario, devuelve `authorizationId`, `platform`, `status`, `errorCode` y `expiresAt`. Estados: `PENDING`, `SUCCEEDED`, `FAILED`, `EXPIRED`. Otro propietario obtiene 404; empresa obtiene 403.
5. El cliente consulta el resultado y recarga `/api/v1/social-accounts/me` para mostrar vínculos confirmados. Regresar del navegador no equivale por sí solo a vinculación exitosa.

Los errores persistidos se limitan a códigos internos, como `AUTHORIZATION_DENIED`, `SOCIAL_ACCOUNT_ALREADY_LINKED` o `PROVIDER_FAILED`. Éxito y vínculo se confirman en la misma transacción. Repetir el callback no cambia un resultado terminal ni permite reutilizar el estado.

## Alcance y verificación

Se mantienen las capacidades de US-09, US-10, US-11, US-13, US-14, US-15, US-16, US-17, US-18 y US-19 dentro de las primeras 18 posiciones ordenadas. No se adelantan US-12 (perfil empresarial completo), US-20 (evaluación de postulantes) ni colaboraciones, pagos, métricas o APIs generales de historias posteriores. La aplicación Android no se integra en esta intervención.

Las pruebas cubren contratos REST con filtros reales, JWT inválidos/vencidos/revocados, permisos, recuperación, resultados OAuth, publicación/condiciones, disponibilidad, descarte/cierre y concurrencia/reintentos. La prueba opcional de correo real requiere Mailpit y `COLLABPRO_TEST_MAILPIT=true`. Las pruebas OAuth utilizan un proveedor de prueba; no certifican aprobación ni credenciales de Instagram/TikTok en producción.

Verificación local del 6 de octubre de 2026: `mvnw.cmd package` terminó correctamente, con 143 pruebas aprobadas y 1 omitida (correo real/Mailpit), sin fallos ni errores. Incluye actualización de una base V5 a V7 y validación de límites DDD. Se generó `target/platform-0.0.1-SNAPSHOT.jar`. No se verificó contra MySQL real porque Docker estaba detenido; el esquema y los flujos se comprobaron con H2 en modo MySQL.
