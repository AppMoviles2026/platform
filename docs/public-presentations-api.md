# US-01 y US-02 — API de presentación pública

Implementación del backend y refactor a Identity completados el 05/10/2026. Las historias originales son de landing; estos endpoints permiten centralizar el contenido para que lo consulte tanto la landing como la app móvil. La presentación visual y la comprensión del usuario se validarán en el cliente, que aún no está conectado.

## Endpoints disponibles

| Historia | Método y ruta | Propósito |
|---|---|---|
| US-01 | GET /api/v1/public/presentations/brands | Propuesta de valor y beneficios para pymes. |
| US-02 | GET /api/v1/public/presentations/creators | Propuesta de valor y beneficios para creadores. |

Ambos responden 200 application/json y son públicos: no requieren sesión ni un ID de usuario. Su contenido está en español; cambio de idioma US-06 no está implementado. No crean cuentas, campañas ni colaboraciones.

Ejemplo de estructura (el endpoint devuelve la lista completa de beneficios):

```json
{
  "audience": "BRAND",
  "language": "es",
  "title": "Más control para tu marca",
  "valueProposition": "CollabPro permite a las pymes gestionar campañas y colaboraciones con creadores de contenido de forma profesional y justa, con condiciones claras para ambas partes.",
  "benefits": [
    {
      "code": "campaigns",
      "title": "Centraliza tus campañas",
      "description": "Organiza las campañas con creadores y sus objetivos de marketing en un mismo espacio."
    }
  ]
}
```

Los códigos de beneficios son estables para el cliente. US-01 contiene campaigns, requirements, deliverables, compensation, tracking. US-02 contiene discovery, management, requirements, deliverables, dates, compensation. Cada elemento expone título y explicación.

## Arquitectura

Ambas consultas se alojan en **Identity & Profile Management**, como extensión técnica de presentación previa al registro, según el plan revisado. El reporte no define un dominio editorial; esta ubicación es una decisión técnica, no una nueva regla de Account. Se mantienen únicamente sus cinco bounded contexts y Shared. No existe un módulo adicional al mismo nivel ni se coloca contenido comercial en Shared.

- Domain: el modelo Account/perfiles permanece intacto; estas consultas no necesitan un agregado ni nuevas entidades del dominio.
- Application: GetPresentationQuery, GetPresentationQueryHandler y puerto PresentationContentProvider. Proyecciones inmutables PublicPresentationView, PresentationBenefitView y PresentationAudience; la audiencia del visitante no es un rol autenticado.
- Infrastructure: PropertiesPresentationContentProvider en `identity/infrastructure/content` y composición Spring PublicPresentationConfiguration.
- Interfaces: PublicPresentationController en `identity/interfaces/rest`, resources y PresentationResourceAssembler.

Flujo: controller → query handler → puerto de contenido → adapter de catálogo versionado → proyección → recurso JSON. Application es Java puro y no depende de Spring, persistencia ni otros contextos. No se necesitan commands ni agregados mutables para dos consultas de lectura: tampoco hay un CRUD de contenido o administradores definido por estas historias.

El contenido real se carga desde `src/main/resources/identity/presentations/presentations_es.properties`, leído como UTF-8 una vez al iniciar. No es una base de datos simulada: es el catálogo publicado, cuyo cambio se versiona y requiere reiniciar/republicar la aplicación. El arranque falla claramente si faltan propiedades obligatorias, hay descripciones vacías o códigos de beneficio duplicados. Las listas expuestas son inmutables.

## Consumo previsto desde las pantallas existentes

AboutScreen(brand=true) consulta /brands; AboutScreen(brand=false) consulta /creators. El cliente usa title, valueProposition y benefits para sustituir su texto fijo, con estado de carga/error. WelcomeScreen puede reutilizar ese contenido por segmento. La app actual de E:/CollabPro sigue siendo un prototipo local y no fue modificada en esta tarea.

No usar el rol de una cuenta para esta consulta: un visitante sin registro puede escoger cualquiera de las dos presentaciones. Si posteriormente se agrega Spring Security, permitir explícitamente GET /api/v1/public/presentations/**.

## Ejecutar y consultar

```powershell
.\mvnw.cmd spring-boot:run
Invoke-RestMethod 'http://localhost:8080/api/v1/public/presentations/brands'
Invoke-RestMethod 'http://localhost:8080/api/v1/public/presentations/creators'
.\mvnw.cmd test
```

Para el problema de cache de Maven de esta máquina, utilizar el MAVEN_USER_HOME alternativo documentado en el README.

## Cobertura de aceptación y pruebas

| Historia / escenario del reporte | Respuesta del backend verificada |
|---|---|
| US-01 / 1: propuesta de valor | Gestión de campañas con creadores de manera profesional y justa. |
| US-01 / 2: beneficios | Campañas, requisitos, entregables, compensación y seguimiento con título y descripción. |
| US-02 / 1: propuesta de valor | Encontrar y gestionar campañas y colaboraciones con empresas. |
| US-02 / 2: condiciones antes de aceptar | Requisitos, entregables, fechas y compensación explicados antes de comprometerse. |

PresentationsApiTests verifica esos cuatro escenarios con controller, handler y catálogo reales; además prueba que POST no está disponible, que una audiencia desconocida devuelve 404 y que ambos endpoints responden por HTTP en un servidor con puerto aleatorio. GetPresentationQueryHandlerTests verifica el puerto de lectura y validación de entradas sin Spring. PresentationContentProviderTests verifica UTF-8, ambas audiencias, listas inmutables y rechazo de contenido incompleto/duplicado. ArchitectureBoundariesTests comprueba imports de las capas internas y el inventario exacto de cinco contextos más Shared con cuatro capas cada uno. No sustituye una auditoría completa de arquitectura.

Verificación ejecutada: `mvnw.cmd -B clean test` con cache aislado, 17 pruebas satisfactorias incluyendo el arranque de Spring Boot. No se implementaron otras historias ni se modificó la app móvil. La cobertura del backend no demuestra por sí sola que un visitante comprenda la propuesta en la interfaz.
