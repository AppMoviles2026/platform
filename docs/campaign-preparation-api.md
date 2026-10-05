# Campaign — US-15 y US-16

Backend implementado dentro de Campaign Management. Fuentes: escenarios de US-15/16 y modelo táctico 2.6.2 de `E:/report/README.md`, diagrama `database-diagram-campaign.png` y formularios de `E:/CollabPro`. La app móvil y el reporte no se modificaron.

US-15 se completa con publicación real, no por crear un DRAFT. US-16 guarda requisitos, especificaciones de contenido, cierre de postulaciones y compensación dentro del mismo agregado. No se crean postulaciones, colaboraciones, entregables ejecutados ni pagos.

## Endpoints para las pantallas

Prefijo `/api/v1`, JSON y `Authorization: Bearer TOKEN` de US-11 en todas las rutas. UUID como string; instantes ISO-8601 UTC.

| Método y ruta | Cuenta autorizada | Resultado / pantalla |
|---|---|---|
| POST /campaigns | Empresa habilitada | 201, Location y campaña DRAFT; paso 1 CampaignFormScreen |
| PUT /campaigns/{id}/conditions | Empresa propietaria, DRAFT | 200, campaña con condiciones; paso 2 CampaignTermsScreen |
| POST /campaigns/{id}/publication | Empresa propietaria, DRAFT completa | 200, OPEN y publicationDate; acción Publicar |
| GET /campaigns/{id} | Empresa propietaria | Detalle propio para recuperar el formulario; no es todavía detalle público US-18 |
| GET /campaigns/mine?page=0&size=20 | Empresa habilitada | Página de campañas propias, incluidos borradores; BrandCampaignsScreen |
| GET /campaigns/published?page=0&size=20 | Creador habilitado | Página básica de campañas OPEN, nunca DRAFT; visibilidad exigida por US-15 |

El listado publicado no implementa aún filtros de búsqueda US-17, detalle público completo US-18 ni postulaciones US-19. No usar esas funciones como si ya estuvieran implementadas. El listado está disponible para cada creador registrado con sesión válida, independientemente de su nicho o audiencia.

### Paso 1: crear el borrador

```json
{
  "title": "Sabores de Lima",
  "objective": "Dar a conocer el nuevo menú",
  "category": "Gastronomía",
  "targetAudience": "Adultos jóvenes de Lima",
  "description": "Contenido auténtico sobre nuestra experiencia gastronómica",
  "location": "Lima"
}
```

Obligatorios: title (1–200), objective (1–2000), category (1–100), targetAudience (1–2000). Opcionales: description (hasta 5000), location (hasta 150; si se omite se utiliza la ubicación disponible de la empresa). Se recortan espacios en extremos. Category es texto, no un enum nuevo que obligue a modificar las categorías del prototipo.

La respuesta incluye id, brandId, brandName, esos campos, status=DRAFT, requirements=[], deliverables=[], compensation=null, applicationDeadline=null, publicationDate=null y acceptsApplications=false. Guardar id para el segundo paso. BrandId es el ID del perfil empresarial, no el de Account. Se obtiene del actor autenticado mediante Identity; no se acepta brandId, accountId, rol o estado en el body. Campos desconocidos se rechazan.

### Paso 2: definir todas las condiciones

```json
{
  "requirements": [
    { "description": "Afinidad con contenido gastronómico local", "mandatory": true }
  ],
  "deliverables": [
    {
      "contentType": "VIDEO",
      "description": "Un video de la experiencia en el local",
      "quantity": 1,
      "deadline": "2026-11-15T23:59:00Z"
    }
  ],
  "applicationDeadline": "2026-11-01T23:59:00Z",
  "compensation": {
    "type": "CASH",
    "amount": 500.00,
    "currency": "PEN",
    "description": "Compensación por el contenido acordado"
  }
}
```

Requirements y deliverables: entre 1 y 50 elementos, ninguno null. Descripciones obligatorias hasta 2000; contentType hasta 100; quantity de 1 a 1000; mandatory boolean obligatorio. IDs de requisitos/especificaciones los genera el servidor. PUT reemplaza todas las condiciones, no hace un patch. Al reenviar, usar el formato anterior sin IDs de respuesta; las filas reemplazadas se eliminan en la misma transacción. Solo se permite reemplazar mientras la campaña siga DRAFT.

Política explícita propuesta en el plan: applicationDeadline debe ser futura respecto al servidor y cada deadline de entregable debe ser estrictamente posterior al cierre de postulaciones. Se comprueba al guardar y nuevamente al publicar. Una campaña preparada que deja vencer el cierre no puede publicarse sin corregir sus condiciones. Fechas incompatibles no guardan nada ni reemplazan las condiciones anteriores.

Compensación:

- CASH: monto positivo, máximo diez dígitos enteros y dos decimales; currency ISO reconocida por Java, normalizada a mayúsculas; description obligatoria.
- PRODUCT, SERVICE, CREDIT o BARTER: description concreta obligatoria; amount y currency deben omitirse o ser null. No se interpreta una cadena como “S/ 500” ni se genera una operación de Billing.

La respuesta devuelve la campaña completa con condiciones e IDs persistidos. En esta etapa sigue siendo privada y DRAFT.

### Publicar

POST `/campaigns/{id}/publication`, sin body. Comprueba propiedad, cuenta habilitada, estado DRAFT y condiciones completas/vigentes. Cambia a OPEN, establece publicationDate con el reloj del servidor y registra internamente CampaignPublished; no publica mensajes externos ni crea una colaboración.

Condiciones no se pueden editar una vez publicada, para no cambiar la oferta que verán los creadores. Republicación devuelve 409. Cierre/cancelación de campañas y sus endpoints no se implementan en este alcance. El listado publicado mantiene visibles campañas OPEN incluso si posteriormente vence su cierre; no equivale a autorizar una postulación fuera de plazo.

GET own devuelve la misma estructura completa; GET mine/published devuelve `{items,total,page,size}` con resúmenes (id, brandId, brandName, title, category, location, compensation, applicationDeadline, status). Página desde cero, size 1–100; una página sin resultados es 200 con items=[]. BrandName es un snapshot obtenido al crear la campaña, no un join entre tablas de contextos.

## Errores y autorización

Se reutiliza `ApiError(code,message,fieldErrors)` de Shared.

| HTTP | Código / situación |
|---|---|
| 400 | VALIDATION_ERROR con campos; INVALID_REQUEST para JSON, UUID, paginación o compensación mal formada |
| 401 | Sesión ausente, inválida, vencida o cuenta suspendida |
| 403 | BRAND_REQUIRED, CREATOR_REQUIRED, ACCOUNT_NOT_ACTIVE o FORBIDDEN por otra empresa propietaria |
| 404 | CAMPAIGN_NOT_FOUND |
| 409 | CAMPAIGN_NOT_DRAFT; CONCURRENT_UPDATE ante versión obsoleta o conflicto de bloqueo |
| 422 | INCOMPLETE_CAMPAIGN o INVALID_CONDITIONS por fechas/reglas incompatibles |

Todo fallo conserva el estado anterior. Ante 409 de concurrencia volver a leer antes de reenviar. Los detalles de otras empresas y los borradores no se exponen a creadores.

## DDD, fronteras y persistencia

Domain: Campaign, CampaignRequirement, DeliverableSpecification, IDs/CompensationTerms, invariantes y CampaignPublished. Application: tres CommandHandlers tipados, queries/handlers de listado, CampaignPreparationService y puertos CampaignUnitOfWork/CampaignCatalog/CampaignActorGateway. Interfaces: CampaignController, DTOs/ensamblado y traducción de errores. Infrastructure: adapter ACL, JPA separado, mapper y composición/transacciones Spring. Domain/Application no importan Spring/JPA ni modelos internos de Identity.

Identity publica IdentityProfileFacade, que ofrece únicamente una referencia segura del perfil activo. El adapter ACL de Campaign la consume y traduce a su propio BrandId. No hay FK hacia Identity, repositorio cruzado ni JOIN entre sus tablas. El puerto anterior IdentityProfileGateway permanece reservado para futuros datos de elegibilidad de US-19; CampaignActorGateway resuelve la cuenta autenticada sin simular esos datos.

V3 crea campaign_campaign, campaign_requirement y campaign_deliverable_specification con FK locales, versión optimista y restricciones. V4 amplía las fechas de Campaign a DATETIME(6), evitando el límite 2038 de TIMESTAMP para fechas introducidas por el usuario; se guardan/leen en UTC. V1/V2 y los datos existentes no se alteran ni se borran. Las migraciones viven en db/migration/campaign y se incluyen en Flyway junto a Identity.

Extensiones del diagrama documentadas: category, targetAudience y location (ya presentes en el esqueleto/formularios), brandName como snapshot de lectura y version como metadato técnico de concurrencia. No se crea la tabla de Application hasta implementar las historias correspondientes. JPA usa ddl-auto=validate.

## Integración móvil pendiente y pruebas

No se modificó `E:/CollabPro`. Al integrar, reemplazar las simulaciones por POST draft → PUT conditions → POST publication; mostrar éxito solo tras OPEN. Conservar el id/los formularios entre pasos y usar GET own/mine para recuperarlos. El formulario actual contiene una sola “fecha límite”: añadir cierre de postulaciones y fecha de entrega por especificación, y campos estructurados de compensación/cantidad, sin inventarlos al enviar. Convertir fechas locales dd/mm/aaaa a instantes con zona horaria definida.

Prueba rápida: `mvnw.cmd test` (H2; SMTP opcional omitido). Prueba completa: `scripts/Test-MySqlRegistration.ps1` (nombre conservado por compatibilidad; ejecuta toda la suite Identity y Campaign contra collabpro_test en Docker, además de SMTP Mailpit). No escribe en collabpro ni borra datos existentes. Se verifican creación/publicación visible a dos creadores, rechazo de incompletitud, persistencia/reemplazo/rollback, fechas incompatibles/vencidas, compensación inválida y canje, aislamiento de empresa, roles, datos falsificados, paginación, HTTP real, versiones obsoletas y dos escrituras simultáneas. Se mantienen las pruebas anteriores de Identity y límites de capas.

Resultado verificado del 05/10/2026: 86 pruebas contra MySQL Docker, sin fallos, errores u omisiones; H2 pasa 85 y omite únicamente SMTP opcional. También se comprueba el guardado/lectura de fechas de 2060, sin errores por el límite TIMESTAMP de 2038. El mapeo JDBC de DATETIME se declara explícitamente en Infrastructure y usa calendario UTC en ambos entornos.
