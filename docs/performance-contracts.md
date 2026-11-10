# Performance: métricas y evidencias — bloque 09

Implementación de US-29, escenarios 1–3, y consulta de métricas/registro de evidencia de TS-07. Atribución, enlaces/códigos, clics y estimaciones comerciales quedan en el bloque 10. Billing 06–08 sigue pendiente. Android deberá consumir estas rutas posteriormente.

## Modelo y límites DDD

Performance posee su reporte por colaboración, publicaciones vinculadas a versiones de entregas, capturas automáticas, intentos de actualización, evidencias manuales, revisiones y archivos privados. Se comunica mediante los contratos publicados de Identity y Collaboration; no consulta sus tablas ni importa sus repositorios. Los archivos de evidencia permanecen en MySQL y sobreviven a reinicios del contenedor.

La migración nueva V13 crea exclusivamente tablas de Performance; las migraciones anteriores no cambian. No transforma datos anteriores ni inventa métricas para colaboraciones existentes. La inspección técnica de archivos se comparte con Collaboration; los permisos y asociaciones siguen dentro de cada contexto.

## Rutas

Prefijo `/api/v1`. Todas requieren JWT y una cuenta activa. Solo la empresa y el creador de esa colaboración pueden consultar sus datos.

| Método y ruta | Resultado |
| --- | --- |
| GET /collaborations/{id}/metrics | 200: reporte, publicaciones paginadas, última captura, último intento y cantidad de evidencias manuales. |
| POST /collaborations/{id}/metrics/refresh | 200: consulta autorizada de una publicación y estado explícito del proveedor. |
| GET /collaborations/{id}/metrics/snapshots | 200: capturas históricas paginadas, sin sumar contadores acumulados. |
| POST /collaborations/{id}/evidence | 201: evidencia manual del creador; también en reintento idéntico. Location apunta a su consulta. |
| GET /collaborations/{id}/evidence | 200: evidencias paginadas; filtro opcional status. |
| GET /performance-evidence/{id} | 200: evidencia, procedencia, periodo y revisión. |
| POST /performance-evidence/{id}/verification | 200: revisión empresarial con expectedVersion. |
| POST /collaborations/{id}/evidence-assets | 201: archivo multipart privado; cabecera Idempotency-Key UUID. |
| GET /performance-assets/{id} | 200: metadatos y ruta de descarga protegida. |
| GET /performance-assets/{id}/content | 200: bytes originales, attachment, no-store y nosniff. |

La paginación usa `page` desde 0 y `size` de 1 a 100; respuesta `items, total, page, size`.

## Métricas automáticas

Ambos participantes pueden solicitar una actualización. Body:

```json
{
  "requestId": "<uuid de la operación>",
  "socialId": "<uuid de la red vinculada al creador>",
  "submissionId": "<uuid de una versión actual de entrega>",
  "postId": "<identificador del video>"
}
```

La red debe pertenecer al creador de esa colaboración y estar activa. La versión debe pertenecer a una entrega de esa colaboración, ser actual y no estar rechazada; se admiten versiones pendientes de validación o aprobadas. Un archivo sin enlace publicado permite evidencia manual, pero no una consulta automática de publicación.

TikTok requiere el enlace canónico `https://www.tiktok.com/@usuario/video/ID` o el mismo dominio sin www; ID debe coincidir con postId. No se siguen enlaces cortos ni redirecciones. Se utiliza únicamente el endpoint oficial fijo, que comprueba que el video pertenece al usuario autorizado. No se extraen métricas de enlaces arbitrarios ni mediante scraping.

El adaptador solicita `video.list` mediante el ACL de Identity. Identity comprueba titularidad, permisos y vencimiento y renueva las credenciales cuando es posible. El token se usa solo durante la llamada; no aparece en respuestas, capturas, logs ni archivos.

Se conservan VIEWS, LIKES, COMMENTS y SHARES cuando el proveedor realmente los devuelve como enteros no negativos. Un contador ausente se omite; cero se conserva solo si el proveedor devolvió cero. No se calculan alcance, impresiones, engagement, ventas o conversiones a partir de otros contadores.

Cada captura informa `sourceType: AUTOMATED`, proveedor, publicación, versión de entrega, `capturedAt` y periodo. `periodKind: LIFETIME` significa acumulado desde el create_time informado por TikTok hasta el instante de consulta, no una ventana de campaña ni una diferencia entre capturas.

| Estado del intento | Significado |
| --- | --- |
| AVAILABLE | Captura obtenida con valores y periodo válidos. |
| NO_DATA | El proveedor no devolvió video o contadores disponibles. |
| AUTHORIZATION_REQUIRED | Falta autorización utilizable o se requiere nuevo consentimiento. |
| INSUFFICIENT_SCOPE | Falta video.list u otro permiso requerido. |
| PROVIDER_NOT_CONFIGURED | Falta configuración necesaria del proveedor. |
| UNSUPPORTED_PROVIDER | No hay un adaptador automático implementado para esa red. |
| RATE_LIMITED | El proveedor limitó las solicitudes. |
| PROVIDER_UNAVAILABLE | Error HTTP, conexión o disponibilidad del proveedor. |
| INVALID_RESPONSE | Identidad del video, fechas o contadores incompatibles con el contrato. |

Estos estados devuelven 200 porque representan el resultado consultable de la actualización. Solo AVAILABLE genera una captura. Para volver a intentar una consulta fallida se genera un requestId nuevo; repetir el anterior recupera el intento ya registrado.

GET no llama al proveedor. Sin datos responde `automaticAvailability: NO_DATA`, cantidad cero de publicaciones con captura válida y páginas vacías; no necesita crear un reporte. Una caída posterior conserva la captura anterior: la publicación indica AVAILABLE_STALE y el último intento explica el fallo. Una versión rechazada o reemplazada conserva su evidencia histórica con HISTORICAL_ONLY y deja de aportar disponibilidad actual.

Las capturas son acumuladas: no se suman entre sí. Una misma publicación vinculada a varias versiones conserva una sola identidad en el reporte y asociaciones auditables, evitando doble conteo. La secuencia transaccional ordena intentos/capturas incluso si coinciden sus timestamps.

## Evidencia manual

Solo el creador participante registra evidencia. Ejemplo ilustrativo:

```json
{
  "requestId": "<uuid>",
  "submissionId": "<uuid opcional de una versión de entrega>",
  "description": "Captura de estadísticas de la publicación",
  "references": [
    {"assetId": "<uuid de un archivo propio de esta colaboración>"},
    {"url": "https://example.com/estadisticas"}
  ],
  "claimedMetrics": {"VIEWS": 125, "LIKES": 10},
  "observedAt": "2026-11-09T10:00:00Z",
  "period": {
    "start": "2026-11-08T10:00:00Z",
    "end": "2026-11-09T10:00:00Z"
  }
}
```

Se necesitan descripción de hasta 4000 caracteres, entre una y diez referencias distintas y fecha/periodo coherentes: inicio ≤ fin ≤ observación ≤ recepción por servidor. Cada referencia contiene exactamente URL HTTPS sin credenciales o assetId, nunca ambos. Los archivos deben ser propios y del mismo reporte; no se aceptan los de otra colaboración/contexto. Los enlaces son referencias declaradas y no se descargan ni se validan como estadísticas reales del proveedor.

claimedMetrics es opcional; solo admite tipos conocidos y enteros no negativos representables como Long. Los valores son declaraciones del creador, no mediciones automáticas. El servidor asigna `sourceType: MANUAL`, estado UNVERIFIED y `demonstration: false`; el cliente no puede suplantarlos.

La empresa participante puede revisar una vez con:

```json
{
  "requestId": "<uuid>",
  "expectedVersion": 0,
  "decision": "VERIFIED",
  "observations": "Revisada contra la captura adjunta"
}
```

REJECTED exige observaciones de hasta 2000 caracteres. VERIFIED significa revisión manual por la empresa: la fuente continúa siendo MANUAL, los contadores no pasan a las métricas automáticas y no certifica al proveedor. Se conserva la evidencia original y se registra autor/fecha/versión de revisión. Para aportar una aclaración se crea otra evidencia.

Los archivos se cargan como multipart, parte `file`: PDF, PNG, JPEG, WebP o MP4 con firma, extensión y MIME compatibles, máximo 10 MiB por defecto. Antes de asociarlos a una evidencia solo su propietario puede descargarlos; al asociarlos pueden acceder ambos participantes. Nunca tienen un enlace público.

## Estados, concurrencia y errores

Se admiten registros de resultados después de completar una colaboración y mientras exista una incidencia, sin aprobar entregas, desbloquear compensaciones ni modificar el estado de Collaboration. Cancelar impide nuevas escrituras; las lecturas y los reintentos idénticos de operaciones ya registradas siguen disponibles.

requestId/Idempotency-Key se identifica por reporte y actor. Una repetición idéntica recupera el recurso; reutilizar la clave con otro payload u operación devuelve 409. El bloqueo del reporte serializa mutaciones, llamada al proveedor y registro del resultado; la llamada externa tiene límites de conexión/lectura de cinco segundos. El bloqueo inicial de Collaboration solo protege la creación de un reporte único y termina antes de la llamada externa. La revisión exige expectedVersion para impedir sobrescrituras.

| HTTP | Casos |
| --- | --- |
| 400 | UUID/paginación/cuerpo inválido; periodos futuros/invertidos; métricas negativas/fraccionarias; rechazo sin observaciones. |
| 401 | Falta JWT válido. |
| 403 | Recurso ajeno, rol incorrecto, cuenta no activa o archivo no disponible para esa evidencia. |
| 404 | Colaboración, evidencia o versión no encontrada en el alcance solicitado. |
| 409 | Colaboración cancelada, fuente histórica/sin publicación, postId incompatible, clave reutilizada, versión antigua o revisión ya realizada. |
| 413 / 415 | Archivo demasiado grande / formato incompatible. |

Los errores mantienen ApiError. La consulta privada del historial de Collaboration obtiene el identificador del reporte mediante PerformanceReportFacade cuando existe; AVAILABLE en esa referencia significa reporte existente, no garantiza métricas automáticas. Billing continúa NOT_AVAILABLE.

## Demo y comprobaciones

El seed optativo se ejecuta después de los seeds anteriores y añade una evidencia manual explícitamente demostrativa a la colaboración de demo.creator2. Usa valores ilustrativos y example.com, UNVERIFIED y demonstration=true. Nunca crea contadores automáticos, consentimientos ni credenciales de proveedor. Al reiniciar no modifica evidencia/revisión existente.

Las pruebas cubren invariantes, API con JWT y ambos roles, permisos por recurso, referencias y archivos privados, revisiones concurrentes, reintentos simultáneos con una sola consulta, fallos conservando capturas, versiones históricas, origen manual, seed y actualización V12→V13. Las pruebas del adaptador usan HTTP controlado y el ACL real de Identity para verificar renovación/permisos. No acreditan mediciones con cuentas externas reales. Ver [validación de capacidades y límites SS-03](ss03-metrics-feasibility.md).

Verificación final: 300 casos aprobados con MySQL 8.4/Mailpit aislados (regresión completa más comprobación final de Performance/seed/DDD); regresión H2 completa con 299 aprobados y una prueba de correo omitida por requerir Mailpit. Sin fallos ni errores. Los contenedores de prueba se detuvieron conservando sus datos; no se alteró la base normal de la aplicación.
