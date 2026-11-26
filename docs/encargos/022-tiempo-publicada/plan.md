# 022 · Tiempo, publicada

Aprobado: 2026-11-26 · Uso semanal al empezar: 46 %

> Escrito el 26-11-2026 junto con el 021. Lanzado el 26-11-2026 con el «lanza el 022» de Álvaro, que confirma tener la copia de `~/.weatherapp/` fuera del PC. Estado al lanzar (comprobado): el 021 está entregado; la organización existe (`github.com/TrasteaLabs`, verificada); `trastealabs.com` está registrado y con correo; el repo `trastealabs/tiempo` existe, público y vacío, con los cuatro secretos `RELEASE_*`. Repasado contra `docs/encargos/021-tiempo-abierta/entrega.md`.

## Qué se quiere y por qué
Que cualquiera pueda bajarse Tiempo de un repo público de Trastea Labs y que la app se actualice sola. El 021 dejó la app preparada; aquí se publica: repo `trastealabs/tiempo` con historial limpio, GitHub compila, firma y publica, cada cambio que llega a `main` sale como beta y la estable se promociona con el visto bueno de Álvaro. Álvaro pasa su móvil a la app nueva y el repo privado se archiva.

Lo que quede montado es la plantilla para las demás apps del estudio: se deja escrito qué se copia y qué se cambia.

## Qué no entra
- Web del estudio, donaciones, Play Store, inglés.
- Llevar la plantilla al plugin Becario: aquí solo se deja preparada y propuesta.
- Desinstalar la Tiempo antigua del OPPO: lo hace Álvaro cuando quiera.

## Cómo se hace

### 1. Corte limpio
- Comprobaciones previas: `curl` sin sesión a `https://api.github.com/orgs/trastealabs` (200) y a `…/repos/trastealabs/tiempo`; `gh secret list --repo trastealabs/tiempo` enseña los cuatro `RELEASE_*`; `main` limpio y subido al repo privado; ninguna otra sesión trabajando en la carpeta.
- Repaso de lo que se va a publicar, fichero a fichero para imágenes: nada personal (mismas búsquedas del 021), ninguna clave, ninguna captura con datos reales.
- Se sigue trabajando en esta misma carpeta (conserva `local.properties`, que Claude no puede leer ni copiar, y los ajustes de Claude). El `.git` actual se **mueve** a `../WeatherApp-privado.git` (no se borra) y se crea uno nuevo con un único commit, «Tiempo 1.0.0: primera versión pública», que se sube a `trastealabs/tiempo`. Las etiquetas `antes-*` se quedan en el privado.
- Autor de los commits públicos (configuración local del repo, sin tocar la global): «Trastea Labs <hola@trastealabs.com>». Nunca su correo personal.
- Contacto: ahora que el correo existe, `hola@trastealabs.com` se añade junto a los issues de GitHub en `PRIVACY.md`, `SECURITY.md`, `README.md` y en «Comentarios» de Acerca de (como texto que se puede copiar o abrir en el correo). Va dentro del primer commit público.
- Ajustes del repo: descripción y temas, issues sí, wiki no; `main` y las etiquetas `v*` protegidas contra borrado y reescritura; avisos privados de seguridad, detección de secretos con bloqueo al subir y alertas de dependencias; Actions con permisos de solo lectura por defecto y aprobación obligatoria para flujos que vengan de forks.

### 2. GitHub Actions
Tres flujos en `.github/workflows/`, con las acciones fijadas por hash y permisos mínimos. Los secretos solo los ve el trabajo que firma, y ese trabajo nunca corre en un pull request.
- **`check.yml`** · en cada push y pull request: el `check` del proyecto. Sin secretos.
- **`beta.yml`** · en cada push a `main` que toque la app (`app/**`, `benchmark/**`, `gradle/**`, `*.kts`, `version.properties`; no los `.md` ni `docs/`): `check`, compila la release con el `versionCode` del número de commits (clonado completo), la firma con los secretos, comprueba con `apksigner` que la huella del certificado es la esperada (`firma.sha256` en el repo, dato público sacado del propio APK), crea la etiqueta `v<versionName>-beta.<versionCode>` y publica una «prerelease» con `tiempo-<versionName>-<versionCode>.apk` y notas generadas de los commits. Falla con un mensaje claro si `versionName` ya tiene una estable publicada.
- **`estable.yml`** · al subir una etiqueta `vX.Y.Z`: busca la beta de ese mismo commit y publica **ese mismo fichero** como release normal (la «latest»). No recompila ni usa secretos. Falla si no hay beta de ese commit o si la etiqueta no coincide con `version.properties`.
- `tools/publicar.sh estable`: comprueba que el commit de `main` tiene su beta publicada, pone la etiqueta y la sube. Es lo que hace Claude cuando Álvaro dice «publica la estable». Regla nueva en `CLAUDE.md`.

### 3. Probarlo de verdad
- Primera beta: sale sola con el primer push. Se instala desde la release en un emulador limpio.
- Segunda beta (un cambio mínimo): la app, con el interruptor beta encendido, la ofrece, la baja, la comprueba y se instala encima sin perder los sitios. Con el interruptor apagado no ofrece nada.
- Estable `v1.0.0`: la ve una instalación con el interruptor apagado; el fichero es idéntico al de la beta (mismo SHA-256).
- Si en GitHub fallan los tests que ya fallaban a ratos (`MainViewModelTest`, `WidgetMatchesAppTest`, `MoonIconTest`), se arregla la causa (esperar al evento, no a un tiempo fijo). Sin reintentos automáticos. El `check` tiene que pasar tres veces seguidas.

### 4. El móvil de Álvaro
Pasada corta al final, con turno y por cable, siguiendo `CLAUDE.local.md`: instalar «Tiempo» (`com.trastealabs.tiempo`) desde la release **al lado** de la antigua, sin abrir ni tocar la antigua. Comprobar en ColorOS el permiso de «instalar apps desconocidas», una actualización real y que el widget refresca.

### 5. Cierre
- Repo privado de hoy (el `origin` de `git remote -v` antes del cambio): archivado (se puede desarchivar). `../WeatherApp-privado.git` se queda de copia.
- `docs/plantilla-publicacion.md`: qué se copia tal cual a otra app (los tres flujos, `update/`, `version.properties` y su lógica de Gradle, `tools/subir-secretos.ps1`, `tools/publicar.sh`, esqueletos de `README`, `PRIVACY`, `SECURITY`, `CONTRIBUTING` y plantillas de issues), qué se cambia en cada una (identificador, repo, carpeta de la clave, textos), los pasos que son del usuario y las trampas encontradas. Una fila en `plugins/becario/manual/propuestas.md` que apunte a ese documento.
- `ESTADO.md` y `CLAUDE.md` al día (remoto nuevo, cómo se publica).

## Bloques
Construcción: Opus, esfuerzo alto. Una sesión. No hay etiqueta de vuelta atrás: la vuelta atrás es `../WeatherApp-privado.git`.

1. **Corte limpio y repo público** · normal.
2. **Flujos de GitHub** · normal.
3. **Betas, actualización real y estable** · normal · incluye arreglar los tests inestables si salen.
4. **Pasada en el OPPO** · normal, corta.
5. **Plantilla y cierre** · rutinario.

## Cómo se comprueba
- `check` verde en local y tres veces seguidas en GitHub.
- En `https://github.com/trastealabs/tiempo/releases`: dos betas y la estable 1.0.0, con el APK firmado por la clave de siempre.
- En el emulador: instalar, actualizar de beta a beta y ver la estable, como en el punto 3.
- En el OPPO: el punto 4.
- Sin sesión iniciada (`curl`), el repo y el APK se pueden bajar.

## Ayudas externas
Ninguna.

## Trampas
- Mover `.git` con otra sesión abierta en la carpeta la rompe: comprobarlo antes. Rutas escritas tal cual, sin variables.
- Un pull request de un fork no puede ver secretos ni lanzar la firma: revisar los disparadores de `beta.yml` (solo `push` a `main`).
- El `versionCode` necesita el historial entero (`fetch-depth: 0`); con un clonado superficial saldría 1.
- El flujo que firma lleva `RELEASE_SIGNING_REQUIRED=true` (del 021): sin secretos, falla en vez de publicar un APK sin firmar.
- La app antigua y la nueva conviven en el OPPO con el mismo nombre «Tiempo»: no confundirlas; todo lo de Claude va contra `com.trastealabs.tiempo`.
- Si la cuenta u organización da 404 sin sesión después de crearla, está marcada: parar y avisar antes de subir nada.
- Publicar es difícil de deshacer: el repaso del punto 1 se hace antes del primer push, no después.

## Del usuario
- Antes de lanzar: copia de `~/.weatherapp/` fuera del PC y `tools/subir-secretos.ps1` ejecutado (`powershell -ExecutionPolicy Bypass -File tools/subir-secretos.ps1`). La organización y el dominio ya están.
- Durante la pasada final: el OPPO conectado por cable y libre unos minutos.
- Después, en la Tiempo nueva: añadir sus sitios, pegar la clave de IA, poner los widgets y encender «Recibir versiones beta» (también en los móviles de la familia). Cuando la vea bien, desinstalar la antigua.
