"""Contraste del texto blanco sobre una captura del móvil (009, H5).

Uso: python tools/contraste.py <captura.png> [<captura2.png> ...] --zona tarjetas|cabecera
     python tools/contraste.py <captura.png> [...] --rect x,y,w,h [--alpha 0.9]   (010, P2)

Pensado para capturas de la demo con `--ez bare true` (sin textos, con la foto y el cristal): pasa la captura a dp
(420 de ancho), la parte en bloques de 20 × 20 dp y calcula, en cada uno, el contraste del blanco sobre su color medio
(WCAG). Da el de los bloques peores como `solveGlass` de la maqueta:
  - tarjetas: el cristal de la zona de las tarjetas (del 33 al 95 % del alto, entre los márgenes), el 3 % peor;
  - cabecera: la zona de texto de arriba (del 6 al 40 % del alto, del 8 al 92 % del ancho), el 10 % peor.
  - `--rect x,y,w,h`: una zona libre de la captura, en dp de una pantalla de 420 dp de ancho (x, y desde arriba a la
    izquierda), en bloques de 4 × 4 dp y el 1 % peor. El texto es blanco con opacidad `--alpha` (por defecto 0,9: la de
    los textos de las postales va de 0,75 a 0,92). Sirve para medir una postal de Sitios con `--ez bare true`.
Escribe una línea por captura y sale con código 1 si alguna no llega a 4,5:1.
"""
import math
import sys

import numpy as np
from PIL import Image

DP_WIDTH = 420
BLOCK = 20
LIMIT = 4.5
ZONES = {
    # (y0, y1, x0, x1, cuantil): fracciones de la pantalla.
    "tarjetas": (.33, .95, .06, .94, .03),
    "cabecera": (.06, .40, .08, .92, .10),
}


def lum(c):
    v = c / 255.0
    v = np.where(v <= .03928, v / 12.92, ((v + .055) / 1.055) ** 2.4)
    return .2126 * v[..., 0] + .7152 * v[..., 1] + .0722 * v[..., 2]


def contrast(path, zone):
    img = Image.open(path).convert("RGB")
    w, h = img.size
    dp_h = round(h * DP_WIDTH / w)
    px = np.asarray(img.resize((DP_WIDTH, dp_h), Image.BOX), dtype=np.float64)
    y0, y1, x0, x1, q = ZONES[zone]
    blocks = []
    by = round(dp_h * y0)
    while by + BLOCK <= dp_h * y1:
        bx = round(DP_WIDTH * x0)
        while bx + BLOCK <= DP_WIDTH * x1:
            blocks.append(px[by:by + BLOCK, bx:bx + BLOCK].reshape(-1, 3).mean(axis=0))
            bx += BLOCK
        by += BLOCK
    ratios = np.sort(1.05 / (lum(np.array(blocks)) + .05))
    return float(ratios[int(math.floor(len(ratios) * q))]), float(ratios[0]), len(ratios)


def rect_contrast(path, rect, alpha):
    """El 1 % peor de los bloques de 4 × 4 dp de [rect] (x, y, w, h en dp), con texto blanco al [alpha]."""
    img = Image.open(path).convert("RGB")
    w, h = img.size
    dp_h = round(h * DP_WIDTH / w)
    px = np.asarray(img.resize((DP_WIDTH, dp_h), Image.BOX), dtype=np.float64)
    x, y, rw, rh = (int(round(v)) for v in rect)
    step = 4
    blocks = []
    for by in range(y, y + rh - step + 1, step):
        for bx in range(x, x + rw - step + 1, step):
            if by < 0 or bx < 0 or by + step > dp_h or bx + step > DP_WIDTH:
                continue
            blocks.append(px[by:by + step, bx:bx + step].reshape(-1, 3).mean(axis=0))
    if not blocks:
        raise SystemExit(f"{path}: la zona {rect} cae fuera de la captura ({DP_WIDTH} × {dp_h} dp)")
    base = np.array(blocks)
    lb = lum(base)
    lt = lum(255 * alpha + base * (1 - alpha))
    ratios = np.sort((np.maximum(lb, lt) + .05) / (np.minimum(lb, lt) + .05))
    return float(ratios[int(math.floor(len(ratios) * .01))]), float(ratios[0]), len(ratios)


def main():
    args = sys.argv[1:]
    if "--rect" in args:
        i = args.index("--rect")
        rect = [float(v) for v in args[i + 1].split(",")]
        rest = args[:i] + args[i + 2:]
        alpha = 0.9
        if "--alpha" in rest:
            j = rest.index("--alpha")
            alpha = float(rest[j + 1])
            rest = rest[:j] + rest[j + 2:]
        if len(rect) != 4 or not rest:
            print(__doc__)
            sys.exit(2)
        bad = 0
        for path in rest:
            r, worst, n = rect_contrast(path, rect, alpha)
            ok = r >= LIMIT
            bad += not ok
            print(f"{path}: rect {','.join(f'{v:g}' for v in rect)} alfa {alpha:g} {r:.2f}:1 (bloque peor {worst:.2f}:1, {n} bloques) {'bien' if ok else 'NO LLEGA'}")
        sys.exit(1 if bad else 0)
    if "--zona" not in args:
        print(__doc__)
        sys.exit(2)
    i = args.index("--zona")
    zone = args[i + 1]
    paths = args[:i] + args[i + 2:]
    if zone not in ZONES or not paths:
        print(__doc__)
        sys.exit(2)
    bad = 0
    for path in paths:
        r, worst, n = contrast(path, zone)
        ok = r >= LIMIT
        bad += not ok
        print(f"{path}: {zone} {r:.2f}:1 (bloque peor {worst:.2f}:1, {n} bloques) {'bien' if ok else 'NO LLEGA'}")
    sys.exit(1 if bad else 0)


if __name__ == "__main__":
    main()
