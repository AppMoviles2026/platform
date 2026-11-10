# SS-03: capacidades y límites de métricas

Estado al 9 de noviembre de 2026: adaptador TikTok implementado y probado con HTTP controlado, evidencia manual implementada para cualquier plataforma. **La validación con cuentas externas reales continúa pendiente.** No se declara concluido el spike completo de SS-03; atribución y estimaciones corresponden al bloque 10.

## Evidencia documental y alcance

| Proveedor | Capacidad implementada | Condiciones y límites |
| --- | --- | --- |
| TikTok Display API | Consulta autorizada de un video y sus contadores disponibles: vistas, likes, comentarios, compartidos. | Requiere video.list y consentimiento del titular; el endpoint comprueba que el video pertenece al usuario autorizado. |
| Instagram | Evidencias manuales, fuente MANUAL; actualización automática informa UNSUPPORTED_PROVIDER. | Este bloque no incluye un adaptador de Instagram Insights. El permiso de perfil no prueba acceso a estadísticas; requiere validar producto de Meta, tipo de cuenta y permisos vigentes antes de implementarlo. |

Fuentes oficiales consultadas para TikTok:

- [Query Videos](https://developers.tiktok.com/docs/en/tiktok-api-v2-video-query): endpoint y pertenencia de los videos, scope requerido y campos solicitables.
- [Video Object](https://developers.tiktok.com/docs/en/tiktok-api-v2-video-object): definición de create_time y contadores.

La implementación utiliza POST `https://open.tiktokapis.com/v2/video/query/` con filters.video_ids y fields explícitos. No obtiene estadísticas generales del perfil ni asume métricas que el video no devuelve.

Los valores son contadores acumulados observados en una consulta. El periodo se identifica como LIFETIME, desde la creación del video hasta la consulta; no se afirma que correspondan exclusivamente a la campaña. No se suman capturas ni se equipara engagement a ventas verificadas.

## Configuración y recorrido externo pendiente

1. Registrar/configurar la aplicación TikTok con las variables OAuth de Identity: TIKTOK_CLIENT_KEY, TIKTOK_CLIENT_SECRET, TIKTOK_REDIRECT_URI.
2. Solicitar/obtener el permiso video.list. Configurar `TIKTOK_SCOPES=user.info.basic,video.list` y volver a autorizar al creador; cambiar la variable no amplía consentimientos existentes.
3. Usar un video propio accesible por esa autorización y registrar su enlace canónico como contenido de una entrega.
4. Consultar /social-accounts/{socialId}/access y pedir la actualización de Performance con los UUID reales de red, colaboración y versión.
5. Comprobar con ambos participantes fuente, periodo, contadores realmente devueltos, ausencia de datos y reautorización. Guardar evidencia sin tokens ni secretos.
6. Registrar aceptación/rechazo del consentimiento, renovación y respuesta real del proveedor. La disponibilidad depende también de la configuración y aprobación de la aplicación externa.

No se alteran los scopes por defecto de Identity ni se simula consentimiento en el seed. La vinculación previa de perfil básico puede seguir funcionando y Performance responderá INSUFFICIENT_SCOPE hasta autorizar video.list.

Para Instagram, realizar una validación separada con documentación oficial de Meta y una cuenta admitida por el producto elegido antes de añadir llamadas a Insights. Hasta entonces la alternativa funcional es adjuntar evidencia manual identificada y revisable.

## Verificado localmente

- Contrato HTTP fijo de TikTok, bearer solo dentro de infraestructura, filters.video_ids y scope video.list.
- Renovación de credenciales vencidas mediante el ACL real de Identity y uso del token renovado en la consulta.
- Permisos insuficientes, consentimiento no renovable y proveedor no configurado impiden la consulta.
- HTTP 401/403/429/503, video ausente/ajeno, contadores ausentes/negativos/fraccionarios y fechas inválidas.
- Reintentos, concurrencia, conservación de capturas, fuente/periodo, evidencia manual y permisos por colaboración.

Estas comprobaciones usan respuestas HTTP controladas; no son capturas ni aprobaciones de producción. Las verificaciones externas de SS-02/SS-03 y el despliegue Azure permanecen diferenciados de las pruebas locales.
