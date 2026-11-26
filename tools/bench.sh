#!/bin/sh
# Macrobenchmark del encargo 012 (Cristal 1.3, «Medir»): arranque en frío y desplazar la principal de la demo.
#
# Uso (Git Bash, desde la raíz del repo):
#   sh tools/bench.sh arranque    # M1: timeToInitialDisplayMs, 10 iteraciones, en el OPPO por cable
#   sh tools/bench.sh desplazar   # M2: frameOverrunMs P50 y P90, en el OPPO por cable
#   sh tools/bench.sh perfil      # genera el perfil de base: SOLO en el emulador (app/src/main/generated/baselineProfiles)
#
# Instala con `installPerf` (encima de lo que haya, misma clave de debug: no borra datos) y lanza con `am instrument`.
# NUNCA connectedAndroidTest (desinstala la app al acabar y se llevaría los sitios guardados), ni pm clear, ni uninstall.
# Se niega si ve una conexión inalámbrica (`IP:puerto`, `_adb-tls`) en `adb devices`, salvo en `perfil`, que exige el emulador.
# El perfil de base se copia a app/src/main/generated/baselineProfiles/baseline-prof.txt (va al repo).
# Para probar el propio script en el emulador: BENCH_TARGET=emulator sh tools/bench.sh arranque
# Los JSON salen en build/bench/; cada medida se añade a tools/medidas.log (no se versiona).

export MSYS_NO_PATHCONV=1 PYTHONIOENCODING=utf-8
WHAT="$1"
TAG="${2:--}"
PY=python
PKG=com.trastealabs.tiempo.dev   # «Tiempo dev» (017); el paquete del benchmark sigue siendo com.trastealabs.tiempo.benchmark
TESTPKG=com.trastealabs.tiempo.benchmark
OUT=build/bench
LOG=tools/medidas.log
MEDIA=/sdcard/Android/media/$TESTPKG

die() { echo "bench.sh: $1" >&2; exit 1; }

case "$WHAT" in
  arranque) CLASS=StartupBenchmark ;;
  desplazar) CLASS=ScrollBenchmark ;;
  perfil) CLASS=BaselineProfileGenerator ;;
  *) die "uso: sh tools/bench.sh arranque|desplazar|perfil" ;;
esac

DEVICES=$(adb devices | tr -d '\r' | awk 'NR>1 && $2=="device" {print $1}')
WIRELESS=$(adb devices | tr -d '\r' | awk 'NR>1 && NF>=2 && ($1 ~ /:[0-9]+$/ || $1 ~ /_adb-tls/) {print $1}')
EMULATORS=$(echo "$DEVICES" | awk '/^emulator-/')
PHONES=$(echo "$DEVICES" | awk '!/^emulator-/ && !/:[0-9]+$/ && !/_adb-tls/')

if [ "$WHAT" = "perfil" ] || [ "$BENCH_TARGET" = "emulator" ]; then
  [ "$(echo "$EMULATORS" | grep -c .)" = "1" ] || die "hace falta exactamente un emulador en adb devices (arranca un emulador)"
  SERIAL=$EMULATORS
  EMULATOR=1
else
  [ -z "$WIRELESS" ] || die "hay una conexión inalámbrica en adb devices ($WIRELESS): pasa el OPPO a cable USB (adb disconnect) y repite"
  [ "$(echo "$PHONES" | grep -c .)" = "1" ] || die "hace falta exactamente un móvil por cable en adb devices"
  SERIAL=$PHONES
  EMULATOR=0
fi
export ANDROID_SERIAL="$SERIAL"
echo "Dispositivo: $SERIAL"

HZ=$(adb shell dumpsys display | grep -oE "renderFrameRate [0-9.]+" | head -1 | tr -d '\r')
MODE=$(adb shell dumpsys display | grep -oE "mDisplayModeSpecs=\{baseModeId=[0-9]+ [^}]*" | head -1 | tr -d '\r')
echo "Hz: ${HZ:-?} | $MODE"

# Las variantes las crea el plugin de perfiles a partir de `perf` (misma app y clave de debug que `medir.sh`, con la demo):
# nonMinifiedPerf para generar el perfil, benchmarkPerf (R8 + perfil) para medir. Nada de connectedAndroidTest.
if [ "$WHAT" = "perfil" ]; then VARIANT=NonMinifiedPerf; else VARIANT=BenchmarkPerf; fi
./gradlew ":app:install$VARIANT" ":benchmark:install$VARIANT" --console=plain -q || die "no se pudo instalar"
adb shell input keyevent 224    # despierta la pantalla

ARGS="-e class $TESTPKG.$CLASS"
# En el emulador el Macrobenchmark se niega a medir; se le deja (los números allí no valen, solo se prueba el script).
[ "$EMULATOR" = 1 ] && ARGS="$ARGS -e androidx.benchmark.suppressErrors EMULATOR,LOW-BATTERY,NOT-PROFILEABLE"
# ColorOS retiene el proceso tras `am kill` y el Macrobenchmark exige que no esté en marcha: repetir tras Inicio hasta que `pidof` no dé nada.
if [ "$WHAT" = "arranque" ]; then
  for try in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15; do
    adb shell am force-stop "$PKG"
    [ -z "$(adb shell pidof $PKG)" ] && break
    adb shell input keyevent 3
    sleep 1
  done
fi
adb shell "rm -f $MEDIA/*.json $MEDIA/*.perfetto-trace" >/dev/null 2>&1

adb shell am instrument -w $ARGS $TESTPKG/androidx.test.runner.AndroidJUnitRunner | tee "$OUT.instrument.txt" | tail -25
grep -q "^OK (" "$OUT.instrument.txt" || die "el instrumentado no acabó bien (mira $OUT.instrument.txt)"

if [ "$WHAT" = "perfil" ]; then
  rm -rf "$OUT"; mkdir -p "$OUT"
  adb pull "$MEDIA/." "$OUT/" >/dev/null || die "no hay resultados en $MEDIA"
  PROF=$(ls "$OUT"/*baseline-prof.txt | head -1)
  [ -n "$PROF" ] || die "el generador no dejó ningún baseline-prof.txt en $OUT"
  mkdir -p app/src/main/generated/baselineProfiles
  # Las reglas, ordenadas y sin repetir, para que el fichero del repo cambie lo mínimo entre generaciones.
  sort -u "$PROF" > app/src/main/generated/baselineProfiles/baseline-prof.txt
  echo "Perfil: $(wc -l < app/src/main/generated/baselineProfiles/baseline-prof.txt) reglas en app/src/main/generated/baselineProfiles/baseline-prof.txt"
  exit 0
fi

rm -rf "$OUT"; mkdir -p "$OUT"
mv "$OUT.instrument.txt" "$OUT/instrument.txt"
adb pull "$MEDIA/." "$OUT/" >/dev/null || die "no hay resultados en $MEDIA"
JSON=$(ls "$OUT"/*.json | head -1)
LINE=$($PY -I - "$JSON" "$WHAT" <<'EOF'
import json, sys
d = json.load(open(sys.argv[1], encoding="utf-8"))
b = d["benchmarks"][0]
m, s = b["metrics"], b.get("sampledMetrics", {})
if sys.argv[2] == "arranque":
    t = m["timeToInitialDisplayMs"]
    runs = ",".join("%d" % r for r in t["runs"])
    print("timeToInitialDisplayMs mediana %.1f ms | min %.1f | max %.1f | %d iteraciones | %s" % (t["median"], t["minimum"], t["maximum"], len(t["runs"]), runs))
else:
    o = s["frameOverrunMs"]
    print("frameOverrunMs P50 %.1f | P90 %.1f | P95 %.1f | P99 %.1f | duracion P50 %.1f P90 %.1f" % (o["P50"], o["P90"], o["P95"], o["P99"], s["frameDurationCpuMs"]["P50"], s["frameDurationCpuMs"]["P90"]))
EOF
)
line="$(date '+%Y-%m-%d %H:%M') | $TAG | bench $WHAT | $SERIAL | ${HZ:-?} | $LINE"
echo "$line"
echo "$line" >> "$LOG"
