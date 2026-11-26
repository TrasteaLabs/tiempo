# 022 · Entrega

2026-11-26 · en `main` de `TrasteaLabs/tiempo` · vuelta atrás: el repo privado de antes, en `../WeatherApp-privado.git` (y archivado en GitHub)

## Qué hace ahora
Tiempo es pública: https://github.com/TrasteaLabs/tiempo, con historial limpio (empieza en «Tiempo 1.0.0: primera versión pública», autor Trastea Labs). Cada cambio de la app que llega a `main` sale solo como beta firmada en Releases; la estable promociona el mismo fichero de una beta cuando Álvaro dice «publica la estable» (`sh tools/publicar.sh estable`). Hay tres betas (1.0.0 beta 4 y 6, 1.0.1 beta 10) y la estable 1.0.0. En Acerca de, el correo `hola@trastealabs.com` (tocar abre el correo, mantener lo copia). Queda escrita la plantilla para las demás apps: `docs/plantilla-publicacion.md`.

## Cómo verlo
1. Releases: https://github.com/TrasteaLabs/tiempo/releases (sin sesión: se baja con `curl` y el SHA-256 cuadra).
2. En un Android: bajar el `.apk` de la última estable, instalar, Ajustes › Versión de la app; con «Recibir versiones beta» encendido ofrece la 1.0.1.

Capturas: `captura-beta-nueva.png` (beta ofrecida con el interruptor encendido), `captura-estable.png` (estable ofrecida con él apagado), `captura-acerca-de-correo.png`.

## Comprobado
- `check`: `BUILD SUCCESSFUL` en local (1514 tests) y verde en GitHub en todas las ejecuciones desde el arreglo de la zona (rama, `main` y el trabajo `check` de cada beta: más de tres seguidas).
- Firma: `beta.yml` comprueba con `apksigner` la huella `9c91d397…a0e1` (`firma.sha256`) antes de publicar. La estable 1.0.0 y la beta 6 tienen el mismo SHA-256 (`6bd52df9…7c00`), comprobado en el flujo y bajándola sin sesión.
- En un emulador limpio (Android 16), con las APK de Releases: beta 4 instalada; con el interruptor apagado, «Está al día» aunque había beta 6; encendido, ofrece la 6, pide el permiso de instalar, instala encima y Toledo sigue guardado. Emulador borrado, beta 4 y interruptor apagado: ofrece la estable 1.0.0 e instala. Desde la 1.0.0, encendido: ofrece e instala la 1.0.1 beta 10.
- `tools/publicar.sh`: publicó la 1.0.0; se niega si ya hay estable o sin la palabra `estable`.
- Revisión: sin bloqueantes. Arreglado: la estable podía salirse de la lista que pide la app tras 30 betas (ahora 100 por página y, si no hay estable en la lista, `releases/latest`); tocar el correo sin app de correo no hacía nada (ahora lo copia) y en Android 12 copiar no avisaba (ahora «Correo copiado»); comentario de `concurrency` corregido; trampa de `estable.yml` añadida a la plantilla.

## Pendiente
- Del usuario: la pasada en el OPPO (bloque 4) no se ha hecho: el móvil no estaba conectado. Con el OPPO por cable unos minutos, Claude instala «Tiempo» (`com.trastealabs.tiempo`) desde Releases al lado de la antigua sin tocarla, y comprueba el permiso de «instalar apps desconocidas» de ColorOS, una actualización real y que el widget refresca. Después, en la Tiempo nueva: sus sitios, la clave de IA, los widgets y «Recibir versiones beta» (también en los móviles de la familia); cuando la vea bien, desinstalar la antigua.
- En el móvil real (el emulador no lo ve): instalar y actualizar en ColorOS, widget refrescando.
- Para más adelante: el aviso «ColorOS cierra las apps…» sale también en móviles que no son OPPO (se vio en el emulador); «Hay una beta nueva: 1.0.0» no dice el número cuando la versión instalada se llama igual; valorar las «releases inmutables» de GitHub; la 1.0.1 beta 10 espera su estable cuando Álvaro quiera.

## Decisiones tomadas por el camino
- `mv .git` daba «Permission denied» (algo vigila la carpeta); `Move-Item` movió el contenido y dejó `.git` vacía. Se comprobó la copia (`fsck`, mismo árbol) y se hizo `git init` sobre la vacía. Etiquetas `antes-*` en el privado.
- El 022 trabajó en la rama `encargo/022-tiempo-publicada` y la fusionó a `main` tres veces, porque las betas solo salen de `main` (primera beta, segunda beta, y los arreglos).
- Los tests corren con zona UTC e idioma inglés fijos (`app/build.gradle.kts`): `WidgetMatchesAppTest` fallaba en GitHub (UTC) y pasaba en el PC; ahora fallaría en los dos. No era un test inestable, era la zona.
- `tools/publicar.sh` pone la estable en el commit de la última beta (no en la punta de `main`), siempre que después no haya cambios de la app: así los commits de docs no bloquean la estable.
- Ajustes extra del repo: fijado por hash obligatorio en Actions y rulesets para `main` y `v*`. La descripción venía con acentos rotos de `subir-secretos.ps1` y se corrigió.
- Tras la estable 1.0.0, los arreglos de la revisión suben a 1.0.1 (retoque), como dice `CLAUDE.md`.
- Las notas de la estable ponen primero los cambios y al final de qué beta sale (la app las enseña sin Markdown); se editó así la de la 1.0.0.

## Quién hizo qué
| Bloque | Quién | Modelo |
|---|---|---|
| 1 Corte limpio y repo público | construcción | Opus |
| 2 Flujos de GitHub | construcción | Opus |
| 3 Betas, actualización y estable | construcción | Opus |
| 4 Pasada en el OPPO | pendiente (móvil sin conectar) | — |
| 5 Plantilla y cierre | rutinario (plantilla, fila en propuestas) + construcción (archivo del privado, estado) | Opus |
| Revisión | revisor | Opus |

Reparto por modelo (de `consumo.py`): Opus 83 %, subagentes en Opus 17 % · Uso semanal: 46 % → 48 %

## Para la casa
- Un `check` falló con `NoSuchFileException` en los resultados de tests porque el revisor lanzaba el suyo a la vez en la misma carpeta: el revisor no debería pasar el `check` mientras la construcción sigue trabajando, o la construcción esperar a que acabe.
- El rutinario volvió a correr en Opus.
