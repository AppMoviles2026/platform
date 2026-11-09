# Plan de implementación del backend de CollabPro

Alcance aprobado: máximo diez bloques de implementación, organizados por bounded context. Las tareas de cada US se mantienen separadas dentro de su bloque. Fuentes: [reporte](../../report/README.md), código y contratos Android actuales.

La numeración identifica bloques de trabajo; los commits de control de versiones los realiza manualmente el usuario. No se ejecutan comandos de Git.

## Estado actual — 9 de noviembre de 2026

**Tres bloques implementados y siete pendientes. El siguiente es el bloque 04.** «Implementado» describe el código y sus verificaciones, no confirma que el usuario haya creado el commit manual ni desplegado los cambios.

| Bloque | Estado | Contexto | US y soporte | Entrega |
| --- | --- | --- | --- | --- |
| 01 | Implementado | Identity | US-12; regresión US-09/10/11/13/14; TS-01/02; SS-02 | Perfil empresarial, contratos de perfiles y ciclo de credenciales sociales. |
| 02 | Implementado | Campaign | US-15/16/19/20; regresión US-17/18; TS-03 | Edición de borradores, versiones, evaluación y resultado de postulaciones. |
| 03 | Implementado | Collaboration | US-21/22; TS-04 | Acuerdo bilateral, activación idempotente y consulta del ciclo de vida. |
| 04 | Pendiente | Collaboration | US-23/24; TS-04/05 | Entregas puntuales/tardías, archivos, revisión y correcciones versionadas. |
| 05 | Pendiente | Collaboration | US-25/30; TS-05 | Incidencias, resolución e historial por participante. |
| 06 | Pendiente | Billing | US-26; TS-06; SS-01 | Validación Sandbox del proveedor, vinculación financiera y base de webhooks. |
| 07 | Pendiente | Billing | US-27; TS-06 | Planes y suscripciones confirmadas por el proveedor. |
| 08 | Pendiente | Billing | US-28; TS-05/06 | Compensaciones, bloqueos, transacciones y pagos sin duplicación. |
| 09 | Pendiente | Performance | US-29, escenarios 1–3; TS-07; SS-03 | Métricas con fuente/periodo y evidencias manuales diferenciadas. |
| 10 | Pendiente | Performance e integración | US-29, escenarios 4–5; TS-07; SS-03 | Atribución, estimaciones y validación de recorridos completos. |

US-01 a US-08 corresponden a la landing y quedan fuera de estos bloques. El consumo de los contratos nuevos desde Android se implementará posteriormente.

## Capacidades que ya están disponibles

- **Base previa:** registro empresarial y creador, login JWT con rol del servidor, recuperación/restablecimiento de acceso, perfil creador, OAuth y listado de redes; creación de campañas, condiciones, publicación, cierre y descarte de borradores, búsqueda/detalle y postulaciones propias.
- **Bloque 01:** consulta y edición del perfil empresarial con versión; consultas internas por ID de perfil; estado, renovación y reautorización de credenciales sociales; migración V8 y entorno de pruebas MySQL/Mailpit aislado. Detalles en [identity-contracts.md](identity-contracts.md).
- **Bloque 02:** PATCH de borradores, versión de campaña y comprobación de condiciones; PATCH de postulaciones con versión obligatoria; consulta empresarial de postulantes, selección/rechazo con auditoría y resultado visible al creador; migración V9 y seed corregido/ampliado. Detalles en [campaign-contracts.md](campaign-contracts.md).
- **Bloque 03:** acuerdos bilaterales desde selecciones, condiciones inmutables, firma/rechazo explícitos por participante, activación idempotente, obligaciones con cantidades y fechas, estado/vencimientos, outbox con recibos y reintentos, migración V10 y demo bilateral. Detalles en [collaboration-contracts.md](collaboration-contracts.md).

La regresión completa registrada tras el bloque 03 reúne **216 casos aprobados**, sin fallos, errores ni omisiones, con MySQL 8.4 y Mailpit aislados. Las pruebas específicas de actualización utilizan H2 dedicado. Se verificaron además firmas y propuestas simultáneas, activaciones repetidas, decisiones opuestas, rollback ante fallos, vencimientos, outbox, seed y Swagger. La regresión completa H2 finalizó también sin fallos: 215 aprobadas y una prueba de Mailpit omitida en ese entorno.

### Límites y verificaciones que siguen pendientes

- Las redes sociales se probaron con respuestas HTTP controladas. Configurar y verificar consentimiento/renovación con cuentas externas reales sigue pendiente; SS-02 no debe presentarse como validación real concluida.
- Android todavía debe consumir las rutas nuevas. Las condiciones y mutaciones heredadas que aceptan versión opcional conservan compatibilidad; los clientes deben enviar versión para detectar una pantalla desactualizada. Las rutas nuevas que la requieren están documentadas.
- Seleccionar una postulación sigue sin crear una colaboración ni una aceptación bilateral. El bloque 03 exige las dos firmas y cubre US-21/22. Entregas/revisiones e incidencias/historial continúan en 04–05; Billing y Performance conservan su estructura base para 06–10.
- Los resultados locales no acreditan que estos cambios estén desplegados en Azure. Ese despliegue y su verificación son independientes del estado de implementación.

## Bloque 01: alcance de implementación

Estado del código: implementado y verificado el 8 de noviembre de 2026. Los bloques 02 y 03 también están implementados; los bloques 04–10 continúan pendientes. La verificación contra cuentas reales de redes sociales requiere configurar las aplicaciones externas; se detalla separadamente en los contratos de Identity.

Mensaje sugerido para el commit manual: `feat(identity): complete brand profiles and authorized social access`.

- US-12: consulta y actualización del perfil empresarial desde la sesión, campos permitidos, validación, versión y protección frente a ediciones antiguas o simultáneas.
- Consultas internas por identificador de perfil empresarial y creador, manteniendo `accountId` y `profileId` diferenciados y sin exponer credenciales.
- SS-02: estado de permisos/vencimiento, renovación soportada por proveedor, necesidad de reautorizar, sustitución de credenciales de la misma identidad y preservación del callback Android de un solo uso.
- Regresión de registro, JWT, recuperación, perfil creador, autorización aceptada/rechazada y duplicados.
- Alinear Java entre Maven y Docker, añadir migración nueva y preparar ejecución MySQL/Mailpit aislada.
- Documentar endpoints, errores, integración y verificaciones externas pendientes en [identity-contracts.md](identity-contracts.md).

## Bloques 02 y 03 implementados y commits sugeridos para lo pendiente

### 02. Campaign

Estado: implementado. Contratos, compatibilidad y escenarios en [campaign-contracts.md](campaign-contracts.md). Mensaje sugerido para el commit manual: `feat(campaign): implement versioned drafts and application evaluation`.

Depende de 01. Separar metadatos de borradores (US-15), condiciones y fechas/versión (US-16), requisitos/duplicados/edición/cancelación (US-19) y consulta/selección/rechazo empresarial (US-20). Conservar filtros, detalle, disponibilidad y paginación de US-17/18. La selección no activa una colaboración ni rechaza automáticamente a los demás postulantes. Registrar cambios de payload que requieran adaptación móvil.

### 03. Collaboration: acuerdo y estado

Estado: **implementado y verificado el 9 de noviembre de 2026**. Contratos y alcance en [collaboration-contracts.md](collaboration-contracts.md).

Commit manual sugerido: `feat(collaboration): implement bilateral agreements and lifecycle`.

Depende de 02. US-21 implementa selección previa, snapshot inmutable y decisiones de ambos participantes sobre el mismo `termsVersion`; un rechazo impide la activación. La segunda firma activa una sola colaboración, con protección ante reintentos y concurrencia. US-22 consulta estado, obligaciones, acciones pendientes y vencimientos con hora del servidor. Se incluyen los escenarios de crear/consultar de TS-04, migración V10, contratos ACL y eventos persistidos con recibos/reintentos. Las entregas de TS-04 siguen en el bloque 04.

Regla confirmada por el usuario: aceptación hasta la primera fecha de entrega acordada, aunque haya cerrado la recepción de postulaciones; al llegar ese instante ya no se puede aceptar. El seed añade una colaboración demo explícita y conserva acuerdos existentes. Android consumirá estas rutas posteriormente.

### 04. Collaboration: entregas y revisión

Estado: **pendiente; siguiente bloque a implementar**.

Commit manual sugerido: `feat(collaboration): implement submissions reviews and revisions`.

Depende de 03. US-23 admite entrega válida y tardía; respetar cantidades, propiedad, referencias y archivos. US-24 conserva versiones y revisiones, requiere observaciones al rechazar y permite corrección según lo acordado. No aprobar una nueva versión mediante una decisión antigua ni marcar un pago como realizado al aprobar contenido.

### 05. Collaboration: incidencias e historial

Estado: **pendiente**.

Commit manual sugerido: `feat(collaboration): implement incidents and collaboration history`.

Depende de 04. US-25 conserva registro/resolución de incidencias e historial de acciones, con plazo y autoridad de resolución explícitos. Varias incidencias abiertas mantienen sus bloqueos independientes. US-30 incluye historial paginado existente/vacío y detalle propio para ambos participantes, conservando condiciones históricas y referencias a compensación/resultados.

### 06. Billing: vinculación financiera

Estado: **pendiente**.

Commit manual sugerido: `feat(billing): implement verified payment method linking`.

Depende de 01 y usa 03–05 para el flujo de compensación de SS-01. Validar el proveedor Sandbox antes de presentar una integración real como terminada. US-26 cubre vinculación correcta/fallida y operación sin medio requerido. Distinguir medios para cobro de destinos para recibir pagos; tokenizar, verificar retornos y preparar webhooks firmados/idempotentes.

### 07. Billing: suscripción

Estado: **pendiente**.

Commit manual sugerido: `feat(billing): implement company subscriptions`.

Depende de 06. US-27 obtiene planes/precios/monedas desde el servidor. Medio válido y confirmación del proveedor son necesarios para activar la suscripción; un cobro fallido no activa beneficios. Evitar suscripciones o cargos duplicados y conservar estados pendientes. No imponer nuevas restricciones comerciales a campañas sin una regla del negocio.

### 08. Billing: compensación

Estado: **pendiente**.

Commit manual sugerido: `feat(billing): implement compensation lifecycle and payments`.

Depende de 03–07. US-28 crea la compensación desde el acuerdo, la mantiene pendiente hasta el cumplimiento, refleja incidencias y registra el pago confirmado. Cubrir autorización, fallo, espera, timeout, reintento, concurrencia y eventos duplicados/fuera de orden. Los canjes no realizan cargos. Una incidencia posterior no borra el hecho de un pago ni inicia automáticamente un reembolso.

### 09. Performance: métricas y evidencia

Estado: **pendiente**.

Commit manual sugerido: `feat(performance): implement sourced metrics and manual evidence`.

Depende de 01, 04 y 05. US-29 escenarios 1–3: métricas disponibles con fuente/periodo, ausencia explícita sin inventar ceros y evidencia manual diferenciada. SS-03 verifica capacidades y restricciones reales; el permiso de perfil básico no garantiza estadísticas. Probar permisos, vencimientos, caída del proveedor, snapshots repetidos y evidencia ajena/inválida.

### 10. Performance: atribución e integración

Estado: **pendiente**.

Commit manual sugerido: `feat(performance): implement attribution and verify backend journeys`.

Depende de 01–09. US-29 escenarios 4–5: enlaces/códigos de colaboración, interacciones atribuibles sin duplicados y resultados con límites/advertencia de estimación. Impedir destinos arbitrarios y no equiparar clics a ventas verificadas. Ejecutar recorridos completos con MySQL y ambos roles, actualizaciones de esquema sin borrar datos, seed idempotente y revisión final de contratos móviles y OpenAPI.

## Reglas de cierre por bloque

Mantener los diez bloques originales: tres implementados y siete restantes. Cada bloque pendiente es una entrega funcional con su mensaje de commit sugerido; no se añaden commits separados para capas, pruebas o documentación. Los commits los crea manualmente el usuario cuando revise la entrega.

Cada bloque incluye dominio, aplicación, infraestructura, REST, migración nueva, pruebas de sus escenarios y documentación. Mantener las cuatro capas, JWT y autorización por objeto, errores `ApiError`, fechas del servidor y control de reintentos/concurrencia. Las pruebas finales complementan las de cada bloque, no las sustituyen. Mantener separadas las pruebas controladas y las verificaciones externas reales.

La vigencia de aceptación quedó confirmada en el bloque 03: primera fecha de entrega. Antes de los bloques siguientes deben concretarse plazos de corrección, ventana y autoridad de incidencias, viabilidad financiera, catálogo comercial, tratamiento de compensación mixta y métricas respaldadas. No fijar reglas comerciales arbitrarias en el código.
