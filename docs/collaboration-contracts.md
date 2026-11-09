# Collaboration: bloque 03

US-21 y US-22; escenarios 1 y 2 de TS-04. Se mantienen las cuatro capas DDD y la autorización JWT por participante. Las entregas/revisiones pertenecen al bloque 04 y las incidencias/historial al 05.

## Acuerdo bilateral

Seleccionar una postulación en Campaign no equivale a aceptar un acuerdo. Cualquiera de sus dos participantes puede proponerlo; esa operación tampoco firma en su nombre. Deben confirmar expresamente ambos usuarios autenticados.

El servidor conserva un snapshot inmutable de la postulación seleccionada: identificadores y versiones originales, propuesta y requisitos confirmados, participantes y nombres, campaña, entregables con cantidades/fechas y compensación. Cambiar después un nombre o cerrar la recepción de la campaña no modifica lo aceptado.

**Regla confirmada por el responsable del proyecto:** se puede aceptar hasta la primera fecha de entrega acordada, aunque haya cerrado la recepción de postulaciones. El instante límite es exclusivo: `serverNow < acceptanceDeadline`. Se conserva también `applicationDeadline` como dato histórico; no limita la firma. Todas las fechas se intercambian como instantes ISO-8601 y se persisten en UTC.

Las firmas se refieren a `snapshot.termsVersion`, un UUID que identifica las condiciones congeladas. No se usa el contador mutable `version` como precondición de firma: la aceptación de una parte no invalida la pantalla de la otra. Una versión de condiciones distinta devuelve `409 TERMS_CHANGED`.

- Acuerdo: `PENDING → ACCEPTED`, `REJECTED` o `EXPIRED`.
- Una primera firma mantiene `PENDING`. La segunda firma registra `ACCEPTED` y activa una única colaboración dentro de la misma transacción.
- Cualquiera puede rechazar antes de registrar su aceptación; el rechazo impide la activación. No se permite retirar una aceptación o sobrescribir la decisión ajena.
- Repetir una aceptación registrada o el rechazo propio recupera su resultado mientras sea compatible con el estado final. Un acuerdo rechazado o vencido no se reabre mediante reintentos.
- No existe edición unilateral de condiciones ni negociación por mensajería en este bloque.

## Contratos REST

Todas las rutas están bajo `/api/v1` y requieren JWT de cuenta activa. Solo la marca propietaria y el creador seleccionado pueden consultar o decidir sobre el recurso. El servidor obtiene rol, cuenta y perfil de la sesión; no acepta participantes o firmas enviados por el cliente.

| Método | Ruta | Cuerpo / resultado |
| --- | --- | --- |
| POST | `/agreements` | `{"applicationId":"UUID"}`; 201 con acuerdo y `Location`. Solo postulaciones `SELECTED`. Repetir recupera el mismo acuerdo. |
| GET | `/agreements/{id}` | 200 con snapshot, estado, firmas y `collaborationId` si existe. |
| GET | `/agreements/mine` | Lista propia paginada; filtros `status=PENDING\|ACCEPTED\|REJECTED\|EXPIRED`, `page` y `size`. |
| POST | `/agreements/{id}/decisions` | `{"termsVersion":"UUID","decision":"ACCEPT"}` o `REJECT`; 200 con el estado actualizado. |
| POST | `/collaborations` | `{"agreementId":"UUID"}`; 201 con colaboración y `Location`. Recupera la activación realizada por la segunda firma, sin duplicarla. Rechaza acuerdos sin las dos aceptaciones. |
| GET | `/collaborations/{id}` | 200 con estado, fechas, condiciones, obligaciones y acciones pendientes del participante. |
| GET | `/collaborations/mine` | Lista propia paginada; filtro opcional de estado, `page` y `size`. |

La paginación usa `page=0`, `size=20` por defecto; tamaño permitido de 1 a 100. Una lista vacía devuelve `200` con `items: []` y `total: 0`. Orden estable por creación descendente e identificador. Los filtros temporales reflejan el vencimiento efectivo, aunque todavía no se haya ejecutado el proceso periódico.

Ejemplo de recorrido desde Android/Swagger:

1. Consultar la postulación seleccionada y hacer `POST /agreements`.
2. Mostrar su `snapshot` completo y usar el `snapshot.termsVersion` devuelto al aceptar o rechazar.
3. Cada participante confirma desde su propia sesión. Tras la primera firma, mostrar espera de la contraparte.
4. La segunda respuesta incluye `collaborationId`; consultar `/collaborations/{id}`. También se puede recuperar mediante el POST exigido por TS-04.
5. Recuperar acuerdos y colaboraciones mediante las listas propias al volver a iniciar sesión; no depender de identificadores guardados solo en memoria.

`AgreementView` incluye los campos `brandAcceptedAt/By`, `creatorAcceptedAt/By`, `decidedAt`, `rejectedBy`, `version`, `collaborationId` y `availableActions`. Estas últimas contienen `ACCEPT_TERMS` y `REJECT_TERMS` únicamente mientras el usuario todavía puede decidir.

## Estado y vencimientos

`CollaborationView` incluye `snapshot`, `status`, `startedAt`, `expiredAt`, `version`, `nextDeadline`, cantidades requeridas/aprobadas y `deliverables`. Cada obligación conserva `specificationId`, un `deliverableId` propio, tipo, descripción, cantidades, fecha y `overdue`. Una especificación de dos videos continúa requiriendo dos videos; no se reduce a una sola entrega.

Las colaboraciones nacen en `ACTIVE`. Si vence alguna obligación pendiente, las consultas muestran `EXPIRED` inmediatamente y el proceso periódico persiste ese estado. La fecha próxima corresponde a la obligación incompleta más temprana. El vencimiento conserva las obligaciones para permitir el tratamiento de entregas tardías en el bloque 04. `expiredAt` puede seguir nulo hasta que el proceso periódico registre la transición.

El catálogo contempla además `UNDER_REVIEW`, `COMPLETED`, `CANCELLED` y `DISPUTED` para las siguientes entregas; este bloque no ofrece comandos para forzar esos estados. No existe un PATCH genérico de estado ni cierre automático que ignore obligaciones.

`pendingActions` describe tareas del negocio: `SUBMIT_DELIVERABLE` o `SUBMIT_LATE_DELIVERABLE` para el creador; `WAIT_FOR_DELIVERY` para la marca. `availableActions` de colaboración se mantiene vacío hasta implementar sus comandos en el bloque 04. Android debe distinguir una tarea pendiente de una operación actualmente disponible.

## Errores y concurrencia

Se conserva el formato `ApiError`: `code`, `message` y `fields`.

| HTTP | Casos principales |
| --- | --- |
| 400 | JSON/campos inválidos, UUID o enum inválido, falta `termsVersion`, paginación fuera de rango. |
| 401 | JWT ausente, inválido o vencido. |
| 403 | `FORBIDDEN` para un tercero; `ACCOUNT_NOT_ACTIVE` si no están habilitados los participantes. |
| 404 | `NOT_FOUND` para acuerdo, colaboración o postulación inexistente. |
| 409 | `SOURCE_NOT_SELECTED`, `TERMS_EXPIRED`, `TERMS_CHANGED`, `AGREEMENT_CLOSED`, `DECISION_ALREADY_RECORDED`, `AGREEMENT_NOT_ACCEPTED`, `CONCURRENT_UPDATE`. |

Las mutaciones usan transacciones `READ_COMMITTED`, bloqueo de campaña al proponer, bloqueo de acuerdo al decidir/activar, versiones JPA y restricciones únicas por postulación y acuerdo. Así se evitan lecturas antiguas de MySQL y duplicados ante solicitudes simultáneas. Un error al guardar la colaboración o su evento revierte también la segunda aceptación: se puede reintentar. El vencimiento descubierto al decidir se confirma antes de devolver `409 TERMS_EXPIRED`.

## Integración entre contextos y persistencia

Campaign publica `CampaignAgreementFacade` y devuelve datos de una selección, sin exportar agregados. Los adaptadores de infraestructura de Collaboration consumen esa interfaz y `IdentityProfileFacade`; dominio y aplicación no importan repositorios de otros contextos.

La migración nueva **V10** agrega acuerdos, colaboraciones, outbox y recibos por consumidor. No modifica migraciones anteriores ni transforma automáticamente postulaciones seleccionadas en acuerdos aceptados. El seed de demostración es una operación separada y explícita.

`CollaborationCreated` y `CollaborationExpired` se guardan en el outbox junto con la transición de negocio. El sobre publicado conserva `eventId`, `collaborationId`, tipo, `schemaVersion=1`, `occurredAt` y el snapshot como JSON. Los futuros consumidores se registran mediante `CollaborationEventConsumer`, con nombre estable y único.

El despachador registra efecto local y recibo en una transacción, serializa cada evento y reintenta fallos con espera progresiva hasta cinco minutos. Un fallo del consumidor revierte sus cambios locales y conserva el evento pendiente. Los recibos impiden aplicar dos veces efectos transaccionales confirmados. Si un consumidor llama a un proveedor externo, debe usar `eventId` como clave de idempotencia; la transacción local no garantiza por sí sola un único efecto remoto.

Mientras Billing/Performance no tengan consumidores, los eventos quedan conservados sin confirmaciones ficticias. Esto prepara su integración; todavía no crea compensaciones, pagos o métricas. Los consumidores deben tolerar eventos repetidos y consultar su estado local para tratar el orden de llegada.

Configuración opcional:

- `collaboration.scheduling.enabled=true`: activa las tareas de vencimiento y publicación; las pruebas lo deshabilitan.
- `collaboration.expiration.delay-ms=30000`: hasta 100 acuerdos y 100 colaboraciones por ciclo.
- `collaboration.events.delay-ms=5000`: hasta 50 eventos pendientes por consumidor y ciclo.

## Demo y verificación

Con el seed habilitado se agrega un acuerdo aceptado y una colaboración activa para la postulación demo `30000000-0000-4000-8000-000000000004`, entre `demo.brand@collabpro.app` y `demo.creator2@collabpro.app`. Las aceptaciones son datos de demostración explícitos. El runner se ejecuta después del seed existente y crea acuerdo, firmas y colaboración en una transacción; si ya existe un acuerdo, conserva sus decisiones y condiciones. No se reabren fechas vencidas ni se simulan pagos o accesos a redes sociales.

Pruebas: dominio, recorrido HTTP con ambos roles, rechazo, firma individual, firma de condiciones distintas, aislamiento, JWT, listas, cantidades, snapshot inmutable, cierre de recepción, frontera de vencimiento, propuestas/firmas/activaciones concurrentes, decisiones opuestas, rollback y recuperación, outbox con fallo/reintento y consumidores simultáneos, seed idempotente, migración V9→V10 y regresiones anteriores. Swagger publica los siete endpoints nuevos. Ejecutar `.\mvnw.cmd test` o `.\scripts\test-mysql.ps1` para MySQL/Mailpit aislados.

Resultado del 9 de noviembre de 2026: suite completa con MySQL 8.4/Mailpit, **216 pruebas aprobadas**, sin fallos, errores ni omisiones; las pruebas dedicadas de actualización utilizan H2. La regresión completa con H2 también terminó sin fallos: 215 aprobadas y una prueba de correo omitida por requerir Mailpit. Los siete endpoints y las respuestas 201 están verificados en OpenAPI. Los servicios aislados de prueba se detuvieron al finalizar.

Estas verificaciones no acreditan un despliegue en Azure ni una integración móvil de las rutas nuevas. El código Android no forma parte del bloque 03.
