"""Compara dos capturas del móvil (antes/después, 008 H8) sin la barra de estado.

Uso: python tools/comparar_capturas.py antes.png despues.png [--diff salida.png]

Quita el 4 % de arriba (hora, batería, notificaciones), y cuenta los píxeles en los que algún canal cambia más de 8
niveles. Imprime ese % («iguales» si no pasa del 0,5 %). Con --diff guarda una imagen con lo distinto en rojo.
"""
import sys

from PIL import Image, ImageChops

TOP = 0.04
THRESHOLD = 8
LIMIT = 0.5


def compare(a_path, b_path, diff_path=None):
    a = Image.open(a_path).convert("RGB")
    b = Image.open(b_path).convert("RGB")
    if a.size != b.size:
        b = b.resize(a.size)
    w, h = a.size
    box = (0, int(h * TOP), w, h)
    a, b = a.crop(box), b.crop(box)
    delta = ImageChops.difference(a, b)
    # Un píxel cuenta si cualquiera de los tres canales pasa del umbral.
    r, g, bl = delta.split()
    mask = ImageChops.lighter(ImageChops.lighter(r, g), bl).point(lambda v: 255 if v > THRESHOLD else 0)
    changed = sum(1 for v in mask.getdata() if v)
    pct = 100 * changed / (mask.size[0] * mask.size[1])
    if diff_path:
        red = Image.new("RGB", a.size, (255, 0, 0))
        Image.composite(red, a.convert("L").convert("RGB"), mask).save(diff_path)
    return pct


if __name__ == "__main__":
    args = sys.argv[1:]
    diff = None
    if "--diff" in args:
        i = args.index("--diff")
        diff = args[i + 1]
        del args[i:i + 2]
    if len(args) != 2:
        sys.exit(__doc__)
    pct = compare(args[0], args[1], diff)
    print(f"{pct:.2f} % distinto ({'iguales' if pct <= LIMIT else 'mirar'})")
