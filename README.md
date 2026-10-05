# CollabPro Platform — base DDD

Base de backend Spring Boot/Java para preparar las primeras 18 **posiciones del Product Backlog** del reporte. [Plan detallado y contratos propuestos](docs/implementation-plan.md).

## Alcance actual

US-09 y US-10 implementadas: registro de empresa y creador en Identity, con Account/perfil inicial, commands/handlers, value objects, hashing, persistencia JPA y migración Flyway. Las primeras ocho posiciones son de landing; se retiró toda la API informativa de US-01/US-02. Los cinco bounded contexts del informe y Shared conservan cuatro capas; los demás casos de uso siguen pendientes.

```text
com.collabtech.platform
├── identity          # registro implementado; acceso/perfiles/redes pendientes
├── campaign          # Campaign y Application (postulación)
├── collaboration     # reservado, fuera del corte
├── billing           # reservado, fuera del corte
├── performance       # reservado, fuera del corte
└── shared            # contratos técnicos mínimos
    [cada paquete contiene domain/application/infrastructure/interfaces]
```

Los nombres de paquete son minúsculas conforme a Java. Shared contiene contratos técnicos, no un sexto dominio de negocio ni modelos internos compartidos. Domain/Application no importan Spring/JPA/HTTP; las entidades persistidas y transacciones se ubican en Infrastructure y se mapean al agregado Account.

## Ejecución de la base

Se conservan Spring Boot 4.1.1 y Java 17. Se agregan Flyway, soporte MySQL para Flyway y Spring Security Crypto (solo hashing, sin sesiones ni filtros de login). H2 se utiliza únicamente en tests. El perfil por defecto es `local`, conectado a MySQL 8.4 en Docker, puerto 3307. El MySQL instalado en Windows no se modifica.

```powershell
docker compose up -d --wait
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run
```

Disponibles `POST /api/v1/auth/brands` y `POST /api/v1/auth/creators`. Ver [contrato, ejemplos y pruebas](docs/registration-api.md). Las antiguas rutas `/api/v1/public/presentations/**` ya no existen.

Compose usa la base `collabpro`, usuario `collabpro`, volumen persistente y puerto publicado solo en localhost. Las contraseñas por defecto son exclusivas de desarrollo local y están visibles en Compose: no utilizarlas en producción. Pueden reemplazarse con MYSQL_PASSWORD/MYSQL_ROOT_PASSWORD; la aplicación también acepta MYSQL_URL y MYSQL_USER. Si cambia MYSQL_PORT, ajustar MYSQL_URL. Cambiar variables no cambia las credenciales de un volumen ya inicializado; no borrar ese volumen para resolverlo sin respaldar los datos.

Flyway crea únicamente las tablas nuevas de Identity y JPA valida el esquema (`ddl-auto=validate`). No hay cuentas demo en la base local. El perfil opcional `skeleton` permite arrancar sin DB y no expone registros. Para detener MySQL: `docker compose stop` (conserva el volumen).

El cache de Maven Wrapper existente en esta máquina falló con clases de Maven faltantes. Se puede descargar una copia aislada sin modificar/borrar la existente:

```powershell
$env:MAVEN_USER_HOME = Join-Path ([System.IO.Path]::GetTempPath()) 'collabpro-scaffold-maven-cache'
.\mvnw.cmd test
```

## Siguiente paso

US-01–08 son de landing y están excluidas del backend. US-09/10 están implementadas; siguen pendientes US-11, US-13–19 dentro del corte aplicable. US-12 y US-20 en adelante quedan fuera. La app móvil no se modificó: registrar devuelve una cuenta, no una sesión; no navegar a Home como si hubiera login hasta implementar US-11.

Pruebas rápidas: `mvnw.cmd test` usa H2 aislado. Para verificar las mismas pruebas contra MySQL en Docker: `./scripts/Test-MySqlRegistration.ps1`, que crea una base separada `collabpro_test`, sin borrar registros existentes. Las pruebas generan correos aleatorios y no escriben en `collabpro`.
