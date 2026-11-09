# Plan de implementación del backend de CollabPro

Alcance aprobado: máximo diez bloques de implementación, organizados por bounded context. Las tareas de cada US se mantienen separadas dentro de su bloque. Fuentes: [reporte](../../report/README.md), código y contratos Android actuales.

La numeración identifica bloques de trabajo; los commits de control de versiones los realiza manualmente el usuario. No se ejecutan comandos de Git.

| Bloque | Contexto | US y soporte | Entrega |
| --- | --- | --- | --- |
| 01 | Identity | US-12; regresión US-09/10/11/13/14; TS-01/02; SS-02 | Perfil empresarial, contratos de perfiles y ciclo de credenciales sociales. |
| 02 | Campaign | US-15/16/19/20; regresión US-17/18; TS-03 | Edición de borradores, versiones, evaluación y resultado de postulaciones. |
| 03 | Collaboration | US-21/22; TS-04 | Acuerdo bilateral, activación idempotente y consulta del ciclo de vida. |
| 04 | Collaboration | US-23/24; TS-04/05 | Entregas puntuales/tardías, archivos, revisión y correcciones versionadas. |
| 05 | Collaboration | US-25/30; TS-05 | Incidencias, resolución e historial por participante. |
| 06 | Billing | US-26; TS-06; SS-01 | Validación Sandbox del proveedor, vinculación financiera y base de webhooks. |
| 07 | Billing | US-27; TS-06 | Planes y suscripciones confirmadas por el proveedor. |
| 08 | Billing | US-28; TS-05/06 | Compensaciones, bloqueos, transacciones y pagos sin duplicación. |
| 09 | Performance | US-29, escenarios 1–3; TS-07; SS-03 | Métricas con fuente/periodo y evidencias manuales diferenciadas. |
| 10 | Performance e integración | US-29, escenarios 4–5; TS-07; SS-03 | Atribución, estimaciones y validación de recorridos completos. |

US-01 a US-08 corresponden a la landing y quedan fuera de estos bloques. El consumo de los contratos nuevos desde Android se implementará posteriormente.

## Bloque 01: alcance de implementación

Estado del código: implementado y verificado el 8 de noviembre de 2026. Los bloques 02–10 continúan pendientes. La verificación contra cuentas reales de redes sociales requiere configurar las aplicaciones externas; se detalla separadamente en los contratos de Identity.

Mensaje sugerido para el commit manual: `feat(identity): complete brand profiles and authorized social access`.

- US-12: consulta y actualización del perfil empresarial desde la sesión, campos permitidos, validación, versión y protección frente a ediciones antiguas o simultáneas.
- Consultas internas por identificador de perfil empresarial y creador, manteniendo `accountId` y `profileId` diferenciados y sin exponer credenciales.
- SS-02: estado de permisos/vencimiento, renovación soportada por proveedor, necesidad de reautorizar, sustitución de credenciales de la misma identidad y preservación del callback Android de un solo uso.
- Regresión de registro, JWT, recuperación, perfil creador, autorización aceptada/rechazada y duplicados.
- Alinear Java entre Maven y Docker, añadir migración nueva y preparar ejecución MySQL/Mailpit aislada.
- Documentar endpoints, errores, integración y verificaciones externas pendientes en [identity-contracts.md](identity-contracts.md).

## Escenarios y dependencias de los siguientes bloques

### 02. Campaign

Depende de 01. Separar metadatos de borradores (US-15), condiciones y fechas/versión (US-16), requisitos/duplicados/edición/cancelación (US-19) y consulta/selección/rechazo empresarial (US-20). Conservar filtros, detalle, disponibilidad y paginación de US-17/18. La selección no activa una colaboración ni rechaza automáticamente a los demás postulantes. Registrar cambios de payload que requieran adaptación móvil.

### 03. Collaboration: acuerdo y estado

Depende de 02. US-21 requiere selección previa, snapshot inmutable, aceptación de cada parte sobre la misma versión y ausencia de activación tras un rechazo. Probar reintentos y confirmaciones simultáneas: una sola colaboración. US-22 debe consultar etapa/acciones y reflejar vencimientos con hora del servidor. Añadir integración fiable por contratos/eventos, sin acceso directo a repositorios de otro contexto.

### 04. Collaboration: entregas y revisión

Depende de 03. US-23 admite entrega válida y tardía; respetar cantidades, propiedad, referencias y archivos. US-24 conserva versiones y revisiones, requiere observaciones al rechazar y permite corrección según lo acordado. No aprobar una nueva versión mediante una decisión antigua ni marcar un pago como realizado al aprobar contenido.

### 05. Collaboration: incidencias e historial

Depende de 04. US-25 conserva registro/resolución de incidencias e historial de acciones, con plazo y autoridad de resolución explícitos. Varias incidencias abiertas mantienen sus bloqueos independientes. US-30 incluye historial paginado existente/vacío y detalle propio para ambos participantes, conservando condiciones históricas y referencias a compensación/resultados.

### 06. Billing: vinculación financiera

Depende de 01 y usa 03–05 para el flujo de compensación de SS-01. Validar el proveedor Sandbox antes de presentar una integración real como terminada. US-26 cubre vinculación correcta/fallida y operación sin medio requerido. Distinguir medios para cobro de destinos para recibir pagos; tokenizar, verificar retornos y preparar webhooks firmados/idempotentes.

### 07. Billing: suscripción

Depende de 06. US-27 obtiene planes/precios/monedas desde el servidor. Medio válido y confirmación del proveedor son necesarios para activar la suscripción; un cobro fallido no activa beneficios. Evitar suscripciones o cargos duplicados y conservar estados pendientes. No imponer nuevas restricciones comerciales a campañas sin una regla del negocio.

### 08. Billing: compensación

Depende de 03–07. US-28 crea la compensación desde el acuerdo, la mantiene pendiente hasta el cumplimiento, refleja incidencias y registra el pago confirmado. Cubrir autorización, fallo, espera, timeout, reintento, concurrencia y eventos duplicados/fuera de orden. Los canjes no realizan cargos. Una incidencia posterior no borra el hecho de un pago ni inicia automáticamente un reembolso.

### 09. Performance: métricas y evidencia

Depende de 01, 04 y 05. US-29 escenarios 1–3: métricas disponibles con fuente/periodo, ausencia explícita sin inventar ceros y evidencia manual diferenciada. SS-03 verifica capacidades y restricciones reales; el permiso de perfil básico no garantiza estadísticas. Probar permisos, vencimientos, caída del proveedor, snapshots repetidos y evidencia ajena/inválida.

### 10. Performance: atribución e integración

Depende de 01–09. US-29 escenarios 4–5: enlaces/códigos de colaboración, interacciones atribuibles sin duplicados y resultados con límites/advertencia de estimación. Impedir destinos arbitrarios y no equiparar clics a ventas verificadas. Ejecutar recorridos completos con MySQL y ambos roles, actualizaciones de esquema sin borrar datos, seed idempotente y revisión final de contratos móviles y OpenAPI.

## Reglas de cierre por bloque

Cada bloque incluye dominio, aplicación, infraestructura, REST, migración nueva, pruebas de sus escenarios y documentación. Mantener las cuatro capas, JWT y autorización por objeto, errores `ApiError`, fechas del servidor y control de reintentos/concurrencia. Las pruebas finales complementan las de cada bloque, no las sustituyen. Mantener separadas las pruebas controladas y las verificaciones externas reales.

Antes del bloque afectado deben concretarse la vigencia de aceptación, plazos de corrección, ventana y autoridad de incidencias, viabilidad financiera, catálogo comercial, tratamiento de compensación mixta y métricas respaldadas. No fijar reglas comerciales arbitrarias en el código.
