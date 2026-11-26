#!/bin/sh
# Mantiene viva la depuración inalámbrica del OPPO mientras se prueba desde el PC.
#
# Uso (Git Bash, en segundo plano, antes de la primera prueba en el móvil):
#   sh tools/adb_keepalive.sh [minutos]        # por defecto 240
#
# Cada 30 s le hace un "ping" al móvil; si ha desaparecido de `adb devices`, lo vuelve a
# conectar por mDNS. Solo usa adb: no toca ajustes del móvil ni de la app.
# Con el emulador también conectado, los demás comandos tienen que llevar `adb -s <id>`.

export MSYS_NO_PATHCONV=1
MINUTES="${1:-240}"
PING=30

oppo() {
  adb devices | awk '/_adb-tls-connect/ && $2=="device" {print $1; exit}'
}

reconnect() {
  for addr in $(adb mdns services | awk '/_adb-tls-connect/ {print $NF}'); do
    adb connect "$addr" >/dev/null 2>&1
  done
}

end=$(( $(date +%s) + MINUTES * 60 ))
while [ "$(date +%s)" -lt "$end" ]; do
  D=$(oppo)
  if [ -z "$D" ]; then
    echo "$(date +%H:%M:%S) sin OPPO, reconectando"
    reconnect
    D=$(oppo)
  fi
  [ -n "$D" ] && adb -s "$D" shell true >/dev/null 2>&1
  sleep "$PING"
done
