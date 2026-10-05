# CollabPro Platform — base DDD

Base de backend Spring Boot/Java para preparar las primeras 18 **posiciones del Product Backlog** del reporte. [Plan detallado y contratos propuestos](docs/implementation-plan.md).

## Alcance actual

US-09/10: registro de empresa y creador. US-11: acceso y recuperación; US-13: consulta/edición del perfil creador; US-14: inicio/callback OAuth y persistencia de asociaciones sociales, con adapters configurables para Instagram/TikTok. Todo pertenece a Identity, con Domain/Application sin Spring y adapters en Infrastructure. La autorización contra proveedores reales requiere credenciales y permisos de desarrollador; no se ha verificado todavía. Las primeras ocho posiciones son de landing y siguen excluidas. Los cinco bounded contexts del informe y Shared conservan cuatro capas.

```text
com.collabtech.platform
├── identity          # registro, acceso, recuperación, perfil creador y asociación social
├── campaign          # Campaign y Application (postulación)
├── collaboration     # reservado, fuera del corte
├── billing           # reservado, fuera del corte
├── performance       # reservado, fuera del corte
└── shared            # contratos técnicos mínimos
    [cada paquete contiene domain/application/infrastructure/interfaces]
```

Los nombres de paquete son minúsculas conforme a Java. Shared contiene contratos técnicos, no un sexto dominio de negocio ni modelos internos compartidos. Domain/Application no importan Spring/JPA/HTTP; las entidades persistidas y transacciones se ubican en Infrastructure y se mapean al agregado Account.

## Ejecución de la base

Se conservan Spring Boot 4.1.1 y Java 17. Flyway versiona MySQL y Spring Security protege el acceso con sesiones Bearer opacas persistidas como hash. Spring Mail entrega recuperación mediante outbox cifrado. H2 se utiliza únicamente en tests. El perfil por defecto es `local`, conectado a MySQL 8.4 en Docker, puerto 3307. El MySQL instalado en Windows no se modifica.

```powershell
docker compose up -d --wait
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run
```

Disponibles `POST /api/v1/auth/brands` y `POST /api/v1/auth/creators`. Ver [contrato, ejemplos y pruebas](docs/registration-api.md). Las antiguas rutas `/api/v1/public/presentations/**` ya no existen.

Ver [API de US-11/13/14, configuración OAuth e integración móvil pendiente](docs/identity-access-profile-social-api.md). Compose también inicia Mailpit: SMTP en localhost:1025 y bandeja de desarrollo en http://localhost:8025. No envía correos a destinatarios reales. El enlace de recuperación predeterminado es `collabpro://password-reset`; la app aún necesita implementar su recepción y formulario. En producción configurar SMTP, RECOVERY_RESET_URL, HTTPS y una clave AES propia (`IDENTITY_ENCRYPTION_KEY`, Base64 de 32 bytes); la clave fija del perfil local es solo para desarrollo, y no debe cambiarse con secretos cifrados existentes sin migrarlos.

Compose usa la base `collabpro`, usuario `collabpro`, volumen persistente y puerto publicado solo en localhost. Las contraseñas por defecto son exclusivas de desarrollo local y están visibles en Compose: no utilizarlas en producción. Pueden reemplazarse con MYSQL_PASSWORD/MYSQL_ROOT_PASSWORD; la aplicación también acepta MYSQL_URL y MYSQL_USER. Si cambia MYSQL_PORT, ajustar MYSQL_URL. Cambiar variables no cambia las credenciales de un volumen ya inicializado; no borrar ese volumen para resolverlo sin respaldar los datos.

Flyway crea únicamente las tablas nuevas de Identity y JPA valida el esquema (`ddl-auto=validate`). No hay cuentas demo en la base local. El perfil opcional `skeleton` permite arrancar sin DB y no expone registros. Para detener MySQL: `docker compose stop` (conserva el volumen).

El cache de Maven Wrapper existente en esta máquina falló con clases de Maven faltantes. Se puede descargar una copia aislada sin modificar/borrar la existente:

```powershell
$env:MAVEN_USER_HOME = Join-Path ([System.IO.Path]::GetTempPath()) 'collabpro-scaffold-maven-cache'
.\mvnw.cmd test
```

## Siguiente paso

US-01–08 son de landing y están excluidas del backend. Los casos de Campaign US-15–19 siguen pendientes; US-12 y US-20 en adelante quedan fuera. La app móvil no se modificó: conectar los contratos de Identity, utilizar el rol devuelto al iniciar sesión y retirar las simulaciones sociales. Registrar sigue devolviendo una cuenta, no una sesión.

Pruebas rápidas: `mvnw.cmd test` usa H2 aislado (prueba SMTP opcional omitida). Para verificar Identity contra MySQL y entrega SMTP real a Mailpit: `./scripts/Test-MySqlRegistration.ps1`, que crea una base separada `collabpro_test`, sin borrar registros existentes. Las pruebas generan correos aleatorios y no escriben en `collabpro`. Los tests de OAuth utilizan fixtures HTTP y un stub explícito; no constituyen una autorización real con Instagram/TikTok.
