# 021 · Tiempo, lista para abrirse

Aprobado: 2026-11-26 · Uso semanal al empezar: 41 %

## Qué se quiere y por qué
Tiempo deja de ser «la app de Álvaro» y pasa a ser la primera app pública de **Trastea Labs**: código abierto, una sola app para todos (Álvaro es un usuario más), sin recoger ningún dato, distribuida por GitHub Releases con un actualizador dentro de la app. Las decisiones de marca están en `../Estudio/guia-marca.md` y no se reabren.

Este encargo deja **la app y el repo preparados** sin depender de nada que aún no existe (la organización `trastealabs` de GitHub y el dominio no están creados a 26-11-2026). Al terminar, la app se llama `com.trastealabs.tiempo`, no tiene nada personal dentro, lleva versión real, sabe actualizarse sola, cumple las atribuciones y trae README, licencia y política de privacidad. La publicación de verdad (repo público con historial limpio, GitHub Actions, primera release, paso del móvil de Álvaro) es el **022**, que se lanza cuando existan la organización y los secretos de firma.

Es el piloto: lo que salga de aquí y del 022 será la plantilla de Becario para Cuaderno, PersonalOS y Finanzas. Por eso lo reutilizable se hace con nombres genéricos y en piezas separadas.

## Decidido por Álvaro (26-11-2026)
- **Licencia:** GPL-3.0.
- **IA:** se queda, opcional y con la clave del propio usuario. El estudio no paga ni ve nada.
- **Canal beta:** una sola app con un interruptor «Recibir versiones beta» en Ajustes. No hay «Tiempo beta» aparte: la estable es el mismo fichero que se probó en beta.
- **Sus datos:** los pasa a mano a la app nueva (en el 022). No se construye exportar/importar.

## Qué no entra
- Crear el repo público, GitHub Actions, la primera release, tocar el OPPO y archivar el repo privado: es el 022.
- Botón de donaciones (falta la consulta fiscal), web del estudio, inglés, Play Store.
- Exportar/importar datos. IA sin clave (pagada por el estudio o en el dispositivo).
- Nada enlaza a `trastealabs.com` ni usa `hola@trastealabs.com` todavía: el contacto es GitHub. Cuando exista el dominio será un retoque.
- La release de `es.alvaro.weather` que Álvaro usa en el OPPO no se toca, ni se instala en el OPPO la app nueva.

## Cómo se hace

### 1. Identificador
Hoy: `namespace` y `applicationId` `es.alvaro.weather` en `app/build.gradle.kts`; debug y perf con sufijo `.dev`; 256 `.kt` con `package es.alvaro.weather` (136 main, 114 test, 3 debug, 3 benchmark).

Queda: `com.trastealabs.tiempo` (release), `com.trastealabs.tiempo.dev` (debug y perf, «Tiempo dev»). También el paquete Kotlin y las carpetas (`git mv`), para que en el código público no quede el nombre antiguo. Todo lo que lo nombra:
- `app/build.gradle.kts`, `benchmark/build.gradle.kts`, `app/proguard-perf.pro`, `app/src/debug/AndroidManifest.xml` (acción `…debug.DEMO`).
- Literales: `RefreshAlarmReceiver.kt` (acciones `…action.*`), `MainActivity.kt` (`EXTRA_OPEN_HERE`), `StartupBenchmark.kt`.
- `app/src/main/generated/baselineProfiles/baseline-prof.txt` (6 MB de firmas con `Les/alvaro/weather/`): sustituir el prefijo por `Lcom/trastealabs/tiempo/`; no hace falta regenerarlo.
- `tools/` (`bench.sh`, `medir.sh`, `prueba_reposo.sh`, `registro.py`, y los generadores `build_looks.py`, `build_skies.py`, `medir_niveles.py`, que escriben rutas de paquete), `CLAUDE.md`.
- La clave de firma no cambia de sitio (`~/.weatherapp/`) ni las reglas `deny` de `.claude/settings.json`. Se firma con la clave de siempre.

### 2. Fuera lo personal
Criterio: se quita lo que identifica a la persona o su montaje (Álvaro como «el usuario», «su móvil», su cuenta privada de GitHub, rutas de su PC); se conservan los hechos técnicos («en ColorOS, medido en un OPPO Find X8 Pro»). Córdoba como ciudad de ejemplo en tests es pública y puede quedarse; comprobar que ningún test o demo lleva coordenadas de más de 2 decimales cerca de casa.
- **App:** `about_seal` («Hecho en casa por Álvaro · Córdoba, 2026») pasa a la firma de marca: «Trastea Labs · por Álvaro García · Córdoba». Revisar `PlaceButton.kt:49` y los comentarios de `Scene.kt`, `TextMatch.kt`, `WidgetPainter.kt`.
- **`CLAUDE.md`:** se reescribe como documento del proyecto público (qué es, comandos, reglas, trampas). «Álvaro» pasa a «el usuario» o «quien mantiene»; la regla de la clave de IA se generaliza. Todo lo del móvil de Álvaro (sección «El móvil de Álvaro (OPPO)», turnos, páginas del escritorio, emulador `PersonalOS`, rutas `C:\Users\…`) va a `CLAUDE.local.md`, fuera de git (añadirlo a `.gitignore` junto con `.claude/settings.local.json`; confirmar en la documentación de Claude Code que ese fichero se sigue cargando).
- **Historial de encargos:** `docs/specs/` (001–018) y `docs/encargos/019` y `020`, con sus 299 capturas (57 MB, muchas del OPPO), se quitan de `main` (`git rm`). Siguen en el historial del repo privado, que se archiva en el 022. Antes, inventariar lo que los cita (`CLAUDE.md`, `DESIGN.md`, `tools/`, `.claude/launch.json` con el servidor `maquetas`, comentarios): lo que siga haciendo falta se pasa al propio documento o a `docs/` ya revisado. `docs/encargos/` continúa desde este 021.
- **`ESTADO.md`, `DESIGN.md`, `tools/`:** mismas reglas.

### 3. Versión y firma
- `version.properties` en la raíz con `versionName=1.0.0` (primera versión pública). `versionCode` = número de commits (`git rev-list --count HEAD`), con `-PversionCode` o la variable `VERSION_CODE` por delante y 1 de respaldo si no hay git. La app enseña «Versión 1.0.0 (N)».
- El APK de release se llama `tiempo-<versionName>-<versionCode>.apk`: el actualizador lee el `versionCode` del nombre.
- Firma: si existen las variables `RELEASE_KEYSTORE_FILE`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS` y `RELEASE_KEY_PASSWORD` se usan (así firmará GitHub); si no, lo de hoy (`~/.weatherapp/keystore.properties`). Nombres genéricos a propósito: valen para las demás apps.
- `tools/subir-secretos.ps1`: lo ejecuta **Álvaro**, nunca Claude. Lee `~/.weatherapp/keystore.properties`, crea el repo `trastealabs/tiempo` (público, vacío) si no existe y sube con `gh secret set` cuatro secretos: `RELEASE_KEYSTORE_BASE64`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`. No imprime ningún valor. Con parámetros (carpeta de la clave, repo) para reutilizarlo.
- Regla para `CLAUDE.md`: publicada una estable, el siguiente cambio sube `versionName` (encargo: el segundo número; retoque: el tercero).

### 4. Actualizador
Paquete nuevo `update/`, aislado (un Play Store futuro lo dejará fuera) y sin dependencias nuevas (OkHttp, kotlinx.serialization y `PackageInstaller`).
- **Fuente:** `GET https://api.github.com/repos/trastealabs/tiempo/releases?per_page=10`, sin clave. Dueño y repo en un solo sitio (`UpdateConfig`). Se elige la primera release que no sea borrador, que tenga un `.apk` y que no sea «prerelease» salvo que el interruptor beta esté encendido. Hay versión nueva si su `versionCode` es mayor que el instalado.
- **Cuándo:** al abrir la app, como mucho una vez cada 24 h, y con el botón «Buscar ahora» de Ajustes. Nunca desde el widget ni desde `refresh/`. Sin red, con 403 (límite) o con 404 (el repo aún no existe): silencio y se reintenta otro día; en Ajustes, «No se ha podido comprobar».
- **Aviso:** una línea discreta en la principal que lleva a Ajustes (se puede descartar para esa versión) y una fila en Ajustes con la versión nueva y sus notas. Nunca un diálogo que bloquee. Se llama «Versión de la app» o similar, para no confundirla con la sección que ya existe sobre el refresco de datos.
- **Descarga e instalación:** solo cuando el usuario pulsa. Baja a `cacheDir`, con progreso; antes de instalar comprueba el SHA-256 contra el `digest` que da GitHub para el fichero (si viene) y que el APK es del mismo paquete y está firmado con el mismo certificado que la app instalada; si no, lo borra y lo dice. Instala con una sesión de `PackageInstaller`; hace falta el permiso `REQUEST_INSTALL_PACKAGES` y, la primera vez, Android pide permitir «instalar apps desconocidas».
- **Interruptor** «Recibir versiones beta», apagado de serie, con una frase que diga qué son.
- La versión `.dev` no se actualiza nunca, pero trae en la demo una escena que pinta cada estado (al día, hay versión, bajando, error, fichero no válido) con datos inventados.
- Se guarda en el móvil: última comprobación, interruptor beta, versión descartada. Añadirlo a la lista de datos de `CLAUDE.md`, y la excepción a «solo `refresh/` descarga».

### 5. IA para un desconocido
No cambia cómo funciona (`data/ai/`, `ui/AiSection.kt`): apagada hasta que alguien pega su clave, y sin clave la app usa las reglas de `Summary.kt`. Cambian los textos de Ajustes › IA para quien no sabe qué es: qué hace, que es opcional y de pago por uso con su proveedor, enlace a dónde se saca la clave de cada uno, qué sale del móvil (cifras y horas, sin ubicación ni nombre del sitio; la pregunta, tal cual) y que la clave se guarda cifrada solo en el móvil. Sin clave, la app no insiste en ningún sitio.

### 6. Atribuciones y Acerca de
- **Open-Meteo** (términos leídos el 26-11-2026): la capa gratuita admite donaciones voluntarias; no admite anuncios ni suscripciones; pide un enlace junto a los datos. `footer_sources` y «Acerca de» llevan «Datos del tiempo de Open-Meteo.com» como enlace a `https://open-meteo.com/`, con «CC BY 4.0».
- **AEMET:** leer su nota legal (`https://www.aemet.es/es/nota_legal`) y poner la mención que pida.
- GeoNames, NASA y la letra ya están. Comprobar el origen de los iconos `ic_wx_*`. Añadir «Licencias de terceros»: lista fija de las librerías de `gradle/libs.versions.toml` con su licencia, y un test que falle si el catálogo tiene una librería que la lista no.
- **User-Agent** propio en todas las llamadas: `Tiempo/<versionName> (+https://github.com/trastealabs/tiempo)`, sin nada del aparato. Open-Meteo limita «por IP y por aplicación» y puede bloquear una app sin avisar: hay que ser identificable.
- **Acerca de:** versión, «Código abierto (GPL-3.0)» con enlace al repo, «Privacidad» (enlace a `PRIVACY.md` del repo), «Comentarios» (abre un issue nuevo en GitHub) y la firma de marca.

### 7. Documentos públicos
En español, cortos:
- `README.md`: qué es, capturas de la demo (datos inventados), cómo instalarla (Releases; Android 12 o superior), qué datos usa y de quién, resumen de privacidad, cómo compilarla, que se hace con IA y con Becario, licencia. Sin apartado de donaciones.
- `LICENSE` (texto oficial de la GPL-3.0) y aviso de copyright «© 2026 Álvaro García (Trastea Labs)»; el nombre y el icono no entran en la licencia.
- `PRIVACY.md`: el estudio no recoge nada. A dónde se conecta la app y qué ve cada uno: Open-Meteo (ubicación redondeada a 2 decimales), AEMET (nada del usuario), GitHub (solo al buscar o bajar una versión nueva) y, si el usuario pone clave, su proveedor de IA. Permisos y para qué. Qué se guarda en el móvil. Contacto: issues de GitHub. Fecha.
- `SECURITY.md`, `CONTRIBUTING.md` (issue antes de un cambio grande) y `.github/ISSUE_TEMPLATE/` (fallo, idea).

## Bloques
Construcción: Opus, esfuerzo alto (el actualizador tiene muchos casos límite). Una sesión. Etiqueta `antes-021` antes de empezar.

1. **Identificador y paquete** · rutinario · todo pasa a `com.trastealabs.tiempo` y el `check` sigue verde. Patrón: el punto 1.
2. **Versión y firma** · normal · `version.properties`, nombre del APK, firma por variables y `tools/subir-secretos.ps1`.
3. **Actualizador: lógica** · normal · consulta, elección por canal, comprobaciones del fichero y tests.
4. **Actualizador: pantallas e instalación** · normal · aviso, fila de Ajustes, interruptor beta, instalación y escena de demo.
5. **Atribuciones, Acerca de, User-Agent y textos de IA** · normal.
6. **Fuera lo personal** · rutinario, salvo `CLAUDE.md`, que lo reescribe la sesión principal.
7. **Documentos públicos** · rutinario · con el punto 7 y las capturas de la demo.

## Cómo se comprueba
- `check` en verde.
- Tests nuevos: elección de release por canal (solo estables, betas, borradores, sin `.apk`, `versionCode` igual o menor, nombre de fichero raro), respuestas 403/404/sin red, una comprobación cada 24 h con `now` inyectado, rechazo de un APK con otro paquete o que no cuadra con el `digest`, respaldo del `versionCode`, lista de licencias frente al catálogo. Datos inventados con MockWebServer.
- Búsqueda en los ficheros versionados, tests incluidos: ningún `es.alvaro`, ningún `C:\Users` ni `alvarogarcian97`; «Álvaro» solo en la firma, `LICENSE`, `README.md`, `PRIVACY.md` y `docs/encargos/`.
- En el emulador con «Tiempo dev» (`com.trastealabs.tiempo.dev`): la principal y el widget como antes; cada estado del actualizador en la demo; Ajustes › IA sin clave; Acerca de con sus enlaces.
- `./gradlew assembleRelease` da `tiempo-1.0.0-<N>.apk` firmado; instalado en el emulador, arranca y la comprobación de versión (404) no molesta. El fichero se desinstala del emulador al acabar (ver trampas).
- Queda para el 022, y la entrega lo dice como «solo en tests»: el actualizador contra GitHub real y la instalación en ColorOS.

## Ayudas externas
Ninguna.

## Trampas
- El `versionCode` cuenta commits y el repo público del 022 empezará de cero: un APK de release compilado ahora tendría un número mayor que los primeros de GitHub y no dejaría instalarlos encima. Por eso la release nueva no se instala en ningún móvil real en este encargo.
- Dos apps `.dev` en el emulador: la antigua `es.alvaro.weather.dev` se queda huérfana; desinstalarla solo del emulador. En el OPPO no se toca nada.
- Mover 256 ficheros de paquete: `git mv` de la carpeta y un solo reemplazo; compilar antes de seguir. `PhotoLooks.kt`, `ScreenLooks.kt` y `TextBackdrops.kt` los generan scripts de `tools/` que llevan la ruta dentro.
- Al quitar `docs/specs/`, el `grep` de lo que los cita incluye tests y comentarios.
- GitHub sin clave admite 60 consultas por hora y por IP: una al día sobra, pero los tests no llaman nunca a GitHub.
- Para el día de Play Store (no ahora), tres permisos darán guerra: `REQUEST_INSTALL_PACKAGES`, `USE_EXACT_ALARM` y `ACCESS_BACKGROUND_LOCATION`. Solo anotarlo en la entrega.

## Del usuario
- Antes: nada.
- Después de la entrega, para poder lanzar el 022:
  1. Tener copia de `~/.weatherapp/` (la clave de firma) fuera del PC.
  2. Crear la organización `trastealabs` en GitHub, plan gratuito, **desde su cuenta actual** (`https://github.com/account/organizations/new`), no con una cuenta de usuario nueva.
  3. Ejecutar `tools/subir-secretos.ps1`.
  4. El dominio `trastealabs.com` no bloquea nada; cuando lo tenga, se añade el correo con un retoque.
