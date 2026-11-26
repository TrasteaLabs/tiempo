"""Mide cómo se lee el texto sobre la escena desenfocada de Ajustes y Sitios y escribe `ScreenLooks.kt` (encargo 010).

Lee las 14 fotos del 009 (`app/src/main/res/drawable-nodpi/sky_*.webp`; nunca las escribe: eso es de
`build_skies.py`) y porta de la maqueta HTML del encargo 010 (ya retirada) el muestreo a
270 × 600, el desenfoque de 9 px, `brightest` (percentil 98, un píxel de cada 37), `sceneVeilFor`, `glassTintFor` y
`ratioAt`. Ejecutado dos veces, el fichero sale igual.

P1 escribe la escena (sceneVeil, screenTint, sceneContrast). P2 añade las postales de Sitios: `crop` (tabla FRANJA de la
maqueta: el centro de la franja de la foto que se ve en una postal) y el velo uniforme de la postal de 112 dp y el de la
de «Donde estoy» (148 dp), medidos con el `solve()` de la maqueta sobre las zonas de texto de ZONAS (percentil 1 ≥ 4,6:1).

Uso (desde la raíz del repo; necesita Pillow y numpy):
    python tools/build_looks.py
"""

import os

import numpy as np
from PIL import Image, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES_DIR = os.path.join(ROOT, "app", "src", "main", "res", "drawable-nodpi")
KT_FILE = os.path.join(ROOT, "app", "src", "main", "java", "com", "trastealabs", "tiempo", "ui", "scene", "ScreenLooks.kt")

SW, SH = 270, 600
INK = np.array([10.0, 16.0, 40.0])
TARGET = 4.6          # la maqueta pide 4,6:1 para dar 4,5 con margen
TEXT_ALPHA = 0.80     # texto sobre la escena
GLASS_TEXT_ALPHA = 0.75
MIN_VEIL = 0.20

# P2 · postales de Sitios. La foto se escala a su ancho (396 dp, como la maqueta) y se ve el trozo de alto H centrado en
# `FRANJA` (fracción del alto de la foto), sin salirse. Velo uniforme (capa «all» de la maqueta), tope 0,92.
POSTAL_W = 396
PHOTO_W, PHOTO_H = 1080, 2400
MAX_VEIL = 0.92
FRANJA = {  # tabla FRANJA de la maqueta (variante-a.html)
    "CLEAR_COOL": .14, "CLEAR_MILD": .18, "CLEAR_HOT": .40, "SCORCHING": .46, "CLOUDS": .34, "FOG": .30, "RAIN": .20,
    "STORM": .26, "SNOW": .30, "GOLDEN_CLEAR": .36, "GOLDEN_CLOUDS": .30, "NIGHT_CLEAR": .30, "NIGHT_CLOUDS": .26,
    "NIGHT_RAIN": .30,
}
# Zonas de texto (x, y, ancho, alto, opacidad) en dp dentro de la postal, con la disposición de `PlaceCard.kt`: columna
# izquierda con 20 dp de margen y columna derecha de la temperatura. La maqueta las mide del DOM (`data-a`); aquí se
# recogen de la disposición real de la app (otra letra, otros roles), con la misma regla: la zona entera del texto, con
# y sin segunda línea de datos. Los alfa son los `data-a` de la maqueta.
ZONAS = {
    112: [  # postal de un guardado: nombre 22/28, cielo 14/20, edad 12/16 | temperatura 45/52, máx/mín 12/16
        (20, 21.5, 224, 28, .92), (20, 51.5, 224, 20, .90), (20, 74.5, 224, 16, .75),   # con la línea de edad
        (20, 31.0, 224, 28, .92), (20, 61.0, 224, 20, .90),                              # sin ella
        (256, 21.0, 120, 52, .92), (256, 75.0, 120, 16, .80),
    ],
    148: [  # «Donde estoy»: etiqueta 12/16, nombre 24/32, cielo 14/20, edad 12/16 | temperatura 68/70, máx/mín 12/16
        (20, 29.5, 120, 16, .92), (20, 45.5, 224, 32, .92), (20, 79.5, 224, 20, .90), (20, 102.5, 224, 16, .75),
        (20, 39.0, 120, 16, .92), (20, 55.0, 224, 32, .92), (20, 89.0, 224, 20, .90),
        (246, 30.0, 130, 70, .92), (246, 102.0, 130, 16, .80),
    ],
}

# (valor del enum de Kotlin, fichero) en el orden de SkyPhoto
PHOTOS = [
    ("CLEAR_COOL", "sky_despejado_frio"), ("CLEAR_MILD", "sky_despejado_templado"), ("CLEAR_HOT", "sky_despejado_calor"),
    ("SCORCHING", "sky_calor_fuerte"), ("CLOUDS", "sky_nubes"), ("FOG", "sky_niebla"), ("RAIN", "sky_lluvia"),
    ("STORM", "sky_tormenta"), ("SNOW", "sky_nieve"), ("GOLDEN_CLEAR", "sky_dorada_despejado"),
    ("GOLDEN_CLOUDS", "sky_dorada_nubes"), ("NIGHT_CLEAR", "sky_noche_despejada"),
    ("NIGHT_CLOUDS", "sky_noche_nubes"), ("NIGHT_RAIN", "sky_noche_lluvia"),
]

_LIN = np.array([(i / 255 / 12.92) if i / 255 <= .03928 else ((i / 255 + .055) / 1.055) ** 2.4 for i in range(256)])


def lum(c):
    c = np.clip(np.rint(np.asarray(c, dtype=np.float64)), 0, 255).astype(int)
    return .2126 * _LIN[c[..., 0]] + .7152 * _LIN[c[..., 1]] + .0722 * _LIN[c[..., 2]]


def ratio_at(c, a):
    """Contraste del texto blanco con opacidad [a] sobre el color [c]."""
    c = np.asarray(c, dtype=np.float64)
    lb = lum(c)
    lt = lum(255 * a + c * (1 - a))
    return (max(lb, lt) + .05) / (min(lb, lt) + .05)


def blurred_pixels(path):
    """La foto a 330 × 660, desenfocada 9 px y recortada a 270 × 600 (`drawImage(im, -30, -30, SW + 60, SH + 60)`)."""
    im = Image.open(path).convert("RGB").resize((SW + 60, SH + 60), Image.BOX)
    im = im.filter(ImageFilter.GaussianBlur(9)).crop((30, 30, 30 + SW, 30 + SH))
    return np.asarray(im, dtype=np.float64).reshape(-1, 3)


def brightest(px):
    sample = px[::37]
    order = np.argsort(lum(sample), kind="stable")
    return sample[order[int(len(sample) * .98)]]


def search(f):
    """El menor valor de 0 a 0,9 con f(v) ≥ TARGET (14 pasos); 0 si f(0) ya llega."""
    lo, hi = 0.0, 0.9
    for _ in range(14):
        m = (lo + hi) / 2
        if f(m) >= TARGET:
            hi = m
        else:
            lo = m
    return hi


def look(path):
    px = blurred_pixels(path)
    c = brightest(px)

    def scene(a):
        return ratio_at(c * (1 - a) + INK * a, TEXT_ALPHA)

    veil = MIN_VEIL if scene(MIN_VEIL) >= TARGET else search(scene)
    base = c * (1 - veil) + INK * veil

    def glass(t):
        fill = base * .75 + 255 * .25
        return ratio_at(fill * (1 - t) + INK * t, GLASS_TEXT_ALPHA)

    tint = 0.0 if glass(0) >= TARGET else search(glass)
    return dict(veil=veil, tint=tint, contrast=scene(veil))


def postal_look(path, name):
    px = sample_pixels(path)
    crop = FRANJA[name]
    veil, c1 = postal_veil(px, crop, 112)
    here, c2 = postal_veil(px, crop, 148)
    return dict(crop=crop, postal=veil, here=here, contrast=min(c1, c2))


def sample_pixels(path):
    """La foto a 270 × 600 (`drawImage(im, 0, 0, SW, SH)` de la maqueta), sin desenfoque."""
    im = Image.open(path).convert("RGB").resize((SW, SH), Image.BOX)
    return np.asarray(im, dtype=np.float64).reshape(SH, SW, 3)


def postal_veil(px, crop, height):
    """Velo uniforme mínimo de una postal de [height] dp (puede ser [MAX_VEIL] si ni así llega) y su contraste."""
    hs = POSTAL_W * PHOTO_H / PHOTO_W
    top = min(max(crop * hs - height / 2, 0), hs - height)
    samples = []
    for (x0, y0, w, h, a) in ZONAS[height]:
        y = y0 + h * .18
        while y < y0 + h * .82:
            x = x0 + 1
            while x < x0 + w - 1:
                u = min(max(int(x / POSTAL_W * SW), 0), SW - 1)
                v = min(max(int((top + y) / hs * SH), 0), SH - 1)
                samples.append((px[v, u], a))
                x += 1.5
            y += 1.5
    colors = np.array([c for c, _ in samples])
    alphas = np.array([a for _, a in samples])

    def worst(k):
        mixed = colors * (1 - k) + INK * k
        lb = lum(mixed)
        lt = lum(255 * alphas[:, None] + mixed * (1 - alphas[:, None]))
        r = (np.maximum(lb, lt) + .05) / (np.minimum(lb, lt) + .05)
        return float(np.sort(r)[int(len(r) * .01)])

    if worst(0) >= TARGET:
        return 0.0, worst(0)
    lo, hi = 0.0, MAX_VEIL
    for _ in range(14):
        m = (lo + hi) / 2
        if worst(m) >= TARGET:
            hi = m
        else:
            lo = m
    return hi, worst(hi)


def f(v):
    s = f"{v:.2f}"
    return s + "f"


def main():
    rows = []
    for name, fid in PHOTOS:
        path = os.path.join(RES_DIR, fid + ".webp")
        r = look(path)
        q = postal_look(path, name)
        rows.append(
            f"    SkyPhoto.{name} to ScreenLook({f(r['veil'])}, {f(r['tint'])}, {f(r['contrast'])}, "
            f"{f(q['crop'])}, {f(q['postal'])}, {f(q['here'])}, {f(q['contrast'])}),"
        )
        print(f"{fid:24s} velo {r['veil']:.2f}  tono {r['tint']:.2f}  contraste {r['contrast']:.2f}  "
              f"postal {q['postal']:.2f} / {q['here']:.2f}  contraste {q['contrast']:.2f}")
    kt = f"""// Generado por tools/build_looks.py: no se edita a mano. Para cambiarlo, cambia el script y vuelve a ejecutarlo.
package com.trastealabs.tiempo.ui.scene

import com.trastealabs.tiempo.domain.SkyPhoto

/**
 * Cómo se lee el texto sobre cada foto en las pantallas de la 010 (medido por el script con los cálculos de la maqueta).
 * [sceneVeil]: alfa del velo de tinta #0A1028 sobre la foto desenfocada de Ajustes y Sitios (≥ 0,20).
 * [screenTint]: tono del cristal de encima (0 = cristal claro). [sceneContrast]: texto al 80 % sobre la escena (≥ 4,5).
 * Los demás campos son de las postales de Sitios (P2): [crop] es el centro de la franja de la foto que se ve (fracción de
 * su alto, tabla FRANJA de la maqueta), [postalVeil] y [herePostalVeil] el velo de tinta uniforme de la postal de 112 dp y
 * el de la de «Donde estoy» (148 dp), y [postalContrast] el peor texto de las dos (≥ 4,5).
 */
data class ScreenLook(
    val sceneVeil: Float,
    val screenTint: Float,
    val sceneContrast: Float,
    val crop: Float,
    val postalVeil: Float,
    val herePostalVeil: Float,
    val postalContrast: Float,
)

val SCREEN_LOOKS: Map<SkyPhoto, ScreenLook> = mapOf(
{chr(10).join(rows)}
)
"""
    with open(KT_FILE, "w", encoding="utf-8", newline="\n") as out:
        out.write(kt)
    print(os.path.relpath(KT_FILE, ROOT))


if __name__ == "__main__":
    main()
