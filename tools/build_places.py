"""Genera app/src/main/assets/places.tsv.gz desde GeoNames (CC BY 4.0).

Texto UTF-8 en gzip, en dos bloques (006):

    R<TAB>cc<TAB>region                              una línea por región; su índice es el orden (0, 1, …)
    name<TAB>regionIdx<TAB>population<TAB>lat<TAB>lon[<TAB>nameEs]

- region: en España, la provincia en español y sin prefijo (tabla PROVINCES, por código de GeoNames); fuera, la región
  de primer nivel de admin1CodesASCII.txt tal cual. Vacía si GeoNames no la da.
- population: habitantes según GeoNames (0 si no lo sabe).
- lat/lon con 2 decimales.
- nameEs: solo fuera de España, el nombre en español de alternateNamesV2 (ni coloquial ni histórico, el preferido si
  hay varios) cuando es distinto del principal: «London» → «Londres».

Fuentes: cities500 (todo el mundo, > 500 habitantes) + ES completo (lugares poblados), sin barrios (PPLX).
Los lugares van en el mismo orden que antes del 006 (por nombre, país, lat y lon): a igual distancia gana el mismo y el
nombre de «donde estoy» no cambia.

Solo hace falta ejecutarlo si se cambia la lista de lugares; el resultado se versiona. Las descargas (alternateNamesV2
pesa ~205 MB) se guardan en la carpeta temporal del sistema, en `geonames/`, y se reutilizan.
"""
import gzip
import io
import tempfile
import urllib.request
import zipfile
from pathlib import Path

BASE = "https://download.geonames.org/export/dump/"
OUT = Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "assets" / "places.tsv.gz"
CACHE = Path(tempfile.gettempdir()) / "geonames"

# Las 52 provincias por código de GeoNames (admin1.admin2), con su nombre en español y sin «Provincia de».
PROVINCES = {
    "ES.59.VI": "Álava", "ES.54.AB": "Albacete", "ES.60.A": "Alicante", "ES.51.AL": "Almería",
    "ES.34.O": "Asturias", "ES.55.AV": "Ávila", "ES.57.BA": "Badajoz", "ES.07.PM": "Baleares",
    "ES.56.B": "Barcelona", "ES.55.BU": "Burgos", "ES.57.CC": "Cáceres", "ES.51.CA": "Cádiz",
    "ES.39.S": "Cantabria", "ES.60.CS": "Castellón", "ES.54.CR": "Ciudad Real", "ES.51.CO": "Córdoba",
    "ES.58.C": "A Coruña", "ES.54.CU": "Cuenca", "ES.56.GI": "Girona", "ES.51.GR": "Granada",
    "ES.54.GU": "Guadalajara", "ES.59.SS": "Guipúzcoa", "ES.51.H": "Huelva", "ES.52.HU": "Huesca",
    "ES.51.J": "Jaén", "ES.27.LO": "La Rioja", "ES.53.GC": "Las Palmas", "ES.55.LE": "León",
    "ES.56.L": "Lleida", "ES.58.LU": "Lugo", "ES.29.M": "Madrid", "ES.51.MA": "Málaga",
    "ES.31.MU": "Murcia", "ES.32.NA": "Navarra", "ES.58.OR": "Ourense", "ES.55.P": "Palencia",
    "ES.58.PO": "Pontevedra", "ES.55.SA": "Salamanca", "ES.53.TF": "Santa Cruz de Tenerife", "ES.55.SG": "Segovia",
    "ES.51.SE": "Sevilla", "ES.55.SO": "Soria", "ES.56.T": "Tarragona", "ES.52.TE": "Teruel",
    "ES.54.TO": "Toledo", "ES.60.V": "Valencia", "ES.55.VA": "Valladolid", "ES.59.BI": "Vizcaya",
    "ES.55.ZA": "Zamora", "ES.52.Z": "Zaragoza", "ES.CE.CE": "Ceuta", "ES.ML.ME": "Melilla",
}
assert len(PROVINCES) == 52


def download(name):
    CACHE.mkdir(parents=True, exist_ok=True)
    path = CACHE / name
    if not path.exists():
        tmp = path.with_suffix(path.suffix + ".part")
        with urllib.request.urlopen(BASE + name, timeout=600) as r, open(tmp, "wb") as f:
            while chunk := r.read(1 << 20):
                f.write(chunk)
        tmp.replace(path)
    return path


def zipped_rows(zip_name, inner_name):
    with zipfile.ZipFile(download(zip_name)) as z:
        with z.open(inner_name) as f:
            for raw in io.TextIOWrapper(f, encoding="utf-8"):
                yield raw.rstrip("\n").split("\t")


def admin1_names():
    names = {}
    with open(download("admin1CodesASCII.txt"), encoding="utf-8") as f:
        for raw in f:
            c = raw.rstrip("\n").split("\t")
            names[c[0]] = c[1]
    return names


def region_of(cc, admin1, admin2, admin1s):
    if cc == "ES":
        if not admin1 or not admin2:
            return ""
        code = f"ES.{admin1}.{admin2}"
        if code not in PROVINCES:
            raise SystemExit(f"Provincia sin nombre en la tabla: {code}")
        return PROVINCES[code]
    return admin1s.get(f"{cc}.{admin1}", "") if admin1 else ""


def spanish_names(ids):
    """geonameid → nombre en español (ni coloquial ni histórico; el preferido si hay varios)."""
    best = {}
    for c in zipped_rows("alternateNamesV2.zip", "alternateNamesV2.txt"):
        if len(c) < 8 or c[2] != "es":
            continue
        gid = c[1]
        if gid not in ids or c[6] == "1" or c[7] == "1" or not c[3]:
            continue
        preferred = c[4] == "1"
        if gid not in best or (preferred and not best[gid][1]):
            best[gid] = (c[3], preferred)
    return {gid: name for gid, (name, _) in best.items()}


def main():
    admin1s = admin1_names()
    seen = set()
    places = []  # (orden de antes, geonameid, name, cc, region, population, lat, lon)
    for zip_name, inner, only_populated in (("cities500.zip", "cities500.txt", False), ("ES.zip", "ES.txt", True)):
        for c in zipped_rows(zip_name, inner):
            if only_populated and c[6] != "P":
                continue
            if c[7] == "PPLX":  # barrios y distritos: el sitio que se enseña es el pueblo o la ciudad
                continue
            gid = c[0]
            if gid in seen:
                continue
            seen.add(gid)
            name, cc = c[1], c[8]
            if not name or not cc:
                continue
            lat, lon = f"{float(c[4]):.2f}", f"{float(c[5]):.2f}"
            population = int(c[14]) if c[14] else 0
            region = region_of(cc, c[10], c[11], admin1s)
            places.append((f"{name}\t{cc}\t{lat}\t{lon}", gid, name, cc, region, population, lat, lon))
    places.sort(key=lambda p: p[0])

    abroad = {p[1] for p in places if p[3] != "ES"}
    es_names = spanish_names(abroad)

    regions = {}
    lines = []
    with_es = 0
    for _, gid, name, cc, region, population, lat, lon in places:
        idx = regions.setdefault((cc, region), len(regions))
        line = f"{name}\t{idx}\t{population}\t{lat}\t{lon}"
        name_es = es_names.get(gid)
        if name_es and name_es != name:
            line += f"\t{name_es}"
            with_es += 1
        lines.append(line)
    header = [f"R\t{cc}\t{region}" for (cc, region) in regions]
    OUT.parent.mkdir(parents=True, exist_ok=True)
    with gzip.open(OUT, "wt", encoding="utf-8", compresslevel=9, newline="\n") as f:
        f.write("\n".join(header + lines) + "\n")
    no_region = sum(1 for p in places if p[3] == "ES" and not p[4])
    print(f"{len(lines)} lugares, {len(regions)} regiones, {with_es} con nombre en español, "
          f"{no_region} de España sin provincia, {OUT.stat().st_size / 1e6:.2f} MB")


if __name__ == "__main__":
    main()
