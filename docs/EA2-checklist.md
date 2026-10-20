# Checklist EA2 (mensajería asíncrona con RabbitMQ) contra la guía de interrogación

Estado: **Listo** = implementado y probado en local contra el clúster de 2 nodos · **AWS** = el código está,
falta ejecutarlo en la nube y capturar la evidencia · **Concepto** = pregunta teórica, no requiere código.

| N° | Indicador | Peso | Estado | Dónde está / cómo mostrarlo |
|---|---|---|---|---|
| P1 | Dos nodos de RabbitMQ | 5% | Listo (AWS para la evidencia) | `infra/mq/docker-compose.yml` (`rabbit1`, `rabbit2`); `docker ps`; panel de cada nodo (15672 y 15673) |
| P2 | Clúster de dos nodos | 8% | Listo (AWS para la evidencia) | `docker exec rabbit1 rabbitmqctl cluster_status`; panel → Overview → Nodes |
| P3 | Clúster en la nube con Docker Compose | 17% | **AWS** | Pasos en `infra/mq/README.md`: EC2 + Security Group + `docker compose up -d` + `docker compose ps` |
| P4 | Tres colas con casos de uso distintos | 7% | Listo | `q.cmd.email` (avisa al cliente), `q.cmd.kitchen` (ticket de cocina al aceptar), `q.cmd.invoice` (boleta al entregar). Productor: `OrderCommandProducer` (orders). Consumidores: `EmailCommandConsumer`, `KitchenCommandConsumer`, `InvoiceCommandConsumer` (notify) |
| P5 | Cada cola tiene su DLQ | 9% | Listo | Panel → Queues → `q.cmd.email` → Features: `x-dead-letter-exchange=cmd.dead.dlx` y `x-dead-letter-routing-key`. Bean: `RabbitMQConfig.mainQueue(...)` |
| P6 | Exchanges direct y topic | 6% | Listo + Concepto | Panel → Exchanges → `cmd.direct` (direct) y `cmd.topic` (topic) con sus bindings. Productor usa `cmd.direct` |
| P7 | Microservicios que escriben y leen colas | 10% | Listo (requiere login real) | Angular → cambiar estado de un pedido → log de `orders` (comando publicado) → log de `notify` (ACK). Panel: colas en 0 |
| P8 | DLQ con logs de mensajes no entregados | 8% | Listo | `DeadLetterConsumer` (notify): registra DLQ, cola de origen, motivo `x-death` y payload, y hace ACK. Cómo provocarlo, abajo |
| P9 | Microservicio administrador | 12% | Listo (AWS: falta la ruta en el Gateway) | `ms-pedidos360-mqadmin`, `RabbitAdminService`, validación con `@Pattern`/`@NotBlank`. Ejemplos, abajo |
| P10 | Métricas del clúster | 8% | Concepto | Ver respuesta, abajo |
| P11 | Retención y limpieza | 10% | Listo | Panel → Admin → Policies: `retention-main` y `retention-dlq` (`max-length`, `max-length-bytes`, `message-ttl`) |

## Cómo provocar los errores y ver la DLQ (P8)

Con el clúster, `orders` y `notify` levantados. Panel → **Exchanges → cmd.direct → Publish message**, con
*Routing key* `email.send` y estos *Payload*:

| Caso | Payload | Qué debe pasar (log de notify) |
|---|---|---|
| Inválido | `esto no es json` | `Mensaje inválido, se envía a DLQ` y luego `[DLQ] Mensaje no entregado: ... colaOrigen=q.cmd.email, motivo=rejected` |
| Error permanente | JSON válido con `"simulateError":"permanente"` | `Error no recuperable ... Se envía a DLQ` y el log de la DLQ |
| Error temporal | JSON válido con `"simulateError":"temporal"` | `Error temporal ... Se reintenta una vez`, luego `Falló también el reintento ... Se envía a DLQ` y el log de la DLQ |

JSON válido de ejemplo (el `eventId` debe ser distinto en cada envío; si se repite, se ignora como duplicado):

```json
{"type":"EmailSend","eventId":"demo-001","timestamp":"2026-10-20T15:00:00Z","traceId":"t-1","correlationId":"1",
 "payload":{"orderId":1,"status":"ACEPTADO","customerId":"cliente-demo","simulateError":"permanente"}}
```

Para ver los mensajes quedarse en la DLQ en el panel, arrancar notify con `RABBIT_DLQ_LISTENER_ENABLED=false`.

## Microservicio administrador (P9)

Todas las rutas exigen un token con rol **Admin**; con otro rol responde 403 y sin token 401.
Se llama por el API Gateway → BFF (`/api/admin/**`) o directo en `:8083` (Swagger: `/swagger-ui.html`).

| Operación | Llamada | Cuerpo / parámetros |
|---|---|---|
| Crear cola (con DLQ) | `POST /api/admin/queues` | `{"name":"ventas","durable":true,"withDlq":true}` (opcionales: `messageTtlMs`, `maxLength`) |
| Consultar cola | `GET /api/admin/queues/ventas` | Mensajes listos y consumidores |
| Eliminar cola | `DELETE /api/admin/queues/ventas` | Las `q.cmd.*` del sistema están protegidas (409) |
| Crear exchange | `POST /api/admin/exchanges` | `{"name":"eventos","type":"topic","durable":true}` (`direct`, `topic` o `fanout`) |
| Eliminar exchange | `DELETE /api/admin/exchanges/eventos` | `cmd.direct`, `cmd.topic` y `cmd.dead.dlx` protegidos (409) |
| Crear binding | `POST /api/admin/bindings` | `{"exchange":"eventos","queue":"ventas","routingKey":"ventas.#"}` |
| Quitar binding | `DELETE /api/admin/bindings?exchange=eventos&queue=ventas&routingKey=ventas.%23` | |

Códigos: 201 creado · 204 eliminado · 400 datos inválidos (nombre con caracteres no permitidos, tipo fuera de
`direct/topic/fanout`, falta `routingKey`) · 404 no existe · 409 ya existe o es del sistema · 503 sin RabbitMQ.
Para la demostración: abrir el panel antes y después de cada llamada y mostrar que aparecen/desaparecen.

## Respuestas para las preguntas conceptuales

**P6, direct vs topic.** Direct enruta por coincidencia exacta de la routing key; es ideal para comandos
puntuales (aquí `email.send` va a una sola cola). Topic enruta por patrones (`*` una palabra, `#` cero o más), y
un mensaje puede llegar a varias colas; sirve para eventos con clasificación jerárquica (notificaciones,
auditoría).

**P10, Ready / Unacked / Total.** *Ready*: mensajes en la cola listos para entregarse que nadie ha tomado.
*Unacked*: entregados a un consumidor que todavía no los confirmó (ACK). *Total* = Ready + Unacked. En
estado sano y sin carga las colas quedan en 0/0/0; un Unacked que no baja indica un consumidor atascado.

**P11, retención.** Se definieron como *policies* (Admin → Policies) con `max-length` (cantidad de mensajes),
`max-length-bytes`, `message-ttl` (vida de cada mensaje) y `overflow: drop-head`; las colas principales conservan
24 h y las DLQ 7 días. No se usa `expires` para no perder las colas por inactividad.

**Por qué el stock es síncrono y las notificaciones asíncronas.** Aceptar un pedido debe fallar de inmediato si
no hay stock, así que `orders` llama a `catalog` por HTTP. En cambio el email, el ticket y la boleta no deben
retrasar ni romper el pedido: se publican después del commit y los procesa `notify`.

## Lo que solo el equipo puede hacer

1. **EC2 con el clúster** (P3, 17%): seguir `infra/mq/README.md` y capturar la consola de AWS, el Security Group,
   `docker compose ps` y el panel.
2. **Ruta del administrador en el API Gateway**: agregar `ANY /api/admin/{proxy+}` con el mismo JWT Authorizer
   hacia el BFF, y definir `MQADMIN_SERVICE_URL` en el BFF.
3. **Usuario con rol Admin** en el tenant de Azure, para poder llamar a `/api/admin/**` con un token real.
4. **Desplegar** `orders`, `notify`, `mqadmin` y `bff` apuntando a `RABBITMQ_ADDRESSES` del clúster.
5. **Pantallazos de la DLQ** (P8) con los tres casos de la tabla de arriba.

## Limitaciones a tener presentes

- Las colas son clásicas (no replicadas): si cae `rabbit1`, sus colas no están disponibles hasta que vuelva.
- El mensaje de la DLQ desaparece al registrarse (ACK del listener) salvo que se desactive con
  `RABBIT_DLQ_LISTENER_ENABLED=false`.
- "Eliminar pedidos" figura como hecho en el resumen del equipo, pero no existe en `main` (ni front, ni orders,
  ni BFF).
