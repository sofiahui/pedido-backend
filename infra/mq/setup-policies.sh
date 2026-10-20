#!/usr/bin/env bash
# Espera a que el clúster tenga sus 2 nodos y aplica las políticas de retención.
# Las políticas se aplican por patrón de nombre, así que cubren tanto las colas que
# crea el código (orders/notify) como las que cree el microservicio administrador.
set -euo pipefail

NODE="rabbit@rabbit1"

# rabbitmqctl se autentica con la cookie compartida del clúster.
printf '%s' "${RABBITMQ_ERLANG_COOKIE}" > "${HOME}/.erlang.cookie"
chmod 600 "${HOME}/.erlang.cookie"

echo "[setup] esperando a que ${NODE} esté en línea..."
for i in $(seq 1 30); do
  if rabbitmqctl -n "${NODE}" await_startup >/dev/null 2>&1; then break; fi
  sleep 3
done

echo "[setup] esperando a que el clúster tenga 2 nodos..."
rabbitmqctl -n "${NODE}" await_online_nodes 2

echo "[setup] aplicando políticas de retención"
# Colas principales (q.cmd.email, q.cmd.kitchen, q.cmd.invoice): máx. 10.000 mensajes,
# 50 MB y 24 h de vida. Un mensaje que expira o desborda el límite se envía a su DLQ.
rabbitmqctl -n "${NODE}" set_policy --apply-to queues --priority 1 retention-main \
  '^q\.cmd\.[a-z]+$' \
  '{"max-length":10000,"max-length-bytes":52428800,"message-ttl":86400000,"overflow":"drop-head"}'

# DLQ (q.cmd.*.dlq): se conservan más tiempo para poder revisarlas (7 días).
rabbitmqctl -n "${NODE}" set_policy --apply-to queues --priority 2 retention-dlq \
  '^q\.cmd\..+\.dlq$' \
  '{"max-length":5000,"max-length-bytes":26214400,"message-ttl":604800000,"overflow":"drop-head"}'

echo "[setup] estado del clúster:"
rabbitmqctl -n "${NODE}" cluster_status
echo "[setup] políticas vigentes:"
rabbitmqctl -n "${NODE}" list_policies
echo "[setup] listo"
