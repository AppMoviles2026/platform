# Revisión de inconsistencias y valores fijos
11 de noviembre de 2026. Alcance: código de producción, configuración, migraciones, seeds y contratos del backend; contraste de solo lectura con el reporte y Android. Esta revisión no acredita despliegue ni consentimiento externo.

## Corregido en el bloque 08
| Hallazgo | Corrección |
| --- | --- |
| El historial financiero respondía siempre NOT_AVAILABLE. | Consulta mediante CompensationReferenceFacade; devuelve AVAILABLE y el ID existente, sin declarar que esté pagado. |
| La selección de un destino podía depender de los primeros cien métodos. | Consulta del último PAYOUT activo, con propietario y revocaciones efectivas, desde persistencia de Billing. |
| Payouts no consume directamente el medio de la empresa. | Ingreso Orders V2 aprobado/capturado y envío posterior desde la plataforma; hechos y claves distintos. |
| Las campañas demo monetarias existentes usaban PEN. | Se conservan; bloqueo explícito por moneda. Nuevo acuerdo USD independiente, sin conversión silenciosa. |
| Éxito del lote, autorización del navegador o ingreso podían confundirse con pago al creador. | PAID exige ítem SUCCESS, receptor/importe/moneda/correlación y transacción confirmados. |
| Un timeout podía hacer incierto si se debía volver a cobrar. | Operaciones persistidas, claves estables, recuperación por referencia/webhook/polling y ventanas limitadas; envío fallido conserva fundedAt. |
| Repetir una solicitud completada podía depender de la disponibilidad de PayPal. | Se reconoce el comando persistido antes del preflight/confirmación; el resultado financiero se conserva sin otro cargo. |
| Bearer vencido en Payouts no reutilizaba la lógica de recuperación. | Renovación una vez conservando la misma identidad de envío. |
| Una consulta REST accedía directamente al Store. | Consulta/autorización a través del servicio de aplicación. |
| Mensaje genérico de error hablaba solo de vinculación. | Mensaje de operación financiera válido para los tres flujos de Billing. |
| Las tareas financieras podían competir con otros jobs. | Scheduler de compensación separado del de suscripciones y del general; activación independiente comprobada. |
| Documentación señalaba que la compensación no existía. | Contratos, paquete, README y plan actualizados; evidencia externa pendiente explícita. |

## Valores fijos que se conservan por su propósito
- Host api-m.sandbox.paypal.com y aprobación sandbox.paypal.com: restricción deliberada a pruebas, sin modo live. No son URLs del backend que deban cambiar con Azure.
- USD 5/10 y nombres de los planes: decisión de demo del usuario, obtenidos del catálogo del servidor y configurables. No son precios comerciales confirmados ni límites de campañas.
- USD 5 de la nueva colaboración: dato de seed explícito; una vez firmado forma parte del acuerdo, no es una tarifa aplicada a todas las colaboraciones.
- UUID/correos de demo, example.com y métricas manuales ilustrativas: fixtures identificados, activados por configuración; reinicios no sobrescriben datos. No se crean tokens, autorizaciones, capturas ni pagos ficticios en producción.
- localhost, puertos y destinos de ejemplo: defaults locales. Retornos de PayPal, origen público de atribución, SMTP, MySQL y secretos se configuran por entorno. En Azure los callbacks deben apuntar al servidor publicado; para webhooks se necesita HTTPS accesible.
- Tipos/estados de dominio, validaciones, longitud de nonces, timeouts HTTP, lease y márgenes de reintento: invariantes o límites operativos. Batch size, polling, monedas y ventanas de reintento disponen de configuración; no se retiran controles para eliminar constantes.
- NOT_AVAILABLE, NO_DATA, UNSUPPORTED_PROVIDER y referencias nulas: ausencia/capacidad real, no datos inventados. Instagram todavía no aporta estadísticas automáticas.
- Valores locales de desarrollo y credenciales de pruebas están limitados a esos perfiles; prod exige secretos de entorno. No se copiaron claves privadas a documentación ni a archivos versionables.

No se encontraron marcadores TODO/FIXME ni UnsupportedOperationException en el código de producción revisado que sustituyan un caso de uso. Esto no equivale a una garantía de ausencia de defectos.

## Inconsistencias y verificaciones que permanecen
1. **Reporte:** SS-01 aún se titula «Viabilidad de Stripe en Sandbox» y menciona Stripe. La decisión de esta conversación y el backend usan PayPal Sandbox. Corregir ese apartado/evidencia editorial antes de entregar; no atribuirle validación externa que no se realizó. El dominio sigue separado del proveedor.
2. **Android:** AppState aún instancia PreviewCollaborations, PreviewBilling y PreviewResults para los contextos nuevos. PreviewBilling muestra «Por definir», «S/ 180 + productos» y «Canje + pago». Esa combinación no corresponde a un tipo MIXED del backend ni a los precios vigentes del catálogo. Al conectar esas pantallas se deben reemplazar por DTO reales y estados vacíos/error, conservando muestras exclusivamente en previews. No se modificó Kotlin en esta entrega de backend.
3. **Integración móvil pendiente:** incorporar acuerdos/entregas/incidencias, medios financieros, suscripciones, compensación, métricas/atribución y control de versiones según los contratos. Implementar manejo de collabpro://compensation-return, refresco con JWT y confirmación expresa por la empresa; no confiar en un status del enlace para marcar un pago.
4. **PayPal externo:** OAuth/scopes comprobados; faltan consentimiento humano de comprador y receptor, ingreso, envío recibido y webhook público. Scope habilitado no demuestra saldo suficiente, permisos efectivos de cada producto ni éxito de un desembolso. Solo Sandbox.
5. **Redes externas:** SS-02/SS-03 requieren consentimiento real y permisos; las pruebas HTTP controladas no prueban una integración social externa completa. Instagram usa evidencia manual.
6. **Azure:** no se desplegaron estos cambios en esta sesión. Verificar migración V17, secretos, callback público, HTTPS/webhook y estado de los workers tras desplegar. No borrar volúmenes para actualizar el esquema.
7. **Dinero retenido o resultado incierto:** si el ingreso se confirmó y no puede completarse el envío, el recurso lo informa sin volver a cobrar. Un bloqueo permanente por identidad/importe/ventana requiere investigación operativa; no se añadió un botón administrativo para forzar pago ni un reembolso fuera del alcance.
8. **Carrera con el proveedor:** una incidencia posterior al despacho HTTP no revierte ese despacho. Se registra el hecho y se bloquean pasos posteriores; un incidente posterior no borra el pago ni causa reembolso automático.

Evidencia reproducible y conteos: [validación del backend](backend-validation-and-mobile-contracts.md). Contratos del bloque: [compensation-contracts.md](compensation-contracts.md).
