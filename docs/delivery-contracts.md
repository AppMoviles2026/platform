# Collaboration: bloque 04 — entregas y revisión

Implementa US-23, US-24, los escenarios 3–4 de TS-04 y 1–2 de TS-05, sobre los acuerdos del [bloque 03](collaboration-contracts.md). Se conservan dominio puro, aplicación con puertos, infraestructura y REST. Incidencias/historial global permanecen en el bloque 05; compensaciones y pagos pertenecen a Billing.

## Reglas de ejecución

Una obligación del acuerdo puede exigir varias piezas. Por ejemplo, dos videos generan dos entregas independientes; cada una requiere su propia aprobación. El servidor asigna el número de pieza bajo bloqueo de colaboración y nunca supera la cantidad acordada. Una entrega rechazada sigue ocupando su pieza: se corrige mediante una versión nueva, no creando una pieza adicional.

**Regla confirmada por el usuario:** un rechazo debe incluir observaciones y una nueva fecha límite futura para corregir. La corrección debe llegar antes de ese instante, que es exclusivo. No hay un número arbitrario de rondas: cada nuevo rechazo puede fijar otro plazo. Una revisión aprobada es definitiva; no se modifica ni se reemplaza mediante estos endpoints.

Las primeras entregas se admiten también después de su fecha original y quedan marcadas con `late=true`. Las correcciones conservan esa referencia a la fecha original; la ampliación no reescribe las condiciones ni borra la tardanza. Una corrección presentada una vez vencido su nuevo plazo devuelve `409 CORRECTION_WINDOW_CLOSED`. El tratamiento posterior del desacuerdo corresponde al bloque de incidencias.

El contenido debe tener una referencia y entre una y diez evidencias distintas. Cada referencia contiene exactamente un `url` HTTPS completo o un `assetId` autorizado. Las notas son opcionales, hasta 4000 caracteres. El servidor conserva las referencias y archivos; no descarga enlaces externos ni afirma que una publicación sea auténtica por haber recibido su URL. La empresa contrasta la evidencia con las condiciones antes de aprobar.

## Identificadores y versiones

- `GET /collaborations/{id}` conserva la lista `deliverables` del bloque anterior: sus elementos representan **obligaciones**, y su `deliverableId` se envía como `obligationId` al registrar una pieza.
- Los endpoints de entregas devuelven un **Deliverable** por pieza, con su propio `id`, `obligationId`, `pieceNumber`, `version`, `currentSubmission` y `currentReview`.
- `currentSubmission.id` identifica la versión de contenido; `currentSubmission.revision` aumenta al corregir. El UUID cambia en cada versión.
- Las decisiones requieren el `submissionId` exacto y el `expectedVersion` actual de la pieza. Las correcciones también requieren `expectedVersion`. Una pantalla antigua no puede aprobar contenido nuevo ni sobrescribir una revisión concurrente.
- `snapshot.termsVersion` sigue identificando el acuerdo; no se utiliza como versión de una entrega.

## REST

Rutas bajo `/api/v1`, todas con JWT. Solo los participantes pueden consultar; solo el creador presenta contenido/archivos y solo la marca valida o rechaza.

| Método | Ruta | Uso |
| --- | --- | --- |
| POST | `/collaborations/{id}/deliverables` | Registrar una pieza, 201 con Location. |
| GET | `/collaborations/{id}/deliverables` | Lista paginada propia; filtro `status`. |
| GET | `/deliverables/{id}` | Pieza y versión/revisión actuales, 200. |
| GET | `/deliverables/{id}/versions` | Versiones históricas con su revisión, paginadas y ordenadas por revisión descendente. |
| POST | `/deliverables/{id}/versions` | Nueva versión después de un rechazo, 201. |
| POST | `/deliverables/{id}/validation` | Aprobar la versión pendiente, 200. |
| POST | `/deliverables/{id}/rejection` | Rechazar con observaciones y plazo futuro, 200. |
| POST | `/collaborations/{id}/assets` | Multipart con campo `file` e `Idempotency-Key: UUID`, 201. |
| GET | `/collaboration-assets/{id}` | Metadatos autorizados y ruta de descarga, 200. |
| GET | `/collaboration-assets/{id}/content` | Archivo privado, 200; requiere Authorization también al descargar. |

Las listas usan `page=0` y `size=20` por defecto, con tamaño entre 1 y 100. Estados de entrega: `PENDING_VALIDATION`, `APPROVED` y `REJECTED`. Una lista vacía responde 200 con `items: []`, no 404.

Ejemplo de primera entrega:

```json
{
  "requestId": "UUID-nuevo-por-operacion",
  "obligationId": "UUID-de-la-obligacion",
  "content": {
    "content": { "url": "https://example.com/publicacion" },
    "evidence": [
      { "assetId": "UUID-del-archivo-subido" }
    ],
    "notes": "Contenido publicado y captura de evidencia."
  }
}
```

Para contenido local se puede sustituir la referencia `content.content` por `{"assetId":"UUID"}`. Para evidencia externa se puede enviar `{"url":"https://example.com/captura"}`.

Aprobación:

```json
{
  "requestId": "UUID-nuevo",
  "submissionId": "UUID-de-currentSubmission",
  "expectedVersion": 0,
  "observations": "Cumple las condiciones."
}
```

Rechazo:

```json
{
  "requestId": "UUID-nuevo",
  "submissionId": "UUID-de-currentSubmission",
  "expectedVersion": 0,
  "observations": "Incluir el mensaje del producto acordado.",
  "correctionDeadline": "2030-02-10T18:00:00Z"
}
```

La fecha del ejemplo debe sustituirse por el nuevo plazo real. La corrección usa `requestId`, el nuevo `expectedVersion` obtenido tras el rechazo y un objeto `content` completo igual al de la primera entrega.

Las mutaciones JSON devuelven `{deliverable, affectedSubmissionId, replayed}`: la pieza está actualizada al momento de consultar; `affectedSubmissionId` conserva la versión sobre la que actuó esa solicitud. Así, repetir un rechazo antiguo después de una corrección no vuelve a rechazar la versión nueva.

## Reintentos y concurrencia

Cada operación lleva un `requestId` UUID; la subida usa ese UUID como encabezado `Idempotency-Key`. Android debe conservarlo para reintentar la **misma** solicitud y generar otro para una operación nueva. La clave está limitada a usuario y colaboración. Reutilizarla con otro cuerpo, objetivo u operación devuelve `409 IDEMPOTENCY_CONFLICT`.

Un reintento válido recupera el resultado aunque la versión ya haya sido revisada o la colaboración haya terminado. No crea otra pieza, revisión, archivo ni evento. Una solicitud nueva con una versión antigua devuelve 409; el cliente debe recargar, mostrar el cambio y pedir una nueva decisión.

Las escrituras mantienen `READ_COMMITTED`, bloqueo del padre y versiones de pieza. Entrega/versión, revisión, progreso del padre, registro de idempotencia y evento se confirman en una transacción. Un fallo revierte todo; repetir después con la misma clave puede completar la operación.

## Estado y tareas pendientes

El resumen de colaboración añade cantidades presentadas, pendientes de validación y rechazadas, más `completedAt`. Cada obligación conserva su fecha original y muestra el avance, el plazo de corrección más próximo y `nextActionDeadline`.

- Falta una pieza o corrección: su fecha limita la siguiente acción del creador.
- Todas las piezas están presentadas y esperan revisión: `UNDER_REVIEW`, sin vencer por la demora de la empresa en aprobar.
- Vence una acción de entrega/corrección pendiente: `EXPIRED`.
- Una entrega tardía puede pasar a revisión conservando `late` y `expiredAt`.
- Todas las cantidades aprobadas: `COMPLETED` con fecha del servidor. Un rechazo o una pieza sin aprobar impiden completar.
- `CANCELLED` y `DISPUTED` bloquean modificaciones; esos comandos se abordarán en bloques posteriores.

`pendingActions` distingue `SUBMIT_DELIVERABLE`, `SUBMIT_LATE_DELIVERABLE`, `CORRECT_DELIVERABLE`, `CORRECTION_WINDOW_EXPIRED`, `WAIT_FOR_REVIEW`, `REVIEW_DELIVERABLE`, `WAIT_FOR_DELIVERY` y `WAIT_FOR_CORRECTION`, según el participante.

`availableActions` de colaboración ahora anuncia los comandos implementados: presentar, corregir, subir archivo o revisar cuando corresponda. El detalle de pieza informa `APPROVE_DELIVERABLE`, `REJECT_DELIVERABLE` o `SUBMIT_REVISION` con sus condiciones actuales. Debe consultarse la pieza concreta para conocer su plazo individual.

## Archivos y despliegue

Se aceptan PDF, PNG, JPEG, WebP y MP4, hasta 10 MiB por archivo por defecto. Se comprueban tamaño, nombre, extensión, MIME y firma binaria. Un tipo incompatible devuelve 415 y un archivo demasiado grande, 413. No se aceptan HTML o SVG.

Los binarios y metadatos se guardan en MySQL mediante la misma transacción. Persisten al reiniciar o reconstruir el contenedor y al desplegar con una base persistente; no requieren una carpeta local ni un volumen adicional de archivos.

Antes de presentar una entrega, únicamente el creador puede descargar su archivo. Cuando se incluye en cualquier versión, la marca participante también puede consultarlo. Se conserva el acceso a evidencias históricas tras una corrección. Un archivo de otra colaboración o usuario no puede reutilizarse en la entrega.

La descarga usa JWT, `Content-Disposition: attachment`, `Cache-Control: no-store` y `X-Content-Type-Options: nosniff`. No se entrega un enlace público permanente. Android deberá descargar con su cliente autenticado y compartir el archivo mediante un URI local autorizado si lo abre en otra aplicación.

Límites configurables: `COLLABORATION_MAX_FILE_BYTES` (inspección, bytes), `COLLABORATION_MAX_FILE_SIZE` (multipart, por defecto `10MB`) y `COLLABORATION_MAX_REQUEST_SIZE` (por defecto `11MB`). Al aumentarlos, mantenerlos coherentes con la memoria y los límites de la base de datos; el inspector admite hasta 50 MiB.

## Errores

Se conserva `ApiError(code,message,fields)`.

| HTTP | Casos |
| --- | --- |
| 400 | Falta contenido/evidencia, URL inválida, campos o versiones inválidos, falta observación/plazo, plazo no futuro, falta clave multipart. |
| 401 | Sesión ausente, inválida o vencida. |
| 403 | Rol incorrecto, tercero o `ASSET_NOT_AVAILABLE` para una referencia no autorizada. |
| 404 | Entrega, obligación, colaboración o archivo inexistente. |
| 409 | `QUANTITY_EXCEEDED`, `CONCURRENT_UPDATE`, `STALE_SUBMISSION`, `DELIVERABLE_ALREADY_REVIEWED`, `REVISION_NOT_ALLOWED`, `CORRECTION_WINDOW_CLOSED`, `COLLABORATION_CLOSED`, `IDEMPOTENCY_CONFLICT`. |
| 413 / 415 | Archivo demasiado grande / formato no soportado o incompatible. |

## Migración, eventos y demo

**V11** añade piezas, versiones, revisiones, archivos y referencias, registro de idempotencia y fecha de finalización. Conserva los acuerdos y snapshots previos. La evolución del progreso tolera las obligaciones JSON de V10, que todavía no incluían contadores de entrega.

Se elimina la restricción por agregado/tipo del outbox: una colaboración puede volver a vencer al agotarse un plazo de corrección. Los eventos conservan su UUID único y los recibos por consumidor. No se cambian migraciones antiguas.

Se publican `DeliverableSubmitted`, `DeliverableRevisionSubmitted`, `DeliverableApproved`, `DeliverableRejected` y `CollaborationCompleted`, además de los vencimientos. Los eventos de entrega contienen identificadores de pieza y versión, actor, fecha y snapshot; los eventos de colaboración conservan el payload de condiciones del bloque 03. Aprobar o completar **no confirma un pago**: los consumidores de Billing se implementarán posteriormente.

El seed explícito agrega una entrega para la colaboración demo de la marca y `demo.creator2@collabpro.app`, con rechazo y plazo históricos y una versión corregida pendiente de revisión. Si una base demo tiene acordadas dos o más piezas, presenta una rechazada y otra pendiente; conserva siempre las cantidades del snapshot. Si ya existen entregas, conserva todas sus versiones y decisiones. Los enlaces example.com son ilustrativos, no publicaciones reales. No modifica colaboraciones cerradas ni habilita accesos externos ficticios.

## Verificación

Pruebas de dominio y HTTP para US-23/24, contratos TS-04/05, cantidades, roles/JWT, fechas originales y correcciones, aprobaciones parciales/finales, versiones antiguas, historial y paginación, claves repetidas/conflictivas, concurrencia, rollback, archivos privados/históricos, formatos/tamaño y seed. Se verifican migraciones incrementales y Swagger, con regresión de las funcionalidades anteriores.

Resultado del 9 de noviembre de 2026: **245 pruebas aprobadas** en la suite completa con MySQL 8.4/Mailpit, sin fallos, errores ni omisiones; las pruebas dedicadas de actualización usan H2. La regresión completa con H2 también terminó sin fallos: 244 aprobadas y una prueba de correo omitida por requerir Mailpit. Se verificaron los diez endpoints nuevos y la descarga binaria en OpenAPI. Los servicios de prueba aislados se detuvieron al finalizar.

Android y el despliegue Azure se actualizan por separado; este bloque implementa y documenta el backend.
