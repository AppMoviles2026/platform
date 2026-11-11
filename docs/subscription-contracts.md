# Billing · bloque 07 · suscripciones de empresa

Alcance: US-27 y escenarios 2–3 de TS-06, con eventos de suscripción del escenario 5. PayPal **Sandbox exclusivamente**. El bloque 08 (compensaciones/pagos de colaboraciones) continúa pendiente. No se modificó Android ni se desplegó esta versión en Azure.

## Catálogo confirmado para la demo

| Plan | Precio | Periodicidad |
| --- | --- | --- |
| Esencial | USD 5.00 | Mensual |
| Crecimiento | USD 10.00 | Mensual |

Importes confirmados por el usuario; no representan precios comerciales validados. Ambos planes son de demostración, sin prueba gratuita ni cuota inicial. Los beneficios son informativos y se devuelven cuando existe un periodo pagado confirmado. No se añadieron cuotas, bloqueos comerciales ni restricciones nuevas a campañas, colaboraciones o Performance.

El catálogo se origina en el servidor. El cliente selecciona un `planId`; no envía precio, moneda, plan PayPal ni condiciones de cobro. Al listar/iniciar se verifica el plan remoto: activo, precio y moneda iguales al catálogo, mensual, sin trial, impuestos ni cuota inicial. Las definiciones son inmutables: cambiar configuración crea otra versión de catálogo y conserva el plan histórico de las suscripciones anteriores.

## Configuración

El archivo `.env` del backend conserva las claves del bloque 06. Se incorporaron:

```properties
PAYPAL_SUBSCRIPTION_RETURN_URL=http://localhost:8081/api/v1/billing/paypal/subscription-callback
PAYPAL_ESSENTIAL_PLAN_ID=
PAYPAL_GROWTH_PLAN_ID=
BILLING_DEMO_CATALOG_ENABLED=true
BILLING_ESSENTIAL_AMOUNT=5.00
BILLING_GROWTH_AMOUNT=10.00
```

Desde la raíz del backend, ejecutar `.\scripts\initialize-paypal-subscriptions.ps1`. Crea/reutiliza un producto y los dos planes en Sandbox, verifica las tarifas y actualiza únicamente la configuración del catálogo en `.env`. No crea pagos ni suscripciones de clientes. El archivo local `.env.subscription-catalog.json` conserva claves de reintento e identificadores para recuperar ejecuciones incompletas; está excluido del repositorio y de Docker. No borrar ese archivo para repetir una creación incierta. Si se pierde la respuesta de creación de un plan, la repetición automática se limita a 48 horas, dentro de la retención documentada del proveedor.

Los dos planes ya se prepararon con las claves locales. Ejecutar `docker compose up -d --build` para que el contenedor use las nuevas variables y el código. En Azure, configurar las mismas variables y los IDs de los planes de la misma aplicación Sandbox; el seed de catálogo se habilita mediante `BILLING_DEMO_CATALOG_ENABLED` o el seed global de demo. El perfil local lo habilita por defecto. El seed publica planes; **no fabrica suscripciones activas ni pagos**.

El callback remoto debe usar un host accesible desde el navegador del dispositivo. Para webhooks se necesita HTTPS público y el Webhook ID de la aplicación: conservar `PAYPAL_WEBHOOK_ID` y añadir eventos `BILLING.SUBSCRIPTION.*` y `PAYMENT.SALE.COMPLETED`, `PAYMENT.SALE.REFUNDED`, `PAYMENT.SALE.REVERSED` a los eventos Vault del bloque 06.

## Recorrido manual en Sandbox

1. Iniciar sesión como empresa y usar el JWT en Swagger.
2. Completar la [vinculación financiera del bloque 06](billing-linking-contracts.md) con una cuenta compradora Sandbox y consultar su método PAYMENT activo.
3. Consultar `/subscription-plans` y elegir el UUID de un plan disponible.
4. Crear el intento con un UUID propio de reintento, el plan, el método y WEB; abrir su approvalUrl y aprobar usando la misma cuenta Sandbox.
5. Consultar `/user/{id}/subscription` o `/subscriptions/{id}/refresh`. Comprobar ACTIVE, benefitsActive y paidThrough; si el cobro todavía no aparece, conservar PAYMENT_PENDING y consultar nuevamente.
6. Para comprobar cancelación de un contrato activo, usar la ruta privada y consultar hasta que PayPal confirme CANCELLED. La ruta de retorno del navegador no sustituye este comando.

## Rutas bajo /api/v1

Se exige JWT, cuenta activa, rol BRAND y propiedad del recurso, salvo el callback público validado por estado y el webhook firmado ya existente. `{id}` en rutas de usuario es **accountId**, no profileId.

| Método y ruta | Resultado |
| --- | --- |
| GET `/subscription-plans` | Planes con precio, moneda, intervalo, beneficios, demostración, disponibilidad y error de configuración/proveedor. |
| POST `/user/{id}/subscription` | 201 y Location del intento local creado/recuperado. Todavía puede estar pendiente; 201 no acredita un cobro. |
| GET `/user/{id}/subscription` | 200, suscripción actual o última; `status: NONE` si no existe. Reconcilia con PayPal cuando hay referencia disponible. |
| GET `/user/{id}/subscriptions?page=0&size=20` | Historial paginado, tamaño 1–100, sin llamadas remotas por cada fila. |
| GET `/subscriptions/{id}` | Consulta privada con reconciliación. |
| POST `/subscriptions/{id}/refresh` | Reconciliación explícita para seguimiento al volver del navegador. |
| POST `/subscriptions/{id}/cancellation` | Solicita cancelación; solo termina al confirmarla PayPal. Un fallo conserva CANCEL_PENDING y no permite otro contrato paralelo. |
| POST `/subscriptions/{id}/resume-approval` | Reanuda un intento que el proveedor aún mantiene pendiente, con `expectedVersion`; no crea otra suscripción. |
| GET `/billing/paypal/subscription-callback?state=...` | Retorno público: consulta al proveedor, no confía en flags de éxito del navegador. `cancelled=true` informa rechazo/cancelación del consentimiento y nunca cancela por sí solo una suscripción pagada. |

Inicio:

```json
{
  "requestId": "1d788c89-66c1-49ea-9e0a-647010d94300",
  "planId": "<UUID devuelto por subscription-plans>",
  "methodId": "<UUID de un método PAYMENT propio activo>",
  "client": "WEB"
}
```

Persistir el mismo `requestId` para todos los reintentos de la operación. Cambiar el contenido con esa clave produce `IDEMPOTENCY_CONFLICT`. Otra clave no permite evadir una suscripción o creación pendiente: devuelve `SUBSCRIPTION_EXISTS`. La consulta del método financiero exige el flujo real del bloque 06; un UUID inventado, un destino PAYOUT o un medio ajeno/revocado no habilitan la creación.

La respuesta incluye `subscriptionId, planId, planName, amount, currency, status, providerStatus, provider, environment, methodId, benefitsActive, benefits, approvalUrl, consentExpiresAt, paidThrough, lastCheckedAt, errorCode, version`. Las referencias PayPal, claves de idempotencia y estado, identidad del pagador y datos cifrados no se publican. `approvalUrl` solo se expone al propietario mientras la aprobación sigue disponible.

## Confirmación y fallo del cobro

La empresa completa un consentimiento **Subscriptions** propio de PayPal; el token Vault no activa por sí solo una suscripción. Debe aprobar con la misma cuenta PayPal vinculada. El backend compara la identidad verificada con el método seleccionado; un pagador distinto deja la suscripción en revisión, sin beneficios, y el propietario puede solicitar cancelación.

Para activar se exige respuesta PayPal correlacionada por referencia, plan y `custom_id`, estado ACTIVE y una transacción COMPLETED de importe/moneda correctos del periodo, sin reversión, fallo posterior ni saldo de tiempo vencido. La vigencia termina en `paidThrough`; el cliente no puede extenderla. Se consultan todas las páginas de transacciones mediante enlaces del mismo recurso Sandbox; nunca se siguen direcciones arbitrarias.

APPROVAL_PENDING, APPROVED o un retorno del navegador no prueban el cobro. La API conserva PAYMENT_PENDING mientras falta evidencia. Un cobro fallido o revertido produce PAYMENT_FAILED y beneficios vacíos; un pago posterior válido permite recuperar la suscripción. SUSPENDED tampoco otorga beneficios. CANCELLED/EXPIRED no se reactivan por eventos antiguos. Los registros de pagos se deduplican y una devolución/reversión no se deshace por una captura antigua del proveedor.

Una consulta remota fallida conserva el estado conocido y registra `errorCode`. Nunca activa beneficios nuevos; un periodo previamente pagado puede continuar hasta su vencimiento si no existe un fallo/reversión conocido. Una inconsistencia de identidad/respuesta requiere revisión y no otorga beneficios.

## Reintentos, cancelación y retornos

La creación remota conserva también la URL de retorno original del intento: cambiar de host durante un despliegue no altera el cuerpo de su reintento. Usa el UUID local como PayPal-Request-Id y guarda el intento antes de llamar al proveedor. Las llamadas remotas se ejecutan fuera de las transacciones. Raíz bloqueada, versión, slot único por empresa y reservas temporales de trabajo evitan escrituras simultáneas y contratos paralelos. Una creación incierta se recupera con la misma clave durante 48 horas; pasado ese plazo queda RECONCILIATION_REQUIRED, conservando el bloqueo hasta reconciliarla. No se crea otra a ciegas.

PayPal rechazó cancelar el contrato de diagnóstico mientras estaba APPROVAL_PENDING. Por ello, una solicitud privada de cancelación no lo marca ficticiamente cancelado: conserva el intento. Si la empresa desea continuar con ese mismo intento, puede usar `resume-approval` con la versión actual mientras el consentimiento local esté vigente; recupera el enlace original. No se promete cambiar el plan pendiente ni liberar el slot sin confirmación remota.

El consentimiento local vence a los 30 minutos. Las suscripciones ya confirmadas se siguen consultando con JWT, independientemente del retorno del navegador. Android recibe un 303 a `collabpro://subscription-return?subscriptionId=<UUID>&status=<estado>`, más `cancelled=true` si se canceló la aprobación. No incluye tokens, estado secreto ni credenciales. Al volver, consultar el recurso protegido; el deep link no demuestra éxito.

## Webhooks y reconciliación duradera

Se reutilizan la verificación de firma, JSON estricto, límite de cuerpo y deduplicación del bloque 06. Los eventos de suscripción aceptados quedan QUEUED junto con trabajo durable; el backend responde sin afirmar que ya fueron aplicados. Un trabajador procesa la cola y consulta el estado actual de PayPal antes de actualizar la suscripción; solo entonces marca el inbox PROCESSED.

Un fallo conocido puede retirar beneficios de forma inmediata. Para activar o recuperar se requiere confirmación del proveedor. Los eventos anteriores al guardado de la referencia se correlacionan mediante el identificador propio o se conservan sin aplicar hasta que exista la relación. Los eventos financieros del bloque 08 permanecen diferidos.

El trabajador usa un scheduler dedicado para no bloquear correo/recuperación ni otros procesos programados durante llamadas lentas al proveedor. Cada 30 segundos se recuperan eventos atrasados y se revisan contratos debidos, con reservas de 120 segundos, reintentos diferidos y un máximo de 50 trabajos por ciclo. Las reservas vencidas se pueden recuperar tras reiniciar. Los periodos pagados y estados terminales no retroceden por respuestas antiguas. `BILLING_SUBSCRIPTION_SCHEDULING_ENABLED=false` deshabilita el trabajador para pruebas; en la aplicación normal se mantiene activo.

## Resultado de las pruebas

Verificación del bloque 07, 11 de noviembre de 2026: **424 casos únicos aprobados con MySQL 8.4/Mailpit**, sin fallos, errores ni omisiones: regresión completa de 420 casos, revalidación final de API/adaptador/OpenAPI (47 casos, tres adicionales respecto de la regresión) y aislamiento del scheduler (un caso adicional). La regresión H2 final ejecutó **424 casos: 423 aprobados y una omisión de correo**, cubierta en MySQL/Mailpit. Incluye 26 pruebas API de suscripción, 20 del adaptador, una de aislamiento de tareas programadas, diez de migraciones y toda la regresión anterior. También se verificaron Compose, enlaces internos e inicialización/reutilización de planes reales Sandbox. La aprobación del comprador, el cobro ficticio completo y el webhook público no se acreditan como verificados.

## Verificación externa y límites

El 11 de noviembre de 2026 se prepararon los dos planes activos, se validaron sus tarifas reales en Sandbox y se creó un contrato de diagnóstico con HTTP 201. Su consulta confirmó APPROVAL_PENDING, plan y custom_id; no hubo aprobación del comprador ni cobro. Se comprobó que el proveedor puede omitir update_time/status_update_time en ese estado; se usa create_time como referencia inicial.

Todavía faltan consentimiento humano con cuenta Sandbox, cobro ficticio confirmado de extremo a extremo y recepción de webhooks firmados en un host público. Las pruebas automáticas del adaptador usan HTTP controlado. Estos resultados no acreditan disponibilidad productiva en Perú ni despliegue en Azure.

Referencias: [API de creación](https://developer.paypal.com/api/subscriptions/v1/subscriptions-create/), [consulta del contrato](https://developer.paypal.com/api/subscriptions/v1/subscriptions-get/), [transacciones](https://developer.paypal.com/api/subscriptions/v1/subscriptions-transactions), [especificación oficial](https://developer.paypal.com/api/subscriptions/v1/schema.json).
