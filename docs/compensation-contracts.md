# Compensación de colaboraciones · bloque 08
Actualizado: 11 de noviembre de 2026. US-28 y pago de colaboración de SS-01. Implementación DDD en Billing; integración móvil y comprobación humana externa se registran por separado.

## Flujo y reglas
Una compensación por colaboración, proyectada desde el acuerdo bilateral inmutable mediante el contrato publicado de Collaboration y su outbox. GET también recupera proyecciones aún no procesadas. Billing no consulta tablas de otros contextos. Se conservan tipo, descripción, importe y moneda; no se recalculan al editar una campaña.

CASH queda pendiente hasta que todas las piezas estén aprobadas. Una incidencia abierta bloquea nuevas autorizaciones, captura y envío; al resolverla bilateralmente se vuelve a comprobar el cumplimiento. PRODUCT, SERVICE, CREDIT y BARTER son no monetarios: no generan cargos ni un pago ficticio. No existe el tipo MIXED en el dominio actual.

Decisión del usuario: **solo Sandbox**, conservar PEN sin conversión y añadir otro ejemplo acordado en USD. Moneda habilitada por defecto: USD; PEN devuelve UNSUPPORTED_PAYMENT_CURRENCY sin cambiar el acuerdo. No se impone una suscripción como requisito de colaboración.

El flujo monetario tiene dos etapas:
1. La empresa inicia un intento con un medio PAYMENT propio y un destino PAYOUT verificado del creador. Se prepara una orden Orders V2, con importe del acuerdo y claves persistidas.
2. La empresa abre approvalUrl, aprueba con la misma cuenta PayPal vinculada y llama a confirmation con JWT. El retorno del navegador solo consulta: **no captura**.
3. El servidor comprueba orden, identidad, moneda e importe y captura a favor de la plataforma. fundedAt acredita ese ingreso; todavía no significa que el creador recibió su compensación.
4. Payouts envía desde el saldo ficticio de la plataforma al PAYPAL_ID verificado del creador. Solo el ítem SUCCESS, con referencia de transacción y fecha verificadas, establece PAID/wasPaid/paidAt. El estado SUCCESS del lote por sí solo no basta.

La plataforma necesita saldo Sandbox suficiente para importe y tarifa de Payouts; no se descuenta una comisión del importe acordado. Si el envío falla tras capturar, se conserva el ingreso y se reintenta el envío, **sin cobrar nuevamente a la empresa**. No hay reembolso automático ni endpoint para forzar PAID.

## Contratos REST
Todas las rutas siguientes comienzan con /api/v1. JWT de participante; solo la empresa propietaria inicia/confirma. Las consultas ajenas se rechazan.

| Método y ruta | Contrato |
| --- | --- |
| GET /collaborations/{id}/compensation | Snapshot, estado real, elegibilidad, incidencias, fechas financieras, versión y acciones disponibles. Reconcilia lecturas; no crea/captura/envía dinero. |
| GET /compensations/{id} | Misma autorización y representación, mediante ID financiero. |
| POST /collaborations/{id}/payment | 201 + Location. Body: requestId UUID, methodId UUID para un ingreso nuevo, client WEB o ANDROID, expectedVersion. Reserva operaciones, prepara orden o reintenta exclusivamente un envío fallido. |
| POST /collaborations/{id}/payment/confirmation | Body: requestId UUID y expectedVersion. Comprueba aprobación e identidad; autoriza captura del intento existente. |
| POST /collaborations/{id}/payment/refresh | Reconciliación de lectura. No acepta flags de éxito ni importes del cliente. |
| GET /collaborations/{id}/compensation/history?page=0&size=20 | Auditoría paginada para los participantes. |
| GET /billing/paypal/compensation-callback?state=... | Público, estado aleatorio correlacionado y caducidad de 30 minutos. WEB obtiene resultado seguro; ANDROID redirige a collabpro://compensation-return con collaborationId/status, sin credenciales. |
| POST /webhooks/payments | Inbox existente, firma de PayPal verificada y deduplicación. Eventos de Orders/Capture/Payout se encolan y se comprueban consultando al proveedor. |

La versión se obtiene de la última consulta. Reutilizar requestId y el mismo contenido lógico ante timeout; expectedVersion no cambia la identidad del comando. Reutilizar una clave para otra acción/payload produce IDEMPOTENCY_CONFLICT. Una intención pendiente no se reemplaza con otra; PAYMENT_ALREADY_IN_PROGRESS invita a consultar la existente.

Estados principales: PENDING_DELIVERABLES, READY_FOR_PAYMENT, AWAITING_COMPANY_APPROVAL, CAPTURE_PENDING, FUNDED, PAYOUT_PENDING, PAYMENT_FAILED, BLOCKED_INCIDENT, CANCELLED, NON_MONETARY, PAID, PAYMENT_REVERSED y RECONCILIATION_REQUIRED. availableActions y unavailableReason explican qué puede hacer la empresa. fundedAt y paidAt son hechos diferentes. approvalUrl se entrega solo a la empresa y dentro de la vigencia local.

Los errores mantienen ApiError. 400 para entrada inválida; 403 para rol/propiedad; 404 si no existe; 409 para versión, requisitos, moneda, identidad o conflicto financiero; 503 para configuración/indisponibilidad del proveedor. No se publican tokens, receptor sin enmascarar, cuerpos de error de PayPal ni referencias cifradas.

## Persistencia, reintentos y eventos
V17 agrega raíz, comandos, referencias cifradas/hasheadas, auditoría y trabajos de reconciliación; no reescribe V1–V16 ni inventa pagos históricos. Se bloquea primero la colaboración y después la raíz propia. Las decisiones locales son transaccionales; HTTP se ejecuta fuera de la transacción.

Las operaciones tienen lease de 120 segundos, claves estables y límites de recuperación conservadores: creación/captura 5 horas; envío 28 días. Vencida una ventana incierta se exige reconciliación, nunca una operación nueva a ciegas. La clave de captura es distinta de la de creación. DUPLICATE_BATCH_ID recupera el lote original únicamente desde hosts Sandbox permitidos y verifica su identidad completa.

Inbox firmado y trabajos durables permiten recuperar referencias antes de recibir la respuesta HTTP. Se consulta el estado actual ante eventos duplicados/fuera de orden. Polling separado de suscripciones y tareas generales. Una operación ya despachada no puede deshacerse atómicamente si aparece una incidencia durante HTTP: se conserva el resultado externo, se bloquean pasos posteriores y no se simula un reembolso. Una incidencia después de pagar conserva wasPaid/paidAt.

El historial global obtiene compensationId mediante un ACL publicado, en lugar de una ausencia fija. AVAILABLE significa que existe el recurso financiero; no que esté pagado.

## Configuración y demo
Copiar nombres desde [.env.example](../.env.example); guardar secretos solo en .env local o en el gestor de secretos del despliegue. La API está fijada a api-m.sandbox.paypal.com y no admite modo live.

Variables adicionales: PAYPAL_COMPENSATION_RETURN_URL, BILLING_COMPENSATION_ENABLED, BILLING_COMPENSATION_CURRENCIES (USD), BILLING_AUTOMATIC_PAYOUTS (true), BILLING_COMPENSATION_SCHEDULING_ENABLED (true), BILLING_COMPENSATION_BATCH_SIZE (50), BILLING_ORDER_RETRY_SECONDS (18000), BILLING_PAYOUT_RETRY_SECONDS (2419200). Estos dos últimos límites se validan antes de arrancar; no deben ampliarse arbitrariamente.

Con seed habilitado se añade **Demo PayPal Sandbox USD**, campaña 41000000-0000-4000-8000-000000000008, aplicación 31000000-0000-4000-8000-000000000008, marca demo.brand@collabpro.app y creadora demo.creator@collabpro.app. Acuerdo separado de USD 5, una pieza ilustrativa aprobada. No altera PEN ni crea autorizaciones/pagos. Se omite si ya existe y no sobrescribe cambios del usuario.

Recorrido en Swagger:
1. Login empresa y creadora; localizar la colaboración USD mediante /collaborations/mine.
2. Vincular PAYMENT de empresa y PAYOUT de creadora con cuentas **de prueba** diferentes de la cuenta merchant de la plataforma; completar los consentimientos del bloque 06.
3. Consultar compensación y copiar version. Iniciar payment con UUID nuevo, methodId, client WEB y expectedVersion.
4. Abrir approvalUrl y aprobar con el comprador Sandbox vinculado. Consultar otra vez la versión y enviar confirmation con UUID nuevo.
5. Consultar ambos JWT y el historial. Un envío pendiente/UNCLAIMED o fallido no aparece como PAID. Para reintento terminal de envío usar payment con otra clave y versión actual; conservar el ingreso.
6. Para webhook público configurar HTTPS y PAYPAL_WEBHOOK_ID. Registrar CHECKOUT.ORDER.APPROVED/COMPLETED, PAYMENT.CAPTURE.COMPLETED/DENIED/REFUNDED/REVERSED y los eventos disponibles de PAYOUTS-BATCH/PAYOUTS-ITEM de esta aplicación Sandbox.

## Evidencia y límites
Pruebas API con registro/JWT, campaña, postulación, acuerdo bilateral, entrega, aprobación e incidencias reales del backend; proveedor controlado para escenarios financieros. Pruebas de protocolo HTTP, migración V16→V17, seed, Swagger y arquitectura. [Resultado de regresión](backend-validation-and-mobile-contracts.md).

Autenticación externa Sandbox y scopes Orders/Payouts comprobados con las claves locales, sin mostrarlas. El consentimiento del comprador y receptor, captura, recepción del envío y entrega de un webhook público **no se declaran verificados externamente**. Android y Azure requieren integración/despliegue posteriores.

Fuentes oficiales: [Orders V2](https://developer.paypal.com/api/orders/v2/orders-create/), [captura de orden](https://developer.paypal.com/api/orders/v2/orders-capture/), [Payouts y estados](https://developer.paypal.com/api/payouts), [países y monedas admitidos](https://developer.paypal.com/payouts/supported-features), [tarifas a cargo del remitente](https://developer.paypal.com/payouts/fees/).
