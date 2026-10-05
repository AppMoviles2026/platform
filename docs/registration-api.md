# US-09 y US-10 — Registro de empresa y creador

Implementadas en Identity & Profile Management. Fuente: escenarios 2.4.1 y modelo táctico 2.6.1 de `E:/report/README.md`. Las posiciones 1 a 8 pertenecen a landing: se retiró toda la implementación de US-01/US-02 y sus endpoints.

## Contratos para el frontend

### US-09: empresa

`POST /api/v1/auth/brands`, Content-Type: application/json.

```json
{
  "businessName": "Mi Empresa",
  "email": "empresa@example.com",
  "password": "UnaClaveSegura123!"
}
```

### US-10: creador

`POST /api/v1/auth/creators`, Content-Type: application/json.

```json
{
  "displayName": "Mi Nombre",
  "email": "creador@example.com",
  "password": "UnaClaveSegura123!"
}
```

El nombre y correo se recortan; el correo se normaliza en minúsculas. La contraseña no se recorta. Nombre obligatorio de hasta 150 caracteres, correo válido de hasta 254 y contraseña de 8 a 128 caracteres. Esta política de contraseña es una decisión técnica propuesta, no una regla específica del curso/reporte. La app actual acepta 6 y debe alinearse al integrar. Campos desconocidos como accountType/status se rechazan: el tipo lo fija el endpoint, nunca el cliente.

## Respuesta

201 Created. Ejemplo empresarial (UUID ilustrativos):

```json
{
  "accountId": "53fe8cfb-6841-44ea-85e3-b788ac01d421",
  "profileId": "242fdd89-cddf-4773-8f7a-f7c5caa27c1f",
  "name": "Mi Empresa",
  "accountType": "BRAND",
  "status": "ACTIVE"
}
```

Para creador, accountType es CREATOR. Los IDs son UUID generados por el servidor. El frontend usa name para la confirmación, los IDs como referencias futuras y accountType para identificar el resultado. **No hay accessToken ni sesión**: ACTIVE indica estado de la cuenta, no autenticación. Hasta implementar US-11, el cliente puede confirmar el registro pero no simular un login automático ni acceso protegido a Home.

Account y su perfil inicial se guardan en una sola transacción. El resto del perfil queda vacío para completarse en historias posteriores; no se crean campañas, suscripciones ni vínculos sociales.

## Errores

400 para campos inválidos/cuerpo incorrecto; 409 para correo existente, incluso si ya lo registró el otro tipo de usuario. No se devuelve contraseña, hash ni causa SQL.

```json
{
  "code": "EMAIL_ALREADY_REGISTERED",
  "message": "El correo ya está registrado.",
  "fieldErrors": {
    "email": "Utiliza otro correo o inicia sesión cuando esa funcionalidad esté disponible."
  }
}
```

Otros códigos: VALIDATION_ERROR (con fieldErrors) e INVALID_REQUEST. El frontend debe asociar fieldErrors al input correspondiente y conservar el formulario ante errores.

## DDD y Clean Architecture

- Domain: Account.registerBrand/registerCreator crea el agregado con exactamente un perfil del tipo correcto y un evento interno AccountRegistered. EmailAddress, RegistrationPassword e IDs son value objects; AccountRepository es un puerto de dominio. RegistrationPassword es transitorio, nunca parte del estado persistido de Account.
- Application: RegisterBrandCommand/RegisterCreatorCommand y sus handlers, PasswordHasher, Clock y AccountView. No Spring, JPA ni HTTP.
- Infrastructure: entidades JPA separadas, mapper, AccountPersistenceAdapter transaccional, Pbkdf2PasswordHasher y composición Spring. Se aplica unicidad global del correo en DB además del precheck; un conflicto concurrente se traduce a DuplicateEmailException. save también permite actualización versionada del agregado para US-11/13/14.
- Interfaces: AuthController, request/response resources, assembler y errores HTTP reutilizando ApiError de Shared. No se serializan agregados ni entidades JPA.

Las contraseñas se protegen con PBKDF2-HMAC-SHA256, 600000 iteraciones y salt aleatorio de 16 bytes, mediante Spring Security Crypto. No se utiliza SHA256 simple ni cifrado reversible. Referencia de parámetros: [OWASP Password Storage](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html). Migraciones y validación de esquema: [Spring Boot — Database Initialization](https://docs.spring.io/spring-boot/how-to/data-initialization.html).

## Docker y ejecución

```powershell
docker compose up -d --wait
$env:MAVEN_USER_HOME = Join-Path ([System.IO.Path]::GetTempPath()) 'collabpro-scaffold-maven-cache'
.\mvnw.cmd spring-boot:run
```

MySQL 8.4 en localhost:3307, base collabpro y volumen persistente; credenciales visibles de desarrollo, no para producción. Local es el perfil por defecto. Flyway aplica V1 (registro) y V2 (acceso/perfil/social) sobre Identity y JPA valida sin crear/borrar tablas automáticamente. El registro usa identity_account, identity_brand_profile e identity_creator_profile con FK locales y UNIQUE por correo/perfil; no hay relaciones cruzadas entre contextos.

La app móvil sigue sin cambios. RegisterScreen(brand=true) debe enviar businessName; brand=false envía displayName. Integrar mediante un ViewModel/cliente HTTP, con estados loading/success/error y sin el botón de simulación de duplicados. En el emulador, host del backend: 10.0.2.2:8080. Login y recuperación US-11 ya tienen backend: ver [contratos de Identity](identity-access-profile-social-api.md).

## Verificación

`mvnw.cmd -B clean test` utiliza H2 solo para pruebas; `scripts/Test-MySqlRegistration.ps1` ejecuta la misma suite contra collabpro_test en Docker, separada de la base local. Los tests generan datos aleatorios y no eliminan datos existentes. Se verifican registros válidos de ambos tipos, duplicados en cada rol y entre roles, normalización, validación, inyección de rol rechazada, redacción de secretos, hashing con salt, evento de registro, carrera de unicidad, rollback por fallo de perfil, límites de capas y eliminación de rutas informativas. También se prueban ambos POST por HTTP real.

El registro se verificó inicialmente con 28 pruebas; la suite ampliada de Identity ahora pasa 52 contra MySQL Docker, incluida entrega SMTP a Mailpit. La app/landing no se modificaron. US-11 añade protección de sesiones; verificación de email, limitación de peticiones y despliegue productivo siguen pendientes antes de publicar el servicio. OAuth real necesita credenciales/permisos y validación con cada proveedor.
