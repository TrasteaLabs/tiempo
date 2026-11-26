"""Cifras de fluidez a partir de volcados de `adb shell dumpsys gfxinfo <paquete> framestats` (008, plan.md).

Uso:
  python tools/framestats.py < volcados.txt      # se pueden pegar varios volcados seguidos
  python tools/framestats.py --slow < volcado    # solo el % de lentos (011: una pasada de `medir.sh transiciones`)
  python tools/framestats.py --slow --first 400 < volcado   # lo mismo, solo con los fotogramas de los primeros 400 ms
  python tools/framestats.py --slow-android < volcado   # el % de lentos con la definición de Android (014, para comparar)
  python tools/framestats.py --jank < volcado    # solo los tirones
  python tools/framestats.py --hz < volcado      # solo la frecuencia a la que pintó la app (60, 90 o 120)

Cada fotograma cuenta una vez (por IntendedVsync) y se descartan las filas con Flags ≠ 0. Imprime:
- fotogramas;
- lentos: % de fotogramas que tardan más de su plazo, que es de dos vsync a la cadencia de la app, con suelo de 60 Hz:
  33,3 ms (SLOW_INTERVALS × 1000 / min(Hz de la app, 60)). Un fotograma de 17–22 ms llega a tiempo y no se nota.
  No es la definición de Android (FrameCompleted > FrameDeadline), que en el OPPO depende del estado de frecuencia de
  ColorOS: con `renderFrameRate` en reposo a 120,00001 el FrameDeadline sale de una línea de 120 Hz (12, 20, 28, 37 ms
  desde el vsync, de 8,33 en 8,33) aunque la app pinte a 60 Hz, y casi todo sale «tarde» sin que se note (pantalla quieta
  67–82 % frente a 0 %; 014). La de Android sigue en `--slow-android`;
- tirones: cuántos tardan más de 32 ms (duración = FrameCompleted − IntendedVsync), los que se notan;
- peor, p90 y mediana de la duración, en ms;
- con gestos marcados (una línea `### gesto` antes de los volcados de cada uno, como hace `medir.sh dias`), el
  primer fotograma de cada gesto, el primero con entrada (InputEventId ≠ 0): lo que tarda la app en él, del vsync a
  entregar el dibujo (SyncQueued − IntendedVsync), y en cuántos gestos es de 17 ms o menos (H9 del 009, decisión del
  usuario en P5: el resto del fotograma es la GPU, 10–17 ms en cualquier fotograma del OPPO, también con todo quieto);
  y su duración entera, como los tirones. Los gestos sin fotograma con entrada salen como «?» y no cuentan como buenos.

`framestats` solo guarda los últimos ~120 fotogramas: `tools/medir.sh` vuelca cada segundo (escena quieta) o tras
cada gesto.
"""
import statistics
import sys

JANK_MS = 32
SLOW_INTERVALS = 2     # el plazo de «lento»: dos vsync de la cadencia de la app
SLOW_MIN_HZ = 60       # suelo de la cadencia: a 90 o 120 Hz el plazo no baja de 33,3 ms (la GPU del OPPO tarda 10–17 ms
                       # en cualquier fotograma; a 120 Hz, 2 vsync = 16,7 ms contaría como lento casi cada fotograma)
FIRST_FRAME_MS = 17
GESTURE_MARK = "### gesto"


def rows(text):
    """Fotogramas acabados y sin Flags, uno por IntendedVsync: {intended: (duración ms, tarde, con entrada, app ms)}."""
    frames = {}
    header = None
    for line in text.splitlines():
        if line.startswith("Flags,"):
            header = line.rstrip(",").split(",")
            continue
        if header is None or not line[:1].isdigit():
            continue
        cells = line.rstrip(",").split(",")
        if len(cells) < len(header):
            continue
        row = dict(zip(header, cells))
        if row["Flags"] != "0":
            continue
        intended = int(row["IntendedVsync"])
        completed = int(row["FrameCompleted"])
        if completed <= intended:  # el fotograma aún no había acabado al volcar
            continue
        queued = int(row.get("SyncQueued", "0"))
        frames[intended] = ((completed - intended) / 1e6, completed > int(row["FrameDeadline"]),
                            row.get("InputEventId", "0") != "0", (queued - intended) / 1e6 if queued > intended else None)
    return frames


def slow_limit_ms(hz):
    """Plazo de «lento» en ms para una app que pinta a [hz] (None si no se sabe): SLOW_INTERVALS vsync, con suelo de SLOW_MIN_HZ."""
    return SLOW_INTERVALS * 1000 / min(hz or SLOW_MIN_HZ, SLOW_MIN_HZ)


def parse(text, first_ms=None, android=False):
    """
    Duración y «tarde» de cada fotograma; con [first_ms], solo los que empiezan en los primeros [first_ms] ms del volcado.
    «Tarde» = tarda más de slow_limit_ms de la cadencia de la app (refresh_hz del volcado entero); con [android], la
    definición de Android (FrameCompleted > FrameDeadline).
    """
    frames = rows(text)
    if first_ms is not None and frames:
        t0 = min(frames)
        frames = {k: v for k, v in frames.items() if (k - t0) / 1e6 <= first_ms}
    limit = slow_limit_ms(refresh_hz(text))
    return [(d, late if android else d > limit) for d, late, _, _ in frames.values()]


def first_frames(text, part="app"):
    """
    El primer fotograma con entrada de cada gesto marcado, en orden y en ms: lo que tarda la app ([part] = "app") o
    entero ("total"). None si el gesto no tiene fotograma con entrada (o, para la app, sin SyncQueued).
    """
    out = []
    for chunk in text.split(GESTURE_MARK)[1:]:
        with_input = sorted((t, f) for t, f in rows(chunk).items() if f[2])
        if not with_input:
            out.append(None)
            continue
        dur, _, _, app = with_input[0][1]
        out.append(app if part == "app" else dur)
    return out


def slow_percent(frames):
    """Solo el % de lentos (con un decimal), o None si el volcado no tiene fotogramas."""
    if not frames:
        return None
    return round(100 * sum(1 for _, late in frames if late) / len(frames), 1)


def jank_count(frames):
    return sum(1 for d, _ in frames if d > JANK_MS)


def refresh_hz(text):
    """
    Frecuencia a la que pintó la app: la mediana de lo que pasa entre dos vsync seguidos de los fotogramas del
    volcado (los saltos de más de 40 ms son pausas, no frecuencia), redondeada a 60, 90 o 120. None si no se puede.
    """
    starts = sorted(rows(text))
    gaps = [(b - a) / 1e6 for a, b in zip(starts, starts[1:]) if 0 < (b - a) / 1e6 <= 40]
    if not gaps:
        return None
    hz = 1000 / statistics.median(gaps)
    return min((60, 90, 120), key=lambda f: abs(f - hz))


def summary(frames, firsts=(), totals=()):
    n = len(frames)
    if n == 0:
        return "fotogramas 0"
    durations = sorted(d for d, _ in frames)
    late = sum(1 for _, missed in frames if missed)
    jank = sum(1 for d in durations if d > JANK_MS)
    p90 = durations[min(n - 1, int(0.9 * n))]
    line = (f"fotogramas {n} | lentos {100 * late / n:.1f} % | tirones {jank} | peor {durations[-1]:.0f} ms | "
            f"p90 {p90:.1f} ms | mediana {statistics.median(durations):.1f} ms")
    if firsts:
        good = sum(1 for d in firsts if d is not None and d <= FIRST_FRAME_MS)
        shown = " ".join("?" if d is None else f"{d:.0f}" for d in firsts)
        line += f" | primer fotograma (app) ≤ {FIRST_FRAME_MS} ms en {good} de {len(firsts)} | primeros (app) {shown}"
    if totals:
        line += " | primeros (enteros) " + " ".join("?" if d is None else f"{d:.0f}" for d in totals)
    return line


if __name__ == "__main__":
    args = sys.argv[1:]
    first = None
    if len(args) == 3 and args[1] == "--first" and args[2].isdigit():
        first = int(args[2])
        args = args[:1]
    option = args[0] if args else None
    if (option not in (None, "--slow", "--slow-android", "--jank", "--hz") or len(args) > 1
            or (first is not None and option not in ("--slow", "--slow-android"))):
        sys.exit(__doc__)
    sys.stdout.reconfigure(encoding="utf-8")  # «≤» por la tubería de Git Bash en Windows
    text = sys.stdin.read()
    if option == "--slow":
        value = slow_percent(parse(text, first))
        print("-" if value is None else f"{value:.1f}")
        sys.exit(0)
    if option == "--slow-android":
        value = slow_percent(parse(text, first, android=True))
        print("-" if value is None else f"{value:.1f}")
        sys.exit(0)
    if option == "--jank":
        print(jank_count(parse(text)))
        sys.exit(0)
    if option == "--hz":
        value = refresh_hz(text)
        print("-" if value is None else value)
        sys.exit(0)
    print(summary(parse(text), first_frames(text), first_frames(text, "total")))
