# Performance: atribución e interpretación — bloque 10

Implementa US-29 escenarios 4–5 y creación/consulta de atribución de TS-07, complementando [métricas y evidencia del bloque 09](performance-contracts.md). Los mecanismos pertenecen a una colaboración, no a toda la campaña ni a una persona visitante. Billing 06–08 permanece pendiente.

## Rutas y permisos

Prefijo `/api/v1`. Todas requieren JWT y una cuenta activa, salvo GET/HEAD del enlace público.

| Método y ruta | Permiso y respuesta |
| --- | --- |
| GET /attribution/destinations | Cuenta activa; 200 con los IDs y URLs aprobados en configuración. |
| POST /collaborations/{id}/attribution | Empresa participante; 201 con mecanismo y replayed. |
| GET /collaborations/{id}/attribution | Ambos participantes; 200 con periodo, fuentes, totales, mecanismos y advertencia. |
| POST /attribution/{id}/status | Empresa participante; 200, requestId y expectedVersion obligatorios. |
| POST /attribution/{id}/interactions | Empresa participante; 201, declaración respaldada por una evidencia de la colaboración. |
| GET /attribution/{id}/interactions | Ambos participantes; 200, auditoría paginada y origen de cada interacción. |
| GET /public/attribution/{token} | Público; 302 al destino fijo y registro deduplicado del clic. |
| HEAD /public/attribution/{token} | Público; 302 al mismo destino, sin registrar clic ni generar cookie. |

La paginación usa page desde 0 y size de 1 a 100; respuesta items, total, page y size. Los datos privados no pueden consultarse usando el token público.

## Crear y compartir

LINK:

```json
{
  "requestId": "<uuid estable de la operación>",
  "kind": "LINK",
  "label": "Visitas a la promoción",
  "destinationId": "demo"
}
```

CODE:

```json
{
  "requestId": "<uuid>",
  "kind": "CODE",
  "label": "Código de la colaboración"
}
```

El servidor genera el token aleatorio del enlace o un código CP- de doce caracteres. El cliente no puede elegir token/código ni enviar destination, URL, estado o marca de demostración. LINK devuelve trackingUrl absoluto; CODE devuelve code y carece de URL/destino. Ambos se comparten con el creador mediante la consulta privada.

La empresa selecciona un destinationId aprobado por el operador del backend. La URL completa, incluyendo ruta y query si estuvieran configuradas, queda fija en el mecanismo. El enlace público rechaza query parameters, nunca acepta un destino del visitante y no consulta remotamente la URL. Si el ID se retira o su URL cambia en configuración, los enlaces anteriores dejan de redirigir. Esto evita convertir CollabPro en un redirector de URLs elegidas por cualquier usuario. Referencia de diseño: [OWASP Unvalidated Redirects and Forwards](https://cheatsheetseries.owasp.org/cheatsheets/Unvalidated_Redirects_and_Forwards_Cheat_Sheet.html).

El estado persistido es ACTIVE/INACTIVE. availability distingue AVAILABLE, INACTIVE, COLLABORATION_CANCELLED y DESTINATION_UNAVAILABLE. Crear o registrar resultados no requiere ni simula un pago.

Cambiar estado:

```json
{
  "requestId": "<uuid>",
  "expectedVersion": 0,
  "status": "INACTIVE"
}
```

El destino, token, código y pertenencia no se editan; para otro destino se crea otro mecanismo. Desactivar conserva su historial. Una colaboración completada puede seguir recibiendo resultados; una cancelada bloquea nuevas operaciones y sus enlaces públicos responden 410.

## Clics registrados por CollabPro

El enlace público responde 302 con Location fijo, Cache-Control: no-store y Referrer-Policy: no-referrer. No publica UUID de colaboración, participantes, evidencias ni contadores.

La primera visita normal genera una cookie opaca de sesión, HttpOnly y SameSite=Lax; Secure depende del origen público HTTPS configurado. El par mecanismo/sesión deduplica visitas repetidas de ese navegador durante su sesión. El hash de deduplicación se guarda; la cookie original, IP, user-agent e identidad visitante no se persisten.

Un cliente HTTP puede enviar Idempotency-Key UUID estable para reintentos: con la misma clave y mecanismo se conserva un solo clic, también ante solicitudes simultáneas. Debe seguir usando esa clave durante el reintento. Las sesiones de navegador y las claves HTTP constituyen estrategias distintas de deduplicación; no deben alternarse para representar el mismo hecho.

HEAD y las solicitudes identificadas como prefetch mediante Purpose o Sec-Purpose redirigen sin incrementar contadores. No se afirma que otros bots puedan distinguirse de personas. Dos navegadores, cookies eliminadas o distintas claves pueden producir interacciones diferentes; los contadores no son personas únicas.

## Consultas y usos de código declarados

Solo la empresa participante puede registrar:

- INQUIRY: consulta declarada y respaldada por evidencia, para LINK o CODE.
- CODE_REDEMPTION: uso declarado de un CODE; no realiza descuentos, cobros ni certifica una compra.

```json
{
  "requestId": "<uuid de la solicitud>",
  "eventId": "<uuid estable del hecho de negocio>",
  "type": "CODE_REDEMPTION",
  "occurredAt": "<instante ISO-8601 entre creación del mecanismo y recepción>",
  "evidenceId": "<uuid de evidencia manual de esta colaboración>"
}
```

eventId identifica el mismo hecho incluso si cambia requestId. Reutilizarlo para otro mecanismo o payload dentro de esa colaboración devuelve 409; repetir el mismo hecho devuelve la interacción original y no suma otra. No deben generarse nuevos eventId al reintentar. Este identificador evita incluir nombres, teléfonos u otros datos de clientes.

No se admite declarar CLICK, CONVERSION, importes ni ventas verificadas. La evidencia debe pertenecer al mismo reporte, no estar REJECTED y tener la misma condición demostrativa que el mecanismo. Su fecha/periodo se valida al registrarla en Performance.

Las declaraciones tienen origen BRAND_DECLARATION y mantienen evidencia/autor/fecha de registro. Si la evidencia es rechazada posteriormente, el registro sigue en auditoría con includedInTotals=false y evidenceStatus=REJECTED, pero queda excluido de los totales. Una revisión VERIFIED no transforma la declaración en dato automático.

## Consulta e interpretación

GET /collaborations/{id}/attribution acepta start y end opcionales: enviar ambos juntos, inicio ≤ fin ≤ hora del servidor. El intervalo es [start,end), con fin exclusivo, aplicado a occurredAt. Sin filtro se usa desde la creación del reporte hasta la consulta. No hay periodo futuro ni se usa la fecha de captura de una métrica social para atribuir clics.

El resultado contiene:

| Campo | Significado |
| --- | --- |
| availability | NO_MECHANISM, NO_INTERACTIONS o AVAILABLE para datos ordinarios. |
| period / periodEndBoundary | Intervalo de referencia y EXCLUSIVE. |
| totals | Clics, consultas y usos de código ordinarios, sin sumar demostraciones. |
| demonstrationTotals | Interacciones de mecanismos explícitamente demostrativos. |
| mechanisms | Página de mecanismos, sus estados y contadores del mismo periodo. |
| sources | CLICK → FIRST_PARTY_TRACKING; INQUIRY/CODE_REDEMPTION → BRAND_DECLARATION. |
| isEstimate | Siempre true, también cuando existen registros efectivos. |
| verifiedSalesAvailable / uniquePeopleMeasured | Siempre false. |
| warning | Advertencia explícita de estimación y límites de causalidad. |

Los totales se calculan sobre todo el reporte y periodo, independientemente del tamaño/página de mecanismos. Cada interacción se cuenta una sola vez en su tipo; no se suman consultas y usos de código para presentarlos como ventas ni se mezclan con vistas/likes de redes sociales. Las lecturas de resultados bloquean brevemente el reporte para mantener coherencia con mutaciones y revisiones concurrentes.

El registro privado de interacciones ofrece type, source, occurredAt, recordedAt y evidencia/estado. Omite hashes internos y cualquier identificador de sesión visitante.

La asociación verificable es entre solicitud del enlace o declaración registrada y ese mecanismo de esa colaboración. No verifica una persona real, venta, pago, conversión comercial ni causalidad exclusiva. No se aplican tasas de conversión inventadas, ROI ni coste por adquisición mientras no existan fuentes que los respalden.

## Errores y consistencia

| HTTP | Casos |
| --- | --- |
| 400 | Campos/UUID/periodo inválidos; destino no aprobado; tipo de declaración incompatible; intento de alterar la URL pública. |
| 401 / 403 | Sin JWT válido / recurso ajeno, rol incorrecto o cuenta no activa. |
| 404 | Recurso o token no encontrado. |
| 409 | Clave/evento reutilizado con otro payload, versión antigua, colaboración cancelada o evidencia no disponible. |
| 410 | Mecanismo desactivado, destino retirado o enlace de colaboración cancelada. |

requestId se conserva por actor/reporte usando los comandos de Performance. El bloqueo del reporte serializa creación, declaraciones, revisiones y clics; índices únicos impiden repetir tokens/códigos o claves de eventos. El fallo del registro de comando revierte también el mecanismo/interacción. Las repeticiones de hechos ya registrados recuperan el recurso sin crear nuevas interacciones; un enlace público inactivo siempre deja de redirigir.

V14 añade tablas propias de mecanismos/interacciones, sin modificar migraciones anteriores. La actualización desde V13 conserva métricas, evidencias y archivos; no fabrica interacciones para datos históricos.

## Configuración, demo y despliegue

Variables:

```text
PERFORMANCE_ATTRIBUTION_DESTINATIONS=demo=https://example.com/collabpro-demo
PERFORMANCE_ATTRIBUTION_PUBLIC_BASE_URL=http://localhost:8081
```

La primera es una lista separada por comas de id=URL exacta. Los IDs admiten minúsculas, dígitos y guiones; las URLs requieren HTTPS, sin credenciales, fragmentos ni IP literal. Las comas que formen parte de una URL deben codificarse. La lista local/test incluye demo; fuera del perfil local no se habilitan destinos automáticamente. Los códigos siguen disponibles con lista vacía.

El origen público admite HTTP/HTTPS, host y puerto opcional, sin ruta, query o credenciales. Se configura explícitamente y no se deriva de Host ni X-Forwarded-Host del visitante. Para la VM Azure indicada en el proyecto: `PERFORMANCE_ATTRIBUTION_PUBLIC_BASE_URL=http://40.75.23.68:8081`, mientras ese sea el origen publicado. Los destinos empresariales reales deben configurarse por separado; example.com es solo demostración.

El seed optativo añade un CODE demostrativo y, cuando existe el destino demo, un LINK demostrativo. Registra un uso de código únicamente contra evidencia también demostrativa; no inventa clics, consentimientos, ventas o pagos. El resultado queda en demonstrationTotals y no en totals. Reiniciar conserva estados, revisiones y hechos existentes.

Recorridos y compatibilidad móvil: [backend-validation-and-mobile-contracts.md](backend-validation-and-mobile-contracts.md). Capacidades externas y propuesta de medición: [ss03-metrics-feasibility.md](ss03-metrics-feasibility.md).

Verificación al cierre del bloque 10: 318 casos aprobados con MySQL/Mailpit aislados; regresión H2 completa con 317 aprobados y una prueba de correo omitida. Sin fallos ni errores. Incluye el recorrido completo para ambos roles, concurrencia, rollback, periodos, fuentes, privacidad, actualización V13→V14, seed y documentación OpenAPI.
