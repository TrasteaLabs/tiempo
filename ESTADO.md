# Estado del proyecto

Actualizado: 2026-11-26. Proyecto al día con Becario 1.0 y Cristal 1.6 (`.claude/becario.json` 1.0.2). `check`: `./gradlew :buildSrc:test lintDebug testDebugUnitTest` (1509 tests de la app, verde).

## Encargo en curso
022-tiempo-publicada · fase: construcción (bloque 3 de 5)

Plan en `docs/encargos/022-tiempo-publicada/plan.md`: publicar en `trastealabs/tiempo` (ya existe, público y vacío, con los secretos de firma). El 021 está entregado (`docs/encargos/021-tiempo-abierta/entrega.md`).

## Qué hay hecho
- Todo fusionado en `main` hasta el 021: la app es `com.trastealabs.tiempo` (dev: `.dev`), versión 1.0.0 con `versionCode` = commits, APK `tiempo-<versión>-<N>.apk`, firma por variables `RELEASE_*` o la clave del PC, actualizador desde las Releases de GitHub (`update/`), atribuciones y Acerca de con enlaces y licencias de terceros, User-Agent propio, textos de IA para quien no sabe qué es, y README, LICENSE (GPL-3.0), PRIVACY, SECURITY, CONTRIBUTING y plantillas de issues.
- La app: tiempo de donde estoy y de los sitios guardados (Open-Meteo), avisos de AEMET, fiabilidad, aire y polen, parte opcional con IA (resumen, frase de cada día, pregunta libre, frase en el widget 4×2, notificación de la mañana), widgets 4×1 y 4×2.
- Historial de encargos anteriores al 021 (`docs/specs/`, 019 y 020): fuera de `main`, en el historial del repo privado. El montaje personal (móvil de pruebas, emulador) vive en `CLAUDE.local.md`, fuera de git.
- Etiquetas de vuelta atrás `antes-011`, `antes-019`, `antes-020` y `antes-021`; sin ramas ni worktrees sueltos.

## Pendiente
- Del usuario, para el 022: copia de `~/.weatherapp/` fuera del PC; crear la organización `trastealabs` en GitHub desde su cuenta actual; ejecutar `tools/subir-secretos.ps1`.
- Solo probado en tests (va en el 022): el actualizador contra GitHub real y la instalación en ColorOS. En el móvil de pruebas siguen las apps con el id viejo; la nueva no se instala hasta el 022.
- Para el 022: Actions con `fetch-depth: 0` (o `VERSION_CODE`) y `RELEASE_SIGNING_REQUIRED=true`; repo público sin el historial privado.
- Del 020 (para más adelante): «ahora» pisa el «12» en «Sol y luna»; morado de M3 en el `TimePicker`; «Hecho» sin verbo en Sitios; tests que fallan a ratos con el PC cargado (`MainViewModelTest`, `WidgetMatchesAppTest`, `MoonIconTest`).
- Del 019 (para más adelante): franja del día a 196 dp fijos (a 200 % recorta), tira de `DeleteSheet` sin medir, nombres largos de postales con «…».
- Abiertos menores del 018 (sin probar a mano): girar con la hoja abierta y tocar la notificación; «He cortado lo pegado…» al teclear el 201.º; un día del mes cuenta como cifra; demo con lunas de noche en escenas de día. Del 017: falta captura del 4×2 con pocas horas.
- Si la notificación de la mañana llega algún día más de 30 min tarde: pasar a `setAlarmClock`.
- Para más adelante: Navigation 3 en lugar de `ScreenHost`; `targetSdk` 37 con pruebas de ColorOS; fase 3 del parte (repreguntas, voz, avisos por AEMET o lluvia próxima, frase en el 4×1); si llega Play Store, los permisos `REQUEST_INSTALL_PACKAGES`, `USE_EXACT_ALARM` y `ACCESS_BACKGROUND_LOCATION` y dejar fuera el actualizador.

## Decisiones que siguen vigentes
- Texto (019): niveles medidos por superficie en `Type.kt` (`OnSky` 100 %, `OnScreen` 85 %, `OnSheet` 78/62 %), `tools/medir_niveles.py` y `TextLevelsTest`; sin alfas de texto sueltos.
- Previsión de Open-Meteo; de AEMET solo avisos, sin clave. Nombre del sitio con GeoNames dentro de la APK.
- Refresco: worker de 30 min + alarma de respaldo; el widget nunca descarga. El actualizador es la única otra descarga, y solo al abrir la app o cuando se pide.
- Versiones: publicada una estable, un encargo sube el segundo número y un retoque el tercero (`version.properties`).
- Cielo: 14 fotos del 009; escena quieta (solo sube el astro al abrir); widget pintado con `WidgetPainter`.
- Con ubicación denegada y datos guardados no se ofrece «Dar permiso»; `yesterdayPhrase` usa `now`.
- Descartados: pantalla de bienvenida con el parte en grande; 2×1 e intermedio del widget; arreglar el progreso del atrás en ColorOS; tirones del 011.
- Medidas: rápidas (una pasada) salvo que se note algo; `tools/framestats.py --slow` (lento = más de 33,3 ms); apuntar siempre `renderFrameRate` y Hz.
