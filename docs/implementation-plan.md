# Plan de implementación — primer entregable

## Estado y fuentes

Esta entrega crea una base de contratos y modelos Java, no una API funcional. No hay controllers, handlers concretos, repositorios JPA, migrations, tokens reales ni integraciones de proveedores. Los paquetes de Infrastructure/Interfaces se versionan con `package-info.java` para reservar responsabilidad sin registrar componentes falsos.

Fuentes locales revisadas el 02/10/2026:

- `E:/report/README.md`: 2.4.1 (descripción y escenarios), 2.4.3 (orden del backlog), 2.5 (contextos y arquitectura), 2.6.1–2.6.5 (modelo táctico).
- Los cinco diagramas PNG en `E:/report/assets/C02/DDD/DatabaseDiagram/` fueron inspeccionados. Los diagramas de clases se describen en texto en 2.6; no hay imágenes separadas de UML de clases referenciadas en esas secciones.
- `E:/CollabPro/app/src/main/java/com/example/collabpro/features/identity/presentation/PublicAndIdentityScreens.kt`, `features/campaign/presentation/CampaignScreens.kt`, `navigation/AppState.kt`, `HomeScreen.kt` y modelos/repositorios preview.
- El proyecto Android es un prototipo Compose: autenticación, OAuth, campañas y postulaciones son estados locales de demostración. Aún no consume una API.

## Corte exacto del backlog

Se toman las **posiciones 1 a 18**, no el intervalo numérico US-01 a US-18. No se sustituye una historia de landing por una fila posterior.

| Posición | Historia | Pantallas / rutas actuales | Alcance del backend |
|---:|---|---|---|
| 1 | US-01 Presentación para empresas | WelcomeScreen, AboutScreen / ABOUT_BRAND | Contenido local/landing. Sin API de negocio. |
| 2 | US-02 Presentación para creadores | WelcomeScreen, AboutScreen / ABOUT_CREATOR | Contenido local/landing. |
| 3 | US-03 Funcionamiento para empresas | HowScreen / HOW_BRAND | Contenido local/landing. |
| 4 | US-04 Funcionamiento para creadores | HowScreen / HOW_CREATOR | Contenido local/landing. |
| 5 | US-05 Contacto | ContactScreen / CONTACT | Excluida como historia de landing en este entregable. Su envío real sí requiere un servicio de recepción; ver nota debajo. |
| 6 | US-06 Idioma | WelcomeScreen, AboutScreen, HowScreen / language | Presentación y conservación del idioma en cliente. |
| 7 | US-07 Compatibilidad visual | Landing y presentación móvil | UI adaptable; no caso de uso backend. |
| 8 | US-08 Acceso a registro | RolePickerScreen / ROLE_PICK | Navegación a formularios. Registro real se cubre por US-09/US-10. |
| 9 | US-17 Búsqueda de campañas | CampaignSearchScreen / CAMPAIGN_SEARCH | Campaign: búsqueda paginada por texto/categoría y respuesta vacía. |
| 10 | US-18 Condiciones de campaña | CampaignDetailScreen / CAMPAIGN_DETAIL | Campaign: detalle completo abierto/cerrado. |
| 11 | US-19 Postulación | ApplicationFormScreen, MyApplicationsScreen | Campaign: presentar, consultar propias, editar/cancelar pendiente. |
| 12 | US-10 Registro creador | RegisterScreen(brand=false) / REGISTER_CREATOR | Identity: Account + CreatorProfile inicial. |
| 13 | US-11 Acceso/recuperación | LoginScreen, RecoverScreen | Identity: autenticación, rol servidor, recuperación. |
| 14 | US-13 Perfil creador | ProfileScreen(brand=false) / CREATOR_PROFILE | Identity: lectura/actualización propia. |
| 15 | US-14 Redes sociales | SocialAccountsScreen / SOCIAL_ACCOUNTS | Identity: inicio/callback OAuth, lectura de cuentas autorizadas. |
| 16 | US-15 Creación campaña | CampaignFormScreen / CAMPAIGN_FORM | Campaign: borrador y publicación solo tras condiciones completas. |
| 17 | US-16 Condiciones campaña | CampaignTermsScreen / CAMPAIGN_TERMS | Campaign: requisitos, entregables, plazos y compensación. |
| 18 | US-09 Registro empresa | RegisterScreen(brand=true) / REGISTER_BRAND | Identity: Account + BrandProfile inicial. |

**US-05:** sus escenarios exigen registrar y confirmar una consulta; un botón que cambia estado local no cumple ese requisito. Se considera parte de la landing excluida por el alcance indicado y no se inventa un contexto de soporte dentro de Identity o Shared. En el trabajo de landing debe acordarse un servicio externo de contacto o documentar un contexto de soporte si se quiere que CollabPro almacene estas solicitudes. Se conserva su posición 5, sin reemplazarla.

Fuera del corte: US-12 (posición 19), evaluación US-20, acuerdo US-21, colaboración/entregables/incidencias US-22–25, pagos US-26–28, resultados US-29 e historial US-30. También quedan fuera las filas posteriores de spikes/technical stories como entregables independientes; las tareas técnicas mínimas para hacer funcionar las historias seleccionadas sí forman parte de su implementación.

## Arquitectura y dependencias

Monolito modular con los cinco bounded contexts del informe y un Shared Kernel mínimo. Nombres Java en minúsculas: `identity`, `campaign`, `collaboration`, `billing`, `performance`, `shared`. Cada uno conserva `domain`, `application`, `infrastructure`, `interfaces`.

- **Domain:** agregados, entidades, value objects, eventos, políticas y repositorios abstractos. Java puro, sin Spring/JPA, HTTP o dependencias a capas externas.
- **Application:** commands/queries y servicios de entrada; puertos de salida para seguridad, OAuth y lecturas. Los futuros handlers coordinan transacciones, autorizaciones, dominio y puertos.
- **Infrastructure:** adapters de puertos, entidades JPA separadas, mappers, consultas, seguridad y clientes externos. Aquí se configura Spring y la persistencia.
- **Interfaces:** controllers REST, recursos/request DTOs, ensambladores, errores HTTP y facades publicadas de cada contexto. Reciben datos y delegan; no contienen reglas de negocio.
- **Shared:** identidad de entidades/agregados, eventos, contratos CQRS, paginación, actor autenticado y contrato de error. No alberga perfiles, campañas, compensaciones ni un repositorio universal.

Dependencias: Interfaces/Infrastructure → Application → Domain → Shared.Domain. Application también usa Shared.Application. El código interno de Campaign nunca depende de Account/CreatorProfile/JPA de Identity. `IdentityProfileGateway` define el snapshot que Campaign necesita; un adapter ACL de Campaign consultará una facade de Identity en Interfaces. No habrá joins/repositorios cruzados ni asociaciones JPA entre contextos.

En esta base, Identity y Campaign tienen modelos/puertos tipados. Collaboration, Billing y Performance conservan sus cuatro capas documentadas para su futura implementación, sin crear comportamiento de historias fuera del corte.

## Correspondencia con el modelo y los diagramas

| Contexto | Agregados y pertenencia | Restricciones de persistencia a implementar |
|---|---|---|
| Identity | Account posee exactamente un BrandProfile o CreatorProfile según rol; SocialMediaAccount pertenece a CreatorProfile. EmailAddress e IDs son value objects. | `account.email` UNIQUE normalizado; `brand_profile.account_id` y `creator_profile.account_id` FK local + UNIQUE; social account FK local al perfil y UNIQUE(profile, platform, externalAccountId). Hash, nunca contraseña en claro. |
| Campaign | Campaign contiene requisitos, especificaciones y CompensationTerms; Application es un agregado separado que referencia Campaign y CreatorId. | `campaign_requirement`/`deliverable_specification` FK locales a Campaign; `application.campaign_id` FK local. BrandId/CreatorId son referencias externas a perfiles, sin FK entre contextos. Evitar duplicados bajo concurrencia con restricción única y traducción de conflicto. |
| Collaboration (reservado) | Collaboration, AgreementSnapshot, Deliverable, Evidence, DeliverableReview, Incident. | Snapshot de condiciones e historial de revisiones preservados; IDs externos de Campaign/Identity. |
| Billing (reservado) | PaymentAccount, Subscription y Compensation separados; PaymentMethod/PaymentTransaction; Money. | Referencias tokenizadas del proveedor; jamás PAN/CVV. No confundir oferta de Campaign con pago procesado. |
| Performance (reservado) | PerformanceReport, MetricSnapshot, PerformanceEvidence, AttributionLink e interacciones. | Mantener fuente y periodo; diferenciar datos automáticos y manuales. |

Los IDs se modelan como UUID como propuesta técnica: el diagrama solo especifica “identifier”. Al persistir, decidir uniformemente CHAR(36) o BINARY(16) en MySQL y documentar la conversión. La app usa actualmente IDs Int de muestra; debe pasar a IDs String/UUID al integrar.

Campos añadidos al esqueleto para cubrir inputs existentes de la app: CreatorProfile.location; Campaign.category, targetAudience y location. Son extensiones del diagrama, no datos ya presentes allí. La ubicación puede precargarse del BrandProfile inicial, sin implementar edición de perfil empresarial US-12. Actualizar UML/diagramas cuando se confirme el modelo.

CampaignRequirement se conserva como **entidad** por tener requirementId y una fila propia en el diagrama. CompensationTerms es un value object local a Campaign, almacenado junto al agregado; aún no implica cobrar o activar Billing.

Los agregados actuales contienen estructura y acceso de lectura; faltan transiciones como publish, defineConditions, submit/update/cancel. Las invariantes mínimas de IDs, correo, tipo de perfil y copia defensiva de colecciones ya están en la base; completar las reglas de escenarios será trabajo del siguiente paso. No usar setters genéricos que permitan saltar las transiciones.

## Contratos REST propuestos (aún no implementados)

Prefijo `/api/v1`. UUID como string, fechas ISO-8601 y tiempos UTC con zona; convertir desde dd/mm/aaaa y formatear en el cliente. Nombres de propiedades y estados en inglés, traducidos en Compose. Paginación desde cero; límite 1–100. Ningún ID de actor o rol enviado por la app será autoridad: derivarlo del token autenticado y su perfil.

| Endpoint futuro | Caso / respuesta | Pantalla |
|---|---|---|
| POST /auth/brands | businessName, email, password → 201 AccountView | Registro empresa US-09 |
| POST /auth/creators | displayName, email, password → 201 AccountView | Registro creador US-10 |
| POST /auth/sessions | email, password → SessionView(accessToken, tokenType, expiresAt, account) | Login US-11 |
| POST /auth/recovery-requests | email → 202 respuesta genérica | Recover US-11 |
| POST /auth/password-resets | token, newPassword → 204; completar mecanismo de recuperación | Enlace/pantalla reset a añadir como cierre técnico de US-11 |
| GET /accounts/me | accountId, profileId, name, accountType, status | Encabezado Home; rol decidido por servidor |
| GET /profiles/me/creator | perfil propio | ProfileScreen creador US-13 |
| PUT /profiles/me/creator | displayName, biography, niche, audienceDescription, location → perfil | Guardar perfil US-13 |
| POST /social-accounts/{platform}/authorizations | authorizationUrl (estado guardado servidor) | Inicio OAuth US-14 |
| GET /social-accounts/{platform}/callback | validar state/código/error y vincular solo con consentimiento válido | Browser OAuth → app mediante app link |
| GET /social-accounts/me | lista de platform, username, status; no tokens | SocialAccountsScreen US-14 |
| GET /campaigns?q=&category=&page=&size= | PageResult<Summary>; items=[] si sin coincidencias | Búsqueda US-17 |
| GET /campaigns/{id} | Details con condiciones y acceptsApplications | Detalle US-18 |
| GET /campaigns/mine | campañas propias, incluye borradores | BrandCampaignsScreen, apoyo a US-15/16 |
| POST /campaigns | metadatos → 201 id, status=DRAFT | Paso 1 US-15 |
| PUT /campaigns/{id}/conditions | requisitos, entregables, fechas, compensación → condiciones guardadas | Paso 2 US-16 |
| POST /campaigns/{id}/publication | publicar completa → OPEN visible a creadores | Finalizar US-15 |
| POST /campaigns/{id}/applications | message → 201 aplicación PENDING | Postular US-19 |
| GET /applications/mine | aplicaciones propias paginadas | MyApplicationsScreen, apoyo US-19 |
| GET /applications/{id} | detalle propio para editar | ApplicationFormScreen edición |
| PUT /applications/{id} | message, solo propia PENDING | US-19 edición |
| POST /applications/{id}/cancellation | cancelar propia PENDING → CANCELLED | US-19 cancelación |

HTTP: 400 errores de input, 401 acceso inválido/ausente, 403 rol/propiedad insuficiente, 404 recurso inexistente, 409 duplicado/estado que impide la operación, 422 condiciones incompatibles o requisito incumplido. Error propuesto `ApiError(code, message, fieldErrors)`; requisitos incumplidos deben identificarse por ID y explicación cuando se añada el recurso específico. Listas vacías son 200, no 404. No enviar hashes, tokens OAuth ni excepciones técnicas a clientes.

## Plan de trabajo y escenarios

La matriz mantiene el orden del backlog; la secuencia de implementación resuelve primero las dependencias (Identity antes de crear campañas o postular). No cambia el corte de 18 filas.

### 1. Persistencia y composición

Crear entidades JPA/mappers en Infrastructure sin anotar el dominio. Versionar esquema con migraciones; fijar índices y restricciones anteriores. Añadir optimistic locking al modelo persistido y transacciones en handlers. Crear datos de desarrollo controlados con campañas abiertas/cerradas, nunca simular que son productivos. Configurar perfil local MySQL separado del perfil skeleton; no usar ddl-auto=create/update en entornos compartidos.

### 2. Identity: US-10, US-11 y US-09

Implementar RegisterCreatorCommandHandler y RegisterBrandCommandHandler para crear Account/perfil inicial y rechazar correos duplicados (también en condición de carrera por índice UNIQUE). Añadir PasswordHasher y AccessTokenProvider concretos con configuración externa. Account activo recibe acceso; cuentas no activas o contraseña incorrecta reciben denegación. El rol del token dirige Home; retirar selector de rol ficticio de Login.

Implementar RecoverAccountCommandHandler con token aleatorio de un solo uso, hash persistido, expiración y correo mediante adapter. La respuesta pública no debe revelar si existe un correo. Agregar confirmación de cambio de contraseña y pantalla/app-link de recuperación: el prototipo actualmente solo pide correo. Esta extensión cierra el flujo de US-11, no introduce una fila nueva.

### 3. Perfil y OAuth: US-13/US-14

Implementar lectura y actualización del perfil creador propietario activo, campos modificables y validación. Persistir también audiencia: actualmente usa LocalEntry y cambios locales, lo que debe convertirse en estado editable real.

OAuth se inicia desde servidor, registra state con propietario, proveedor, caducidad y uso único; abre navegador con URL permitida. Callback solo crea vínculo al verificar autorización, código y cuenta externa. Rechazo de permisos no crea cuenta vinculada. Duplicado en el mismo perfil se rechaza con restricción única. Tokens del proveedor quedan cifrados en Infrastructure y no entran en el dominio ni en respuestas. Instagram/TikTok requieren revisión de permisos/capacidad real del proveedor; mantener el puerto y no fabricar datos de red social. Listado debe reflejar persistencia real, no el estado “Simular autorización”.

### 4. Campañas: US-15/US-16

CreateCampaignCommandHandler crea DRAFT con propietario autenticado habilitado. La primera pantalla requiere título, objetivo, categoría y público; su estado debe mantenerse al pasar al segundo paso. DefineCampaignConditionsCommandHandler verifica requisitos/entregables, cantidad positiva, compensación válida y fechas consistentes; guarda en una transacción. PublishCampaignCommandHandler comprueba completitud y cambia a OPEN con publicationDate. Fallo mantiene DRAFT y muestra errores; US-15 solo se considera cumplida cuando la campaña queda publicada y visible, no por crear un borrador.

Definir claramente applicationDeadline y deadline de entregables: el prototipo tiene una única fecha, el modelo de dominio distingue ambas. Añadir campos o una regla explícita al formulario; no asumir que la fecha de entrega ocurre antes del cierre de postulaciones. Propuesta: plazos de entrega posteriores al cierre de postulaciones; confirmar con el dominio y validar ambos con Clock controlable.

### 5. Búsqueda/detalle: US-17/US-18

SearchCampaignsQueryHandler aplica q/categoría de UI (ubicación/tipo de compensación preparados como filtros adicionales), paginación y campañas publicadas. Respuesta vacía informa sin coincidencias. GetCampaignDetailsQueryHandler devuelve objetivo, requisitos, entregables, plazos, compensación y acceptsApplications; campaña cerrada puede consultarse, pero no admite postulación. Borradores solo son visibles al propietario. La fecha límite puede cerrar recepción aunque estado todavía sea OPEN: evaluar tiempo servidor. Obtener marca/ubicación mediante ACL/snapshot, sin joins a tablas de Identity desde Campaign.

Reemplazar condiciones fijas de CampaignDetailScreen por el recurso real. El filtrado local actual no debe ser la única fuente de selección; conservar textos/categorías elegidos al paginar.

### 6. Postulaciones: US-19

SubmitApplicationCommandHandler valida creador activo, campaña abierta/plazo vigente y requisitos obligatorios. Guarda Application independiente PENDING y rechaza duplicados, también concurrentes. Update/Cancel solo sobre aplicación propia PENDING; edición modifica propuesta, cancelación registra CANCELLED. Añadir queries de detalle/lista propias para reconstruir pantallas al volver o reiniciar app. No implementar selección/rechazo de empresa (US-20 fuera del corte), aunque el enum documentado incluya esos estados futuros.

Requisitos actualmente son texto libre. Un servicio no puede acreditar “audiencia mínima” interpretando una frase arbitraria: definir reglas estructuradas (tipo, operador, umbral y fuente verificable) o evidencia/confirmación explícita y documentar su limitación. Ampliar el diagrama antes de persistir la extensión. La política de nueva postulación tras cancelación también debe quedar explícita: la base reserva existsByCampaignAndCreator; propuesta inicial es una fila por campaña/creador, sin permitir reenvío, coherente con escenario “ya se ha postulado”. Si se aprueba reenvío, revisar índice e historial; no eliminar postulaciones antiguas para evadir la unicidad.

El informe repite “Escenario 4” para edición y cancelación; se tratarán como dos casos distintos (cuarto y quinto) sin perder ninguno.

## Integración móvil posterior

No se modifican ahora las pantallas de E:/CollabPro. Añadir posteriormente Retrofit/OkHttp o cliente HTTP equivalente, DTOs/adapters de API en Infrastructure del cliente y ViewModels con estado loading/success/empty/error. Reemplazar PreviewProfiles/PreviewCampaigns mediante contratos reales y observar listas, no snapshots únicos en AppState. Retirar botones de simulación al conectar cada flujo.

- Mantener formularios entre los dos pasos y enviar solo al completar la acción pertinente.
- ID UUID como string, enums en inglés y dinero/compensación estructurados (BigDecimal/moneda o canje con descripción). No parsear “S/ 200” en el dominio ni usar strings de UI como contratos.
- Usar el rol que devuelve la sesión, restaurar sesión de forma segura y comprobar 401 para volver a login.
- Persistir audiencia/ubicación y fechas reales, actualmente locales o constantes.
- Conectar los controles dentro del corte. En Home, los contadores/enlaces de colaboraciones, entregables, planes, resultados e historial pertenecen a historias posteriores: ocultarlos o presentarlos explícitamente como no disponibles en este entregable. No ampliar el backend para alimentar toda la demo.
- Base URL del emulador Android hacia host local: 10.0.2.2 y puerto configurado; HTTPS en entornos publicados. Registrar app links/redirects autorizados del proveedor OAuth.

## Validación prevista para la implementación

Pruebas de dominio para todos los escenarios de la sección 2.4.1 seleccionados; integración con MySQL para UNIQUE/FK/races y rollback; pruebas HTTP de autorización/propiedad/estados y JSON esperado; contrato de cliente para listado vacío, campaña cerrada, duplicados y errores por campo. OAuth con proveedor sandbox o stub explícito de prueba, nunca presentado como autorización real. Recovery con entrega verificable en ambiente de prueba. Pruebas E2E: registrar empresa y creador → publicar campaña completa → buscar/detalle → postular/editar/cancelar.

La base actual solo requiere compilación, arranque skeleton y comprobación de límites de dependencias. Estas verificaciones no significan que las user stories estén implementadas.

## Entregables del siguiente paso

Handlers y reglas completos, recursos/controller/assemblers, migraciones y adapters JPA, configuración de seguridad/OAuth/recovery, OpenAPI versionado con ejemplos, matriz de pruebas por escenario y conexión del cliente solo para las historias del corte. URLs/proveedores definitivos se fijarán entonces.
