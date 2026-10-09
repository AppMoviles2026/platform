# CollabPro Platform

Backend REST de CollabPro con Spring Boot, Java 21, MySQL y arquitectura DDD por bounded context.

## Ejecutar localmente

Requiere Docker Desktop. Desde esta carpeta:

```powershell
docker compose up --build -d
docker compose ps
```

- API: `http://localhost:8081/api/v1/`
- Emulador Android: `http://10.0.2.2:8081/api/v1/`
- Mailpit: `http://localhost:8025`
- Detener sin borrar la base: `docker compose stop`

MySQL guarda sus datos en un volumen. No uses `docker compose down -v` salvo que quieras borrarlos.

## Cuentas de demostración

Compose carga automáticamente datos de ejemplo: 1 marca, 2 creadores, 4 campañas publicadas, 1 borrador y 3 postulaciones. El seed es idempotente: no duplica ni sobrescribe datos existentes.

| Rol | Correo |
|---|---|
| Marca | `demo.brand@collabpro.app` |
| Creadora | `demo.creator@collabpro.app` |
| Creador | `demo.creator2@collabpro.app` |

Contraseña local: `CollabProDemo2026!`. Para cambiarla antes del primer inicio, define `DEMO_SEED_PASSWORD`. Las cuentas de demostración no deben habilitarse en producción pública.

## API principal

Las rutas protegidas requieren `Authorization: Bearer <JWT>`. Inicia sesión en `POST /auth/sessions`.

| Área | Rutas bajo `/api/v1` |
|---|---|
| Cuenta y acceso | `POST /auth/brands`, `/auth/creators`, `/auth/sessions`, `/auth/recovery-requests`, `/auth/password-resets`; `GET /accounts/me` |
| Perfil creador | `GET, PUT /profiles/me/creator` |
| Campañas | `GET, POST /campaigns`; `GET /campaigns/{id}`; `GET /campaigns/mine`, `/campaigns/published`; condiciones, publicación, cierre y descarte por campaña |
| Postulaciones | `POST /campaigns/{id}/applications`; `GET /applications/mine`, `/applications/{id}`; `PUT /applications/{id}`; `POST /applications/{id}/cancellation` |
| Redes sociales | `POST /social-accounts/{platform}/authorizations`; `GET /social-accounts/{platform}/callback`, `/social-accounts/authorizations/{authorizationId}`, `/social-accounts/me` |

Para creación y detalle de contratos, consulta los controladores REST en `src/main/java/com/collabtech/platform`. Las migraciones de esquema están en `src/main/resources/db/migration`.

## Despliegue

El seed se activa en Compose local y está apagado por defecto fuera de Compose. En un entorno cloud de demostración, configura:

- `COLLABPRO_DEMO_SEED_ENABLED=true`
- `DEMO_SEED_PASSWORD` como secreto (mínimo 12 caracteres; se usa al crear las cuentas por primera vez)
- `SPRING_PROFILES_ACTIVE=prod`, conexión MySQL (`MYSQL_URL`, `MYSQL_USER`, `MYSQL_PASSWORD`), `JWT_SECRET` e `IDENTITY_ENCRYPTION_KEY`

Genera claves distintas y aleatorias para JWT y cifrado; no uses valores locales ni los subas al repositorio. Configura credenciales OAuth de Instagram/TikTok solo si habilitarás esa integración (`INSTAGRAM_*`, `TIKTOK_*`).

## Desarrollo

```powershell
.\mvnw.cmd test
```

Para ejecutar Spring fuera de Docker, inicia primero MySQL y Mailpit con `docker compose up -d mysql mailpit` y luego `.\mvnw.cmd spring-boot:run`.
