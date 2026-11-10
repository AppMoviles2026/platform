# Collaboration: bloque 05 — incidencias e historial

Implementa US-25, US-30 y los escenarios 3–4 de TS-05. Continúa las [entregas y revisiones](delivery-contracts.md) con dominio puro, aplicación mediante puertos, infraestructura y REST. La consulta/operación financiera de TS-05 sigue en Billing; aquí se publican hechos y bloqueos para su integración posterior.

## Reglas confirmadas

El responsable del proyecto confirmó:

- Se pueden abrir incidencias durante la ejecución y durante **7 días transcurridos** desde la finalización o cancelación registrada. El límite es exclusivo: `now < terminalAt + 7 días`.
- Una parte propone la resolución y **la contraparte debe aceptarla**. El proponente no puede aceptarse o rechazarse su propia propuesta; puede retirarla.

La ventana limita la apertura de incidencias nuevas, no la resolución de las ya registradas. Abrir una incidencia posterior no reinicia los siete días ni borra la fecha de finalización. Una cancelación histórica sin fecha verificable no recibe un plazo inventado.

Pueden abrirlas ambos participantes activos, también cuando la colaboración está vencida o ya tiene otra incidencia. Cada una conserva su propio bloqueo. Mientras quede alguna abierta o con propuesta pendiente, la colaboración muestra `DISPUTED` y bloquea entregas, correcciones y validaciones; siguen disponibles las consultas y la evidencia de incidencias.

Los plazos siguen transcurriendo durante la disputa. Al resolver la última incidencia se recupera el estado que corresponde al progreso y fechas reales: activa, en revisión, vencida, completada o cancelada. Una resolución no amplía plazos por sí sola.

## Incidencia y resolución

Categorías: `CONDITIONS`, `DELIVERABLE`, `COMPENSATION` y `OTHER`. La descripción es obligatoria, hasta 4000 caracteres. Puede referenciar una pieza de la misma colaboración y hasta diez evidencias distintas mediante URL HTTPS o archivo autorizado.

Estados: `OPEN → RESOLUTION_PROPOSED → RESOLVED`. Rechazar o retirar una propuesta devuelve la incidencia a `OPEN`; conserva la propuesta anterior y no elimina su bloqueo.

Las propuestas son inmutables e incluyen uno de estos resultados:

| Resultado | Efecto al aceptar la contraparte |
| --- | --- |
| `KEEP_AGREEMENT` | Resolver esa incidencia conservando condiciones, cantidades y decisiones. |
| `EXTEND_CORRECTION` | Acordar un plazo posterior para una versión rechazada concreta. Exige identificadores de pieza/versión, su versión de concurrencia y fecha futura posterior al plazo efectivo actual. |
| `CANCEL_COLLABORATION` | Cancelar de común acuerdo una colaboración aún no completada. Conserva entregas, condiciones y las demás incidencias. |

Una ampliación conserva la revisión original y agrega un registro de acuerdo con plazo anterior/nuevo, propuesta, participantes y aceptación. El detalle de entrega muestra `effectiveCorrectionDeadline`; sus versiones históricas incluyen `correctionExtensions`. Una corrección nueva continúa pendiente de revisión; no se convierte en aprobada por resolver una incidencia.

Si otra resolución ya cambió la pieza, o la nueva fecha venció antes de aceptarla, se devuelve `409 RESOLUTION_STALE`. La propuesta sigue pendiente y la incidencia mantiene su bloqueo; deben rechazarla o retirarla y acordar otra propuesta. Nunca se aplican condiciones distintas de las que se mostraron al firmante.

No se cancela ni se reabre una colaboración completada para borrar su cumplimiento. Una incidencia reciente puede bloquear su elegibilidad para compensación sin alterar entregas aprobadas o su `completedAt`. No se registran pagos, reembolsos o métricas mediante estas resoluciones.

## Contratos REST

Rutas bajo `/api/v1`, todas con JWT y autorización por participante.

| Método | Ruta | Resultado |
| --- | --- | --- |
| POST | `/collaborations/{id}/incidents` | 201 con incidencia y Location. |
| GET | `/collaborations/{id}/incidents` | Lista propia paginada; filtro opcional `status`. |
| GET | `/incidents/{id}` | Detalle, propuesta actual y acciones permitidas. |
| POST | `/incidents/{id}/resolutions` | 201; propone una resolución sobre el `expectedVersion` de la incidencia. |
| GET | `/incidents/{id}/resolutions` | Propuestas históricas, incluidas rechazadas/retiradas, paginadas. |
| POST | `/incidents/{id}/resolutions/{resolutionId}/decisions` | 200; decisión sobre la propuesta exacta y versión actual. |
| POST | `/collaborations/{id}/incident-assets` | 201; archivo privado de cualquiera de las dos partes. |
| GET | `/collaborations/history` | Participaciones propias, con paginación y filtro de estado. |
| GET | `/collaborations/history/{id}` | Colaboración, acuerdo conservado y referencias autorizadas. |
| GET | `/collaborations/{id}/history` | Cronología de hechos, paginada por secuencia ascendente. |

Las listas usan `page=0` y `size=20`, con tamaño entre 1 y 100. Una lista vacía devuelve 200, `items: []` y `total: 0`. El historial de participaciones incluye las registradas, también vigentes, y permite filtrar las completadas/canceladas.

Abrir:

```json
{
  "requestId": "UUID-nuevo",
  "category": "CONDITIONS",
  "description": "Necesitamos aclarar el mensaje del producto.",
  "evidence": [
    { "url": "https://example.com/evidencia" }
  ]
}
```

Proponer:

```json
{
  "requestId": "UUID-nuevo",
  "expectedVersion": 0,
  "terms": {
    "outcome": "KEEP_AGREEMENT",
    "description": "Ambas partes mantienen el acuerdo y continúan la revisión.",
    "evidence": []
  }
}
```

Para ampliar una corrección, `terms` usa `EXTEND_CORRECTION` y añade `deliverableId`, `submissionId`, `expectedDeliverableVersion` y `correctionDeadline`. No se admiten esos campos en una resolución de otro resultado.

Decidir desde la sesión de la contraparte:

```json
{
  "requestId": "UUID-nuevo",
  "expectedVersion": 1,
  "decision": "ACCEPT"
}
```

`REJECT` requiere `reason` no vacío, hasta 2000 caracteres. `WITHDRAW` corresponde al proponente. Para volver a proponer hay que usar la versión devuelta tras rechazo/retiro.

Las mutaciones devuelven `{incident, affectedResolutionId, replayed}`. El detalle incluye `currentResolution` y `availableActions`: proponer, aceptar, rechazar o retirar según rol, estado y vigencia. Las propuestas vencidas o desactualizadas no anuncian aceptación disponible.

## Concurrencia y reintentos

Cada mutación JSON exige `requestId`; una subida lleva `Idempotency-Key: UUID`. Conservar la misma clave/cuerpo al reintentar. Reutilizarla para otra operación, objetivo o contenido produce `409 IDEMPOTENCY_CONFLICT`.

Los reintentos recuperan la incidencia actual y conservan el identificador de la propuesta afectada. Pueden recuperar una operación ya confirmada incluso después de los siete días; no reabren ni vuelven a aplicar efectos.

Las transacciones `READ_COMMITTED` y el bloqueo de colaboración serializan incidentes, resoluciones y entregas. La versión de incidencia protege sus decisiones; la versión de pieza protege una ampliación concreta. Resolver una incidencia distinta no invalida por sí solo la propuesta de otra.

Incidencia/propuesta/decisión, ampliación o cancelación, contador de bloqueos, idempotencia, historial y outbox se confirman juntos. Un fallo revierte todo; la misma solicitud puede recuperarse al reintentar. Dos resoluciones concurrentes no dejan contadores negativos ni liberan bloqueos pendientes.

## Archivos y privacidad

La subida de evidencia de incidencias funciona para marca y creador, incluso durante una disputa. Usa los tipos y límites del bloque 04. Se puede adjuntar un archivo propio o uno ya compartido dentro de la misma colaboración; nunca un borrador privado de la contraparte ni archivos de otra colaboración.

Se utilizan los endpoints autenticados de metadatos/descarga del bloque 04. La contraparte accede cuando el archivo se adjunta a una incidencia o propuesta; ese acceso histórico permanece tras resolver o retirar. El historial registra la actividad de subida y referencia, sin revelar el nombre o contenido de un archivo aún privado.

Después de los siete días se permite aportar evidencia a incidencias que siguen abiertas; no se habilita por ello una incidencia nueva.

## Historial y datos anteriores

El detalle histórico conserva el snapshot del acuerdo, firmas, fechas y avance. Proporciona rutas existentes para entregas/versiones, incidencias y cronología. Los nombres actuales del perfil o campaña no sustituyen los históricos.

Las referencias usan el identificador de colaboración. Compensación mantiene `NOT_AVAILABLE` con `resourceId: null` mientras Billing no la suministre. Desde el bloque 09, Performance publica `AVAILABLE` y el UUID de su reporte cuando existe; esto no garantiza que haya métricas automáticas y puede incluir únicamente evidencia manual. La consulta `/api/v1/collaborations/{id}/metrics` detalla esa disponibilidad. No se exponen pagos ficticios ni ceros como métricas supuestas.

La cronología registra firmas recuperables, activación, subidas, entregas/revisiones, vencimientos, finalización e incidencias/resoluciones. Cada entrada incluye secuencia, tipo, actor cuando se conoce, fecha, origen y detalles relevantes. Los reintentos y las lecturas no duplican hechos.

Al arrancar se recupera por lotes el historial anterior desde las firmas, outbox y metadatos conservados. Los hechos reconstruidos llevan `reconstructed: true`; los nuevos eventos se registran transaccionalmente. No se inventan actores o fechas ausentes ni se altera el acuerdo. La recuperación puede repetirse o ejecutarse concurrentemente sin duplicación.

## Integración, migración y demo

La migración nueva **V12** añade incidencias, propuestas/decisiones, evidencias, ampliaciones, historial y contador de bloqueos/fecha de cancelación. Mantiene los snapshots, revisiones originales, eventos y recibos previos. No modifica migraciones anteriores.

Los eventos incluyen `IncidentOpened`, `ResolutionProposed`, `ResolutionRejected`, `ResolutionWithdrawn`, `IncidentResolved`, `CorrectionWindowExtended`, `CollaborationDisputed`, `CollaborationCancelled` y `CollaborationDisputeCleared`. Se conservan en el outbox y utilizan los recibos/reintentos existentes.

`CollaborationExecutionFacade` publica hechos para los futuros consumidores: perfiles participantes, condiciones de compensación, cumplimiento, bloqueos y fechas. `findForUpdate` requiere una transacción del consumidor y bloquea la colaboración hasta su confirmación. Billing deberá comprobar la elegibilidad actual dentro de su transacción, además de deduplicar eventos; una notificación antigua no autoriza por sí sola un pago. Ningún consumidor necesita importar repositorios o agregados internos de Collaboration.

Con el seed explícito habilitado, se añade una incidencia demo resuelta y aceptada por ambas cuentas, después de las entregas demo. Conserva la entrega pendiente de revisión y permite mostrar el historial de negociación. Si existen incidencias, no cambia sus propuestas, decisiones ni bloqueos.

## Verificación

Pruebas de dominio y recorridos HTTP con ambos roles: ventana de siete días, firma de contraparte, rechazo/retiro, incidencias múltiples, cancelación y finalización histórica, ampliaciones y propuestas antiguas, concurrencia, reintentos, rollback de ampliación/auditoría, archivos privados, historial vacío/existente y reconstrucción concurrente, contratos internos, seed, V11→V12 y Swagger.

Resultado del 9 de noviembre de 2026: **273 pruebas aprobadas** en la suite completa con MySQL 8.4/Mailpit, sin fallos, errores ni omisiones; las pruebas dedicadas de actualización utilizan H2. Se repitió con éxito la verificación de privacidad del historial con MySQL. La regresión completa con H2 finalizó sin fallos: 272 aprobadas y una prueba de correo omitida por requerir Mailpit. Se verificaron los diez endpoints nuevos en Swagger y los servicios aislados de prueba se detuvieron al finalizar.

El bloque corresponde al backend. El consumo desde Android y el despliegue Azure se verifican en sus etapas correspondientes.
