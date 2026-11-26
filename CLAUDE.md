# Tiempo

App del tiempo para Android, de Trastea Labs, de código abierto (GPL-3.0) y sin cuentas: se instala con el APK de las Releases de GitHub, sin Play Store, y se actualiza sola desde ahí. Saber de un vistazo qué tiempo hace y hará donde estoy y en los sitios guardados, en la app y en un widget que nunca se queda congelado, con los avisos oficiales de AEMET y, si el usuario pone su clave, un parte escrito por IA. Español en docs, commits y UI; código e identificadores en inglés.

Se lleva con **Becario** (plugin de Claude Code): lo nuevo o grande empieza con `/becario:encargo`; lo pequeño se pide directamente. Encargos en `docs/encargos/` (desde el 021; los anteriores están en el historial del repo privado). Si hay `CLAUDE.local.md` (fuera de git), trae el montaje de quien mantiene: su móvil y su emulador.

## Comandos
- `./gradlew :buildSrc:test lintDebug testDebugUnitTest --console=plain` → el `check` (tests de la lógica de compilación, lint, compilación y tests). Tiene que pasar antes de dar nada por hecho. Pega la salida como evidencia.
- `./gradlew installDebug` → instala «Tiempo dev» (`com.trastealabs.tiempo.dev`) en el emulador o el móvil conectado.
- `./gradlew assembleRelease` → `app/build/outputs/apk/release/tiempo-<versionName>-<versionCode>.apk`, firmada. La firma sale de las variables `RELEASE_KEYSTORE_FILE`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS` y `RELEASE_KEY_PASSWORD` (así firma GitHub) o, si no están, de `~/.weatherapp/keystore.properties` (la crea quien mantiene). Paquete: `com.trastealabs.tiempo`.
- `./gradlew installPerf` + `sh tools/medir.sh <medida>` → medir sin depuración (código de la release con R8, la demo y la clave de debug; va encima de la debug sin perder datos; `run-as` no funciona).
- `sh tools/bench.sh arranque|desplazar` → Macrobenchmark (arranque en frío y desplazar de la demo) en un móvil **por cable**; se niega si ve una conexión inalámbrica. `sh tools/bench.sh perfil` regenera `app/src/main/generated/baselineProfiles/baseline-prof.txt`, **solo en un emulador**. Nunca `connectedAndroidTest` (desinstala la app y se lleva los sitios guardados).
- `python tools/build_places.py` → regenera `app/src/main/assets/places.tsv.gz` desde GeoNames. Solo si se cambia la lista de lugares.
- `tools/subir-secretos.ps1` → sube a GitHub los cuatro secretos de firma. Lo ejecuta quien mantiene, nunca Claude.
- `sh tools/publicar.sh estable` → publica la estable: etiqueta `vX.Y.Z` en el commit de la última beta y GitHub publica ese mismo APK. **Solo cuando quien mantiene dice «publica la estable»**, nunca por iniciativa propia.

## Publicar (022)
- Repo público `trastealabs/tiempo`. Tres flujos en `.github/workflows/`: `check.yml` (cada push y pull request, sin secretos), `beta.yml` (cada push a `main` que toque la app: compila, firma, comprueba la huella de `firma.sha256` y publica la «prerelease» `vX.Y.Z-beta.N`) y `estable.yml` (al llegar una etiqueta `vX.Y.Z`: promociona el APK de la beta de ese commit, sin compilar).
- Las rutas que disparan una beta están en `beta.yml` y en `tools/publicar.sh`: si cambian, en los dos.
- Con la estable de una versión publicada, `beta.yml` falla hasta que se sube `versionName`. Las acciones van fijadas por hash: al actualizarlas, hash y comentario de versión.
- Commits con la identidad del estudio (configuración local del repo: «Trastea Labs <hola@trastealabs.com>»), nunca un correo personal.

## Versiones
- `versionName` en `version.properties` (X.Y.Z). `versionCode` = número de commits (`git rev-list --count HEAD`), con `-PversionCode` o la variable `VERSION_CODE` por delante y 1 si no hay git. La lógica vive en `buildSrc/` y tiene sus tests.
- Publicada una estable, el siguiente cambio sube `versionName`: un encargo, el segundo número; un retoque, el tercero.
- El actualizador lee el `versionCode` del nombre del APK: no cambiar `tiempo-<versionName>-<versionCode>.apk` sin cambiar `update/Releases.kt`.

## No se toca
Reglas del producto. Cambiarlas es decisión de quien mantiene.
- **Alcance:** lo que hace la app es lo de arriba más lo aprobado en un encargo. El resumen, la frase de cada día, la explicación de los avisos, la respuesta a una pregunta, la frase del widget 4×2 y la notificación de la mañana pueden escribirlos un modelo de IA con la misma previsión; las reglas fijas (`Summary.kt`) quedan de respaldo y son lo que se ve sin clave.
- **Stack:** Kotlin nativo, una sola app (`app/`), Compose, widget con Glance, segundo plano con WorkManager, Gradle con el wrapper. minSdk 31, targetSdk 36. Dependencia nueva: se pregunta, y se añade a «Licencias de terceros» (`ui/Licenses.kt`; `ThirdPartyLicensesTest` falla si falta).
- **Secretos:** la clave de firma vive en `~/.weatherapp/` (con copia) o en los secretos de GitHub; Claude no la lee (bloqueado en `.claude/settings.json`; si cambia la carpeta, cambia también esas reglas). La clave de la IA la pega cada usuario en Ajustes y vive solo en su móvil, cifrada: no entra en el repo, la APK ni los logs, y Claude no la crea, no la lee ni abre la sección «IA» de una app con datos reales.
- **El widget nunca descarga:** pinta lo guardado. Solo `refresh/` descarga y escribe datos del tiempo; la única excepción es `update/`, que consulta las Releases de GitHub al abrir la app (como mucho una vez al día) o al pulsar «Buscar ahora», y baja el APK solo cuando el usuario lo pide.
- **Sin cuentas, analíticas, anuncios ni rastreo.** El proveedor de IA es pago por uso con la clave del propio usuario. Todas las llamadas llevan el User-Agent `Tiempo/<versionName> (+https://github.com/trastealabs/tiempo)`, sin nada del aparato.
- **Datos guardados en el móvil, y nada más:** por sitio (donde estoy y guardados) la última previsión, avisos, fiabilidad, aire y polen y el parte de la IA; último sitio de «donde estoy», lista de sitios (nombre, provincia, país, coordenadas), contadores de llamadas a Open-Meteo e IA (hoy y mes) y de preguntas (hoy), última temperatura vista, preferencias, copia del último RSS y tar.gz de AEMET en caché, registro de refrescos de 7 días (sin ubicación ni nombre), claves de IA cifradas, proveedor elegido, hora de la notificación y día del último aviso; del actualizador, última comprobación y su fallo, interruptor beta, versión descartada, la última versión encontrada y el APK bajado (en caché, se borra al instalar). Las preguntas y sus respuestas no se guardan. Si cambia esta lista, cambia también `PRIVACY.md`.
- **Privacidad:** la ubicación solo sale hacia Open-Meteo, redondeada a 2 decimales. El nombre del sitio se calcula en el móvil (GeoNames en la APK) y los avisos de AEMET se bajan enteros y se filtran en el móvil. A la IA van cifras y horas, sin coordenadas, nombre del sitio ni zona del aviso; la pregunta sale tal cual la escribe el usuario. A GitHub, solo la consulta de versiones y la descarga.
- **Actualizador:** comprueba el SHA-256 contra el `digest` de GitHub, el paquete y el certificado de firma antes de instalar; la versión `.dev` no se actualiza nunca. Dueño y repo, solo en `update/UpdateConfig.kt`.
- **Tests:** cada criterio con test o comprobación escrita; a los tests, datos inventados, nunca ubicaciones reales ni llamadas a GitHub.
- **Unidades:** °C, km/h y hora local de 24 h.

## Trampas
- Órdenes de Bash sin variables de shell (`L=…; … $L`) ni `$(…)`: con las reglas `deny` el arnés no puede comprobarlas y pide permiso aunque la sesión vaya sin permisos, y la sesión se para. Rutas tal cual, una orden por llamada si hace falta. Para limpiar resultados de tests, `rm -r app/build/test-results/testDebugUnitTest`.
- Dos `gradlew` a la vez en la misma carpeta dan rojos falsos (`EOFException`, `R.jar`): borrar `app/build/test-results/testDebugUnitTest` y repetir.
- Android en Windows: los worktrees en `.claude/worktrees/...` dan rutas demasiado largas para compilar. Para partes en paralelo, worktree de ruta corta o la carpeta principal.
- `PhotoLooks.kt`, `ScreenLooks.kt` y `TextBackdrops.kt` los generan scripts de `tools/` (`build_skies.py`, `build_looks.py`, `medir_niveles.py`) con la ruta del paquete dentro: si se mueve el paquete, cambiarla allí.
- ColorOS corta el segundo plano: el refresco del widget solo se da por bueno probado en un móvil con ColorOS, no en el emulador. Retrasa las alarmas no exactas (respaldo hasta ~22 min, medianoche hasta 1 h) y convierte `setExactAndAllowWhileIdle` en una con ventana (la notificación de las 7:30 llegó a las 07:36).
- Los assets `.gz` pierden la extensión al empaquetar (AGP): `places.tsv.gz` es `places.tsv` dentro del APK. Los tests JVM no lo ven; solo lo comprueba abrir la app.
- Tamaños del widget medidos en un OPPO Find X8 Pro (3 px/dp): 4×1 ≈ 313×82 dp (Android dice 328×90), 4×2 ≈ 328×146 dp. Umbral de la versión grande: 130 dp. El tope diario de preguntas a la IA (20) también lo gasta la demo `--es ask`.
- Horas: todo instante en epoch UTC y se pasa a local con la zona de Open-Meteo; las funciones de cálculo reciben `now` y la zona (nunca `System.currentTimeMillis()` dentro). Lo que dice «hoy» o «este mes» cuelga del `now` que `MainViewModel` recalcula a medianoche; probarlo con el reloj pasada la medianoche.
- Cristal: el desenfoque en vivo (`Modifier.glass`) solo está en la barra superior de Ajustes y Sitios (`glass/GlassTopBar.kt`); tarjetas, chips y campos usan `Modifier.translucent` y hojas, ventana del día y diálogo son opacos. No poner cristal en lo que se mueve (`D24GuardTest` lo vigila).
- `ModalBottomSheet` y los diálogos viven en otra ventana: `localPositionOf` respecto a la principal revienta la app, y solo se vio en el móvil. El atrás predictivo y «Quitar animaciones» no se prueban con adb: van a mano.
- Hoja que baja con el dedo dentro del pager: al volver a tocarla cancela su animación de vuelta (`Job.cancel()` en el `down`); el velo de detrás solo cierra si el toque cae fuera de la hoja, y tocar el asa no cierra (009).
- Emulador: Play Services no da ubicación (`snapshot.json` con `adb push`); `force-stop` borra alarmas y trabajos. Siempre `adb -s emulator-<puerto>`.
- Datos inventados: versión debug, `adb shell am start -S -n com.trastealabs.tiempo.dev/com.trastealabs.tiempo.demo.DemoActivity` (sin `-S` no cambia de escena; `--es update <estado>` pinta cada estado del actualizador) o `DemoReceiver` para el widget (`am broadcast -a com.trastealabs.tiempo.debug.DEMO -n com.trastealabs.tiempo.dev/com.trastealabs.tiempo.demo.DemoReceiver …`); `run-as com.trastealabs.tiempo.dev` deja tocar `shared_prefs`.
- Play Store (si llega): `REQUEST_INSTALL_PACKAGES`, `USE_EXACT_ALARM` y `ACCESS_BACKGROUND_LOCATION` necesitan justificación, y el actualizador tendría que quedar fuera.

## Cómo trabajar aquí
- `ESTADO.md` dice dónde está el proyecto: léelo al empezar y reescríbelo (corto, unas 40 líneas) al terminar una tarea.
- Todo va a `main`: commit y push al acabar, con el `check` en verde.
- Diseño: `DESIGN.md` (Cristal aplicado a esta app). Pantallas y widget se diseñan con Opus; lo que toca widget, segundo plano, avisos o el actualizador, esfuerzo alto.
- `Explore` siempre con `model: haiku`.
- Capturas solo en hitos, reducidas a unos 450 px de ancho, de la demo (datos inventados) y solo de las pantallas afectadas.
- Trabaja en silencio y avisa solo si hay una decisión de quien mantiene, un bloqueo o riesgo de perder algo. Al terminar, pocas líneas en lenguaje llano.

## Compact instructions
Si se compacta, conserva: encargo y bloque en curso, ficheros tocados, decisiones pendientes del usuario y la salida del último `check`.
