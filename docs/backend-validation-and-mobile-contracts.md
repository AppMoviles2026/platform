# Verificación del backend y contratos para Android

Bloque 10: cierre de los recorridos no financieros de los bloques 01–05 y 09–10. Actualización del bloque 06: la vinculación PayPal Sandbox y la base de webhooks están implementadas; Billing 07–08 sigue pendiente. Este documento registra revisión de contratos y pruebas del backend; no declara implementado su consumo nuevo desde Android ni desplegados los cambios en Azure.

## Recorridos comprobados

| Recorrido | Evidencia automatizada |
| --- | --- |
| Registro de ambos roles → login JWT → consultas de cuenta/perfil | RegistrationApiTests, pruebas API de Identity y fixtures de los recorridos completos. |
| Recuperación → correo → restablecimiento → login | RecoveryMailpitTests, con MySQL/Mailpit aislados; el envío externo de producción no se simula como verificado. |
| Perfil/credenciales sociales → permisos → renovación → métricas autorizadas | SocialCredentialLifecycleTests y AuthorizedSocialMetricsProviderTests. HTTP del proveedor controlado, sin cuentas sociales externas reales. |
| Borrador → condiciones → publicación → búsqueda/detalle → postulación → selección | Pruebas API de Campaign y creación real por HTTP de los fixtures de Collaboration/Attribution. |
| Selección → acuerdo inmutable → aceptación de empresa y creador → colaboración | CollaborationApiTests y recorrido completo de AttributionApiTests. Seleccionar no implica firmar. |
| Entrega → rechazo con nuevo plazo → corrección versionada → aprobación → finalización | DeliveryApiTests y AttributionApiTests.fullNonFinancialJourneyPreservesOriginalTermsAndReachesResultsForBothRoles. |
| Incidencia posterior → propuesta → aceptación de contraparte → historial | IncidentApiTests, CollaborationHistoryApiTests y el mismo recorrido completo de atribución. |
| Evidencia manual → código/enlace → declaración/clic → resultados para ambos roles | AttributionApiTests y PerformanceApiTests; métricas automáticas ausentes siguen siendo NO_DATA. |
| Login de ambos roles → consentimiento financiero → método propio → validación/revocación | BillingApiTests con proveedor controlado; PayPalGatewayTests verifica el protocolo HTTP. Autenticación y preparación Vault externas también comprobadas; falta consentimiento externo completo. |
| Reintentos/concurrencia → rollback → permisos por objeto → archivos privados | Pruebas específicas de Campaign, Collaboration, Performance y Attribution; claves duplicadas no generan otro hecho. |
| Esquemas V5–V13 → esquema actual → arranque repetido/seed | BackendMigrationUpgradeTests con H2 dedicado; aplicación y seeds comprobados con MySQL aislado. |
| Contrato REST y documentación pública | OpenApiDocumentationTests: bearer, rutas, 201/302, esquemas obligatorios y descargas binarias. |
| Separación de bounded contexts | ArchitectureBoundariesTests: cuatro capas, dependencias hacia dentro y contratos publicados. |

El recorrido final conserva snapshot de condiciones y completedAt de la colaboración y consulta resultados e historial con ambos JWT. La compensación continúa NOT_AVAILABLE: no existe pago real, cobro simulado ni suscripción activa por completar ese recorrido.

## Resultado registrado del bloque 06

Verificación del bloque 06, 10 de noviembre de 2026: **376 casos únicos aprobados con el entorno MySQL 8.4/Mailpit**, sin fallos, errores ni omisiones. Se ejecutó una regresión completa de 372 casos y, tras revisar la recuperación de Vault y respuestas inválidas, se revalidaron las 33 pruebas del adaptador; cuatro son casos adicionales. La regresión H2 final ejecutó **376 casos: 375 aprobados y una omisión de RecoveryMailpitTests**, cubierta satisfactoriamente en MySQL/Mailpit. Incluye 24 pruebas API de Billing, 33 de protocolo/cifrado, nueve de migraciones, arquitectura, Swagger y toda la funcionalidad anterior. Compose y enlaces internos también están comprobados. El código nuevo no fue desplegado en Azure ni conectado todavía desde Android.

La autenticación externa y la preparación de Vault se comprobaron con PayPal Sandbox, HTTP 200/201 respectivamente; consentimiento humano y webhooks entrantes públicos permanecen pendientes. [Contratos y configuración](billing-linking-contracts.md).

## Resultado registrado del bloque 10 (referencia previa)

- MySQL 8.4/Mailpit aislados: **318 casos aprobados**, sin fallos, errores ni omisiones; regresión completa y comprobación posterior de atribución para verificar también comparación exacta del token público con la collation de MySQL.
- H2: **317 aprobados y uno omitido**, sin fallos ni errores. La única omisión es RecoveryMailpitTests, que requiere el servicio de correo y sí pasó en la ejecución MySQL.
- Compose validado con docker compose config --quiet; enlaces internos de los documentos actualizados comprobados.
- Servicios de prueba detenidos conservando sus volúmenes; la base normal de la aplicación no se utilizó ni se borró.

## Revisión del cliente Kotlin existente

Se revisaron IdentityApi.kt, IdentityDtos.kt, CampaignApi.kt, CampaignDtos.kt, CampaignMappers.kt, ApiJson.kt y las pantallas/modelos actuales de Collaboration y Performance en el proyecto Android.

| Área | Compatibilidad actual y adaptación pendiente |
| --- | --- |
| Registro/login/recuperación | Se conservan rutas y campos que usa IdentityApi. JWT, rol del servidor e instantes ISO-8601 permanecen. |
| Perfil creador y OAuth | Se conservan los cuerpos/listados y el retorno Android de autorización. Cambiar scopes requiere nuevo consentimiento. |
| Perfil empresarial | GET/PUT disponibles; edición con expectedVersion. Añadir repositorio/DTO/estado de UI cuando se conecte esta pantalla. |
| Campañas | Creación, búsqueda, detalle y condiciones heredadas siguen disponibles. CampaignDto aún no incluye version y ConditionsDto no envía expectedVersion: incorporar ambos para detectar cambios concurrentes. El PATCH nuevo exige versión. |
| Postulaciones propias | Se mantiene PUT y cancellation con expectedVersion; ApplicationDto.version ya existe. El enum Kotlin admite PENDING, SELECTED, REJECTED y CANCELLED. Mantener UUID de requisitos y request IDs al reintentar. |
| Evaluación empresarial | Añadir listado/detalle de postulantes y decisión con expectedVersion; la selección no activa la colaboración por sí sola. |
| Acuerdos y colaboración | Las pantallas existentes siguen usando datos de ejemplo. Añadir DTO/repositorio/ViewModel para acuerdos, termsVersion, decisiones y colaboración devuelta por el servidor. |
| Entregas/incidencias/historial | Consumir los contratos documentados, con IDs de versiones, expectedVersion y claves persistidas durante reintentos. Usar JWT también para descargar evidencia privada. |
| Métricas y evidencia | Añadir consumo de Performance, tratar NO_DATA/AVAILABLE_STALE/HISTORICAL_ONLY, separar MANUAL de AUTOMATED y conservar fuente/periodo. |
| Atribución | Cargar destinos por ID, crear LINK/CODE, usar trackingUrl absoluto y mostrar availability real. Conservar eventId del mismo hecho aunque cambie requestId. Mostrar fuentes, advertencia, intervalo y demostraciones separadas. |
| Billing | Añadir repositorio/DTO/ViewModel para begin/listado/consulta/validación, conservar requestId, abrir approvalUrl y recibir collabpro://billing-return con posterior consulta JWT. Mostrar errorCode, vencimiento y estados reales. No declarar LINKED por el deep link sin consultar el servidor; 07/08 permanecen pendientes. |

Gson en ApiJson tolera propiedades nuevas de respuesta y ya maneja Instant. Los contratos posteriores se documentaron sin quitar rutas heredadas. No se modificó código Kotlin en este bloque ni se acredita una prueba visual del emulador.

## Ejecución reproducible

Con JDK 21 o superior:

```powershell
.\mvnw.cmd test
.\scripts\test-mysql.ps1
```

La segunda ejecución usa compose.test.yaml, base MySQL y Mailpit separados; no borra ni utiliza el volumen normal de la aplicación. Las pruebas de actualización tienen bases H2 dedicadas, incluso durante la ejecución MySQL.

Para reconstruir la aplicación local tras aplicar el código:

```powershell
docker compose up --build -d
```

Swagger local: http://localhost:8081/swagger-ui/index.html. Configurar el origen público y destinos de atribución antes de compartir enlaces fuera del equipo; detalles en [attribution-contracts.md](attribution-contracts.md).

Las respuestas de proveedores usadas en pruebas son controladas. Consentimiento/mediciones con cuentas sociales reales, integración financiera, consumo móvil posterior y comprobación del despliegue Azure permanecen diferenciados de la verificación local.
