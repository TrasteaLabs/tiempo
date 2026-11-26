#!/bin/sh
# Prueba H11 en el OPPO: con el móvil en reposo, el dato del widget nunca tiene más de 1 hora.
#
# Uso (Git Bash, con la depuración inalámbrica del OPPO activa y la app instalada):
#   sh tools/prueba_reposo.sh [minutos]        # por defecto 190 (3 h y 10 min)
#
# Cada 45 s le hace un "ping" al móvil para que la depuración inalámbrica no se corte
# (y si se corta, lo vuelve a conectar por mDNS). Cada 10 min anota en tools/prueba_reposo.log
# la hora del dato guardado (lo mismo que pinta el widget), su edad, si la pantalla está
# encendida, el estado de reposo profundo y si está cargando (cargando, Android no entra en reposo
# profundo y la prueba vale menos). Al final escribe la edad máxima con la pantalla apagada.
# Solo lee: no toca ajustes del móvil ni de la app.

export MSYS_NO_PATHCONV=1
MINUTES="${1:-190}"
LOG="$(dirname "$0")/prueba_reposo.log"
PING=45
SAMPLE_EVERY=13 # 13 × 45 s ≈ 10 min

oppo() {
  adb devices | awk '/_adb-tls-connect/ && $2=="device" {print $1; exit}'
}

reconnect() {
  for addr in $(adb mdns services | awk '/_adb-tls-connect/ {print $NF}'); do
    adb connect "$addr" >/dev/null 2>&1
  done
}

sample() {
  D="$1"
  now=$(adb -s "$D" shell date +%s | tr -d '\r')
  fetched=$(adb -s "$D" shell "run-as com.trastealabs.tiempo.dev cat files/snapshot.json" | grep -o '"fetchedAt":[0-9]*' | head -1 | cut -d: -f2)
  wake=$(adb -s "$D" shell dumpsys power | grep -o 'mWakefulness=[A-Za-z]*' | cut -d= -f2)
  idle=$(adb -s "$D" shell dumpsys deviceidle get deep | tr -d '\r')
  plugged=$(adb -s "$D" shell dumpsys battery | grep -c "powered: true")
  hm=$(adb -s "$D" shell date +%H:%M | tr -d '\r')
  fh=$(adb -s "$D" shell date -d @"$fetched" +%H:%M 2>/dev/null | tr -d '\r')
  echo "$hm | $fh | $(( (now - fetched) / 60 )) | $wake | $idle | $([ "$plugged" -gt 0 ] && echo cargando || echo batería)" >> "$LOG"
}

echo "hora_movil | dato_de_las | edad_min | pantalla | reposo | corriente" > "$LOG"
end=$(( $(date +%s) + MINUTES * 60 ))
n=0
while [ "$(date +%s)" -lt "$end" ]; do
  D=$(oppo)
  if [ -z "$D" ]; then
    reconnect
    D=$(oppo)
  fi
  if [ -n "$D" ]; then
    adb -s "$D" shell true >/dev/null 2>&1
    [ $((n % SAMPLE_EVERY)) -eq 0 ] && sample "$D"
  elif [ $((n % SAMPLE_EVERY)) -eq 0 ]; then
    echo "$(date +%H:%M) | sin conexión con el móvil" >> "$LOG"
  fi
  n=$((n + 1))
  sleep "$PING"
done
D=$(oppo)
[ -n "$D" ] && sample "$D"

max=$(awk -F' [|] ' 'NR>1 && $4 ~ /Asleep|Dozing/ && $3 ~ /^[0-9]+$/ { if ($3 > m) m = $3 } END { print m + 0 }' "$LOG")
echo "fin · edad máxima con la pantalla apagada: $max min ($( [ "$max" -le 60 ] && echo "cumple H11" || echo "NO cumple H11"))" >> "$LOG"
