# 021 · Entrega

2026-11-26 · en `main` (a1269f8) · vuelta atrás: etiqueta `antes-021`

## Qué hace ahora
La app se llama `com.trastealabs.tiempo` (la de pruebas, `com.trastealabs.tiempo.dev`), lleva versión real («Versión 1.0.0 (N)», N = commits) y su APK sale como `tiempo-1.0.0-N.apk`. Sabe buscar versiones nuevas en las Releases de GitHub (al abrirla, como mucho una vez al día, o con «Buscar ahora»), avisa con una línea discreta en la principal y, cuando se pulsa, baja el APK, comprueba SHA-256, paquete y firma, e instala. Acerca de enseña las atribuciones con enlaces, «Código abierto», «Privacidad», «Comentarios» y las licencias de terceros; Ajustes › IA se explica para quien no sabe qué es. El repo trae README, LICENSE (GPL-3.0), PRIVACY, SECURITY, CONTRIBUTING y plantillas de issues, y ya no lleva nada personal (eso vive en `CLAUDE.local.md`, fuera de git).

## Cómo verlo
1. `./gradlew installDebug` y la demo: `adb shell am start -S -n com.trastealabs.tiempo.dev/com.trastealabs.tiempo.demo.DemoActivity --es update available --es screen update` (cada estado: `current|never|offline|available|beta|downloading|verifying|ready|permission|installing|error|invalid|install-failed|dev`). IA sin clave: `--es screen settings --es aikey none`.
2. El README tal como se verá en GitHub: `README.md`, con las capturas de `docs/capturas/`.

Capturas: `captura-aviso.png`, `captura-bajando.png`, `captura-acerca-de.png`, `captura-ia-sin-clave.png` (en esta carpeta).

## Comprobado
- `check`: verde en la rama y en `main` (mismo árbol): 1509 tests de la app sin fallos + los de `buildSrc`.
- En un emulador propio (Android 16, borrado al acabar): principal, widget 4×2 en el escritorio, los 14 estados del actualizador, aviso en la principal, IA sin clave, Acerca de y licencias. `assembleRelease` → `tiempo-1.0.0-478.apk` firmado; instalado en el emulador arranca y la comprobación (404, el repo aún no existe) queda en silencio y apuntada como `NOT_FOUND`. Auditoría (Impeccable, nativa): nada grave; añadida semántica de progreso y aviso hablado de los fallos.
- Revisión: sin fallos de código bloqueantes. Arreglados: «Instalar» podía instalar el fichero de la versión anterior si aparecía otra nueva (o al apagar las betas); un fallo de instalación de Android volvía a «Instalar» sin decir nada; `per_page` 10 → 30; la release sin firmar ahora avisa (y falla con `RELEASE_SIGNING_REQUIRED=true`); un `versionCode` pedido no válido falla; PRIVACY con los permisos que añade WorkManager; el plan del 022 sin el nombre de la cuenta privada.

## Pendiente
- Del usuario, para poder lanzar el 022: (1) copia de `~/.weatherapp/` fuera del PC; (2) crear la organización `trastealabs` en GitHub desde su cuenta actual; (3) ejecutar `tools/subir-secretos.ps1` (en PowerShell: `powershell -ExecutionPolicy Bypass -File tools/subir-secretos.ps1`).
- Solo en tests (queda para el 022): el actualizador contra GitHub de verdad y la instalación en ColorOS. En el OPPO no se ha tocado nada: siguen las apps viejas (`es.alvaro.weather` y `.dev`).
- Para el 022: el `checkout` de GitHub Actions tiene que traer el historial entero (`fetch-depth: 0`) o pasar `VERSION_CODE`, si no el `versionCode` sale 1; poner `RELEASE_SIGNING_REQUIRED=true` en el flujo. Los commits del historial privado llevan el correo personal: el repo público empieza de cero, no copiar el historial. `.claude/settings.json` lleva el id del plugin `becario@alvaro` (nombre del marketplace): se queda, es lo que lo carga.
- Para Play Store (si algún día): `REQUEST_INSTALL_PACKAGES`, `USE_EXACT_ALARM` y `ACCESS_BACKGROUND_LOCATION` necesitan justificación y el actualizador tendría que quedar fuera. `play-services-location` es de licencia propietaria (Android SDK License): compatible con distribuir el APK, pero un purista de la GPL lo señalará.
- Para más adelante: la demo enseña «Sol 16:41 – 01:41» si el emulador está en UTC (horas de la demo con la zona del aparato; ya estaba antes).

## Decisiones tomadas por el camino
- Lógica de versión y firma en `buildSrc/` con sus tests; el `check` pasa a `./gradlew :buildSrc:test lintDebug testDebugUnitTest` (`includedBuild` no sirve con `buildSrc`).
- Con varias releases elegibles se ofrece la de mayor `versionCode`, no la primera de la lista. La versión encontrada se guarda para ofrecerla al reabrir sin volver a preguntar. «Bajar e instalar» abre el instalador en cuanto el fichero cuadra.
- `LICENSE` es el texto oficial sin tocar (para que GitHub lo reconozca); el «© 2026 Álvaro García (Trastea Labs)» y la exclusión del nombre y el icono van en el README.
- `docs/specs/`, los encargos 019 y 020 y el servidor `maquetas` se quitan de `main` (siguen en el historial privado). Los comentarios que los citaban dicen ahora el número de encargo.
- `CLAUDE.local.md` (cargado por Claude Code junto a `CLAUDE.md`, comprobado en su documentación) guarda el montaje personal; no existe en los worktrees.
- Icono propio `ic_wx_code` para «Código abierto»: el «</>» de texto partía en dos líneas. Demo nueva `--es aikey none`.
- El emulador se borra entero al acabar (desinstalar está bloqueado en los permisos), en vez de desinstalar la release.

## Quién hizo qué
| Bloque | Quién | Modelo |
|---|---|---|
| 1 Identificador y paquete | construcción (era rutinario: un `git mv` y un reemplazo, más rápido aquí) | Opus |
| 2 Versión y firma | construcción | Opus |
| 3 Actualizador: lógica | construcción | Opus |
| 4 Actualizador: pantallas | construcción | Opus |
| 5 Atribuciones, Acerca de, IA | construcción | Opus |
| 6 Fuera lo personal | rutinario (comentarios, `tools/`, `DESIGN.md`) + construcción (`CLAUDE.md`, `CLAUDE.local.md`, borrado) | Opus |
| 7 Documentos públicos | rutinario (README, PRIVACY, SECURITY, CONTRIBUTING, issues) + construcción (LICENSE, capturas) | Opus |
| Revisión | revisor | Opus |

Reparto por modelo (de `consumo.py`): Opus 83 %, subagentes en Opus 17 % · Uso semanal: 41 % → 45 %

## Para la casa
- El rutinario corrió en Opus (sale como «sub-opus»): si debía ir en Sonnet, su definición no lo fija.
- La sesión gastó muchas capturas en esperar al arranque en frío del emulador (la primera pantalla tarda más de 7 s): esperar 15 s de entrada.
