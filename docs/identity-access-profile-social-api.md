# Identity: US-11, US-13 y US-14

Implementación backend del 05/10/2026, según escenarios de `E:/report/README.md` y pantallas existentes de `E:/CollabPro`. No se modificó la app móvil ni se añadieron bounded contexts. Account sigue siendo el agregado raíz; CreatorProfile y SocialMediaAccount pertenecen a su límite. Los datos sociales no son reportes de Performance.

## Arquitectura y persistencia

Domain contiene invariantes y transiciones de contraseña, perfil y asociación social. Application utiliza commands, queries, proyecciones y puertos; IdentityApplicationService implementa los servicios CQRS existentes, reutilizando los handlers de registro. Infrastructure aporta transacciones, JDBC/JPA, autenticación, hashing, cifrado, SMTP y el adapter anticorrupción OAuth. Interfaces valida DTOs y transforma respuestas. Shared solo proporciona contratos técnicos como CurrentActor.

V2 agrega sesiones, tokens de recuperación, outbox de correo, estados OAuth, asociaciones y credenciales cifradas. V1 no se altera. JPA continúa validando el esquema; las actualizaciones del agregado usan optimistic locking y el vínculo tiene UNIQUE(profile, platform, externalAccountId). Una actualización concurrente devuelve 409; volver a consultar antes de reintentar. Ningún ID/rol recibido del cliente decide la propiedad: se obtiene del Bearer o del state OAuth guardado por el servidor.

## US-11: iniciar sesión y recuperar acceso

Prefijo `/api/v1`. JSON, UUID string, fechas ISO-8601 UTC.

| Método y ruta | Entrada | Resultado |
|---|---|---|
| POST /auth/sessions | email, password | 200: account, accessToken, tokenType="Bearer", expiresAt |
| GET /accounts/me | Authorization: Bearer TOKEN | 200: accountId, profileId, name, accountType, status |
| POST /auth/recovery-requests | email | 202: mensaje genérico igual para correo existente o desconocido |
| POST /auth/password-resets | token, newPassword | 204; invalida recuperación y sesiones anteriores |

Ejemplo de login:

```json
{"email":"creador@example.com","password":"MiClaveSegura123!"}
```

La respuesta accountType (BRAND/CREATOR) dirige la pantalla Home. No usar el selector local de rol como autoridad. Credenciales incorrectas, correo inexistente y cuenta no activa devuelven 401 INVALID_CREDENTIALS. Una sesión ausente, falsa, expirada o de cuenta suspendida devuelve 401 UNAUTHORIZED en rutas protegidas.

La sesión utiliza 32 bytes aleatorios y vence en una hora. Solo su SHA-256 se guarda, no el token original; no es JWT ni una cookie de sesión. No hay refresh de sesión ni endpoint de logout en este alcance. El cliente debe almacenarlo de manera segura, enviarlo únicamente en Authorization, olvidarlo al salir y volver a login tras 401. El registro no inicia sesión automáticamente.

Recuperación: token aleatorio, hash persistido, expiración de 30 minutos y uso único. Nueva solicitud reemplaza la anterior. El correo se encola transaccionalmente y se entrega después; fallos SMTP tienen hasta cinco intentos cada cinco segundos y no revelan existencia de la cuenta en la respuesta. El token temporal del outbox se cifra con AES-GCM y se elimina al entregar o invalidar la solicitud. Confirmar cambio revoca todas las sesiones previas; la nueva contraseña debe tener 8–128 caracteres, sin recortarla.

Mailpit recibe SMTP local y permite inspeccionar mensajes en http://localhost:8025. No entrega correo a Internet. `RECOVERY_RESET_URL` define el enlace (predeterminado `collabpro://password-reset`); la app necesita añadir app-link/deep-link y pantalla que envíe token/newPassword. No se incluyen tokens en la respuesta pública de recuperación.

## US-13: perfil propio del creador

GET y PUT `/profiles/me/creator`, ambos con Bearer. PUT reemplaza los campos editables del perfil; los opcionales omitidos quedan null. No crea un segundo perfil: actualiza el inicial creado por US-10.

```json
{
  "displayName":"Ana Creadora",
  "biography":"Contenido gastronómico local",
  "niche":"Gastronomía",
  "audienceDescription":"Jóvenes adultos de Lima",
  "location":"Lima"
}
```

Respuesta: profileId y esos cinco campos. Nombre obligatorio de 1–150 caracteres; niche/location hasta 150; biography/audienceDescription hasta 2000. Texto se recorta en extremos. La descripción de audiencia es declarada por el creador, no una métrica verificada. Marca no puede consultar/editar perfil creador: 403 CREATOR_REQUIRED. Se mantienen las redes asociadas al actualizar. No incluye edición del perfil empresarial US-12.

## US-14: autorización y vínculo social

Proveedores compatibles: `instagram` y `tiktok`.

1. POST `/social-accounts/{platform}/authorizations` con Bearer del creador devuelve `{"authorizationUrl":"https://..."}`. State aleatorio pertenece a ese creador y proveedor, vence en diez minutos y es de un solo uso.
2. Abrir authorizationUrl en navegador. El proveedor redirige al callback HTTPS del backend registrado en su consola.
3. GET `/social-accounts/{platform}/callback?state=...&code=...` verifica state, intercambia código por credenciales y consulta la identidad al proveedor. Solo entonces persiste el vínculo y devuelve id/platform/username/status. No acepta identidades sociales declaradas por el cliente.
4. GET `/social-accounts/me` con Bearer lista cuentas del creador; lista vacía es 200 `[]`. Refrescar esta lista al regresar a la app.

Permisos rechazados (`error=access_denied` o scope insuficiente): 403 AUTHORIZATION_DENIED y sin asociación. State inválido/vencido/reutilizado: 400 INVALID_OAUTH_STATE; un callback válido rechazado también consume state. Vínculo duplicado en el mismo perfil/plataforma: 409 SOCIAL_ACCOUNT_ALREADY_LINKED. Fallo de proveedor: 502 PROVIDER_FAILED. Sin configuración: 503 PROVIDER_NOT_CONFIGURED, sin simulación de éxito.

Credenciales externas se cifran con AES-256-GCM, quedan en Infrastructure y nunca aparecen en respuestas ni en el dominio. TikTok obtiene open_id y display_name con user.info.basic: el campo de respuesta username representa aquí el nombre público, no garantiza un @handle. Instagram usa instagram_business_basic y requiere una cuenta profesional compatible (Business/Creator), no una cuenta personal arbitraria.

Variables, sin subir secretos a Git:

| Instagram | TikTok |
|---|---|
| INSTAGRAM_CLIENT_ID | TIKTOK_CLIENT_KEY |
| INSTAGRAM_CLIENT_SECRET | TIKTOK_CLIENT_SECRET |
| INSTAGRAM_REDIRECT_URI | TIKTOK_REDIRECT_URI |

Los redirect URI deben ser HTTPS estáticos del backend, coincidir con la consola y terminar en `/api/v1/social-accounts/instagram/callback` o `/api/v1/social-accounts/tiktok/callback`. Se implementó flujo web servidor mediante navegador, no Login Kit SDK nativo; integrar SDK móvil requeriría revisar PKCE y sus contratos. El callback actual devuelve JSON, no redirige automáticamente a la app. El app-link de regreso debe añadirse/configurarse al integrar el frontend.

No se ha autorizado una cuenta real con esos proveedores porque faltan credenciales/permisos. Las pruebas verifican fixtures HTTP y escenarios de aplicación con stub declarado, no aceptación del proveedor. No se implementan métricas, sincronización, revocación, desvinculación o refresh automático de tokens; requieren su propio flujo posterior. La expiración se guarda para no confundir vínculo histórico con credenciales perpetuamente válidas.

## Configuración y comprobación

`docker compose up -d --wait`: MySQL en localhost:3307, Mailpit SMTP localhost:1025 y UI localhost:8025. SMTP_HOST/SMTP_PORT/SMTP_USERNAME/SMTP_PASSWORD/SMTP_AUTH/SMTP_STARTTLS y RECOVERY_FROM/RECOVERY_RESET_URL son configurables. IDENTITY_ENCRYPTION_KEY debe ser Base64 de 32 bytes; la clave fija de application-local es exclusivamente desarrollo. Conservarla con el volumen o migrar/re-cifrar secretos si se rota; una clave distinta no puede descifrar los anteriores.

`mvnw.cmd test`: H2 aislado y prueba SMTP omitida. `scripts/Test-MySqlRegistration.ps1`: toda la suite contra collabpro_test en Docker y SMTP real hacia Mailpit; conserva datos y no escribe en collabpro. Incluye contratos HTTP con filtros, aislamiento de perfiles, cambio de contraseña/invalidación, estados OAuth, duplicados y conservación de asociaciones. SocialOAuthAdapterTests verifica intercambio multipart/form, scope e identidad con fixtures; SecretCipherTests comprueba IV aleatorio y detección de alteraciones. ArchitectureBoundariesTests comprueba las cuatro capas y ausencia de dependencias externas en Domain/Application.

Resultado MySQL del 05/10/2026: 52 pruebas, cero fallos/errores/omisiones. Se incluye carrera login/reset para evitar que una contraseña anterior deje una sesión utilizable tras el cambio, y sustitución de tokens de recuperación anteriores. Las pruebas usan base aislada, mensajes únicos y no eliminan datos existentes.

Antes de exponer públicamente: HTTPS, SMTP real, secretos gestionados externamente, rate limiting para login/recuperación/OAuth, política de limpieza de filas expiradas y monitoreo de entregas fallidas. Estas medidas de despliegue no se sustituyen por Mailpit local. No habilitar logging de bodies, Authorization o query strings de callbacks/reset.

## Referencias técnicas

- [OWASP: recuperación de contraseña](https://cheatsheetseries.owasp.org/cheatsheets/Forgot_Password_Cheat_Sheet.html).
- [Spring Security: gestión de sesiones](https://docs.spring.io/spring-security/reference/7.0/servlet/authentication/session-management.html).
- [TikTok: Login Kit Web](https://developers.tiktok.com/doc/login-kit-web/).
- [TikTok: gestión de tokens](https://developers.tiktok.com/doc/oauth-user-access-token-management/).
- [Meta: Instagram API, documentación oficial en Postman](https://www.postman.com/meta/instagram/documentation/6yqw8pt/instagram-api).
- [Mailpit: API v1](https://mailpit.axllent.org/docs/api-v1/).
