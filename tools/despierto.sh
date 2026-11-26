#!/bin/sh
# Mantiene encendida la pantalla del OPPO mientras se prueba desde el PC (por cable o inalámbrico).
#
# Uso (Git Bash, en segundo plano, mientras se tenga el móvil):
#   sh tools/despierto.sh [minutos]        # por defecto 240
#
# Cada 30 s manda KEYCODE_WAKEUP (si se ha apagado, enciende la pantalla) y KEYCODE_SHIFT_LEFT:
# WAKEUP solo no reinicia la cuenta de inactividad en ColorOS y el móvil acabó bloqueado
# (medido con dumpsys power por la coordinadora de Cuaderno, 2026-11-15); SHIFT_LEFT sí, y no
# escribe nada ni mueve la pantalla. No desbloquea ni toca ajustes. Para pararlo, matar el proceso.

export MSYS_NO_PATHCONV=1
MINUTES="${1:-240}"
PING=30

oppo() {
  for d in $(adb devices | awk 'NR>1 && $2=="device" {print $1}'); do
    [ "$(adb -s "$d" shell getprop ro.product.brand 2>/dev/null | tr -d '\r')" = "OPPO" ] && { echo "$d"; return; }
  done
}

end=$(( $(date +%s) + MINUTES * 60 ))
while [ "$(date +%s)" -lt "$end" ]; do
  D=$(oppo)
  if [ -n "$D" ]; then
    adb -s "$D" shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1
    adb -s "$D" shell input keyevent KEYCODE_SHIFT_LEFT >/dev/null 2>&1
  else
    echo "$(date +%H:%M:%S) sin OPPO"
  fi
  sleep "$PING"
done
