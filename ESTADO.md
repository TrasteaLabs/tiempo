# Estado del proyecto

Actualizado: 2026-11-26. Proyecto al día con Becario 1.0 y Cristal 1.6 (`.claude/becario.json` 1.0.2). `check`: `./gradlew :buildSrc:test lintDebug testDebugUnitTest` (1514 tests de la app, verde; también en GitHub).

## Encargo en curso
022-tiempo-publicada · fase: entregado

Entrega en `docs/encargos/022-tiempo-publicada/entrega.md`. Falta la pasada en el OPPO (bloque 4): el móvil no estaba conectado.

## Qué hay hecho
- Repo público `TrasteaLabs/tiempo` (remoto `origin`), con historial limpio desde «Tiempo 1.0.0: primera versión pública». El repo privado de antes está archivado en GitHub y copiado en `../WeatherApp-privado.git` (con las etiquetas `antes-011` … `antes-021` y los encargos 001–020).
- GitHub Actions: `check.yml` (cada push y pull request), `beta.yml` (cada cambio de la app en `main` → «prerelease» `vX.Y.Z-beta.N` firmada, huella comprobada contra `firma.sha256`) y `estable.yml` (etiqueta `vX.Y.Z` → publica el mismo APK de la beta). La estable la pone `sh tools/publicar.sh estable`, solo cuando Álvaro lo pide.
- Releases: 1.0.0 beta 4 y 6, estable 1.0.0 (= beta 6), 1.0.1 beta 10 (arreglos de la revisión del 022).
- La app: `com.trastealabs.tiempo` (dev: `.dev`), actualizador desde Releases (con interruptor de betas), Acerca de con atribuciones, licencias y el correo `hola@trastealabs.com`; tiempo de donde estoy y sitios guardados (Open-Meteo), avisos de AEMET, fiabilidad, aire y polen, parte opcional con IA, widgets 4×1 y 4×2.
- Ajustes del repo: avisos privados de seguridad, detección de secretos con bloqueo al subir, alertas de dependencias, Actions de solo lectura y con acciones fijadas por hash, aprobación para forks, `main` y `v*` protegidas.
- Plantilla para las demás apps: `docs/plantilla-publicacion.md` (y una fila en `plugins/becario/manual/propuestas.md`).

## Pendiente
- Del usuario: conectar el OPPO por cable unos minutos para la pasada del 022 (instalar la nueva al lado de la antigua, permiso de ColorOS, una actualización real, widget). Después, en la nueva: sus sitios, clave de IA, widgets y «Recibir versiones beta» (también la familia); cuando la vea bien, desinstalar la antigua. Decir «publica la estable» cuando quiera la 1.0.1.
- Del 022 (para más adelante): el aviso «ColorOS cierra las apps…» sale también en móviles que no son OPPO; «Hay una beta nueva: 1.0.0» no dice el número si la instalada se llama igual; valorar «releases inmutables» de GitHub.
- Del 020: «ahora» pisa el «12» en «Sol y luna»; morado de M3 en el `TimePicker`; «Hecho» sin verbo en Sitios. Los tests que fallaban a ratos: `WidgetMatchesAppTest` era la zona (arreglado en el 022); `MainViewModelTest` y `MoonIconTest` no han fallado en GitHub.
- Del 019: franja del día a 196 dp fijos (a 200 % recorta), tira de `DeleteSheet` sin medir, nombres largos de postales con «…».
- Abiertos menores del 018 y 017 (sin probar a mano): girar con la hoja abierta y tocar la notificación; «He cortado lo pegado…» al teclear el 201.º; demo con lunas de noche en escenas de día; captura del 4×2 con pocas horas.
- Si la notificación de la mañana llega algún día más de 30 min tarde: pasar a `setAlarmClock`.
- Para más adelante: Navigation 3 en lugar de `ScreenHost`; `targetSdk` 37 con pruebas de ColorOS; fase 3 del parte; si llega Play Store, los permisos `REQUEST_INSTALL_PACKAGES`, `USE_EXACT_ALARM` y `ACCESS_BACKGROUND_LOCATION` y dejar fuera el actualizador.

## Decisiones que siguen vigentes
- Publicar: betas automáticas desde `main`; la estable es el mismo fichero que una beta, nunca se recompila. Publicada una estable, un encargo sube el segundo número y un retoque el tercero (`version.properties`); si no, `beta.yml` falla.
- Tests con zona UTC e idioma inglés fijos, igual en el PC que en GitHub.
- Texto (019): niveles medidos por superficie en `Type.kt` (`OnSky` 100 %, `OnScreen` 85 %, `OnSheet` 78/62 %), `tools/medir_niveles.py` y `TextLevelsTest`.
- Previsión de Open-Meteo; de AEMET solo avisos, sin clave. Nombre del sitio con GeoNames dentro de la APK.
- Refresco: worker de 30 min + alarma de respaldo; el widget nunca descarga. El actualizador es la única otra descarga, y solo al abrir la app o cuando se pide.
- Cielo: 14 fotos del 009; escena quieta; widget pintado con `WidgetPainter`.
- Medidas: rápidas (una pasada) salvo que se note algo; `tools/framestats.py --slow`; apuntar siempre `renderFrameRate` y Hz.
