"""Genera los fondos del cielo (009, H2) a partir de la tabla FONDOS de la maqueta.

Uso: python tools/build_skies.py [--hoja salida.png]

Escribe en app/src/main/res/drawable-nodpi/:
  - sky_<id>.webp (14): degradado + capas de nubes pintadas con ruido, 1080 × 2400, WebP calidad 80.
  - sky_cloud_layer.png: la capa de nubes que se mueve encima (P2), con transparencia y repetible de lado.
y app/src/main/java/com/trastealabs/tiempo/ui/scene/PhotoLooks.kt (generado: no se edita a mano) con el tono del cristal y el
velo de la cabecera de cada foto, medidos como `solveGlass` de la maqueta.

Es `hash2`, `vnoise`, `fbm`, `presence`, `cloudLayer`, `fondo` y `solveGlass` de
la maqueta HTML del encargo 009 (ya retirada), con las coordenadas del marco de 420 dp pasadas a 1080 px. Siempre da
lo mismo: se puede ejecutar dos veces y comparar los sha256. Con --hoja (por defecto build/skies/hoja-contactos.png)
guarda una hoja de contactos de las 15 para compararla con la hoja de fondos de la maqueta.
"""
import io
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES_DIR = os.path.join(ROOT, "app", "src", "main", "res", "drawable-nodpi")
KT_FILE = os.path.join(ROOT, "app", "src", "main", "java", "com", "trastealabs", "tiempo", "ui", "scene", "PhotoLooks.kt")
SHEET_DEFAULT = os.path.join(ROOT, "build", "skies", "hoja-contactos.png")

# El marco de la maqueta (dp) y la foto (px). 2400 px / (1080/420) = 933 dp: el alto de la foto de la maqueta (PH).
FW = 420
W, H = 1080, 2400
S = W / FW
# Lo que se ve en el OPPO: 421 × 926 dp, la foto cubre el ancho (para medir el cristal como la maqueta).
SEEN_DP = 926
WEBP_QUALITY = 80


def hex_rgb(h):
    return [int(h[i:i + 2], 16) for i in (1, 3, 5)]


def cirrus(c, a, y, seed):
    return dict(c=c, sh=[v * .86 for v in c], a=min(1, a * 1.5), scale=200, stretch=3.2, lo=.5, hi=.74, y=y, res=.5,
                seed=seed, warp=1.1, light=3)


# La tabla FONDOS de la maqueta, tal cual (sin «despejado», que era de la variante C).
FONDOS = {
    "despejado-frio": ([(0, "#1C5CBD"), (.45, "#3B84DA"), (.8, "#7DB3EA"), (1, "#A6CCF1")],
                       [cirrus([250, 252, 255], .55, [0, .55], 3)]),
    "despejado-templado": ([(0, "#2E6DC1"), (.4, "#5994D3"), (.68, "#A8BED0"), (.86, "#E8C48F"), (1, "#EFCB97")],
                           [cirrus([255, 232, 208], .5, [0, .72], 11)]),
    "despejado-calor": ([(0, "#4A83C3"), (.35, "#84A8CA"), (.66, "#D8B48B"), (1, "#ECB176")],
                        [dict(c=[255, 236, 214], sh=[230, 196, 160], a=.32, scale=560, stretch=2.4, lo=.42, hi=.75,
                              y=[0, 1], seed=21, warp=.5, light=2)]),
    "calor-fuerte": ([(0, "#7686A8"), (.3, "#C3A08B"), (.62, "#DE9466"), (1, "#CF6E4A")],
                     [dict(c=[255, 214, 180], sh=[214, 150, 110], a=.36, scale=600, stretch=2.6, lo=.4, hi=.76,
                           y=[0, 1], seed=31, warp=.5, light=2)]),
    "nubes": ([(0, "#5A7397"), (.5, "#8197B6"), (1, "#A6B4C8")],
              [dict(c=[214, 222, 236], sh=[118, 134, 162], a=.7, scale=420, stretch=2, lo=.44, hi=.7, y=[0, 1],
                    seed=41, warp=.7, light=6),
               dict(c=[246, 248, 252], sh=[128, 144, 172], a=.95, scale=230, stretch=1.6, lo=.5, hi=.66, y=[0, .74],
                    seed=43, res=.42, warp=.7, light=8)]),
    "niebla": ([(0, "#959CAE"), (.5, "#B7B9C3"), (1, "#CFCACB")],
               [dict(c=[236, 236, 240], sh=[170, 172, 184], a=.55, scale=620, stretch=3, lo=.38, hi=.78, y=[0, 1],
                     seed=51, warp=.4, light=2)]),
    "lluvia": ([(0, "#4B596F"), (.5, "#69778C"), (1, "#8490A1")],
               [dict(c=[176, 186, 200], sh=[66, 78, 100], a=.85, scale=320, stretch=1.8, lo=.36, hi=.7, y=[0, 1],
                     seed=61, warp=.8, light=7)]),
    "tormenta": ([(0, "#221F37"), (.5, "#383553"), (1, "#53506D")],
                 [dict(c=[124, 118, 156], sh=[28, 26, 46], a=.92, scale=270, stretch=1.5, lo=.38, hi=.7, y=[0, 1],
                       seed=71, warp=.9, light=9)]),
    "nieve": ([(0, "#8394AE"), (.5, "#ACB7C8"), (1, "#CCD3DD")],
              [dict(c=[238, 241, 246], sh=[150, 160, 180], a=.7, scale=340, stretch=2, lo=.4, hi=.74, y=[0, 1],
                    seed=81, warp=.6, light=5)]),
    "dorada-despejado": ([(0, "#2A60B0"), (.33, "#5689CB"), (.6, "#DDB58B"), (.76, "#F1B57F"), (1, "#7C8DA7")],
                         [cirrus([255, 198, 150], .75, [.12, .76], 91)]),
    "dorada-nubes": ([(0, "#394A76"), (.4, "#877D9F"), (.7, "#DE9F7E"), (1, "#8A7B8D")],
                     [dict(c=[255, 178, 132], sh=[92, 82, 122], a=.9, scale=240, stretch=2.3, lo=.48, hi=.68,
                           y=[0, .8], seed=101, warp=.8, light=9)]),
    "noche-despejada": ([(0, "#050A21"), (.5, "#0E193F"), (1, "#21305E")],
                        [dict(c=[110, 130, 200], sh=[60, 70, 130], a=.14, scale=520, stretch=3, lo=.45, hi=.8,
                              y=[0, 1], seed=111, warp=.5, light=1)]),
    "noche-nubes": ([(0, "#0A0F27"), (.5, "#17203E"), (1, "#293251")],
                    [dict(c=[100, 110, 146], sh=[18, 22, 42], a=.8, scale=250, stretch=1.8, lo=.46, hi=.7, y=[0, .85],
                          seed=121, warp=.8, light=8)]),
    "noche-lluvia": ([(0, "#0F1329"), (.5, "#1C223F"), (1, "#2C324F")],
                     [dict(c=[74, 82, 108], sh=[14, 16, 30], a=.88, scale=300, stretch=1.8, lo=.34, hi=.7, y=[0, 1],
                           seed=131, warp=.8, light=7)]),
}

# id de la maqueta → nombre del enum SkyPhoto (plan, contratos).
ENUM = {
    "despejado-frio": "CLEAR_COOL", "despejado-templado": "CLEAR_MILD", "despejado-calor": "CLEAR_HOT",
    "calor-fuerte": "SCORCHING", "nubes": "CLOUDS", "niebla": "FOG", "lluvia": "RAIN", "tormenta": "STORM",
    "nieve": "SNOW", "dorada-despejado": "GOLDEN_CLEAR", "dorada-nubes": "GOLDEN_CLOUDS",
    "noche-despejada": "NIGHT_CLEAR", "noche-nubes": "NIGHT_CLOUDS", "noche-lluvia": "NIGHT_RAIN",
}

# La capa de nubes de delante (VEIL de la maqueta): 2 anchos de pantalla por 0,7 de alto, repetible de lado.
CAPA = dict(c=[255, 255, 255], sh=[150, 155, 170], a=.9, scale=150, stretch=1.8, lo=.54, hi=.72, y=[.02, .62],
            seed=201, res=.5, warp=.7, light=6)
CAPA_W, CAPA_H = FW * 2 * S, 616 * S   # en px; se guarda a su resolución de cálculo (res), la app la estira


# ---------- el ruido de la maqueta ----------
M32 = 0xFFFFFFFF


def imul(a, b):
    """`Math.imul` sin signo: el producto de 32 bits (los bits son los mismos que con signo)."""
    return ((a & M32) * (b & M32)) & M32


def hash2(ix, iy, seed):
    h = (imul(ix, 374761393) + imul(iy, 668265263) + imul(np.int64(seed), 982451653)) & M32
    h = imul(h ^ (h >> 13), 1274126177)
    h ^= h >> 16
    return h.astype(np.float64) / 4294967296.0


def jsmod(a, n):
    return ((a % n) + n) % n


def vnoise(x, y, seed, period):
    ix = np.floor(x)
    iy = np.floor(y)
    fx, fy = x - ix, y - iy
    u = fx * fx * (3 - 2 * fx)
    v = fy * fy * (3 - 2 * fy)
    if period:
        # En JS, `mod` con un periodo no entero da un número con decimales que `Math.imul` trunca.
        i0 = np.trunc(jsmod(ix, period)).astype(np.int64)
        i1 = np.trunc(jsmod(ix + 1, period)).astype(np.int64)
    else:
        i0 = ix.astype(np.int64)
        i1 = i0 + 1
    iy = iy.astype(np.int64)
    a = hash2(i0, iy, seed)
    b = hash2(i1, iy, seed)
    c = hash2(i0, iy + 1, seed)
    d = hash2(i1, iy + 1, seed)
    return a + (b - a) * u + (c - a) * v + (a - b - c + d) * u * v


def fbm(x, y, seed, octaves, period):
    s, a, f, n = 0.0, .5, 1.0, 0.0
    for i in range(octaves):
        s = s + a * vnoise(x * f, y * f, seed + i * 17, period * f if period else 0)
        n += a
        a *= .5
        f *= 2
    return s / n


def smooth(x):
    x = np.clip(x, 0, 1)
    return x * x * (3 - 2 * x)


def sstep(a, b, x):
    return smooth((x - a) / (b - a))


def presence(y, r):
    y0, y1 = r
    top = 1 if y0 <= 0 else sstep(y0 - .1, y0 + .08, y)
    return top * (1 - sstep(y1 - .15, y1 + .1, y))


def cloud_layer(L, width, height, periodic=False):
    """Una capa de nubes a su resolución de cálculo (RGBA, sin premultiplicar), como `cloudLayer` de la maqueta."""
    r = L.get("res", .34)
    w, h = math.ceil(width * r), math.ceil(height * r)
    sc = L["scale"] * S * r
    sy = 1 / sc
    sx = 1 / (sc * L.get("stretch", 1))
    warp = L.get("warp", .6)
    lk = L.get("light", 7)
    period = max(1, round(w * sx)) if periodic else 0
    if periodic:
        # Que el ancho sea justo un número entero de celdas: así lo que sale por la derecha entra por la izquierda sin
        # costura (en la maqueta queda una de un 3 %). Estira la capa menos de un 4 %.
        sx = period / w
    X = np.arange(w, dtype=np.float64)[None, :] * sx
    Y = np.arange(h, dtype=np.float64)[:, None] * sy
    X, Y = np.broadcast_arrays(X, Y)
    pres = presence(np.arange(h, dtype=np.float64)[:, None] / h, L["y"]) * np.ones((1, w))
    half = period / 2 if period else 0
    if warp:
        wx = fbm(X * .5 + 3.1, Y * .5, L["seed"] + 5, 3, half) - .5
        wy = fbm(X * .5, Y * .5 + 7.7, L["seed"] + 9, 3, half) - .5
    else:
        wx = wy = 0
    n = fbm(X + warp * wx, Y + warp * wy, L["seed"], 5, period)
    a = sstep(L["lo"], L["hi"], n) * pres
    n2 = fbm(X + warp * wx, Y + warp * wy - .18, L["seed"], 5, period)
    lit = np.clip(.55 + (n - n2) * lk, 0, 1)
    lit = lit * (1 - .3 * sstep(L["hi"], L["hi"] + .18, n))
    out = np.zeros((h, w, 4), dtype=np.float64)
    c, shade = np.array(L["c"], dtype=np.float64), np.array(L["sh"], dtype=np.float64)
    out[..., :3] = shade + (c - shade) * lit[..., None]
    out[..., 3] = 255 * a * L["a"]
    # La maqueta salta los píxeles casi vacíos (quedan a 0).
    empty = (pres <= .002) | (a <= .003)
    out[empty] = 0
    return Image.fromarray(np.clip(np.rint(out), 0, 255).astype(np.uint8), "RGBA")


def gradient(stops, width, height):
    t = (np.arange(height, dtype=np.float64) + .5) / height
    offs = [o for o, _ in stops]
    cols = np.array([hex_rgb(c) for _, c in stops], dtype=np.float64)
    rgb = np.stack([np.interp(t, offs, cols[:, k]) for k in range(3)], axis=1)
    img = np.repeat(rgb[:, None, :], width, axis=1)
    return Image.fromarray(np.clip(np.rint(img), 0, 255).astype(np.uint8), "RGB")


def upscale(layer, width, height):
    """`drawImage` con suavizado alto: se escala premultiplicado (como el canvas) para que los bordes no se oscurezcan."""
    return layer.convert("RGBa").resize((width, height), Image.BICUBIC).convert("RGBA")


def paint(fid):
    stops, layers = FONDOS[fid]
    img = gradient(stops, W, H).convert("RGBA")
    for L in layers:
        img = Image.alpha_composite(img, upscale(cloud_layer(L, W, H), W, H))
    return img.convert("RGB")


# ---------- el cristal y el velo (solveGlass) ----------
def lum(c):
    v = c / 255.0
    v = np.where(v <= .03928, v / 12.92, ((v + .055) / 1.055) ** 2.4)
    return .2126 * v[..., 0] + .7152 * v[..., 1] + .0722 * v[..., 2]


def ratio_white(c):
    return 1.05 / (lum(c) + .05)


def blocks(px, y0, y1, x0, x1, b=20):
    """Medias de bloques de b × b dp (un píxel de cada dos, como la maqueta) en la foto pasada a dp."""
    out = []
    by = y0
    while by + b <= y1:
        bx = x0
        while bx + b <= x1:
            out.append(px[by:by + b:2, bx:bx + b:2].reshape(-1, 3).mean(axis=0))
            bx += b
        by += b
    return np.array(out)


# Los bancos de niebla y el resplandor del sol que la capa de luz pinta encima de cada foto (`flareTexture` de `LightLayer.kt`, sin los haces ni
# los reflejos, que apenas aclaran): tipo por foto, como `effectsOf` de `SkyPhoto.kt`. La cabecera se mide con él (009, P3:
# sin él, en el OPPO las fotos de día se quedaban entre 2,3 y 4,0:1).
FLARES = {
    "despejado-frio": "full", "despejado-templado": "full", "despejado-calor": "full", "calor-fuerte": "full",
    "dorada-despejado": "warm", "nubes": "glow", "dorada-nubes": "warmglow", "niebla": "fog", "nieve": "glow",
}
# El texto de la cabecera tiene que llegar a 4,5 en el OPPO; se calcula con 4,7 por lo que no se modela (los haces y los
# reflejos) y por lo que se mueve (respira ±6 %).
HEAD_TARGET = 4.7


def with_fog(px):
    """[px] (dp) con los tres bancos de niebla quietos encima, como `drawFog` y `fogBank` de `LightLayer.kt` (al 55 %)."""
    h, w, _ = px.shape
    out = px.copy()
    for i in range(3):
        L = dict(c=[244, 244, 248], sh=[200, 200, 212], a=.75, scale=180 + i * 40, stretch=3.4, lo=.42, hi=.7,
                 y=[.12, .95], seed=301 + i, res=.4, warp=.5, light=1)
        bh = 200 + i * 30
        bank = cloud_layer(L, 2 * w * S, bh * S).resize((2 * w, bh), Image.BILINEAR)
        rgba = np.asarray(bank, dtype=np.float64)[:, :w]
        y = round(h * (.3 + i * .22) - bh / 2)
        y0, y1 = max(0, y), min(h, y + bh)
        if y1 <= y0:
            continue
        part = rgba[y0 - y:y1 - y]
        a = part[..., 3:4] / 255 * .55
        out[y0:y1] = part[..., :3] * a + out[y0:y1] * (1 - a)
    return out


def with_flare(px, kind):
    """[px] (dp, 0–255) con el resplandor encima en modo «screen», en (0,76·ancho, 0,005·alto), como `drawFlare`."""
    if kind is None:
        return px
    h, w, _ = px.shape
    warm = kind in ("warm", "warmglow")
    glow_only = kind in ("glow", "warmglow", "fog")
    radius = w * (1.1 if kind == "fog" else .95 if glow_only else .9)
    a = .55 if kind == "fog" else .5 if glow_only else 1.0
    c0 = np.array([255, 214, 160] if warm else [255, 248, 232], dtype=np.float64) / 255
    c1 = np.array([255, 170, 110] if warm else [255, 232, 196], dtype=np.float64) / 255
    stops = [(0, c0, a), (.07, c0, a * .95), (.13, c0, a * .7), (.28, c1, a * .42), (.55, c1, a * .14), (.8, c1, a * .04), (1, c1, 0)]
    ys, xs = np.mgrid[0:h, 0:w].astype(np.float64) + .5
    d = np.clip(np.hypot(xs - w * .76, ys - h * .005) / radius, 0, 1)
    col = np.zeros((h, w, 3))
    alpha = np.zeros((h, w))
    for (t0, k0, a0), (t1, k1, a1) in zip(stops, stops[1:]):
        m = (d >= t0) & (d <= t1)
        f = ((d - t0) / (t1 - t0))[m]
        col[m] = k0 + (k1 - k0) * f[:, None]
        alpha[m] = a0 + (a1 - a0) * f
    cb = px / 255
    return (cb + alpha[..., None] * col * (1 - cb)) * 255


def solve_glass(img, fid=None):
    seen = img.resize((FW, round(H / S)), Image.BOX).crop((0, 0, FW, SEEN_DP))
    px = np.asarray(seen, dtype=np.float64)
    fh = SEEN_DP
    cards = blocks(px, round(fh * .27), fh, 0, FW)

    def worst(tint, a, bl, q):
        rs = np.sort(ratio_white(np.array(tint) * a + bl * (1 - a)))
        return float(rs[int(math.floor(len(rs) * q))])

    # La cabecera, con el resplandor del sol y el velo parejo que pinta `HeaderVeil` (del todo hasta el 42 % del alto).
    lit = with_fog(px) if fid == "niebla" else px
    head = blocks(with_flare(lit, FLARES.get(fid)), round(fh * .06), round(fh * .40), round(FW * .08), FW * .92)
    hv = 0.0
    while hv < .7 and worst([10, 16, 40], hv, head, .1) < HEAD_TARGET:
        hv += .02
    hv = round(hv, 2)
    hr = worst([10, 16, 40], hv, head, .1)
    # El cristal va en degradado: el claro, del 30 % de blanco arriba al 13 % abajo; el velado, de a − 0,06 arriba a
    # a + 0,06 abajo. La maqueta medía con el medio; aquí, con lo peor de cada uno (el borde de arriba de una tarjeta
    # puede caer en cualquier sitio): con el medio, «calor fuerte» se quedaba en 4,4:1 en el OPPO.
    light = worst([255, 255, 255], .30, cards, .03)
    if light >= 4.6:
        return dict(dark=False, a=.21, r=light, hv=hv, hr=hr)
    a = .1
    while a <= .8 + 1e-9:
        r = worst([16, 22, 46], a - .06, cards, .03)
        if r >= 4.6:
            return dict(dark=True, a=round(a, 2), r=r, hv=hv, hr=hr)
        a += .02
    return dict(dark=True, a=.8, r=worst([16, 22, 46], .74, cards, .03), hv=hv, hr=hr)


def kt_float(v):
    return f"{v:.2f}f"


def write_kotlin(looks):
    lines = [
        "// Generado por tools/build_skies.py: no se edita a mano. Para cambiarlo, cambia la tabla del script y vuelve a",
        "// ejecutarlo.",
        "package com.trastealabs.tiempo.ui.scene",
        "",
        "import com.trastealabs.tiempo.domain.SkyPhoto",
        "",
        "/**",
        " * Cómo se lee el texto blanco sobre cada foto (009, H5), medido por el script como `solveGlass` de la maqueta.",
        " * [glassTint]: 0 = cristal claro de hoy; si no, alfa del velo de tinta rgb(16,22,46) de las tarjetas.",
        " * [headerVeil]: alfa del velo de lectura de la cabecera (tinta #0A1028), 0 si no hace falta.",
        " * [cardContrast] y [headerContrast]: el contraste del texto blanco que queda (≥ 4,5).",
        " */",
        "data class PhotoLook(",
        "    val glassTint: Float,",
        "    val headerVeil: Float,",
        "    val cardContrast: Float,",
        "    val headerContrast: Float,",
        ")",
        "",
        "val PHOTO_LOOKS: Map<SkyPhoto, PhotoLook> = mapOf(",
    ]
    for fid, G in looks:
        tint = G["a"] if G["dark"] else 0.0
        lines.append(f"    SkyPhoto.{ENUM[fid]} to PhotoLook({kt_float(tint)}, {kt_float(G['hv'])}, "
                     f"{kt_float(math.floor(G['r'] * 100) / 100)}, {kt_float(math.floor(G['hr'] * 100) / 100)}),")
    lines.append(")")
    lines.append("")
    data = "\n".join(lines).encode("utf-8")
    with open(KT_FILE, "wb") as f:
        f.write(data)


def save_webp(img, path):
    buf = io.BytesIO()
    img.save(buf, "WEBP", quality=WEBP_QUALITY, method=6)
    with open(path, "wb") as f:
        f.write(buf.getvalue())


def contact_sheet(photos, capa, looks, path):
    """15 miniaturas como la hoja de fondos de la maqueta: foto, dos tarjetas con su cristal y el contraste."""
    tw, th, pad, cols = 180, 400, 16, 5
    rows = math.ceil((len(photos) + 1) / cols)
    sheet = Image.new("RGB", (cols * (tw + pad) + pad, rows * (th + 56 + pad) + pad), (246, 244, 240))
    draw = ImageDraw.Draw(sheet)
    for i, (fid, img) in enumerate(photos):
        x = pad + (i % cols) * (tw + pad)
        y = pad + (i // cols) * (th + 56 + pad)
        thumb = img.resize((tw, th), Image.LANCZOS).convert("RGBA")
        G = looks[fid]
        tint = (16, 22, 46) if G["dark"] else (255, 255, 255)
        a = G["a"]
        over = Image.new("RGBA", thumb.size, (0, 0, 0, 0))
        od = ImageDraw.Draw(over)
        for top, height in ((.38, .13), (.54, .24)):
            box = (round(tw * .08), round(th * top), round(tw * .92), round(th * (top + height)))
            od.rounded_rectangle(box, radius=10, fill=tint + (round(255 * a),), outline=(255, 255, 255, 77))
        thumb = Image.alpha_composite(thumb, over).convert("RGB")
        sheet.paste(thumb, (x, y))
        glass = f"velado {round(a * 100)} %" if G["dark"] else "claro"
        draw.text((x, y + th + 4), fid, fill=(20, 20, 20))
        draw.text((x, y + th + 20), f"cristal {glass} · {G['r']:.1f}:1", fill=(60, 60, 60))
        draw.text((x, y + th + 36), f"velo {round(G['hv'] * 100)} % · {G['hr']:.1f}:1", fill=(60, 60, 60))
    i = len(photos)
    x = pad + (i % cols) * (tw + pad)
    y = pad + (i // cols) * (th + 56 + pad)
    bg = Image.new("RGBA", (tw, th), (29, 36, 51, 255))
    strip = capa.resize((tw * 2, round(capa.height * tw * 2 / capa.width)), Image.LANCZOS)
    bg.alpha_composite(strip.crop((0, 0, tw, min(th, strip.height))), (0, round(th * .1)))
    sheet.paste(bg.convert("RGB"), (x, y))
    draw.text((x, y + th + 4), "capa de nubes", fill=(20, 20, 20))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    sheet.save(path)


def main():
    args = sys.argv[1:]
    sheet_path = args[args.index("--hoja") + 1] if "--hoja" in args else SHEET_DEFAULT
    os.makedirs(RES_DIR, exist_ok=True)
    photos, looks = [], {}
    for fid in FONDOS:
        img = paint(fid)
        G = solve_glass(img, fid)
        looks[fid] = G
        photos.append((fid, img))
        save_webp(img, os.path.join(RES_DIR, "sky_" + fid.replace("-", "_") + ".webp"))
        glass = f"velado {round(G['a'] * 100)} %" if G["dark"] else "claro"
        print(f"{fid:20s} cristal {glass:12s} {G['r']:.2f}:1 · velo {round(G['hv'] * 100):2d} % {G['hr']:.2f}:1")
    capa = cloud_layer(CAPA, CAPA_W, CAPA_H, periodic=True)
    capa.save(os.path.join(RES_DIR, "sky_cloud_layer.png"), optimize=True)
    write_kotlin([(fid, looks[fid]) for fid in FONDOS])
    contact_sheet(photos, capa, looks, sheet_path)
    print(f"capa {capa.width}×{capa.height} · hoja: {sheet_path}")


if __name__ == "__main__":
    main()
