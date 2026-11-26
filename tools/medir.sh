#!/bin/sh
# Medidas de la auditoría (encargo 008): siempre las mismas órdenes, para comparar
# antes y después. Con la compilación `perf` instalada (`./gradlew installPerf`), el móvil desbloqueado y nada delante.
#
# Uso (Git Bash, desde la raíz del repo):
#   sh tools/medir.sh arranque|quieta|cielos|desplazar|dias|sitios|transiciones|cpu|memoria|apk|check [etiqueta]
#   sh tools/medir.sh transiciones <etiqueta> [parte del nombre]   # 011: una transición o todas (ver plan.md del 011)
#
#   RAW=<carpeta> sh tools/medir.sh …   # además guarda los volcados crudos de framestats (transiciones, desplazar, quieta)
#                                       y el estado de frecuencia (renderFrameRate) en <carpeta>/pantalla.txt
#
# Cada medida imprime una línea y la añade a tools/medidas.log (no se versiona), con la etiqueta (p. ej. A1, A2, D1).
# Toma el OPPO de ANDROID_SERIAL o, si no está, el primero de modelo CPH2659 de `adb devices -l`.

export MSYS_NO_PATHCONV=1
P=com.trastealabs.tiempo.dev   # «Tiempo dev» (017): la app de uso diario es com.trastealabs.tiempo y no se toca
WHAT="$1"
TAG="${2:--}"
PY=python
LOG=tools/medidas.log

if [ -z "$ANDROID_SERIAL" ]; then
  ANDROID_SERIAL=$(adb devices -l | awk '/model:CPH2659/ && $2=="device" {print $1; exit}')
fi
export ANDROID_SERIAL

say() {
  line="$(date '+%Y-%m-%d %H:%M') | $TAG | $WHAT | $1"
  echo "$line"
  echo "$line" >> "$LOG"
}

need_phone() {
  [ -n "$ANDROID_SERIAL" ] || { echo "No encuentro el OPPO en adb devices" >&2; exit 1; }
  adb shell input keyevent 224   # despierta la pantalla si se apagó
  if [ -n "$RAW" ] && [ -z "$DRY" ]; then   # RAW=<carpeta>: el estado de frecuencia en reposo, para leer los volcados después
    mkdir -p "$RAW"
    { echo "$(date '+%Y-%m-%d %H:%M:%S') $WHAT $(adb shell dumpsys display | grep -oE 'renderFrameRate [0-9.]+|mActiveModeId=[0-9]+' | head -2 | tr '\n' ' ')"; } >> "$RAW/pantalla.txt"
  fi
}

keep() {  # RAW=<carpeta>: guarda el volcado crudo $1 como $RAW/$2 (sin RAW no hace nada)
  [ -n "$RAW" ] || return 0
  mkdir -p "$RAW" && cp "$1" "$RAW/$(echo "$2" | tr -c 'A-Za-z0-9_.\n' '_')"
}

screen() {  # ancho y alto en px (con el tamaño forzado, si lo hay)
  adb shell wm size | awk -F'[ x]' '/Override/ {w=$3; h=$4} /Physical/ && !w {w=$3; h=$4} END {print w, h}'
}

demo() {  # abre la demo en una escena (siempre con -S: sin él no cambia de escena)
  adb shell am start -S -n "$P/com.trastealabs.tiempo.demo.DemoActivity" "$@" >/dev/null
}

frames_after() {  # vacía framestats, hace el gesto ($@ = input swipe ...) y vuelca cuatro veces hasta ~1,5 s
  # framestats guarda ~120 fotogramas y en los gestos el OPPO pinta a 120 Hz: un volcado solo perdería el principio.
  adb shell dumpsys gfxinfo "$P" reset >/dev/null
  adb shell input swipe "$@"
  adb shell dumpsys gfxinfo "$P" framestats
  for i in 1 2 3; do
    sleep 0.4
    adb shell dumpsys gfxinfo "$P" framestats
  done
}

check_awake() {
  adb shell dumpsys power | grep -q "mWakefulness=Awake" || echo "OJO: la pantalla se apagó durante la medida" >&2
}

case "$WHAT" in
  arranque)
    need_phone
    # Solo cuentan los arranques que Android da por fríos (LaunchState: COLD); ColorOS a veces retiene el proceso.
    times=""; cold=0; other=0
    while [ $cold -lt 10 ] && [ $((cold + other)) -lt 25 ]; do
      adb shell input keyevent 3
      sleep 1
      for try in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15; do
        adb shell am kill "$P"
        [ -z "$(adb shell pidof $P)" ] && break
        adb shell input keyevent 3
        sleep 1
      done
      out=$(adb shell am start -W -n "$P/com.trastealabs.tiempo.ui.MainActivity" | tr -d '\r')
      state=$(echo "$out" | awk '/LaunchState/ {print $2}')
      t=$(echo "$out" | awk '/TotalTime/ {print $2}')
      if [ "$state" = "COLD" ]; then cold=$((cold + 1)); times="$times $t"; else other=$((other + 1)); fi
      sleep 5
    done
    say "$(echo $times | tr ' ' '\n' | sort -n | awk '{a[NR]=$1} END {m=(NR%2)?a[(NR+1)/2]:(a[NR/2]+a[NR/2+1])/2; printf "TotalTime mediana %d ms | mín %d | máx %d | (%s fríos", m, a[1], a[NR], NR}'), $other descartados) |$times"
    ;;
  quieta)
    need_phone
    demo --es scene RAIN --es badge WARNING
    sleep 6
    adb shell dumpsys gfxinfo "$P" reset >/dev/null
    tmp=$(mktemp)
    for i in $(seq 30); do sleep 1; adb shell dumpsys gfxinfo "$P" framestats >> "$tmp"; done
    check_awake
    keep "$tmp" "quieta.txt"
    say "$($PY tools/framestats.py < "$tmp")"
    rm -f "$tmp"
    ;;
  cielos)
    need_phone
    # 012 P6 (H9): fotogramas nuevos en 10 s sin tocar, con sol, nubes, lluvia y noche (la escena quieta no pide ninguno).
    for scene in SUN_MILD CLOUDS RAIN NIGHT_CLEAR; do
      clock="14:00"; [ "$scene" = "NIGHT_CLEAR" ] && clock="23:04"   # la demo usa la hora de ahora: fijarla da de día el sol, las nubes y la lluvia
      demo --es scene "$scene" --es badge WARNING --es clock "$clock"
      sleep 6
      adb shell dumpsys gfxinfo "$P" reset >/dev/null
      sleep 10
      n=$(adb shell dumpsys gfxinfo "$P" | tr -d '\r' | awk '/Total frames rendered/ {print $4}')
      check_awake
      say "$scene | fotogramas nuevos en 10 s sin tocar: ${n:-?}"
    done
    ;;
  desplazar)
    need_phone
    set -- $(screen); W=$1; H=$2
    X=$((W / 2)); LOW=$((H * 75 / 100)); HIGH=$((H * 30 / 100))
    demo --es scene RAIN --es badge WARNING
    sleep 6
    tmp=$(mktemp)
    for i in 1 2 3 4 5; do frames_after $X $LOW $X $HIGH 300 >> "$tmp"; done
    for i in 1 2 3 4 5; do frames_after $X $HIGH $X $LOW 300 >> "$tmp"; done
    keep "$tmp" "desplazar.txt"
    say "$($PY tools/framestats.py < "$tmp")"
    rm -f "$tmp"
    ;;
  dias)
    need_phone
    set -- $(screen); W=$1; H=$2
    Y=$((H * 55 / 100)); RIGHT=$((W * 85 / 100)); LEFT=$((W * 15 / 100))
    demo --ei day 0
    sleep 6
    tmp=$(mktemp)
    # Cada gesto va marcado: framestats.py saca su primer fotograma con entrada (H9 del 009: ≤ 17 ms en 16 de 18).
    for i in 1 2 3 4 5 6 7 8 9; do echo "### gesto" >> "$tmp"; frames_after $RIGHT $Y $LEFT $Y 250 >> "$tmp"; done
    for i in 1 2 3 4 5 6 7 8 9; do echo "### gesto" >> "$tmp"; frames_after $LEFT $Y $RIGHT $Y 250 >> "$tmp"; done
    say "$($PY tools/framestats.py < "$tmp")"
    rm -f "$tmp"
    ;;
  sitios)
    need_phone
    # Abrir Sitios desde la principal con 10 guardados (11 postales, cada una con el trozo de su foto): 5 aperturas.
    # El toque va al botón del sitio («… Cambiar de sitio»), que se busca con uiautomator; cuenta la apertura entera.
    tmp=$(mktemp)
    for i in 1 2 3 4 5; do
      demo --es saved full
      sleep 6
      adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
      xy=$(adb exec-out cat /sdcard/ui.xml | grep -o 'Cambiar de sitio[^>]*' | grep -o '\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]' | head -1 | sed 's/\]\[/,/; s/[][]//g' | awk -F, '{printf "%d %d", ($1+$3)/2, ($2+$4)/2}')
      [ -n "$xy" ] || { echo "No encuentro el botón del sitio en la principal" >&2; rm -f "$tmp"; exit 1; }
      adb shell dumpsys gfxinfo "$P" reset >/dev/null
      adb shell input tap $xy
      sleep 0.3
      adb shell dumpsys gfxinfo "$P" framestats >> "$tmp"
      for k in 1 2 3; do
        sleep 0.4
        adb shell dumpsys gfxinfo "$P" framestats >> "$tmp"
      done
    done
    check_awake
    say "$($PY tools/framestats.py < "$tmp")"
    rm -f "$tmp"
    ;;
  transiciones)
    # 011 (encargo 011, «Medir»): cada transición de la lista de H1, ida y vuelta, cinco
    # pasadas cada una. El botón se busca con uiautomator por su texto o su descripción (el toque lo recibe el pulsable
    # que lo contiene); se vuelca framestats justo después de tocar (4 volcados hasta ~1,5 s) y se saca, por pasada,
    # el % de fotogramas lentos (framestats.py --slow). Una línea por ida y por vuelta: mediana, peor, tirones y Hz
    # (la frecuencia a la que pintó la app, del hueco entre vsync de sus fotogramas: framestats.py --hz; ColorOS la
    # baja sin dedo, así que no se compara con una medida a otra frecuencia). Tercer argumento: solo las transiciones
    # cuyo nombre lo contenga (p. ej. `sh tools/medir.sh transiciones D1 Sitios`). `PASADAS=1 sh tools/medir.sh …`
    # hace menos pasadas (de 1 a 5; 5 si no se dice): la medida rápida de la 012 usa 1.
    # Seguridad: antes de CADA toque, deslizamiento o «atrás» se comprueba que el foco es de la app; si no lo es, no se
    # toca nada más, se relanza la demo y se repite la pasada (tres fallos seguidos: la transición queda «sin medir»).
    # `DRY=1 sh tools/medir.sh transiciones X` imprime las órdenes de adb sin ejecutarlas.
    ONLY="$3"
    if [ -n "$DRY" ]; then
      adb() { echo "  adb $*" >&2; }
      ANDROID_SERIAL=seco; LOG=/dev/null
    else
      need_phone
    fi
    if [ -n "$DRY" ]; then W=1080; H=2376; else set -- $(screen); W=$1; H=$2; fi
    X=$((W / 2)); LOW=$((H * 80 / 100)); HIGH=$((H * 15 / 100))
    tmp=$(mktemp -d)
    trap 'rm -rf "$tmp"' EXIT INT TERM

    focused() {  # ¿tiene el foco la app? Sin él, ni un toque más.
      [ -n "$DRY" ] && return 0
      adb shell dumpsys window | grep mCurrentFocus | grep -q "$P"
    }
    safe() {  # una orden de entrada ($@ = tap|swipe|keyevent ...), solo con el foco en la app
      if focused; then adb shell input "$@"; else echo "Sin foco en la app: no toco nada ($*)" >&2; return 1; fi
    }
    node_xy() {  # «x y» del centro del primer nodo de la pantalla cuyo texto o descripción casa con $1 (regex)
      [ -n "$DRY" ] && { printf '100 100'; return; }
      for dump in 1 2 3; do  # uiautomator falla a ratos con la pantalla en movimiento: hasta tres intentos
        focused || return 1
        adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
        adb exec-out cat /sdcard/ui.xml > "$tmp/ui.xml"
        grep -q '<node' "$tmp/ui.xml" && break
        sleep 0.5
      done
      grep -o '<node[^>]*>' "$tmp/ui.xml" | grep -E "$1" | head -1 \
        | grep -o '\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]' | sed 's/\]\[/,/; s/[][]//g' \
        | awk -F, '{printf "%d %d", ($1+$3)/2, ($2+$4)/2}'
    }
    find_xy() {  # $1 = regex; $2 = "scroll": si no está, desplaza hacia abajo y vuelve a mirar (hasta 6 veces)
      for try in 1 2 3 4 5 6; do
        xy=$(node_xy "$1")
        if [ -n "$xy" ]; then echo "$xy"; return 0; fi
        [ "$2" = "scroll" ] || break
        safe swipe $X $LOW $X $HIGH 300 || return 1
        sleep 1
      done
      return 1
    }
    step() {  # $1 = fichero; el resto, la orden de entrada a medir: vacía framestats, la lanza y vuelca cuatro veces
      out=$1; shift
      focused || return 1
      adb shell dumpsys gfxinfo "$P" reset >/dev/null
      safe "$@" >/dev/null || return 1
      sleep 0.3
      adb shell dumpsys gfxinfo "$P" framestats > "$out"
      for k in 1 2 3; do sleep 0.4; adb shell dumpsys gfxinfo "$P" framestats >> "$out"; done
    }
    report() {  # $1 = nombre de la línea; $2 = prefijo de los ficheros de las cinco pasadas
      slows=""; early=""; jank=0; hzs=""
      for i in 1 2 3 4 5; do
        [ -s "$tmp/$2.$i" ] && keep "$tmp/$2.$i" "$1.$i.txt"
        slows="$slows $($PY tools/framestats.py --slow < "$tmp/$2.$i")"
        early="$early $($PY tools/framestats.py --slow --first 400 < "$tmp/$2.$i")"
        jank=$((jank + $($PY tools/framestats.py --jank < "$tmp/$2.$i")))
        hzs="$hzs $($PY tools/framestats.py --hz < "$tmp/$2.$i")"
      done
      valid=$(echo $slows | tr ' ' '\n' | grep -v '^-$' | sort -n)
      n=$(echo "$valid" | grep -c .)
      if [ "$n" -eq 0 ]; then say "$1 | sin medir"; return; fi
      med=$(echo "$valid" | awk '{a[NR]=$1} END {printf "%.1f", (NR%2)?a[(NR+1)/2]:(a[NR/2]+a[NR/2+1])/2}')
      worst=$(echo "$valid" | tail -1)
      # Además, solo los primeros 400 ms desde el toque: el paso entero, sin la escena que sigue pintando después y que
      # diluiría la transición en el total.
      evalid=$(echo $early | tr ' ' '
' | grep -v '^-$' | sort -n)
      emed=$(echo "$evalid" | awk '{a[NR]=$1} END {if (NR) printf "%.1f", (NR%2)?a[(NR+1)/2]:(a[NR/2]+a[NR/2+1])/2; else printf "-"}')
      eworst=$(echo "$evalid" | tail -1)
      hz=$(echo $hzs | tr ' ' '\n' | grep -v '^-$' | sort | uniq -c | sort -rn | awk 'NR==1 {print $2}')
      say "$1 | lentos mediana $med % | peor $worst % | 400 ms: mediana $emed % peor $eworst % | tirones $jank | $hz Hz | $n pasadas |$slows"
    }
    launch() {  # abre la demo y espera a que tenga el foco (hasta 15 s); $1 = argumentos, $2 = paso previo (regex)
      demo $1
      for w in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15; do focused && break; sleep 1; done
      sleep 6
      if [ -n "$2" ]; then
        if xy=$(find_xy "$2" scroll); then safe tap $xy; fi
        sleep 1.5
      fi
    }
    one_pass() {  # una pasada entera; falla (1) si en algún punto la app pierde el foco. $1 = nombre, $2 = pasada
      : > "$tmp/ida.$2"; : > "$tmp/vuelta.$2"
      xy=$(find_xy "$ido" "$scroll") || { echo "No encuentro «$ido» en $1 (pasada $2)" >&2; return 1; }
      step "$tmp/ida.$2" tap $xy || return 1
      sleep 1.5
      case "$back" in
        back) step "$tmp/vuelta.$2" keyevent 4 || return 1 ;;
        back2) safe keyevent 4 || return 1; sleep 1; step "$tmp/vuelta.$2" keyevent 4 || return 1 ;;
        *) xy=$(find_xy "$back" scroll) || return 1; safe tap $xy || return 1 ;;
      esac
      sleep 1.5
    }
    transition() {
      # $1 nombre · $2 argumentos de la demo · $3 regex del botón de ida · $4 «scroll» si hay que desplazar a buscarlo
      # $5 vuelta: «back» (atrás), «back2» (atrás para el teclado, sin medir, y atrás) o una regex (se toca, sin
      #    medir, para volver a empezar: no hay vuelta que medir) · $6 paso antes de las pasadas (regex de un toque)
      case "$1" in *"$ONLY"*) ;; *) return 0 ;; esac
      ido="$3"; scroll="$4"; back="$5"
      launch "$2" "$6"
      for k in 1 2 3 4 5; do : > "$tmp/ida.$k"; : > "$tmp/vuelta.$k"; done   # las que no se hagan cuentan como «-»
      i=1; fails=0
      while [ $i -le "${PASADAS:-5}" ]; do
        if one_pass "$1" $i; then
          i=$((i + 1)); fails=0
        else
          fails=$((fails + 1))
          if [ $fails -ge 3 ]; then echo "«$1»: tres fallos seguidos, sin medir" >&2; break; fi
          launch "$2" "$6"   # sin foco o sin botón: otra vez desde cero, la pasada entera
        fi
      done
      if [ $fails -ge 3 ]; then
        for k in 1 2 3 4 5; do : > "$tmp/ida.$k"; : > "$tmp/vuelta.$k"; done
      fi
      check_awake
      report "$1 ida" ida
      case "$5" in back|back2) report "$1 vuelta" vuelta ;; esac
    }

    SCENE="--es scene RAIN --es badge WARNING"
    transition "Ajustes"         "$SCENE"                   ' text="Ajustes"'                   scroll back
    transition "Sitios"          "$SCENE --es saved full"   'content-desc="[^"]*Cambiar de sitio"' "" back
    transition "Buscador"        "--es screen places --es saved full" ' text="Buscar un sitio"'  "" back2
    transition "Postal"          "--es screen places --es saved full" 'content-desc="Sevilla, '  "" 'content-desc="[^"]*Cambiar de sitio"'
    transition "Ventana del día" "$SCENE"                   ' text="[0-9]+°"'                   "" back
    transition "Hoja de días"    "$SCENE"                   'content-desc="Calidad del aire'    scroll back
    transition "Hoja de horas"   "$SCENE"                   ' text="Viento"'                    scroll back
    transition "Hoja del aviso"  "$SCENE"                   ' text="Aviso '                     "" back
    transition "Hoja de borrar"  "--es screen places --es saved full" 'content-desc="Borrar '   "" back ' text="Editar"'
    adb shell input keyevent HOME >/dev/null   # al acabar, Inicio
    ;;
  cpu)
    need_phone
    for scene in "SUN_MILD" "RAIN --es badge WARNING" "FOG" "NIGHT_CLEAR --es clock 23:04"; do
      demo --es scene $scene
      sleep 6
      out=$(adb shell simpleperf stat --app "$P" -e cpu-cycles,task-clock --duration 30 2>/dev/null)
      if ! echo "$out" | grep -q "cpus used"; then  # a veces no encuentra el proceso recién abierto: otra vez
        sleep 3
        out=$(adb shell simpleperf stat --app "$P" -e cpu-cycles,task-clock --duration 30 2>/dev/null)
      fi
      check_awake
      g=$(echo "$out" | awk '/cpu-cycles/ {gsub(",", "", $1); printf "%.2f", $1 / 30 / 1e9}')
      c=$(echo "$out" | awk '/task-clock/ {for (i=1;i<=NF;i++) if ($i=="cpus") printf "%.2f", $(i-1)}')
      say "${scene%% *} | $g Gciclos/s | $c núcleos"
    done
    ;;
  memoria)
    need_phone
    demo --es scene NIGHT_CLEAR --es clock 23:04
    sleep 10
    kb=$(adb shell dumpsys meminfo "$P" | awk '/TOTAL PSS:/ {print $3; exit}')
    say "TOTAL PSS $(awk "BEGIN {printf \"%.1f\", $kb / 1024}") MB"
    ;;
  apk)
    ./gradlew assembleRelease assemblePerf --console=plain -q || exit 1
    rel=$(ls app/build/outputs/apk/release/*.apk | head -1)
    perf=$(ls app/build/outputs/apk/perf/*.apk | head -1)
    mb() { awk "BEGIN {printf \"%.2f\", $(stat -c %s "$1") / 1048576}"; }
    say "release $(mb "$rel") MB ($(basename "$rel")) | perf $(mb "$perf") MB"
    ;;
  check)
    f=app/src/main/java/com/trastealabs/tiempo/MedidaCheck.kt
    trap 'rm -f "$f"' EXIT INT TERM
    ./gradlew --console=plain -q help >/dev/null   # demonio caliente
    printf 'package com.trastealabs.tiempo\n\n// Fichero temporal de tools/medir.sh check: se borra solo.\ninternal const val MEDIDA_CHECK = %sL\n' "$(date +%s)" > "$f"
    start=$(date +%s.%N)
    ./gradlew lintDebug testDebugUnitTest --console=plain -q > /dev/null 2>&1
    code=$?
    end=$(date +%s.%N)
    say "$(awk "BEGIN {printf \"%.0f\", $end - $start}") s (salida $code)"
    ;;
  *)
    sed -n '2,10p' "$0"
    exit 1
    ;;
esac
