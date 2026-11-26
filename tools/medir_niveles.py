"""Mide el punto más claro detrás del texto en cada superficie de la app y escribe `TextBackdrops.kt` (encargo 019).

Cristal 1.4 (D25): todo texto llega a 4,5:1 sobre el punto más claro de lo que tiene detrás, con la foto más clara de
las 14 y su velo. Este script no decide velos (los decide `build_skies.py` y `build_looks.py`, y no se tocan): lee las
14 fotos y los velos ya generados (`PhotoLooks.kt`, `ScreenLooks.kt`) y saca, para cada foto y cada superficie, el color
del punto más claro, con la misma zona y el mismo percentil con los que esos scripts miden su velo:

  - header: la cabecera de la principal, con el resplandor del sol (y la niebla) y el velo `headerVeil` (20 dp, 10 %);
  - card: las tarjetas translúcidas de la principal, con el borde más claro de su degradado (20 dp, 3 %);
  - scene: la escena desenfocada de Ajustes y Sitios con su velo `sceneVeil` (percentil 98);
  - screen_card: tarjetas y campos de Ajustes y Sitios sobre esa escena, con su tono `screenTint`;
  - postal: las zonas de texto de las postales de Sitios con su velo (percentil 99, como `WidgetVeil`);
  - day_band: la franja de la foto de la ventana del día con su oscurecido, en la zona del texto (percentil 99). Aquí,
    con el texto al 100 %, siete fotos de día no llegaban: el script calcula un oscurecido extra por foto (el más suave
    que llega a 4,6:1) y lo escribe en `ui/scene/DayBandLooks.kt`.
Las superficies opacas (hojas, ventana del día, diálogo) son colores fijos y van escritos en el test.

El test `TextLevelsTest` comprueba que cada nivel de `OnSky` llega a 4,5:1 sobre todos estos colores. Si se cambian las
fotos o los velos, se vuelve a ejecutar.

Uso (desde la raíz del repo; necesita Pillow y numpy):
    python tools/medir_niveles.py
"""

import math
import os
import re
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import build_looks as bl  # noqa: E402
import build_skies as bs  # noqa: E402

ROOT = bl.ROOT
KT_FILE = os.path.join(ROOT, "app", "src", "test", "java", "com", "trastealabs", "tiempo", "ui", "theme", "TextBackdrops.kt")
SCENE = os.path.join(ROOT, "app", "src", "main", "java", "com", "trastealabs", "tiempo", "ui", "scene")

FID = {name: fid for name, fid in bl.PHOTOS}
SKIES_ID = {name: fid.removeprefix("sky_").replace("_", "-") for name, fid in bl.PHOTOS}

# Franja de la ventana del día (`DayPage.kt`): 196 dp de alto en una ventana de 404 dp (420 − 2 × 8), foto al 118 % del
# ancho, subida un 8 % del alto, y el oscurecido de 0 en el 30 % a 0,55 abajo. El texto va abajo: de 70 a 182 dp de alto,
# con 18 dp de margen a cada lado.
BAND_W, BAND_H = 404, 196
BAND_TEXT = (18, 70, BAND_W - 18, 182)


def parse_looks(path, cls):
    rows = {}
    for m in re.finditer(r"SkyPhoto\.(\w+) to " + cls + r"\(([^)]*)\)", open(path, encoding="utf-8").read()):
        rows[m.group(1)] = [float(v.strip().rstrip("f")) for v in m.group(2).split(",")]
    return rows


def brightest_of(colors, q):
    """El color con el que el blanco da el contraste del cuantil [q] peor (los más claros primero)."""
    colors = np.asarray(colors, dtype=np.float64)
    order = np.argsort(-bl.lum(colors), kind="stable")
    return colors[order[int(math.floor(len(colors) * q))]]


def photo_dp(path):
    img = Image.open(path).convert("RGB")
    seen = img.resize((bs.FW, round(bs.H / bs.S)), Image.BOX).crop((0, 0, bs.FW, bs.SEEN_DP))
    return np.asarray(seen, dtype=np.float64)


def header_and_card(path, name, look):
    glass_tint, header_veil = look[0], look[1]
    px = photo_dp(path)
    fh = bs.SEEN_DP
    fid = SKIES_ID[name]
    lit = bs.with_fog(px) if fid == "niebla" else px
    head = bs.blocks(bs.with_flare(lit, bs.FLARES.get(fid)), round(fh * .06), round(fh * .40), round(bs.FW * .08), bs.FW * .92)
    head = np.array([10, 16, 40]) * header_veil + head * (1 - header_veil)
    cards = bs.blocks(px, round(fh * .27), fh, 0, bs.FW)
    if glass_tint <= 0:
        cards = 255 * .30 + cards * (1 - .30)
    else:
        a = max(.04, glass_tint - .06)
        cards = np.array([16, 22, 46]) * a + cards * (1 - a)
    return brightest_of(head, .10), brightest_of(cards, .03)


def scene_and_screen(path, look):
    veil, tint = look[0], look[1]
    c = bl.brightest(bl.blurred_pixels(path))
    base = c * (1 - veil) + bl.INK * veil
    fill = base * .75 + 255 * .25
    return base, fill * (1 - tint) + bl.INK * tint


def postal(path, look):
    crop, veil112, veil148 = look[3], look[4], look[5]
    px = bl.sample_pixels(path)
    out = []
    for height, veil in ((112, veil112), (148, veil148)):
        hs = bl.POSTAL_W * bl.PHOTO_H / bl.PHOTO_W
        top = min(max(crop * hs - height / 2, 0), hs - height)
        samples = []
        for (x0, y0, w, h, _a) in bl.ZONAS[height]:
            y = y0 + h * .18
            while y < y0 + h * .82:
                x = x0 + 1
                while x < x0 + w - 1:
                    u = min(max(int(x / bl.POSTAL_W * bl.SW), 0), bl.SW - 1)
                    v = min(max(int((top + y) / hs * bl.SH), 0), bl.SH - 1)
                    samples.append(px[v, u])
                    x += 1.5
                y += 1.5
        mixed = np.array(samples) * (1 - veil) + bl.INK * veil
        out.append(brightest_of(mixed, .01))
    return max(out, key=lambda c: float(bl.lum(c)))


def band_shade(t, k):
    """Alfa del oscurecido de la franja a la fracción [t] de su alto: el de siempre (0 en el 30 %, 0,55 abajo) o, si la foto
    lo pide, [k] con una rampa suave (smoothstep) del 5 al 40 % del alto, lo que sea más. Igual que `bandShadeAt` de Kotlin."""
    base = min(max((t - .3) / .7, 0), 1) * .55
    u = min(max((t - .05) / .35, 0), 1)
    return max(base, k * u * u * (3 - 2 * u))


def day_band(path, k=0.0):
    img = Image.open(path).convert("RGB")
    w = BAND_W * 1.18
    h = w * img.height / img.width
    photo = np.asarray(img.resize((round(w), round(h)), Image.BOX), dtype=np.float64)
    x_off = (BAND_W - w) / 2
    y_off = -BAND_H * .08
    x0, y0, x1, y1 = BAND_TEXT
    samples = []
    y = y0
    while y < y1:
        shade = band_shade(y / BAND_H, k)
        x = x0
        while x < x1:
            c = photo[int(y - y_off), int(x - x_off)]
            samples.append(c * (1 - shade) + np.array([10, 16, 40]) * shade)
            x += 1.5
        y += 1.5
    return brightest_of(samples, .01)


def solve_band(path):
    """La fuerza más suave (de 0 a 0,9, en pasos de 0,02) con la que el blanco llega a 4,6:1 en la franja."""
    k = 0.0
    while k < .9 and bl.ratio_at(day_band(path, k), 1.0) < bl.TARGET:
        k = round(k + .02, 2)
    return k


def rgb(c):
    return ", ".join(f"{v:.1f}f" for v in c)


def main():
    photo_looks = parse_looks(os.path.join(SCENE, "PhotoLooks.kt"), "PhotoLook")
    screen_looks = parse_looks(os.path.join(SCENE, "ScreenLooks.kt"), "ScreenLook")
    rows = []
    bands = []
    for name, fid in bl.PHOTOS:
        path = os.path.join(bl.RES_DIR, fid + ".webp")
        header, card = header_and_card(path, name, photo_looks[name])
        scene, screen = scene_and_screen(path, screen_looks[name])
        k = solve_band(path)
        bands.append(f"    SkyPhoto.{name} to {k:.2f}f,")
        surfaces = dict(header=header, card=card, scene=scene, screen_card=screen,
                        postal=postal(path, screen_looks[name]), day_band=day_band(path, k))
        line = []
        for surface, c in surfaces.items():
            rows.append(f'    TextBackdrop(SkyPhoto.{name}, "{surface}", {rgb(c)}),')
            line.append(f"{surface} {bl.ratio_at(c, 1.0):.2f}")
        print(f"{name:14s} " + "  ".join(line) + f"  (franja {k:.2f})")
    kt = f"""// Generado por tools/medir_niveles.py: no se edita a mano. Para cambiarlo, cambia el script y vuelve a ejecutarlo.
package com.trastealabs.tiempo.ui.theme

import com.trastealabs.tiempo.domain.SkyPhoto

/** El punto más claro detrás del texto en [surface] sobre [photo], ya con su velo (rgb 0–255). */
data class TextBackdrop(val photo: SkyPhoto, val surface: String, val r: Float, val g: Float, val b: Float)

val TEXT_BACKDROPS: List<TextBackdrop> = listOf(
{chr(10).join(rows)}
)
"""
    with open(KT_FILE, "w", encoding="utf-8", newline="\n") as out:
        out.write(kt)
    print(os.path.relpath(KT_FILE, ROOT))
    band_kt = f"""// Generado por tools/medir_niveles.py: no se edita a mano. Para cambiarlo, cambia el script y vuelve a ejecutarlo.
package com.trastealabs.tiempo.ui.scene

import com.trastealabs.tiempo.domain.SkyPhoto

/**
 * La fuerza del oscurecido extra de la franja de la ventana del día (019): 0 si el de siempre ya deja el texto blanco a
 * 4,6:1 sobre el punto más claro; si no, la más suave que llega (ver `bandShadeAt` en `DayPage.kt`).
 */
val DAY_BAND_SHADE: Map<SkyPhoto, Float> = mapOf(
{chr(10).join(bands)}
)
"""
    band_file = os.path.join(SCENE, "DayBandLooks.kt")
    with open(band_file, "w", encoding="utf-8", newline="\n") as out:
        out.write(band_kt)
    print(os.path.relpath(band_file, ROOT))


if __name__ == "__main__":
    main()
