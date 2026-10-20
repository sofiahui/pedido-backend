# Clúster RabbitMQ de Pedidos360 (2 nodos)

| Archivo | Para qué sirve |
|---|---|
| `docker-compose.yml` | Define `rabbit1`, `rabbit2` y `rabbit-setup` (tarea de una sola ejecución) |
| `rabbitmq.conf` | Formación automática del clúster (`classic_config`: cada nodo busca al otro al arrancar) |
| `setup-policies.sh` | Espera a que haya 2 nodos y aplica las políticas de retención |
| `.env.example` | Usuario, clave y **cookie de Erlang** (copiar como `.env`) |

## Levantar

```bash
cd infra/mq
cp .env.example .env        # cambiar RABBITMQ_ERLANG_COOKIE por un secreto propio
docker compose up -d
docker compose ps
```

> Si antes usabas el RabbitMQ de un solo nodo (`pedidos360-rabbitmq`), elimínalo primero:
> `docker rm -f pedidos360-rabbitmq` (comparte el puerto 5672).

| Nodo | AMQP | Panel de gestión |
|---|---|---|
| `rabbit1` (`rabbit@rabbit1`) | 5672 | http://localhost:15672 |
| `rabbit2` (`rabbit@rabbit2`) | 5673 | http://localhost:15673 |

## Qué es igual y qué es distinto entre los nodos (guía P1)

| Igual en ambos | Distinto en cada nodo |
|---|---|
| Imagen y versión (`rabbitmq:4.2-management`) | `hostname` y nombre de nodo (`rabbit@rabbit1` / `rabbit@rabbit2`) |
| `RABBITMQ_ERLANG_COOKIE` (secreto compartido: sin él los nodos no se reconocen) | Puertos publicados en el host (5672/15672 y 5673/15673) |
| Usuario y clave (`RABBITMQ_DEFAULT_USER/PASS`) | Volumen de datos propio (`rabbit1_data`, `rabbit2_data`) |
| `rabbitmq.conf` con la lista de nodos del clúster | |

## Cómo comprobar el clúster (guía P2)

```bash
docker exec rabbit1 rabbitmqctl cluster_status     # lista "Running Nodes": rabbit1 y rabbit2
docker exec rabbit2 rabbitmqctl cluster_status     # mismo resultado desde el otro nodo
docker logs rabbit-setup                           # políticas aplicadas y estado del clúster
```

En el panel: **Overview → Nodes** muestra `rabbit@rabbit1` y `rabbit@rabbit2` sin alarmas.

## Políticas de retención (guía P11)

Se aplican por patrón de nombre (Admin → Policies), así que cubren las colas del código y las que
cree el microservicio administrador:

| Política | Aplica a | Definición |
|---|---|---|
| `retention-main` | `q.cmd.email`, `q.cmd.kitchen`, `q.cmd.invoice` | `max-length` 10.000 · `max-length-bytes` 50 MB · `message-ttl` 24 h · `overflow: drop-head` |
| `retention-dlq` | `q.cmd.*.dlq` | `max-length` 5.000 · `max-length-bytes` 25 MB · `message-ttl` 7 días · `overflow: drop-head` |

Un mensaje que vence o desborda el límite en una cola principal se envía a su DLQ (motivo `expired` o
`maxlen` en la cabecera `x-death`). No se usa `expires` (borrado de la cola completa) porque dejaría sin
cola al sistema si pasa un tiempo sin tráfico.

## Conectar los microservicios

```bash
export RABBITMQ_ADDRESSES=localhost:5672,localhost:5673   # en EC2: <ip-privada>:5672,<ip-privada>:5673
export RABBITMQ_USER=pedidos360 RABBITMQ_PASS=pedidos360
```

## Despliegue en AWS EC2 (guía P3)

Pasos estándar; no se ejecutaron en la cuenta del equipo, hay que hacerlos y capturar la evidencia.

1. **Instancia**: EC2 `t3.small` o mayor (2 GB de RAM mínimo: son dos nodos Erlang), Ubuntu 22.04 o
   Amazon Linux 2023, estado *running*.
2. **Security Group** (Inbound):

   | Puerto | Para qué | Origen recomendado |
   |---|---|---|
   | 22 | SSH | solo tu IP |
   | 5672, 5673 | AMQP de `rabbit1` / `rabbit2` | el Security Group de las instancias de los microservicios |
   | 15672, 15673 | Panel de gestión | solo tu IP |

3. **Instalar Docker** (por SSH):

   ```bash
   # Ubuntu
   sudo apt-get update && sudo apt-get install -y docker.io docker-compose-v2 git
   # Amazon Linux 2023
   sudo dnf install -y docker git && sudo systemctl enable --now docker
   sudo mkdir -p /usr/local/lib/docker/cli-plugins
   sudo curl -SL https://github.com/docker/compose/releases/latest/download/docker-compose-linux-x86_64 \
        -o /usr/local/lib/docker/cli-plugins/docker-compose && sudo chmod +x /usr/local/lib/docker/cli-plugins/docker-compose
   sudo usermod -aG docker $USER && newgrp docker
   ```

4. **Levantar el clúster**:

   ```bash
   git clone https://github.com/sofiahui/pedido-back.git && cd pedido-back/infra/mq
   cp .env.example .env && nano .env          # cambiar la cookie y la clave
   docker compose up -d
   docker compose ps
   docker exec rabbit1 rabbitmqctl cluster_status
   ```

5. **Mostrar en la presentación**: consola AWS → EC2 → instancia → pestaña *Security*; la terminal SSH
   con `docker compose ps` y `docker ps`; y el panel `http://<IP-pública>:15672`.

## Limitaciones conocidas (para responder preguntas)

- Las colas son **clásicas**: viven en el nodo que las declaró (hoy `rabbit1`). Si ese nodo cae, sus colas
  quedan no disponibles hasta que vuelva, aunque los clientes puedan conectarse al otro nodo con
  `RABBITMQ_ADDRESSES`. Replicarlas exigiría colas *quorum* (con 3 nodos para tolerar una caída).
- El listener de DLQ de `notify` confirma (ACK) cada mensaje después de registrarlo, así que las DLQ
  quedan en 0. Para conservarlos y revisarlos a mano en el panel: `RABBIT_DLQ_LISTENER_ENABLED=false`.
- Si cambias la cookie después del primer arranque hay que recrear los volúmenes:
  `docker compose down -v`.
