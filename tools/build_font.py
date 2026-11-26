"""Saca la letra de la app: cinco ficheros .ttf fijos de Google Sans Flex (encargo 010, P1).

Cristal 1.2 (§4) solo permite los pesos 300, 400, 500, 600 y 700. Se instancia la letra variable de
Cuaderno (OFL, sin descargar nada) con redondez 0 y anchura 100, un fichero por peso y cada uno con su
propia familia, para que Android no mezcle pesos. El de 300 va con opsz 72 (solo lo llevan cifras de
40 sp o más); los demás, con el valor por defecto (18).

Uso (desde la raíz del repo; necesita fontTools y brotli):
    python tools/build_font.py [ruta/a/google-sans-flex.woff2]

Falla si falta en la letra algún carácter de res/values/strings.xml o de la lista fija «−°·…«»–—».
La salida es la misma cada vez (fechas fijas).
"""

import re
import shutil
import sys
from pathlib import Path

from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont

ROOT = Path(__file__).resolve().parents[1]
CUADERNO = ROOT.parent / "Cuaderno/src/ui/fonts"
SOURCE = CUADERNO / "google-sans-flex.woff2"
LICENSE = CUADERNO / "google-sans-flex.LICENSE.txt"
RES = ROOT / "app/src/main/res"
OUT = RES / "font"
LICENSE_OUT = ROOT / "app/src/main/assets/licenses/google_sans_flex_OFL.txt"
STRINGS = RES / "values/strings.xml"
WEIGHTS = (300, 400, 500, 600, 700)
DISPLAY_WEIGHT, DISPLAY_OPSZ, TEXT_OPSZ = 300, 72, 18
FAMILY = "Google Sans Flex"
FIXED_CHARS = "−°·…«»–—"
FIXED_DATE = 3786825600  # 2020-01-01, para que el fichero salga igual cada vez


def instance(source: Path, weight: int) -> TTFont:
    font = TTFont(source, recalcTimestamp=False)
    opsz = DISPLAY_OPSZ if weight == DISPLAY_WEIGHT else TEXT_OPSZ
    static = instantiateVariableFont(
        font, {"wght": weight, "ROND": 0, "opsz": opsz}, updateFontNames=False
    )
    static.flavor = None
    name = static["name"]
    ps = f"GoogleSansFlex-{weight}"
    for rec in list(name.names):
        if rec.nameID in (16, 17, 21, 22, 25):
            name.removeNames(nameID=rec.nameID)
    name.setName(f"{FAMILY} {weight}", 1, 3, 1, 0x409)
    name.setName("Regular", 2, 3, 1, 0x409)
    name.setName(f"{FAMILY} {weight}", 4, 3, 1, 0x409)
    name.setName(ps, 6, 3, 1, 0x409)
    static["OS/2"].usWeightClass = weight
    static["head"].created = FIXED_DATE
    static["head"].modified = FIXED_DATE
    return static


def used_chars() -> set[str]:
    text = STRINGS.read_text(encoding="utf-8")
    text = re.sub(r"<!--.*?-->", "", text, flags=re.S)
    text = re.sub(r"<[^>]+>", "", text)
    text = text.replace("\'", "'").replace("\n", "\n")
    return {c for c in text + FIXED_CHARS if not c.isspace()}


def main() -> None:
    source = Path(sys.argv[1]) if len(sys.argv) > 1 else SOURCE
    OUT.mkdir(parents=True, exist_ok=True)
    cmap = TTFont(source).getBestCmap()
    # %1$s y similares son marcas de formato, no letra.
    missing = sorted(c for c in used_chars() if ord(c) not in cmap)
    if missing:
        sys.exit("Faltan en la letra: " + " ".join(f"{c!r} U+{ord(c):04X}" for c in missing))
    print(f"Caracteres comprobados: sin ninguno que falte ({len(used_chars())} distintos)")
    for weight in WEIGHTS:
        target = OUT / f"google_sans_flex_{weight}.ttf"
        instance(source, weight).save(target)
        print(f"{target.relative_to(ROOT)}  {target.stat().st_size // 1024} KB")
    LICENSE_OUT.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(LICENSE if source == SOURCE else source.with_suffix(".LICENSE.txt"), LICENSE_OUT)
    print(LICENSE_OUT.relative_to(ROOT))


if __name__ == "__main__":
    main()
