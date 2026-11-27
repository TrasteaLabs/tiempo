# 023 · Entrega (parte de construcción)

2026-11-27 · en `main` · vuelta atrás: etiqueta `antes-023`

## Qué hace ahora
La app no cambia. Queda preparado el registro de Tiempo en Google: los pasos para quien mantiene (`pasos.md`), cómo repetir la prueba del aviso de Play Protect (`linea-base.md`) y la apelación lista por si el aviso sigue (`apelacion.md`). La plantilla de publicación tiene la sección «Registro en Google» para las demás apps.

## Cómo verlo
1. Abrir `docs/encargos/023-registro-google/pasos.md` y seguirlo con DNI, justificante de domicilio y tarjeta a mano.

Capturas: ninguna (no hay pantallas nuevas; las de la consola llevarían datos personales).

## Comprobado
- `check`: `BUILD SUCCESSFUL in 4m 21s` (48 actionable tasks) en la rama; el revisor lo repitió: `BUILD SUCCESSFUL in 3m 25s`.
- Páginas de Google releídas el 27-11-2026 (Play Console, datos de la cuenta, «trader», verificación en Play Console y su guía en PDF): la ruta «Verificación de desarrolladores de Android › Nombres de paquete › Registrar nombre de paquete», «Añadir clave» y «Firmar y subir un APK» salen de ahí.
- Huella con dos puntos de `pasos.md` y `apelacion.md` = `firma.sha256` (comprobada con script por el revisor). Permisos de la apelación = `AndroidManifest.xml`.
- **Sin comprobar:** el aviso de Play Protect en el emulador (no se pudo, ver decisiones); que el registro quite el aviso; plazos de Google; que acepte «Trastea Labs» como nombre en una cuenta personal.
- Revisión: LISTO, sin bloqueantes. Arreglados: la tabla decía que el teléfono nunca es público (lo es si se declara «trader»); «Trastea Labs» como nombre visible marcado sin comprobar; datos de «trader» apuntados con su fuente en `investigacion.md`; quién repite la prueba (el OPPO, no Claude en el emulador); nombre de pila quitado de `linea-base.md`; falta del interruptor de betas en la prueba.

## Pendiente
- Del usuario: seguir `pasos.md` (cuenta, perfil de pagos individual, «trader», identidad, 25 USD, registrar `com.trastealabs.tiempo`). Si pide prueba de propiedad, pegar el texto en el chat. Avisar cuando esté «Registrado».
- Después, como retoques: `adi-registration.properties` si hace falta → beta → él la sube; repetir la prueba en el OPPO (`linea-base.md`); según lo visto, quitar el paso de «Instalar de todas formas» del README o enviar la apelación; completar esta entrega con lo visto y fecha, y actualizar `ESTADO.md` y la trampa de Play Protect de `CLAUDE.local.md`.
- En el móvil real: toda la comparación antes/después.
- Para más adelante: nada nuevo.

## Decisiones tomadas por el camino
- **Bloque 1 sin emulador:** los cuatro AVD usan `google_apis` sin Play Store, así que no hay Play Protect. Poner uno con Play Store pedía bajar ~1,5 GB y entrar con una cuenta de Google (cosa de quien mantiene). Como prevé el plan, la línea base es la del OPPO del 27-11 (022) y la comparación se hará solo allí.
- Los tres bloques quedaron en un mismo commit: el `check` del bloque 1 corría en segundo plano con `git add -A` mientras se escribían los otros dos. Solo es documentación.
- «Probar la app»: no aplica, la app no cambia.

## Quién hizo qué
| Bloque | Quién | Modelo |
|---|---|---|
| 1 Línea base | construcción | Opus |
| 2 Pasos de quien mantiene | construcción | Opus |
| 3 Apelación y plantilla | rutinario | Sonnet |
| Revisión | revisor | Opus |

Reparto por modelo (de `consumo.py`, 2 días): Opus 80 %, sub-Opus 17 %, sub-Sonnet 4 % · Uso semanal: 52 % → 53 %

## Para la casa
- El `check` en segundo plano con `git add -A` en la misma orden se lleva lo que se escriba mientras corre: añadir solo los ficheros del bloque.
- Para encargos que dependen de Play Protect o Play Store, los emuladores de este PC no sirven (imagen sin Play Store).
