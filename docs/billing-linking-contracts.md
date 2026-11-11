# Billing · bloque 06 · PayPal Sandbox

Alcance: US-26 y preparación de TS-06. Se vincula una autorización financiera; no se crean órdenes, suscripciones, cargos, transferencias ni reembolsos. El bloque 07 implementa suscripciones en [subscription-contracts.md](subscription-contracts.md); el 08 sigue pendiente. El cambio de Stripe a PayPal fue indicado por el usuario; el título original de SS-01 en el reporte todavía menciona Stripe y requiere actualizarse cuando se documente ese sprint.

## Configuración y ejecución

El archivo local está en la raíz del backend: `C:\Users\fabio\Documents\platform\.env`. Está excluido del control de versiones y del contexto Docker. Existe una plantilla publicable en `.env.example`.

```properties
PAYPAL_ENABLED=true
PAYPAL_CLIENT_ID=
PAYPAL_CLIENT_SECRET=
PAYPAL_WEBHOOK_ID=
PAYPAL_RETURN_URL=http://localhost:8081/api/v1/billing/paypal/callback
PAYPAL_IDENTITY_RETURN_URL=http://localhost:8081/api/v1/billing/paypal/identity-callback
```

El perfil local importa el archivo opcionalmente; Compose recibe sus variables al iniciar el contenedor. Después de guardar cambios ejecutar `docker compose up -d --build`. Fuera del perfil local configurar las mismas variables en el servidor. `BILLING_ENCRYPTION_KEY` debe ser una clave Base64 estable de 32 bytes; por compatibilidad puede utilizar la clave de cifrado de Identity si no se configura una propia. Cambiar una clave con referencias ya guardadas requiere una migración de cifrado.

La API de PayPal está fijada a `https://api-m.sandbox.paypal.com`. No existe selector de modo Live ni una URL de proveedor enviada por el cliente. Por defecto la integración está apagada. Las pruebas automatizadas no leen el `.env` local ni usan sus claves.

En PayPal Developer, abrir **Apps & Credentials → Sandbox → la aplicación Merchant**:

1. Para empresas, habilitar la capacidad de guardar PayPal mediante Payment Method Tokens/Vault. Disponer de credenciales no garantiza el permiso: un rechazo de permisos se informa como `PROVIDER_PERMISSION_REQUIRED`.
2. Para creadores, habilitar **Log in with PayPal**, registrar exactamente `PAYPAL_IDENTITY_RETURN_URL` en los Return URLs y seleccionar correo electrónico, estado de cuenta verificada y PayPal account ID. No se cambia el login JWT de CollabPro: este consentimiento verifica exclusivamente al destinatario financiero.
3. Crear cuentas de prueba de comprador y receptor desde **Testing Tools → Sandbox Accounts**. El consentimiento se completa con las cuentas Sandbox; el consentimiento de una empresa y la identidad de un creador son procesos diferentes.
4. Para webhooks se necesita una dirección HTTPS pública accesible a PayPal. Registrar `https://<host>/api/v1/webhooks/payments`, suscribir los tres eventos `VAULT.PAYMENT-TOKEN.CREATED`, `VAULT.PAYMENT-TOKEN.DELETED` y `VAULT.PAYMENT-TOKEN.DELETION-INITIATED`, copiar el Webhook ID a `PAYPAL_WEBHOOK_ID` y reiniciar el backend. Con ID vacío los eventos fallan de forma explícita; no se acepta una firma simulada.
5. Para usar otro host, actualizar los retornos configurados y el Return URL de Identity en PayPal. En Android, el navegador debe alcanzar el backend: `localhost` en el teléfono no apunta a la PC. Usar un host accesible durante la demo.

## Contratos bajo /api/v1

`{id}` es el **accountId** de `GET /accounts/me`, nunca el profileId. Todas las rutas privadas requieren JWT vigente y cuenta activa. El rol lo obtiene el servidor.

| Ruta | Comportamiento |
| --- | --- |
| POST `/user/{id}/billing` | Inicia o recupera el intento con `requestId` y `client: WEB/ANDROID`. Responde 202 con el estado actual; 202 no significa que esté vinculado. |
| GET `/user/{id}/billing?page=0&size=20` | Métodos propios paginados; lista vacía si no existe ninguno. Tamaño 1–100. |
| GET `/billing/authorizations/{id}` | Consulta privada del intento, error, vencimiento y URL de aprobación mientras esté vigente. |
| POST `/billing/authorizations/{id}/cancellation` | Cancela un intento pendiente; repetir no duplica efectos. No deshace una vinculación terminada ni interrumpe una verificación en curso. |
| POST `/user/{id}/billing/validation` | Recibe `methodId`; comprueba propiedad, rol y vigencia sin ejecutar un pago. Sin medio válido devuelve 409 `METHOD_REQUIRED`. |
| GET `/billing/paypal/callback?state=...` | Retorno público de Vault. Un parámetro de aprobación del cliente no basta: se verifica el setup-token con PayPal y luego se obtiene el payment-token. `cancelled=true` cancela el consentimiento pendiente. |
| GET `/billing/paypal/identity-callback?state=...&code=...` | Retorno público de identidad del destinatario; verifica email, email_verified, verified_account y payer_id. Un error de autorización cancela el intento. |
| POST `/webhooks/payments` | Público respecto a JWT, obligatorio respecto a firma PayPal. Cuerpo JSON original y cinco cabeceras PAYPAL de transmisión; máximo 256 KiB. |

Solicitud inicial:

```json
{"requestId":"4bf2c301-c7f3-4acb-8870-374c69ad98e0","client":"WEB"}
```

Guardar el UUID antes de enviar y reutilizarlo si se pierde la respuesta. Cambiar el cliente con la misma clave produce 409 `IDEMPOTENCY_CONFLICT`. Tras un estado terminal fallido, cancelado o vencido, iniciar un intento nuevo con otro UUID.

Respuesta de autorización: `authorizationId, purpose, provider, environment, status, expiresAt, approvalUrl, methodId, errorCode, version`. El frontend debe abrir `approvalUrl` en el navegador, consultar el intento al volver y considerar éxito únicamente `LINKED`. `errorCode` informa fallos definitivos o temporales; no sustituye al estado.

Estados: `PREPARING → PENDING_APPROVAL → COMPLETING → LINKED`; también `FAILED, CANCELLED, EXPIRED`. Duración: 30 minutos. Un fallo temporal al preparar se reintenta con la misma solicitud; mientras otra preparación está trabajando se devuelve su estado. Una preparación abandonada se puede recuperar después de 60 segundos.

Cada método público contiene solo `methodId, purpose, status, provider, environment, maskedLabel, linkedAt, version`. La empresa tiene propósito `PAYMENT` y el creador `PAYOUT`. Un destino verificado no constituye una autorización para cobrar al creador ni garantiza que un futuro Payout sea elegible.

## Retornos, éxitos parciales y concurrencia

El estado aleatorio se guarda cifrado y se busca por hash. No admite una dirección de retorno arbitraria. Los callbacks Web entregan un resumen JSON sin credenciales; Android responde 303 a `collabpro://billing-return?authorizationId=<UUID>&status=<estado>`. El deep link no contiene códigos ni tokens. El frontend Android todavía debe consumir este contrato.

Preparación y confirmación usan claves PayPal estables por autorización. Si se pierde la respuesta de guardado y el setup aparece como VAULTED, se recupera el resultado con la misma clave; no se crea otro método. Las llamadas remotas tienen tiempos límite y ocurren fuera de las transacciones; los cambios propios bloquean la raíz de Billing y usan versiones. Un deadlock de base de datos reintenta hasta tres veces únicamente la transacción propia, nunca una llamada al proveedor. La confirmación en curso tiene una reserva de 120 segundos. Un callback repetido después del éxito devuelve el mismo resultado; los concurrentes observan el estado en curso.

Para Identity, el código de un solo uso se canjea una vez y el acceso se cifra antes de consultar el perfil: un fallo posterior permite repetir la consulta sin volver a canjearlo. Si se pierde la respuesta del canje o el proceso muere antes de persistirla, se requiere nuevo consentimiento; no se supone que el código siga siendo utilizable. Un código distinto para el mismo intento se rechaza.

La cuenta del proveedor solo puede pertenecer a un usuario CollabPro por propósito. Un consentimiento nuevo del mismo propietario actualiza su método; no permite apropiarse del método ajeno. Los identificadores del proveedor, estados de consentimiento y cargas completas de eventos están cifrados; las respuestas y la auditoría no incluyen credenciales, PAN ni CVC.

## Webhooks e integración futura

Se verifican las cabeceras y la firma con el endpoint oficial de PayPal y el Webhook ID configurado, incluso para duplicados. No se descargan certificados desde URLs recibidas. El evento se reserva en un inbox transaccional por su ID antes de aplicar efectos:

- Mismo ID y contenido canónico: 200, `duplicate: true`; no se repiten efectos, incluso con entregas concurrentes.
- Mismo ID con contenido distinto: 409; firma inválida: 401. JSON mal formado, claves duplicadas o cuerpo excesivo: 400.
- Revocación/eliminación de Vault: `PROCESSED`, método revocado y bloqueo de disponibilidad. Se guarda una marca incluso si el callback de alta aún no llegó; una creación tardía no borra esa revocación.
- Creación de Vault: `UNMATCHED`; no vincula por sí sola un método sin verificar consentimiento y propietario.
- Eventos financieros ajenos a la gestión de suscripciones: `DEFERRED`, cifrados y conservados para los futuros manejadores del bloque 08. Los eventos de suscripción se encolan y reconcilian según el bloque 07. No se declaran procesados ni generan beneficios.
- Otros eventos: `IGNORED`, retenidos para trazabilidad.

`PaymentReadinessFacade` publica un requisito financiero sin exponer referencias PayPal. Las operaciones económicas deben exigir este requisito **dentro de su propio flujo transaccional** y verificar nuevamente las condiciones de pago, incidencias y respuesta del proveedor. El bloque 07 exige este requisito para iniciar suscripciones; la compensación completa corresponde al bloque 08.

Un payment-token de Vault no activa una suscripción de PayPal. El bloque 07 necesita el consentimiento específico de Subscriptions; el bloque 08 debe validar Payouts, país, divisa, saldo y permisos por separado. El bloque 06 no cambia las compensaciones existentes en PEN ni inventa una conversión a USD. El seed tampoco fabrica cuentas PayPal vinculadas.

## Evidencia y fuentes

Las pruebas cubren ambos roles con JWT, éxitos/fallos, privacidad, vencimiento, devolución Android, reintentos parciales, exclusividad, renovación de consentimiento, concurrencia, firma, duplicados, revocación anticipada, eventos diferidos y migración V14→V15. Las pruebas del adaptador usan HTTP controlado; no equivalen a consentimiento externo completo.

Comprobación externa reproducible desde la raíz: `.\scripts\test-paypal-sandbox.ps1`. El modificador `-PrepareConsent` prepara un setup-token de diagnóstico sin compra y sin vincularlo a un usuario local; para completar una vinculación usar las rutas anteriores.

Verificación del bloque 06, 10 de noviembre de 2026: **376 casos únicos aprobados con el entorno MySQL 8.4/Mailpit**, sin fallos, errores ni omisiones. Se ejecutó una regresión completa de 372 casos y, tras revisar la recuperación de Vault y respuestas inválidas, se revalidaron las 33 pruebas del adaptador; cuatro son casos adicionales. La regresión H2 final ejecutó **376 casos: 375 aprobados y una omisión de RecoveryMailpitTests**, cubierta satisfactoriamente en MySQL/Mailpit. Incluye 24 pruebas API de Billing, 33 de protocolo/cifrado, nueve de migraciones, arquitectura, Swagger y toda la funcionalidad anterior. Compose y enlaces internos también están comprobados. El código nuevo no fue desplegado en Azure ni conectado todavía desde Android.

Evidencia externa realizada el 10 de noviembre de 2026: autenticación con las claves locales aceptada por PayPal Sandbox (HTTP 200) y creación de setup-token sin compra (HTTP 201, `PAYER_ACTION_REQUIRED`, con customer y enlace de aprobación). No se almacenan claves ni la URL de aprobación en esta documentación. Faltan consentimiento humano de ambos roles, retorno real y webhook firmado entregado a un host público. Este resultado demuestra capacidad Sandbox; no acredita elegibilidad productiva en Perú. La [especificación oficial de Vault](https://developer.paypal.com/api/payment-tokens/v3/schema.json) restringe la disponibilidad del producto y debe reevaluarse si el proyecto deja de ser una demo.

Referencias oficiales: [Vault sin compra](https://developer.paypal.com/api/payment-tokens/save-without-purchase/paypal/), [Payment Method Tokens v3](https://developer.paypal.com/api/payment-tokens/v3/), [configuración de Log in with PayPal](https://developer.paypal.com/log-in/build/), [atributos y scopes](https://developer.paypal.com/log-in/references/), [verificación de webhooks](https://developer.paypal.com/api/webhooks/v1/verify-webhook-signature-post/).
