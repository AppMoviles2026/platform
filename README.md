# CollabPro Platform

Backend REST de CollabPro con Spring Boot, Java 21, MySQL y arquitectura DDD por bounded context.

## Ejecutar localmente

Requiere Docker Desktop. Desde esta carpeta:

```powershell
docker compose up --build -d
docker compose ps
```

- API: `http://localhost:8081/api/v1/`
- Swagger: `http://localhost:8081/swagger-ui/index.html`
- Emulador Android: `http://10.0.2.2:8081/api/v1/`
- Mailpit: `http://localhost:8025`
- Detener sin borrar la base: `docker compose stop`

MySQL guarda sus datos en un volumen. No uses `docker compose down -v` salvo que quieras borrarlos.

## Cuentas de demostración

Compose carga automáticamente datos de ejemplo: 1 marca, 2 creadores, 4 campañas publicadas, 1 borrador y 5 postulaciones (3 pendientes y 2 evaluadas). También crea un acuerdo aceptado entre la marca y `demo.creator2@collabpro.app`, con una entrega corregida pendiente de revisión, su rechazo histórico y una incidencia demo resuelta por ambas partes y una evidencia manual de estadísticas marcada como demostración (sin métricas automáticas ficticias). El seed añade también código/enlace demostrativos de atribución y un uso de código separado de los totales reales. También añade una colaboración independiente «Demo PayPal Sandbox USD» de USD 5 con una pieza aprobada, sin medios financieros ni pagos inventados. El seed es idempotente: no duplica ni sobrescribe datos existentes.

| Rol | Correo |
|---|---|
| Marca | `demo.brand@collabpro.app` |
| Creadora | `demo.creator@collabpro.app` |
| Creador | `demo.creator2@collabpro.app` |

Contraseña local: `CollabProDemo2026!`. Para cambiarla antes del primer inicio, define `DEMO_SEED_PASSWORD`. Las cuentas de demostración no deben habilitarse en producción pública.

## API principal

Las rutas protegidas requieren `Authorization: Bearer <JWT>`. Inicia sesión en `POST /auth/sessions`.

En Swagger, ejecuta `POST /api/v1/auth/sessions` con una cuenta demo, copia `accessToken` y pégalo en **Authorize** (sin escribir `Bearer`). Luego puedes usar **Try it out** en las rutas protegidas. El contrato OpenAPI está en `/v3/api-docs`.

| Área | Rutas bajo `/api/v1` |
|---|---|
| Cuenta y acceso | `POST /auth/brands`, `/auth/creators`, `/auth/sessions`, `/auth/recovery-requests`, `/auth/password-resets`; `GET /accounts/me` |
| Perfil creador | `GET, PUT /profiles/me/creator` |
| Perfil empresarial | `GET, PUT /profiles/me/brand`; el guardado requiere `expectedVersion` de la consulta |
| Campañas | `GET, POST /campaigns`; `PATCH /campaigns/{id}` con `expectedVersion`; `GET /campaigns/{id}`; `GET /campaigns/mine`, `/campaigns/published`; condiciones, publicación, cierre y descarte por campaña |
| Postulaciones | `POST /campaigns/{id}/applications`; `GET /applications/mine`, `/applications/{id}`; `PUT /applications/{id}`; `POST /applications/{id}/cancellation` |
| Acuerdos | `POST /agreements`; `GET /agreements/mine`, `/agreements/{id}`; `POST /agreements/{id}/decisions` con `termsVersion` |
| Colaboraciones | `POST /collaborations`; `GET /collaborations/mine`, `/collaborations/{id}` |
| Entregas y revisión | `GET, POST /collaborations/{id}/deliverables`; `GET /deliverables/{id}`; versiones, aprobación y rechazo por entrega |
| Archivos privados | `POST /collaborations/{id}/assets`; `GET /collaboration-assets/{id}` y `/collaboration-assets/{id}/content` con JWT |
| Incidencias | `GET, POST /collaborations/{id}/incidents`; detalle, propuestas y decisiones bajo `/incidents/{id}`; evidencia con `/collaborations/{id}/incident-assets` |
| Historial | `GET /collaborations/history`, `/collaborations/history/{id}`, `/collaborations/{id}/history` |
| Performance | `GET /collaborations/{id}/metrics`; actualización y capturas; `GET, POST /collaborations/{id}/evidence`; revisión bajo `/performance-evidence/{id}` y archivos privados de evidencia |
| Atribución | `GET, POST /collaborations/{id}/attribution`; destinos, estados y declaraciones bajo `/attribution`; enlace público solo GET/HEAD bajo `/public/attribution/{token}` |
| Compensación Sandbox | `GET /collaborations/{id}/compensation`; inicio, confirmación y actualización bajo `/collaborations/{id}/payment`; historial financiero por colaboración |
| Suscripciones Sandbox | `GET /subscription-plans`; `GET, POST /user/{id}/subscription`; historial, consulta, actualización y cancelación bajo `/subscriptions` |
| Vinculación financiera Sandbox | `GET, POST /user/{id}/billing`; consulta/cancelación de autorizaciones, validación de método, callbacks PayPal y `POST /webhooks/payments` |
| Redes sociales | `POST /social-accounts/{platform}/authorizations`; `GET /social-accounts/{platform}/callback`, `/social-accounts/authorizations/{authorizationId}`, `/social-accounts/me`, `/social-accounts/{socialId}/access` |

Evaluación empresarial: `GET /campaigns/{id}/applications`, detalle y `POST /campaigns/{id}/applications/{applicationId}/decision`. [Contratos y compatibilidad del bloque 02](docs/campaign-contracts.md). [Acuerdos bilaterales y estado: bloque 03](docs/collaboration-contracts.md). [Entregas, archivos y revisión: bloque 04](docs/delivery-contracts.md). [Incidencias e historial: bloque 05](docs/incident-history-contracts.md). [Métricas y evidencia: bloque 09](docs/performance-contracts.md). [Atribución: bloque 10](docs/attribution-contracts.md).

Para creación y detalle de contratos, consulta los controladores REST en `src/main/java/com/collabtech/platform`. Las migraciones de esquema están en `src/main/resources/db/migration`.

Métricas automáticas: TikTok requiere `video.list` y nuevo consentimiento si solo estaba autorizado el perfil básico. Instagram admite evidencia manual; su adaptador de estadísticas sigue pendiente. [Capacidades y validación externa pendiente](docs/ss03-metrics-feasibility.md).

PayPal Sandbox: completa `PAYPAL_CLIENT_ID` y `PAYPAL_CLIENT_SECRET` en el `.env` de la raíz y define `PAYPAL_ENABLED=true`. Las empresas autorizan Vault y los creadores verifican su cuenta receptora con Log in with PayPal. Para recibir webhooks configura también `PAYPAL_WEBHOOK_ID` y un host HTTPS público. [Configuración, contratos y verificación pendiente](docs/billing-linking-contracts.md). La vinculación financiera corresponde al bloque 06. El bloque 07 añade suscripciones de empresa; el bloque 08 completa compensaciones Sandbox.

Suscripciones de demo: Esencial USD 5 y Crecimiento USD 10 mensuales. `scripts/initialize-paypal-subscriptions.ps1` crea/reutiliza los planes Sandbox y guarda sus IDs en `.env`. [Contratos, confirmación del cobro y webhooks](docs/subscription-contracts.md). Un intento creado o aprobado por el navegador no activa beneficios hasta confirmar el pago.

Compensaciones: [contratos y demo Sandbox](docs/compensation-contracts.md). Conserva PEN y bloquea su pago sin convertirlo; el ejemplo USD permite probar aprobación, ingreso y envío al creador. Los canjes no generan cargos.

## Despliegue

El seed se activa en Compose local y está apagado por defecto fuera de Compose. En un entorno cloud de demostración, configura:

- `COLLABPRO_DEMO_SEED_ENABLED=true`
- `DEMO_SEED_PASSWORD` como secreto (mínimo 12 caracteres; se usa al crear las cuentas por primera vez)
- `SPRING_PROFILES_ACTIVE=prod`, conexión MySQL (`MYSQL_URL`, `MYSQL_USER`, `MYSQL_PASSWORD`), `JWT_SECRET` e `IDENTITY_ENCRYPTION_KEY`

Genera claves distintas y aleatorias para JWT y cifrado; no uses valores locales ni los subas al repositorio. Configura credenciales OAuth de Instagram/TikTok solo si habilitarás esa integración (`INSTAGRAM_*`, `TIKTOK_*`).

Para compartir enlaces de atribución, configura `PERFORMANCE_ATTRIBUTION_PUBLIC_BASE_URL` con el origen publicado (por ejemplo `http://40.75.23.68:8081`) y `PERFORMANCE_ATTRIBUTION_DESTINATIONS` con destinos HTTPS aprobados por ID. La configuración local incluye solo un destino de demostración. Los códigos y clics se muestran como estimaciones, sin certificar ventas ni pagos.

## Desarrollo

Requiere JDK 21 o superior (Java 21 en Docker). [Plan de diez bloques](docs/implementation-plan.md) · [Contratos de Identity y límites OAuth](docs/identity-contracts.md) · [Validación y contratos móviles](docs/backend-validation-and-mobile-contracts.md).

```powershell
.\mvnw.cmd test
```

Pruebas con MySQL y Mailpit aislados: `.\scripts\test-mysql.ps1`. No utiliza el volumen de la aplicación.

Para ejecutar Spring fuera de Docker, inicia primero MySQL y Mailpit con `docker compose up -d mysql mailpit` y luego `.\mvnw.cmd spring-boot:run`.
