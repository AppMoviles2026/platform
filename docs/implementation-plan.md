# Plan de implementación — primer entregable

## Estado y fuentes

Actualización de alcance: 05/10/2026. Las posiciones 1 a 8 corresponden a landing y no se implementan en este backend. Se retiró la implementación informativa de US-01/US-02. US-09 y US-10 se implementan como registro de empresa y creador en Identity.

La base inicial creó contratos y modelos Java. Ahora existen factories de registro del agregado Account, dos command handlers, hashing de contraseñas, adapters JPA y migración Flyway de cuentas/perfiles. MySQL se ejecuta en Docker. [Contrato de registro](registration-api.md) documenta los endpoints y las pruebas. Inicio de sesión, recuperación, OAuth y campañas permanecen planificados; no se generan tokens de sesión al registrar.

Fuentes locales de la base, contrastadas nuevamente con el reporte el 05/10/2026:

- `E:/report/README.md`: 2.4.1 (descripción y escenarios), 2.4.3 (orden del backlog), 2.5 (contextos y arquitectura), 2.6.1–2.6.5 (modelo táctico).
- Los cinco diagramas PNG en `E:/report/assets/C02/DDD/DatabaseDiagram/` fueron inspeccionados. Los diagramas de clases se describen en texto en 2.6; no hay imágenes separadas de UML de clases referenciadas en esas secciones.
- `E:/CollabPro/app/src/main/java/com/example/collabpro/features/identity/presentation/PublicAndIdentityScreens.kt`, `features/campaign/presentation/CampaignScreens.kt`, `navigation/AppState.kt`, `HomeScreen.kt` y modelos/repositorios preview.
- El proyecto Android es un prototipo Compose: autenticación, OAuth, campañas y postulaciones son estados locales de demostración. Aún no consume una API.

## Corte exacto del backlog

Se toman las **posiciones 1 a 18**, no el intervalo numérico US-01 a US-18. No se sustituye una historia de landing por una fila posterior.

Las posiciones 1–8 (US-01 a US-08) quedan excluidas del backend. La tabla siguiente conserva las posiciones originales de las historias que sí necesitan backend; no renumera ni reemplaza las excluidas.

| Posición | Historia | Pantallas / rutas actuales | Alcance del backend |
|---:|---|---|---|
| 9 | US-17 Búsqueda de campañas | CampaignSearchScreen / CAMPAIGN_SEARCH | Campaign: búsqueda paginada por texto/categoría y respuesta vacía. |
| 10 | US-18 Condiciones de campaña | CampaignDetailScreen / CAMPAIGN_DETAIL | Campaign: detalle completo abierto/cerrado. |
| 11 | US-19 Postulación | ApplicationFormScreen, MyApplicationsScreen | Campaign: presentar, consultar propias, editar/cancelar pendiente. |
| 12 | US-10 Registro creador | RegisterScreen(brand=false) / REGISTER_CREATOR | Implementada: POST /api/v1/auth/creators crea Account + CreatorProfile inicial. |
| 13 | US-11 Acceso/recuperación | LoginScreen, RecoverScreen | Identity: autenticación, rol servidor, recuperación. |
| 14 | US-13 Perfil creador | ProfileScreen(brand=false) / CREATOR_PROFILE | Identity: lectura/actualización propia. |
| 15 | US-14 Redes sociales | SocialAccountsScreen / SOCIAL_ACCOUNTS | Identity: inicio/callback OAuth, lectura de cuentas autorizadas. |
| 16 | US-15 Creación campaña | CampaignFormScreen / CAMPAIGN_FORM | Campaign: borrador y publicación solo tras condiciones completas. |
| 17 | US-16 Condiciones campaña | CampaignTermsScreen / CAMPAIGN_TERMS | Campaign: requisitos, entregables, plazos y compensación. |
| 18 | US-09 Registro empresa | RegisterScreen(brand=true) / REGISTER_BRAND | Implementada: POST /api/v1/auth/brands crea Account + BrandProfile inicial. |

**US-05:** sus escenarios exigen registrar y confirmar una consulta; un botón que cambia estado local no cumple ese requisito. Se considera parte de la landing excluida por el alcance indicado y no se inventa un contexto de soporte dentro de Identity o Shared. En el trabajo de landing debe acordarse un servicio externo de contacto o documentar un contexto de soporte si se quiere que CollabPro almacene estas solicitudes. Se conserva su posición 5, sin reemplazarla.

Fuera del corte: US-12 (posición 19), evaluación US-20, acuerdo US-21, colaboración/entregables/incidencias US-22–25, pagos US-26–28, resultados US-29 e historial US-30. También quedan fuera las filas posteriores de spikes/technical stories como entregables independientes; las tareas técnicas mínimas para hacer funcionar las historias seleccionadas sí forman parte de su implementación.

## Arquitectura y dependencias

Monolito modular con los cinco bounded contexts del informe y un Shared técnico mínimo, sin compartir modelos internos de negocio (2.5.2). Nombres Java en minúsculas: `identity`, `campaign`, `collaboration`, `billing`, `performance`, `shared`. Cada uno conserva `domain`, `application`, `infrastructure`, `interfaces`.

No se añadirá `publiccontent` ni otro sexto bounded context. Shared es un soporte técnico compartido, no un contexto de negocio que absorba historias sin propietario.

### Responsabilidades y fronteras verificadas en el reporte

La asignación se basa en 2.5.1.3 (canvases), 2.5.2 (Context Map) y 2.6 (modelo táctico), **no en si la pantalla la utiliza una empresa o un creador**.

| Contexto | Responsabilidad y modelo propios | Lo que no le corresponde | Uso en las primeras 18 posiciones |
|---|---|---|---|
| Identity & Profile Management (`identity`) | Registro, acceso, recuperación, perfiles y asociación social autorizada. Aggregate Root: Account; perfiles y SocialMediaAccount dentro de su límite. | Contenido de landing, crear campañas, decidir elegibilidad de una postulación, ejecutar colaboraciones, procesar pagos u obtener reportes de métricas. | US-09 y US-10 implementadas; US-11, US-13 y US-14 pendientes. |
| Campaign Management (`campaign`) | Oportunidades antes del acuerdo: Campaign, requisitos, especificaciones, CompensationTerms y Application como agregado independiente. | Entregables realizados, acuerdo bilateral, validación de evidencias, cobro o métricas de resultados. | US-15, US-16, US-17, US-18, US-19. |
| Collaboration Management (`collaboration`) | Acuerdo aceptado y ejecución: Collaboration, snapshot de condiciones, entregables reales, revisión e incidencias. | Búsqueda de oportunidades y postulaciones pendientes; procesamiento financiero o APIs de métricas. | Ningún caso de uso transaccional del corte. Se conserva la estructura, sin handlers nuevos. |
| Billing & Compensation Management (`billing`) | Métodos de pago, suscripciones, compensaciones autorizadas y transacciones. | La oferta de compensación de una campaña o explicar al visitante cómo funciona un pago. | Ninguno. CompensationTerms de US-16/18 sigue siendo de Campaign. |
| Performance & Attribution Management (`performance`) | Métricas con fuente y periodo, evidencias de desempeño y atribución. | Registro/autorización de una cuenta social o descripción de audiencia del perfil. | Ninguno. US-14 pertenece a Identity, aunque habilite futuras consultas de Performance. |
| Shared (`shared`) | Primitivas técnicas, CQRS, eventos, paginación y errores reutilizables. | Account, Campaign, catálogos de negocio, textos comerciales o tablas comunes que mezclen contextos. | Soporte transversal, sin historia ni agregado propio. |

### Decisión corregida para landing y registro

Los escenarios de US-01 a US-08 son de landing. Se eliminan controllers, queries, proyecciones, puerto, catálogo, recursos y tests exclusivos de las presentaciones. Sus antiguas rutas GET responden 404. No se asignan a Identity ni Shared, y su contenido debe mantenerse en el proyecto de landing.

US-09 y US-10 sí pertenecen a Identity por crear una identidad persistida. Account controla el tipo y posee exactamente un perfil correspondiente; registro no significa iniciar una sesión, editar el perfil completo ni crear campañas. Los handlers dependen de AccountRepository, PasswordHasher y Clock; Spring, transacciones, JPA y criptografía concreta se mantienen en Infrastructure. Los factories registran AccountRegistered como evento interno del agregado, sin publicar integraciones externas en este entregable.

Decisiones técnicas: IDs UUID, correo normalizado en minúsculas, nombres de 1 a 150 caracteres y contraseña de 8 a 128 caracteres sin recortarla. La longitud de contraseña es una política propuesta, no un requisito explícito del reporte; la app actual acepta 6 y deberá alinearse al integrar. Se crea una cuenta ACTIVE porque estas historias no definen verificación de correo. Cambiar esa política a PENDING requiere definir el flujo de activación. La respuesta no concede acceso autenticado.

La transacción del adapter persiste Account y perfil juntos y traduce la restricción UNIQUE del correo a un conflicto de negocio incluso bajo concurrencia. El adapter save actual implementa inserción de registro; deberá ampliarse para actualización/versionado de agregados al abordar US-11/13/14. No persiste redes sociales todavía y rechaza intentos de guardar asociaciones antes de implementar ese adapter, evitando descartarlas silenciosamente.

- **Domain:** agregados, entidades, value objects, eventos, políticas y repositorios abstractos. Java puro, sin Spring/JPA, HTTP o dependencias a capas externas.
- **Application:** commands/queries y servicios de entrada; puertos de salida para seguridad, OAuth y lecturas. Los futuros handlers coordinan transacciones, autorizaciones, dominio y puertos.
- **Infrastructure:** adapters de puertos, entidades JPA separadas, mappers, consultas, seguridad y clientes externos. Aquí se configura Spring y la persistencia.
- **Interfaces:** controllers REST, recursos/request DTOs, ensambladores, errores HTTP y facades publicadas de cada contexto. Reciben datos y delegan; no contienen reglas de negocio.
- **Shared:** identidad de entidades/agregados, eventos, contratos CQRS, paginación, actor autenticado y contrato de error. No alberga perfiles, campañas, compensaciones ni un repositorio universal.

Dependencias: Interfaces/Infrastructure → Application → Domain → Shared.Domain. Application también usa Shared.Application. El código interno de Campaign nunca depende de Account/CreatorProfile/JPA de Identity. `IdentityProfileGateway` define el snapshot que Campaign necesita; un adapter ACL de Campaign consultará una facade de Identity en Interfaces. No habrá joins/repositorios cruzados ni asociaciones JPA entre contextos.

Los adapters entre contextos dependen únicamente del contrato publicado, nunca de entidades o repositorios internos. La composición Spring conecta esos adapters en Infrastructure. Las reglas de Campaign reciben datos de referencia tipados, no objetos Account ni tokens OAuth. La fachada de Identity entrega solo datos necesarios para el caso de uso; un fallo al verificar identidad o requisitos no se interpreta como autorización concedida.

En esta base, Identity y Campaign tienen modelos/puertos tipados. Collaboration, Billing y Performance conservan sus cuatro capas documentadas para su futura implementación, sin crear comportamiento de historias fuera del corte.

## Trazabilidad por historia: contexto, capas y escenarios

US-09 y US-10 están implementadas en el backend; los handlers/contratos de las demás historias de backend siguen siendo objetivos del plan. Se mantienen exactamente las 18 posiciones. Las primeras ocho filas son trazabilidad de landing, no trabajo backend.

| Posición / US | Dueño y modelo | Application y dependencias | Interfaces / Infrastructure | Escenarios y límite de implementación |
|---|---|---|---|---|
| 1 / US-01 | Landing; sin dueño backend. | Sin commands/queries. Implementación informativa retirada. | Presentación para empresas en landing. | Propuesta profesional/justa y beneficios; validar comprensión en landing, no mediante un backend editorial. |
| 2 / US-02 | Landing; sin dueño backend. | Sin commands/queries. Implementación informativa retirada. | Presentación para creadores en landing. | Encontrar/gestionar oportunidades y conocer condiciones antes de aceptar. |
| 3 / US-03 | Presentación de cliente/landing; sin modelo transaccional. | Ningún command o query backend en este corte. | HowScreen / HOW_BRAND con contenido informativo. | Explicar creación, postulación, selección, ejecución y validación previa al pago. Mencionar esos pasos no autoriza implementar US-20 a US-28. |
| 4 / US-04 | Presentación de cliente/landing. | Sin caso de uso backend en este entregable. | HowScreen / HOW_CREATOR. | Explicar búsqueda, postulación, aceptación, entrega y validación; conocer condiciones antes de confirmar. Sin crear AgreementSnapshot ni Collaboration. |
| 5 / US-05 | Contacto de landing, excluido; ningún canvas define soporte al visitante. | No introducir ContactRequest en Account o Shared. Servicio receptor a acordar en el trabajo de landing. | ContactScreen; envío y validación real pendientes fuera de este backend. | Consulta registrada/recepción confirmada e información incompleta. Una confirmación local no cumple el escenario de registro. |
| 6 / US-06 | Localización de landing. | Sin agregado, catálogo backend ni actualización de Account. | Recursos es/en y estado de idioma conservado al navegar. | Inglés, español y conservación de idioma. No confundir locale con rol o autorización. |
| 7 / US-07 | Diseño adaptable, cliente/landing. | Sin operación backend. | Layouts y pruebas visuales móvil/escritorio/orientación. | Acceso sin pérdida de información y cambio de orientación; ningún contexto garantiza responsive mediante un endpoint. |
| 8 / US-08 | Navegación de cliente hacia entrada de Identity. | No crea cuenta; depende de los registros US-09/10 al enviar sus formularios. | RolePickerScreen dirige al formulario correcto. | Ruta empresarial y ruta creador. El selector de segmento no otorga un rol autenticado. |
| 9 / US-17 | Campaign; proyección de campañas publicadas. | SearchCampaignsQuery/handler y puerto de búsqueda paginada; datos de marca por contrato de Identity si se necesitan. | CampaignController; CampaignSearchAdapter, filtros e índices locales a Campaign. | Coincidencias y lista vacía. No devolver borradores ajenos ni crear métricas de Performance para filtrar. |
| 10 / US-18 | Campaign; lectura de Campaign y sus condiciones locales. | GetCampaignDetailsQuery/handler; determina acceptsApplications con estado y reloj servidor. | CampaignController; adapter de lectura con requisitos, especificaciones, plazos y CompensationTerms. | Campaña abierta: condiciones completas; cerrada: informar que no admite postulaciones. Compensación ofrecida no equivale a pago procesado por Billing. |
| 11 / US-19 | Campaign; Application como agregado separado, referencia a Campaign y CreatorId. | Submit/Update/CancelApplicationCommand handlers; GetCreatorApplications/GetApplicationDetailsQuery handlers; ApplicationEligibilityService recibe hechos mediante IdentityProfileGateway. | ApplicationController; repositorio local, restricción de duplicados, concurrencia y autorización del propietario. | Pendiente válida, duplicado, requisito incumplido identificado, edición y cancelación solo pendientes. No seleccionar postulantes (US-20) ni crear colaboración (US-21). |
| 12 / US-10 | Identity; Account(CREATOR) con CreatorProfile inicial. | RegisterCreatorCommand/handler, AccountRepository y PasswordHasher. | AuthController; persistencia local de cuenta/perfil, email normalizado UNIQUE y hash. | Registro válido y correo existente. Sin Campaign ni Performance, aunque el objetivo futuro sea encontrar oportunidades. |
| 13 / US-11 | Identity; Account y estado/credenciales. | AuthenticateAccountCommand y RecoverAccountCommand handlers; AccessTokenProvider y AccountRecoveryService. Añadir contrato de finalización de recuperación. | AuthController; seguridad, correo y almacenamiento de token de recuperación de un uso en Infrastructure. | Cuenta activa con credenciales válidas, denegación de acceso y recuperación iniciada. Rol del servidor; no confiar en selector o IDs del cliente. |
| 14 / US-13 | Identity; CreatorProfile dentro de Account, no un nuevo agregado independiente. | GetUserProfileQuery y UpdateCreatorProfileCommand handlers; validar campos y propietario. | UserProfileController; persistencia del agregado Account y su perfil en una transacción. | Registro de contenido/audiencia y actualización permitida. Descripción de audiencia no son métricas verificadas de Performance. |
| 15 / US-14 | Identity; SocialMediaAccount pertenece a CreatorProfile dentro de Account. | Start/CompleteSocialAuthorizationCommand y GetLinkedSocialMediaQuery handlers; puerto SocialOAuthClient. | SocialMediaController; ACL del proveedor, state/consentimiento y tokens protegidos en Infrastructure. | Autorización válida, rechazo sin vínculo y duplicado impedido. No implementar obtención de métricas US-29 ni modelar tokens del proveedor en Domain. |
| 16 / US-15 | Campaign; Campaign, con BrandId externo. | CreateCampaignCommand y PublishCampaignCommand handlers; GetBrandCampaignsQuery para reconstruir pantalla; IdentityProfileGateway verifica empresa habilitada. | CampaignController; CampaignRepository adapter, transacciones y optimistic locking. | Creación completa se vuelve visible a creadores registrados; información incompleta no se publica. DRAFT técnico no satisface por sí solo el escenario válido. |
| 17 / US-16 | Campaign; requisitos, DeliverableSpecification y CompensationTerms dentro de Campaign. | DefineCampaignConditionsCommand/handler; invariantes de fechas, cantidades y compensación en dominio. | CampaignController; tablas propias del agregado y mapper local. | Condiciones válidas asociadas y condiciones incompatibles rechazadas sin guardar parcialmente. Especificación esperada no es entregable real de Collaboration; oferta no es transacción de Billing. |
| 18 / US-09 | Identity; Account(BRAND) y BrandProfile inicial. | RegisterBrandCommand/handler, mismo control de unicidad que creador y PasswordHasher. | AuthController; persistencia local del agregado y perfil empresarial. | Registro válido y correo duplicado. No implementar edición empresarial US-12 ni crear automáticamente una campaña o suscripción. |

### Contextos reservados y relación con historias fuera del corte

Esta correspondencia explica por qué existen las otras carpetas, **no amplía el entregable**: US-12 → Identity; US-20 → Campaign (evaluar postulantes); US-21 a US-25 → Collaboration (aceptación bilateral, estado, entregables, revisión e incidencias); US-26 a US-28 → Billing (medio de pago, suscripción y compensación); US-29 → Performance. US-30 requiere consultas de historial del contexto propietario de cada hecho, sin inventar un agregado global que mezcle tablas. Al abordar esas historias deberán revisarse sus escenarios completos.

Un flujo futuro puede coordinar varios contextos, pero cada uno conserva sus reglas: Campaign comunica selección y condiciones; Collaboration formaliza el acuerdo y conserva un snapshot; Billing procesa solo una compensación autorizada; Performance mide con fuente y periodo. Ninguno se activa en US-19 por el mero hecho de postular.

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

Account implementa factories de registro e invariantes de perfil/tipo, correo y hash requerido. Los agregados de Campaign aún son estructurales; faltan transiciones como publish, defineConditions, submit/update/cancel. No usar setters genéricos que permitan saltar las transiciones.

## Contratos REST: registro implementado y casos futuros

POST /auth/brands y POST /auth/creators están implementados. Los demás endpoints de esta tabla siguen siendo propuestas. Ver ejemplos y errores del registro en [registration-api.md](registration-api.md).

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

### 0. Retirar backend de landing — completado

Se retiró toda la implementación exclusiva de US-01/US-02 y su documento de API. Las pruebas de registro comprueban que las rutas informativas antiguas ya no existen. Se mantienen las seis carpetas permitidas: cinco contextos del reporte y Shared; las primeras ocho posiciones continúan contando dentro del corte, pero no se implementan en backend.

### 1. Persistencia y composición

Crear entidades JPA/mappers en Infrastructure sin anotar el dominio. Versionar esquema con migraciones; fijar índices y restricciones anteriores. Añadir optimistic locking al modelo persistido y transacciones en handlers. Crear datos de desarrollo controlados con campañas abiertas/cerradas, nunca simular que son productivos. Configurar perfil local MySQL separado del perfil skeleton; no usar ddl-auto=create/update en entornos compartidos.

Completado para registro: tres entidades JPA de Identity, mapper, adapter transaccional, migración V1, columna de versión, perfil local MySQL y Compose con volumen persistente. El dominio sigue sin anotaciones JPA. Persistencia de Campaign/redes sociales permanece pendiente. Local es ahora el perfil por defecto; skeleton es opcional y no expone registros al no tener DB.

### 2. Identity: US-10, US-11 y US-09

Implementar RegisterCreatorCommandHandler y RegisterBrandCommandHandler para crear Account/perfil inicial y rechazar correos duplicados (también en condición de carrera por índice UNIQUE). Añadir PasswordHasher y AccessTokenProvider concretos con configuración externa. Account activo recibe acceso; cuentas no activas o contraseña incorrecta reciben denegación. El rol del token dirige Home; retirar selector de rol ficticio de Login.

Estado: RegisterCreatorCommandHandler, RegisterBrandCommandHandler y PasswordHasher están implementados para US-10/09. AccessTokenProvider, autenticación y recuperación de US-11 no lo están. Crear Account ACTIVE no genera sesión ni permite que el frontend trate el resultado de registro como login.

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

La base requiere compilación y comprobación de límites de dependencias. US-09/US-10 tienen pruebas de dominio/handlers/hashing y pruebas HTTP/JPA de registro válido, duplicado normalizado en ambos roles, validación, ausencia de secretos, concurrencia y rollback. Se verifica la eliminación de rutas de landing. La integración visual de la app permanece pendiente.

## Entregables del siguiente paso

La retirada de US-01/US-02 y el registro de US-09/US-10 están completados. Para las fases posteriores se planifican los demás handlers, seguridad de sesiones/recuperación, OAuth, persistencia y reglas de Campaign y OpenAPI. La conexión del cliente sigue siendo posterior y solo para las historias del corte; esas fases no se implementaron en esta tarea.
