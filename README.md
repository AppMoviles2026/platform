# CollabPro Platform — base DDD

Base de backend Spring Boot/Java para preparar las primeras 18 **posiciones del Product Backlog** del reporte. [Plan detallado y contratos propuestos](docs/implementation-plan.md).

## Alcance actual

Modelo estructural de Identity/Campaign, value objects, entidades, agregados, eventos, commands/queries y puertos de entrada/salida. Los cinco bounded contexts del informe y Shared tienen cuatro capas. US-01 y US-02 cuentan con una API pública de presentación dentro de Identity, como extensión de onboarding de solo lectura; los casos transaccionales y su persistencia siguen pendientes.

```text
com.collabtech.platform
├── identity          # Account, perfiles, autorización social y presentación pública
├── campaign          # Campaign y Application (postulación)
├── collaboration     # reservado, fuera del corte
├── billing           # reservado, fuera del corte
├── performance       # reservado, fuera del corte
└── shared            # contratos técnicos mínimos
    [cada paquete contiene domain/application/infrastructure/interfaces]
```

Los nombres de paquete son minúsculas conforme a Java. Shared contiene contratos técnicos, no un sexto dominio de negocio ni modelos internos compartidos. Domain no importa Spring/JPA/HTTP; las entidades persistidas se ubicarán en Infrastructure y se mapearán a los agregados. Las presentaciones US-01/02 son proyecciones de Application, no agregados del dominio Account.

## Ejecución de la base

Se conserva el POM inicial (Spring Boot 4.1.1, Java 17). El perfil por defecto `skeleton` desactiva la autoconfiguración de DB/JPA; permite iniciar el proceso sin MySQL mientras se preparan adapters. No es un perfil de despliegue productivo.

```powershell
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run
```

Están disponibles `GET /api/v1/public/presentations/brands` y `GET /api/v1/public/presentations/creators`, públicos y sin MySQL. Ver [contrato, arquitectura y pruebas](docs/public-presentations-api.md). Para habilitar persistencia posteriormente, crear un perfil local con datasource y activar ese perfil, sin copiar exclusiones de skeleton.

El cache de Maven Wrapper existente en esta máquina falló con clases de Maven faltantes. Se puede descargar una copia aislada sin modificar/borrar la existente:

```powershell
$env:MAVEN_USER_HOME = Join-Path ([System.IO.Path]::GetTempPath()) 'collabpro-scaffold-maven-cache'
.\mvnw.cmd test
```

## Siguiente paso

Seguir el plan por dependencias, respetando el corte de las primeras 18 posiciones. US-01/US-02 tienen contenido servido por la API; su integración visual queda pendiente. US-03–08 corresponden a landing; las restantes historias de backend incluyen US-17, US-18, US-19, US-10, US-11, US-13, US-14, US-15, US-16 y US-09. US-12 (perfil empresa editable) y US-20 en adelante quedan fuera. Los perfiles iniciales del registro sí están preparados.
