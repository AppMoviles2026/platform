# Campaign: bloque 02

## US-15 y US-16: borradores y condiciones

`GET /api/v1/campaigns/{id}` ahora incluye `version`. `PATCH /api/v1/campaigns/{id}` admite `title`, `objective`, `description`, `category`, `targetAudience`, `location` y requiere `expectedVersion`. Los campos omitidos o nulos no cambian; una cadena vacía permite limpiar los campos opcionales. Se validan todos los valores antes de escribir. No permite cambiar dueño ni estado y solo admite borradores.

Ejemplo:

```json
{"title":"Nuevo título","expectedVersion":1}
```

`PUT /api/v1/campaigns/{id}/conditions` acepta ahora `expectedVersion`. Una versión antigua devuelve 409 `CONCURRENT_UPDATE` sin cambios parciales. El servidor serializa la comprobación con el guardado; las respuestas de escritura contienen la versión persistida. Las condiciones y los metadatos quedan congelados tras publicar.

Compatibilidad: la versión de condiciones continúa siendo opcional para el Android actual. Enviar siempre la última versión desde los nuevos consumidores; sin ella, una petición heredada no puede detectar que su pantalla estaba desactualizada. Este endurecimiento no se introduce silenciosamente. Los nombres de marca almacenados en campañas siguen representando el snapshot de creación; las consultas de postulantes usan su perfil actual.

## US-19: postulaciones propias

Se mantienen envío, consulta propia, PUT y cancelación existentes, incluyendo reglas, confirmaciones por UUID e idempotencia. La versión de las operaciones heredadas sigue siendo opcional para conservar sus contratos; Android ya la envía.

Se añade `PATCH /api/v1/applications/{id}` con `message` y `expectedVersion` obligatorios, coherente con TS-03. Una petición antigua devuelve 409. Solo admite una postulación pendiente del creador autenticado. Selección, rechazo y cancelación impiden modificaciones posteriores. La consulta propia incluye `decidedAt`, `decidedBy` (cuenta empresarial que tomó la decisión) y `decisionReason`; pueden ser nulos en datos históricos sin auditoría.

El reporte repite el número 4 en US-19: en la trazabilidad de pruebas se consideran cinco escenarios independientes: envío, duplicado, requisito incumplido, edición y cancelación.

## US-20: evaluación empresarial

Rutas protegidas por JWT de empresa activa y propiedad de campaña:

| Método | Ruta | Uso |
| --- | --- | --- |
| GET | `/api/v1/campaigns/{id}/applications` | Lista paginada, con `page`, `size` y filtro opcional `status`. |
| GET | `/api/v1/campaigns/{id}/applications/{applicationId}` | Detalle de postulación de esa campaña y datos autorizados del creador. |
| POST | `/api/v1/campaigns/{id}/applications/{applicationId}/decision` | Seleccionar o rechazar una postulación pendiente. |

```json
{"decision":"REJECTED","reason":"La propuesta no coincide con el objetivo.","expectedVersion":0}
```

Las decisiones admitidas son `SELECTED` y `REJECTED`. El rechazo requiere un motivo no vacío, de hasta 2000 caracteres. La selección permite una nota opcional. Se registran decisión, actor y hora del servidor de manera atómica con la versión.

Las respuestas empresariales contienen `application` y `creator`: este último incluye nombre, biografía, nicho, audiencia, ubicación, actividad y referencias públicas de redes activas. Puede ser nulo si un registro histórico ya no tiene perfil resoluble. El contrato interno de Identity se consume desde un adaptador de infraestructura; no se consultan repositorios de Identity desde dominio o aplicación de Campaign.

Una lista vacía devuelve 200 con `items: []`. No se exponen credenciales ni correo. La postulación debe pertenecer a la campaña indicada; un ID de otra campaña devuelve 404. Una empresa ajena o un creador no pueden evaluar. El cierre de recepción no elimina las postulaciones ni impide revisarlas.

Una decisión repetida o basada en versión antigua devuelve 409. Dos decisiones concurrentes, o decisión contra edición/cancelación, no pueden sobrescribirse silenciosamente: solo una operación con la misma versión puede confirmar su cambio. El creador consulta el resultado mediante sus endpoints existentes; no se añade un sistema de push.

Seleccionar no crea una colaboración, no formaliza aceptación bilateral y no rechaza a los demás postulantes. Esas responsabilidades comienzan en el bloque 03.

## Persistencia y demo

V9 agrega auditoría e índice de evaluación y un marcador de modificación de campaña que asegura el incremento inmediato de su versión también cuando solo cambian condiciones hijas. No se editan migraciones anteriores. Una reparación de datos está limitada a los tres IDs conocidos de postulaciones demo antiguas que almacenaban el ID de cuenta como ID de creador. Si ya existe una postulación real del perfil correcto para esa campaña, se conserva y no se fuerza una reparación que produzca un duplicado; el registro demo histórico queda intacto.

También se corrigen únicamente los cinco IDs conocidos de campañas demo cuyo propietario guardaba el ID de cuenta en lugar del perfil empresarial.

El seed conserva registros existentes y añade dos postulaciones evaluadas, una seleccionada y otra rechazada. No crea acuerdos ni pagos ficticios. La base habitual y las cuentas demo no se alteran durante la suite: `scripts/test-mysql.ps1` utiliza los servicios aislados de `compose.test.yaml`.

## Verificación

La suite incluye regresión de US-15 a US-19, casos de PATCH, condiciones antiguas, autorización y listas vacías/paginadas, selección/rechazo, auditoría visible al creador, aislamiento entre campañas, concurrencia con cancelación y validaciones atómicas del dominio. Ejecutar `./mvnw.cmd test` o `./scripts/test-mysql.ps1` para MySQL y Mailpit.
Resultados del 8 de noviembre de 2026: se ejecutó regresión completa y se repitieron los grupos afectados por los ajustes finales de rutas, migración y seed. Las últimas ejecuciones registran 198 casos distintos aprobados, sin fallos, errores ni omisiones, con MySQL y Mailpit aislados (las pruebas específicas de actualización usan H2 dedicado). Se verificaron además los límites DDD y los endpoints de Swagger. Los servicios de pruebas se detuvieron al terminar. No se ejecutaron comandos de Git.

