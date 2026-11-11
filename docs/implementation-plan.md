# Plan de implementación del backend de CollabPro

Alcance aprobado: máximo diez bloques de implementación, organizados por bounded context. Las tareas de cada US se mantienen separadas dentro de su bloque. Fuentes: [reporte](../../report/README.md), código y contratos Android actuales.

La numeración identifica bloques de trabajo; los commits de control de versiones los realiza manualmente el usuario. No se ejecutan comandos de Git.

## Estado actual — 11 de noviembre de 2026

**Nueve bloques implementados (01–07, 09 y 10) y uno pendiente (08). Billing utiliza PayPal Sandbox por decisión del usuario; el consentimiento/cobro externo completo y los pagos de colaboraciones del bloque 08 siguen pendientes. La integración móvil posterior y las verificaciones externas se mantienen diferenciadas del código del backend.** «Implementado» describe el código y sus verificaciones, no confirma que el usuario haya creado el commit manual ni desplegado los cambios.

| Bloque | Estado | Contexto | US y soporte | Entrega |
| --- | --- | --- | --- | --- |
| 01 | Implementado | Identity | US-12; regresión US-09/10/11/13/14; TS-01/02; SS-02 | Perfil empresarial, contratos de perfiles y ciclo de credenciales sociales. |
| 02 | Implementado | Campaign | US-15/16/19/20; regresión US-17/18; TS-03 | Edición de borradores, versiones, evaluación y resultado de postulaciones. |
| 03 | Implementado | Collaboration | US-21/22; TS-04 | Acuerdo bilateral, activación idempotente y consulta del ciclo de vida. |
| 04 | Implementado | Collaboration | US-23/24; TS-04/05 | Entregas puntuales/tardías, archivos, revisión y correcciones versionadas. |
| 05 | Implementado | Collaboration | US-25/30; TS-05 | Incidencias, resolución e historial por participante. |
| 06 | Implementado | Billing | US-26; TS-06; SS-01 | Validación Sandbox del proveedor, vinculación financiera y base de webhooks. |
| 07 | Implementado | Billing | US-27; TS-06 | Planes y suscripciones confirmadas por el proveedor. |
| 08 | Pendiente | Billing | US-28; TS-05/06 | Compensaciones, bloqueos, transacciones y pagos sin duplicación. |
| 09 | Implementado | Performance | US-29, escenarios 1–3; TS-07; SS-03 | Métricas con fuente/periodo y evidencias manuales diferenciadas. |
| 10 | Implementado | Performance e integración | US-29, escenarios 4–5; TS-07; SS-03 | Atribución, estimaciones y validación de recorridos completos. |

US-01 a US-08 corresponden a la landing y quedan fuera de estos bloques. El consumo de los contratos nuevos desde Android se implementará posteriormente.

## Capacidades que ya están disponibles

- **Base previa:** registro empresarial y creador, login JWT con rol del servidor, recuperación/restablecimiento de acceso, perfil creador, OAuth y listado de redes; creación de campañas, condiciones, publicación, cierre y descarte de borradores, búsqueda/detalle y postulaciones propias.
- **Bloque 01:** consulta y edición del perfil empresarial con versión; consultas internas por ID de perfil; estado, renovación y reautorización de credenciales sociales; migración V8 y entorno de pruebas MySQL/Mailpit aislado. Detalles en [identity-contracts.md](identity-contracts.md).
- **Bloque 02:** PATCH de borradores, versión de campaña y comprobación de condiciones; PATCH de postulaciones con versión obligatoria; consulta empresarial de postulantes, selección/rechazo con auditoría y resultado visible al creador; migración V9 y seed corregido/ampliado. Detalles en [campaign-contracts.md](campaign-contracts.md).
- **Bloque 03:** acuerdos bilaterales desde selecciones, condiciones inmutables, firma/rechazo explícitos por participante, activación idempotente, obligaciones con cantidades y fechas, estado/vencimientos, outbox con recibos y reintentos, migración V10 y demo bilateral. Detalles en [collaboration-contracts.md](collaboration-contracts.md).
- **Bloque 04:** entregas por pieza y cantidad, enlaces y archivos privados persistidos en MySQL, revisiones de versiones exactas, rechazo con observaciones/plazo, correcciones e historial de versiones; progreso, finalización y eventos transaccionales, idempotencia, migración V11 y demo con corrección. Detalles en [delivery-contracts.md](delivery-contracts.md).
- **Bloque 05:** incidencias con bloqueos independientes, propuestas y confirmación de contraparte, ampliación/cancelación consensuadas, historial privado y recuperación de hechos anteriores, ACL de ejecución, migración V12 y seed de resolución. Detalles en [incident-history-contracts.md](incident-history-contracts.md).
- **Bloque 06:** vinculación PayPal Sandbox para empresa (Vault) y creador (identidad receptora), estados, reintentos, callbacks Web/Android, requisito de método válido, referencias cifradas, inbox firmado e idempotente, revocación incluso fuera de orden y V15. No implementa suscripciones ni pagos. [Contratos y evidencia externa](billing-linking-contracts.md).
- **Bloque 07:** catálogo de planes Sandbox, suscripción de empresa con medio propio, consentimiento específico, comprobación de cobro, historial, cancelación confirmada, reanudación de aprobación, cola durable de eventos, reconciliación/polling y V16. Esencial USD 5/Crecimiento USD 10 mensuales, confirmados para la demo. [Contratos y evidencia externa](subscription-contracts.md).
- **Bloque 09:** reporte de resultados por colaboración, capturas autorizadas de TikTok con fuente/periodo, ausencia y fallos explícitos, evidencia manual y revisión empresarial separadas, archivos privados, reintentos/concurrencia, referencias desde el historial, migración V13 y seed manual demostrativo. Contratos en [performance-contracts.md](performance-contracts.md); límites externos en [ss03-metrics-feasibility.md](ss03-metrics-feasibility.md).
- **Bloque 10:** mecanismos LINK/CODE, destinos aprobados en servidor, seguimiento público deduplicado y declaraciones privadas con evidencia, fuentes/periodos/advertencia de estimación, estados y versiones, exclusión de evidencia rechazada, V14, demo diferenciada, recorridos completos no financieros y revisión de contratos Android/OpenAPI. Detalles en [attribution-contracts.md](attribution-contracts.md) y [backend-validation-and-mobile-contracts.md](backend-validation-and-mobile-contracts.md).

La verificación previa del bloque 10 reunió **318 casos aprobados**, sin fallos, errores ni omisiones, con MySQL 8.4 y Mailpit aislados. Se ejecutó la regresión completa y después la comprobación final de atribución con tokens sensibles a mayúsculas/minúsculas. Las pruebas específicas de actualización utilizan bases H2 dedicadas. Incluye todos los bloques previos, enlaces/códigos, deduplicación de sesión/evento, concurrencia, periodos, fuentes, exclusión de evidencia rechazada, rollback, privacidad, V13→V14, seed y Swagger. El recorrido completo verifica registro/login de ambos roles, campaña/postulación/acuerdo, rechazo/corrección/aprobación, incidencia/resolución, métricas/evidencia, atribución e historial conservando las condiciones originales. La regresión H2 final concluyó con **317 aprobados y una prueba de correo omitida** por requerir Mailpit, sin fallos ni errores. También se validó la configuración de Compose y los enlaces internos de la documentación. No se acreditan pagos, cuentas sociales externas ni despliegue Azure.

Verificación del bloque 06, 10 de noviembre de 2026: **376 casos únicos aprobados con el entorno MySQL 8.4/Mailpit**, sin fallos, errores ni omisiones. Se ejecutó una regresión completa de 372 casos y, tras revisar la recuperación de Vault y respuestas inválidas, se revalidaron las 33 pruebas del adaptador; cuatro son casos adicionales. La regresión H2 final ejecutó **376 casos: 375 aprobados y una omisión de RecoveryMailpitTests**, cubierta satisfactoriamente en MySQL/Mailpit. Incluye 24 pruebas API de Billing, 33 de protocolo/cifrado, nueve de migraciones, arquitectura, Swagger y toda la funcionalidad anterior. Compose y enlaces internos también están comprobados. El código nuevo no fue desplegado en Azure ni conectado todavía desde Android.

Verificación del bloque 07, 11 de noviembre de 2026: **424 casos únicos aprobados con MySQL 8.4/Mailpit**, sin fallos, errores ni omisiones: regresión completa de 420 casos, revalidación final de API/adaptador/OpenAPI (47 casos, tres adicionales respecto de la regresión) y aislamiento del scheduler (un caso adicional). La regresión H2 final ejecutó **424 casos: 423 aprobados y una omisión de correo**, cubierta en MySQL/Mailpit. Incluye 26 pruebas API de suscripción, 20 del adaptador, una de aislamiento de tareas programadas, diez de migraciones y toda la regresión anterior. También se verificaron Compose, enlaces internos e inicialización/reutilización de planes reales Sandbox. La aprobación del comprador, el cobro ficticio completo y el webhook público no se acreditan como verificados.

### Límites y verificaciones que siguen pendientes

- Las redes sociales y las métricas se probaron con respuestas HTTP controladas. SS-03 mantiene pendiente la validación externa; TikTok requiere video.list e Instagram aún no tiene adaptador de estadísticas. Configurar y verificar consentimiento/renovación con cuentas externas reales sigue pendiente; SS-02 no debe presentarse como validación real concluida.
- Android todavía debe consumir las rutas nuevas. Las condiciones y mutaciones heredadas que aceptan versión opcional conservan compatibilidad; los clientes deben enviar versión para detectar una pantalla desactualizada. Las rutas nuevas que la requieren están documentadas.
- Seleccionar una postulación sigue sin crear una colaboración ni una aceptación bilateral. El bloque 03 exige las dos firmas y cubre US-21/22. El bloque 04 cubre entregas/revisiones y sus versiones; el bloque 05 completa incidencias/historial global con resolución bilateral. Billing implementa la vinculación en 06; las suscripciones están implementadas en 07 y las compensaciones de 08 siguen pendientes. Performance implementa métricas/evidencia en 09 y atribución/interpretación en 10. La confirmación externa completa de suscripción sigue pendiente; las compensaciones se implementarán en 08.
- Los resultados locales no acreditan que estos cambios estén desplegados en Azure. Ese despliegue y su verificación son independientes del estado de implementación.

## Bloque 01: alcance de implementación

Estado del código: implementado y verificado el 8 de noviembre de 2026. Los bloques 02–07, 09 y 10 también están implementados; 08 continúa pendiente. La verificación contra cuentas reales de redes sociales requiere configurar las aplicaciones externas; se detalla separadamente en los contratos de Identity.

Mensaje sugerido para el commit manual: `feat(identity): complete brand profiles and authorized social access`.

- US-12: consulta y actualización del perfil empresarial desde la sesión, campos permitidos, validación, versión y protección frente a ediciones antiguas o simultáneas.
- Consultas internas por identificador de perfil empresarial y creador, manteniendo `accountId` y `profileId` diferenciados y sin exponer credenciales.
- SS-02: estado de permisos/vencimiento, renovación soportada por proveedor, necesidad de reautorizar, sustitución de credenciales de la misma identidad y preservación del callback Android de un solo uso.
- Regresión de registro, JWT, recuperación, perfil creador, autorización aceptada/rechazada y duplicados.
- Alinear Java entre Maven y Docker, añadir migración nueva y preparar ejecución MySQL/Mailpit aislada.
- Documentar endpoints, errores, integración y verificaciones externas pendientes en [identity-contracts.md](identity-contracts.md).

## Bloques 02–10: alcance, estado y commits sugeridos

### 02. Campaign

Estado: implementado. Contratos, compatibilidad y escenarios en [campaign-contracts.md](campaign-contracts.md). Mensaje sugerido para el commit manual: `feat(campaign): implement versioned drafts and application evaluation`.

Depende de 01. Separar metadatos de borradores (US-15), condiciones y fechas/versión (US-16), requisitos/duplicados/edición/cancelación (US-19) y consulta/selección/rechazo empresarial (US-20). Conservar filtros, detalle, disponibilidad y paginación de US-17/18. La selección no activa una colaboración ni rechaza automáticamente a los demás postulantes. Registrar cambios de payload que requieran adaptación móvil.

### 03. Collaboration: acuerdo y estado

Estado: **implementado y verificado el 9 de noviembre de 2026**. Contratos y alcance en [collaboration-contracts.md](collaboration-contracts.md).

Commit manual sugerido: `feat(collaboration): implement bilateral agreements and lifecycle`.

Depende de 02. US-21 implementa selección previa, snapshot inmutable y decisiones de ambos participantes sobre el mismo `termsVersion`; un rechazo impide la activación. La segunda firma activa una sola colaboración, con protección ante reintentos y concurrencia. US-22 consulta estado, obligaciones, acciones pendientes y vencimientos con hora del servidor. Se incluyen los escenarios de crear/consultar de TS-04, migración V10, contratos ACL y eventos persistidos con recibos/reintentos. Las entregas de TS-04 siguen en el bloque 04.

Regla confirmada por el usuario: aceptación hasta la primera fecha de entrega acordada, aunque haya cerrado la recepción de postulaciones; al llegar ese instante ya no se puede aceptar. El seed añade una colaboración demo explícita y conserva acuerdos existentes. Android consumirá estas rutas posteriormente.

### 04. Collaboration: entregas y revisión

Estado: **implementado y verificado el 9 de noviembre de 2026**. Contratos y escenarios en [delivery-contracts.md](delivery-contracts.md).

Commit manual sugerido: `feat(collaboration): implement submissions reviews and revisions`.

Depende de 03. US-23 registra cada pieza y su evidencia como pendiente de validación, admite primeras entregas tardías y conserva cantidades/fechas del acuerdo. Los archivos quedan en MySQL y se descargan con JWT y autorización por participante. US-24 aprueba/rechaza la versión exacta con control de concurrencia y registra nuevas versiones sin borrar las revisiones anteriores. Todas las piezas deben aprobarse para completar la colaboración; no se registra un pago por aprobar contenido.

Regla de corrección confirmada por el usuario: al rechazar, la empresa debe indicar observaciones y una nueva fecha futura; se admite corregir antes de ese límite exclusivo. El plazo original y la tardanza histórica se conservan.

Incluye los escenarios restantes de TS-04 y los dos de revisión de TS-05, migración V11 sin modificar anteriores, compatibilidad de obligaciones JSON de V10, archivos privados/históricos, claves de reintento, bloqueo del padre, eventos y rollback atómico. El seed agrega rechazo histórico y corrección pendiente sin cambiar las cantidades aceptadas. Android consumirá estos contratos posteriormente.

### 05. Collaboration: incidencias e historial

Estado: **implementado y verificado el 9 de noviembre de 2026**. Contratos y escenarios en [incident-history-contracts.md](incident-history-contracts.md).

Commit manual sugerido: `feat(collaboration): implement incidents and collaboration history`.

Depende de 04. US-25 registra incidencias de ambos participantes y conserva sus propuestas/decisiones. Cada incidencia mantiene su bloqueo hasta que la contraparte acepta su resolución. Puede mantenerse el acuerdo, ampliar el plazo de una versión rechazada o cancelar una colaboración no completada. La ampliación no reescribe la revisión original ni altera las cantidades/compensación aceptadas.

Reglas confirmadas por el usuario: apertura hasta siete días transcurridos desde finalizar/cancelar, con límite exclusivo; la contraparte confirma la resolución. Las incidencias ya abiertas pueden resolverse después. Al limpiar el último bloqueo se restaura el estado según avance y fechas, sin pausar plazos automáticamente.

US-30 implementa historial propio paginado, detalle de acuerdo y recursos, cronología persistida y recuperación idempotente de hechos anteriores, identificados como reconstruidos cuando corresponde. Las referencias financieras/resultados indican ausencia mientras sus contextos no las proporcionen. Incluye evidencia privada para ambas partes, control de versiones/reintentos, rollback de auditoría/ampliaciones, eventos, migración V12 y seed de resolución explícita. `CollaborationExecutionFacade` publica hechos y una consulta con bloqueo para las futuras verificaciones de Billing, sin acceso a repositorios de otro contexto.

### 06. Billing: vinculación financiera

Estado: **implementado el 10 de noviembre de 2026**. Proveedor elegido por el usuario: PayPal Sandbox, sin operaciones reales. El bloque 07 está implementado y el 08 sigue pendiente. Stripe fue descartado para esta demo por el registro y la caducidad del entorno anónimo. Contratos y guía: [billing-linking-contracts.md](billing-linking-contracts.md).

US-26 incluye vinculación validada por propósito, fallos explícitos y requisito financiero reutilizable con `METHOD_REQUIRED`. TS-06 incorpora la ruta de preparación y el webhook firmado; las suscripciones se implementan en 07 y los pagos de colaboraciones corresponden a 08. El seed no inventa autorizaciones del proveedor. El spike SS-01 queda **parcial**: autenticación externa Sandbox HTTP 200 y preparación de Vault HTTP 201 verificadas; consentimiento humano de ambos roles y webhook entregado al servidor siguen pendientes. El reporte conserva el título original sobre Stripe y todavía requiere reflejar la sustitución aprobada.

Commit manual sugerido: `feat(billing): implement verified payment method linking`.

Depende de 01 y usa 03–05 para el flujo de compensación de SS-01. Validar el proveedor Sandbox antes de presentar una integración real como terminada. US-26 cubre vinculación correcta/fallida y operación sin medio requerido. Distinguir medios para cobro de destinos para recibir pagos; tokenizar, verificar retornos y preparar webhooks firmados/idempotentes.

### 07. Billing: suscripción

Estado: **implementado el 11 de noviembre de 2026**. Contratos y configuración en [subscription-contracts.md](subscription-contracts.md). El bloque 08 sigue pendiente.

US-27 incluye planes obtenidos desde el servidor, requisito de medio PAYMENT propio, consentimiento Subscriptions, comprobación de transacción/periodo e identidad, fallos sin beneficios, estados pendientes e historial. TS-06 escenarios 2–3 conserva POST 201 y GET 200; un intento creado no se declara pagado. El webhook firma/deduplica, encola trabajo durable y solo lo marca procesado tras reconciliarlo.

Los precios de demo fueron confirmados por el usuario: Esencial USD 5 y Crecimiento USD 10 al mes. No se introdujeron cuotas de campañas ni beneficios comerciales no validados como restricciones. Se agregaron V16, inicializador idempotente de producto/planes Sandbox, configuración Compose y seed de catálogo sin suscripciones ficticias.

Evidencia externa: ambos planes activos y consulta de sus importes; contrato de diagnóstico HTTP 201, correlación de plan/custom_id y estado APPROVAL_PENDING. No hubo consentimiento del comprador ni cobro; el recorrido externo completo y webhook público permanecen pendientes. La integración móvil y Azure se verifican por separado.

Commit manual sugerido: `feat(billing): implement company subscriptions`.

Depende de 06. US-27 obtiene planes/precios/monedas desde el servidor. Medio válido y confirmación del proveedor son necesarios para activar la suscripción; un cobro fallido no activa beneficios. Evitar suscripciones o cargos duplicados y conservar estados pendientes. No imponer nuevas restricciones comerciales a campañas sin una regla del negocio.

### 08. Billing: compensación

Estado: **pendiente por decisión del usuario; depende de 06 y 07**.

Commit manual sugerido: `feat(billing): implement compensation lifecycle and payments`.

Depende de 03–07. US-28 crea la compensación desde el acuerdo, la mantiene pendiente hasta el cumplimiento, refleja incidencias y registra el pago confirmado. Cubrir autorización, fallo, espera, timeout, reintento, concurrencia y eventos duplicados/fuera de orden. Los canjes no realizan cargos. Una incidencia posterior no borra el hecho de un pago ni inicia automáticamente un reembolso.

### 09. Performance: métricas y evidencia

Estado: **implementado y verificado el 9 de noviembre de 2026**. No depende de Billing. Contratos en [performance-contracts.md](performance-contracts.md).

Commit manual sugerido: `feat(performance): implement sourced metrics and manual evidence`.

Depende de 01, 04 y 05. US-29 escenarios 1–3 y las consultas/registro de TS-07: métricas con fuente/periodo, ausencia explícita y evidencia manual diferenciada. Incluye adaptador TikTok con video.list, comprobación del video autorizado, renovación mediante Identity, capturas históricas sin sumar acumulados, estados de permisos/errores, versiones vigentes, revisión manual con expectedVersion, archivos privados y requestId idempotente. V13 no cambia migraciones ni datos anteriores; el seed solo añade evidencia marcada como demostración.

La referencia del historial identifica el reporte mediante un ACL. Las pruebas incluyen MySQL, permisos, renovación, caída del proveedor, concurrencia, marcas de tiempo iguales, rollback, archivos y evidencia ajena/inválida. Instagram mantiene evidencia manual y UNSUPPORTED_PROVIDER para la consulta automática. La validación externa de SS-03 sigue pendiente: disponer de código y HTTP controlado no demuestra consentimiento ni mediciones con cuentas reales. Ver [ss03-metrics-feasibility.md](ss03-metrics-feasibility.md). Android consumirá los contratos posteriormente.

### 10. Performance: atribución e integración

Estado: **implementado y verificado el 9 de noviembre de 2026**. Los bloques 06–07 añaden vinculación y suscripción Sandbox; 08 sigue pendiente. Contratos en [attribution-contracts.md](attribution-contracts.md).

Commit manual sugerido: `feat(performance): implement attribution and verify backend journeys`.

Depende de 01–05 y 09. US-29 escenarios 4–5 y TS-07: creación/consulta de LINK y CODE; destinos elegidos por ID desde configuración del servidor, sin aceptar URLs del visitante; clics registrados mediante redirección 302 y declaraciones empresariales con evidencia y eventId estable. Distingue FIRST_PARTY_TRACKING de BRAND_DECLARATION, deduplica reintentos/concurrencia, conserva auditoría y excluye de los totales las declaraciones con evidencia rechazada. HEAD/prefetch no suman clics; desactivación, cancelación o retiro del destino impiden redirigir.

Los resultados informan periodo con fin exclusivo, fuentes, advertencia obligatoria e isEstimate=true. No certifican personas únicas, conversiones ni ventas; los totales demostrativos se separan. Incluye V14 sin reescribir migraciones, seed idempotente, estados/versiones, rollback, privacidad y pruebas con MySQL de un recorrido completo para ambos roles. La revisión de DTO y rutas Kotlin queda registrada en [backend-validation-and-mobile-contracts.md](backend-validation-and-mobile-contracts.md); no se modificó Android. SS-03 documenta las asociaciones comprobadas localmente y sus límites. La integración financiera y sus recorridos esperan al bloque 08; la suscripción del 07 mantiene pendiente el recorrido externo completo; el consentimiento y las mediciones sociales externas y el despliegue Azure mantienen su validación pendiente.

## Reglas de cierre por bloque

Mantener los diez bloques originales: nueve implementados y uno restante de Billing (08). Cada bloque pendiente es una entrega funcional con su mensaje de commit sugerido; no se añaden commits separados para capas, pruebas o documentación. Los commits los crea manualmente el usuario cuando revise la entrega.

Cada bloque incluye dominio, aplicación, infraestructura, REST, migración nueva, pruebas de sus escenarios y documentación. Mantener las cuatro capas, JWT y autorización por objeto, errores `ApiError`, fechas del servidor y control de reintentos/concurrencia. Las pruebas finales complementan las de cada bloque, no las sustituyen. Mantener separadas las pruebas controladas y las verificaciones externas reales.

La vigencia de aceptación quedó confirmada en el bloque 03: primera fecha de entrega. En el bloque 04 se confirmó que la empresa fija un nuevo plazo futuro al rechazar. El bloque 05 confirma la ventana de siete días y la aceptación por la contraparte para resolver incidencias. Para 08 deben concretarse la viabilidad de Payouts en PayPal Sandbox y el tratamiento de compensación mixta. El catálogo de demo del 07 ya fue confirmado; la validación comercial y los consentimientos/cobros externos siguen pendientes. Los límites de las métricas/estimaciones están documentados; las verificaciones externas siguen pendientes. No fijar reglas comerciales arbitrarias en el código.
