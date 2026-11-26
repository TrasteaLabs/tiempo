#!/usr/bin/env python3
"""Cuenta el registro de refrescos de la app (015, H8): lee `refresh_log.jsonl` por la entrada estándar.

    adb exec-out run-as com.trastealabs.tiempo.dev cat files/refresh_log.jsonl | python tools/registro.py

(con `MSYS_NO_PATHCONV=1` delante en Git Bash si hace falta). Solo la versión debug deja `run-as`.
De cada notificación de la mañana (018) dice cuándo salió y a qué hora estaba pedida.
De las últimas 24 h dice: cuántos refrescos, cuántos «AEMET sin cambios» y cuántos KB bajó AEMET en total.
«Sin cambios» incluye una consulta que no bajó nada (0 bytes) aunque la línea diga NEW: así cuenta igual que la pantalla.
`--horas N` cambia la ventana y `--ahora <epoch>` fija la hora (para probarlo).
"""
import argparse
import json
import sys
import time


def leer(texto):
    """Las líneas legibles; una rota o con campos que faltan se salta, como hace la app."""
    lineas = []
    for linea in texto.splitlines():
        linea = linea.strip().lstrip("﻿")
        if not linea:
            continue
        try:
            d = json.loads(linea)
            lineas.append({
                "at": int(d["at"]),
                "origin": str(d["origin"]),
                "outcome": str(d["outcome"]),
                "aemet": str(d["aemet"]),
                "bytes": int(d.get("aemetBytes", 0)),
                "morning": d.get("morning") if isinstance(d.get("morning"), dict) else None,
            })
        except (ValueError, KeyError, TypeError):
            continue
    return sorted(lineas, key=lambda x: x["at"])


def sin_cambios(x):
    """AEMET sin cambios: UNCHANGED, o NEW que no bajó nada."""
    return x["aemet"] == "UNCHANGED" or (x["aemet"] == "NEW" and x["bytes"] == 0)


def peor_hueco(lineas, ahora, ventana):
    """La mayor edad que llegó a tener el dato en la ventana (mismo cálculo que la app), en segundos; None si no hay buenos."""
    buenos = [x["at"] for x in lineas if x["outcome"] == "OK" and x["at"] <= ahora]
    if not buenos:
        return None
    desde = ahora - ventana
    huecos = [b - a for a, b in zip(buenos, buenos[1:]) if b >= desde]
    return max(huecos + [ahora - buenos[-1]])


def avisos(lineas):
    """Las líneas de aviso de la mañana (018): una por notificación enviada, no son refrescos."""
    return [x for x in lineas if x.get("morning")]


def hora(epoch):
    return time.strftime("%d/%m %H:%M", time.localtime(epoch))


def resumen(lineas, ahora, horas=24):
    ventana = horas * 3600
    # Una línea de aviso de la mañana no es un refresco: no cuenta en nada de lo de abajo.
    lineas = [x for x in lineas if not x.get("morning")]
    dentro = [x for x in lineas if x["at"] >= ahora - ventana]
    consultas = [x for x in dentro if x["aemet"] != "NOT_APPLICABLE"]
    nuevos = [x for x in consultas if x["aemet"] == "NEW" and x["bytes"] > 0]
    fallos = [x for x in consultas if x["aemet"] == "FAILED"]
    return {
        "refrescos": len(dentro),
        "buenos": sum(1 for x in dentro if x["outcome"] == "OK"),
        "consultas": len(consultas),
        "sin_cambios": sum(1 for x in consultas if sin_cambios(x)),
        "nuevos": len(nuevos),
        "fallos": len(fallos),
        "kb": sum(x["bytes"] for x in dentro) / 1024,
        "hueco": peor_hueco(lineas, ahora, ventana),
    }


def duracion(segundos):
    minutos = int(segundos // 60)
    h, m = divmod(minutos, 60)
    if h == 0:
        return f"{m} min"
    return f"{h} h" if m == 0 else f"{h} h {m:02d} min"


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--horas", type=int, default=24)
    ap.add_argument("--ahora", type=int, default=None, help="hora fija en epoch (segundos)")
    args = ap.parse_args(argv)
    for flujo in (sys.stdin, sys.stdout):
        if hasattr(flujo, "reconfigure"):
            flujo.reconfigure(encoding="utf-8", errors="replace")
    todas = leer(sys.stdin.read())
    lineas = [x for x in todas if not x.get("morning")]
    ahora = args.ahora if args.ahora is not None else int(time.time())
    if not todas:
        print("El registro está vacío: la app aún no ha refrescado desde que lleva el registro.")
        return 1
    for x in avisos(todas):
        m = x["morning"]
        print(
            f"Parte de la mañana: salió a las {hora(x['at'])}, pedido a las {hora(int(m.get('askedAt', 0)))}, "
            f"{'con IA' if m.get('withAi') else 'con reglas'}"
        )
    if not lineas:
        print("Sin refrescos en el registro.")
        return 0
    r = resumen(lineas, ahora, args.horas)
    print(f"Últimas {args.horas} h: {r['refrescos']} refrescos ({r['buenos']} bien)")
    print(
        f"AEMET: {r['sin_cambios']} sin cambios de {r['consultas']} consultas, "
        f"{r['nuevos']} con novedades, {r['fallos']} fallos"
    )
    print(f"AEMET bajó {r['kb']:.0f} KB en total")
    if r["hueco"] is not None:
        print(f"Peor hueco: el dato llegó a tener {duracion(r['hueco'])}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
