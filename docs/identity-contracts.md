# Identity: contratos del bloque 01

## Perfil empresarial — US-12

Las rutas requieren una sesión JWT de una cuenta empresarial activa. El perfil se determina desde la sesión, no desde un identificador enviado por el cliente.

### Consultar

`GET /api/v1/profiles/me/brand`

Ejemplo de respuesta:

```json
{
  "profileId": "10000000-0000-4000-8000-000000000002",
  "businessName": "Ayni Café",
  "description": "Café peruano de especialidad",
  "category": "Food & Beverage",
  "location": "Lima, Perú",
  "version": 0
}
```

El registro empresarial ya crea el perfil: esta consulta puede devolver los campos opcionales en `null`. No hay que crear un segundo perfil para completar información.

### Guardar o actualizar

`PUT /api/v1/profiles/me/brand`

```json
{
  "businessName": "Ayni Café",
  "description": "Café peruano de especialidad",
  "category": "Food & Beverage",
  "location": "Lima, Perú",
  "expectedVersion": 0
}
```

- `businessName`: obligatorio, de 1 a 150 caracteres después de normalizar espacios externos.
- `description`: opcional, hasta 2000 caracteres.
- `category` y `location`: opcionales, hasta 150 caracteres.
- Los campos opcionales omitidos, nulos o vacíos se guardan como `null`: PUT reemplaza la información editable completa.
- `expectedVersion`: obligatorio y no negativo; usar el `version` recibido en la última consulta o guardado.
- La respuesta 200 devuelve el perfil persistido y su versión vigente. Un guardado sin cambios puede mantener la misma versión.
- Un conflicto devuelve 409 `CONCURRENT_UPDATE`; volver a consultar y resolver el conflicto antes de reenviar. No sobrescribir automáticamente.
- Campos desconocidos o protegidos, como `accountId`, `profileId`, `email`, `password`, `status` o `accountType`, devuelven 400.

El nombre actualizado también aparece en `GET /api/v1/accounts/me`. El identificador de perfil, credenciales, tipo y estado de cuenta permanecen fuera de esta operación. Las campañas ya creadas conservan el nombre histórico que su modelo actual almacena; la política de presentación actual/histórica de campañas corresponde al bloque 02.

| HTTP | Código / significado |
| --- | --- |
| 400 | `VALIDATION_ERROR` o `INVALID_REQUEST`: campos o versión inválidos. |
| 401 | Sesión ausente, inválida, vencida o cuenta que dejó de estar activa. |
| 403 | `BRAND_REQUIRED`: la sesión pertenece a un creador. |
| 409 | `CONCURRENT_UPDATE`: versión antigua o conflicto concurrente. |

Las rutas existentes de registro, autenticación, recuperación y perfil creador mantienen sus payloads. Para Android, las rutas de perfil empresarial son nuevas; su consumo no se implementa en este bloque.

## Consultas internas entre contextos

`IdentityProfileFacade` conserva `byAccount(accountId)` y añade:

- `brandById(brandProfileId)`: información empresarial descriptiva y estado de actividad.
- `creatorById(creatorProfileId)`: información del creador y referencias públicas de asociaciones sociales activas.

Un ID de cuenta no se interpreta como un ID de perfil. Las consultas por tipo no devuelven perfiles del tipo contrario. No incluyen correo, contraseña, tokens de sesión ni credenciales del proveedor.

Son contratos internos para adaptadores de otros bounded contexts. Antes de usarlos, el contexto consumidor debe autorizar su propio caso de uso, por ejemplo comprobar que la empresa puede evaluar las postulaciones de esa campaña. No se publican endpoints de consulta arbitraria de perfiles.

## Estado de autorización social

`GET /api/v1/social-accounts/{socialId}/access`

Disponible para el creador propietario de la asociación. Devuelve `socialId`, `platform`, `status`, `grantedScopes`, `missingScopes`, `expiresAt`, `renewable` y `reauthorizationRequired`, con `Cache-Control: no-store`. No contacta al proveedor ni devuelve tokens.

| Estado | Interpretación |
| --- | --- |
| `READY` | Las credenciales locales están vigentes y tienen los permisos requeridos. No demuestra que el proveedor no haya revocado el acceso desde la última verificación. |
| `EXPIRED` | El access token venció, pero existe un mecanismo de renovación utilizable. |
| `REAUTHORIZATION_REQUIRED` | Credenciales rechazadas, renovación no disponible o refresh token vencido. |
| `INSUFFICIENT_SCOPE` | Faltan permisos para la operación solicitada por un consumidor interno. |
| `MISSING_CREDENTIALS` | Existe la referencia social, pero no hay credenciales almacenadas. |
| `REVOKED` | La asociación local está revocada. |
| `PROVIDER_NOT_CONFIGURED` | Faltan credenciales de la aplicación externa. |

La consulta REST verifica la asociación y muestra los permisos registrados. Cada consumidor interno especifica los permisos concretos de su operación; no los obtiene como una concesión arbitraria enviada desde la app.

Una asociación ajena o inexistente devuelve 404 `SOCIAL_ACCOUNT_NOT_FOUND`; una sesión empresarial devuelve 403 `CREATOR_REQUIRED`.

### Uso desde otro contexto

`SocialAuthorizationFacade.status(creatorProfileId, socialId, requiredScopes)` consulta metadatos. `withAuthorizedAccess(...)` asegura disponibilidad y ejecuta una operación del adaptador de infraestructura con acceso temporal al token. El dominio de Performance y los recursos REST no reciben credenciales.

El consumidor debe autorizar previamente la colaboración y nunca registrar, serializar ni persistir `TokenAccess`. Su representación de texto está redactada. El contrato usa IDs de perfil y asociación; no acepta URLs de destino arbitrarias para enviar tokens.

### Renovación y reautorización

- TikTok: renovación por servidor mediante su endpoint de token. Se comprueban identidad externa, permisos y expiraciones; se conservan cifrados el nuevo access token y el refresh token rotado.
- En la renovación de TikTok, los tiempos se calculan desde los valores devueltos por el proveedor. El intercambio inicial conserva el límite local de una hora del adaptador existente cuando una respuesta no informa `expires_in`.
- Las renovaciones concurrentes de un mismo creador se serializan en base de datos y vuelven a leer las credenciales después de obtener el bloqueo.
- Un rechazo de la autorización marca la necesidad de nuevo consentimiento. El marcador persiste aunque el caso de uso consumidor falle.
- Un error temporal o de configuración no borra credenciales ni se interpreta automáticamente como una revocación del usuario.
- Instagram: el flujo actual obtiene una credencial de corta duración. No se implementa renovación de tokens de larga duración que el sistema todavía no obtiene; al vencer se informa la necesidad de volver a autorizar.
- La nueva autorización de la misma identidad externa reemplaza credenciales vencidas, revocadas o que necesitan nuevo consentimiento. También admite permisos nuevos explícitamente autorizados. Conserva el ID social y reactiva la asociación.
- Repetir la autorización de una asociación vigente con los mismos permisos conserva la respuesta de duplicado de US-14. No se crea una segunda asociación.
- `TIKTOK_SCOPES` e `INSTAGRAM_SCOPES` permiten configurar permisos solicitados por el servidor; sus valores predeterminados siguen siendo básicos. Solicitar un permiso no lo convierte en concedido: el backend conserva los permisos devueltos por el proveedor. Si Instagram no los enumera, solo se acredita el acceso básico verificado mediante la consulta de perfil.
- Se conserva el retorno Android con identificador opaco de intento. El callback consume `state` una sola vez, incluso si termina en error.

## SS-02: evidencia y límites

| Proveedor | Base del contrato | Verificación realizada | Pendiente externo |
| --- | --- | --- | --- |
| TikTok | `user.info.basic`; identidad `open_id` y nombre; permisos concedidos y vencimientos devueltos por el proveedor. | Fixtures HTTP: intercambio, identidad, permisos, cifrado, renovación, rotación, rechazos, reintentos y concurrencia con base de datos. | Consentimiento y renovación con una aplicación/usuario reales configurados. |
| Instagram | `instagram_business_basic`; identidad y nombre verificados por el endpoint de perfil. | Fixtures HTTP del intercambio y normalización de permisos; lectura de credenciales antiguas y necesidad de reautorizar al vencer. | Aplicación y cuenta compatibles reales; permisos adicionales para métricas y eventual flujo de larga duración. |

Las métricas específicas y sus permisos se decidirán en el bloque de Performance. Una vinculación con permiso básico no garantiza acceso a cualquier estadística. Los tests con respuestas controladas no constituyen una autorización efectuada contra los proveedores reales.

Referencias primarias consultadas:

- TikTok, ciclo de credenciales: <https://developers.tiktok.com/docs/en/oauth-user-access-token-management>
- TikTok, protocolo de renovación: <https://developers.tiktok.com/docs/en/oauth-refresh-a-user-access-token>
- Meta, entrada de documentación del flujo existente: <https://developers.facebook.com/docs/instagram-platform/instagram-api-with-instagram-login/business-login/>. Su consulta automatizada devolvió 429 durante esta revisión; no se añadió un flujo nuevo de larga duración sin validarlo.

## Migración y verificación

V8 añade la versión del perfil empresarial y los metadatos del ciclo de credenciales. Las migraciones V1–V7 no se modifican. Las credenciales antiguas conservan sus valores cifrados; la ausencia de una fecha histórica de refresh se trata como desconocida, no como una nueva concesión del proveedor.

Para pruebas habituales con JDK 21 o superior:

```powershell
.\mvnw.cmd test
```

Para la misma suite contra MySQL y correo real en un Mailpit aislado:

```powershell
.\scripts\test-mysql.ps1
```

`compose.test.yaml` usa un proyecto independiente, MySQL en 3317 y Mailpit en 1026/8026. MySQL utiliza almacenamiento temporal del contenedor y la base `collabpro_test`; no usa el volumen ni la base de la aplicación. El script restaura las variables del proceso al terminar. Para detener exclusivamente estos servicios:

```powershell
docker compose -f compose.test.yaml stop
```

Las pruebas de migración con bases H2 dedicadas siguen ejecutándose dentro de la suite MySQL y se identifican por separado. La suite verifica el esquema y los recorridos con MySQL; la preservación de V5/V7 se prueba además en esas bases H2 de actualización.

### Resultados registrados el 8 de noviembre de 2026

- Suite completa con MySQL/Mailpit: 184 pruebas aprobadas, sin fallos, errores ni omisiones.
- Después de añadir el escenario de lectura de credenciales desde una transacción con snapshot anterior, verificación dirigida final con MySQL: 35 pruebas aprobadas de `BrandProfileApiTests`, `SocialCredentialLifecycleTests` y `OpenApiDocumentationTests`.
- Total de casos distintos registrados: 185, todos aprobados en sus últimas ejecuciones. También se verificaron con H2 los cambios de registro, ciclo social, OpenAPI y límites de arquitectura.
- El envío SMTP se ejecutó contra el Mailpit de pruebas. Los proveedores sociales se probaron con respuestas HTTP controladas, no con cuentas externas reales.
- Se detuvieron los servicios aislados al terminar. Los resultados de ejecución quedan en `target/surefire-reports`; son archivos generados, no documentación fuente.
