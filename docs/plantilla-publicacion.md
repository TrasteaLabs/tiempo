# Plantilla de publicación

Receta para publicar otra app Android de Trastea Labs (Cuaderno, PersonalOS, Finanzas…) igual que Tiempo: repo público en la organización de GitHub, betas automáticas en cada cambio a `main`, estable que promociona la misma APK y actualizador dentro de la app. Sale del encargo 022. En esta receta `<app>` es el nombre en minúsculas (`cuaderno`) y `<dueño>/<repo>` el repo (`trastealabs/cuaderno`).

## Qué se copia tal cual

- `.github/workflows/check.yml`: el `check` en cada push y pull request. Sin secretos y con permisos de solo lectura.
- `.github/workflows/beta.yml`: en cada push a `main` que toque la app, pasa el `check`, compila la release firmada, comprueba la huella y publica la «prerelease» `vX.Y.Z-beta.N` con el APK. Los secretos solo los ve este flujo.
- `.github/workflows/estable.yml`: al llegar una etiqueta `vX.Y.Z`, baja el APK de la beta de ese commit, comprueba su SHA-256 y lo publica como release normal. No compila.
- `firma.sha256`: la huella del certificado. `beta.yml` no publica un APK que no la lleve.
- `tools/publicar.sh`: pone la etiqueta `vX.Y.Z` en el commit de la última beta y la sube. Se niega si hay cambios de la app sin beta.
- `tools/subir-secretos.ps1`: sube a GitHub los cuatro secretos de firma y crea el repo si no existe. Parámetros `-KeyDir`, `-Repo` y `-Description`.
- `tools/crear_clave.ps1`: crea la clave de firma, una sola vez, con contraseña aleatoria que no se imprime.
- `buildSrc/` (`AppVersion.kt`, `ReleaseSigning.kt` y `AppVersionTest.kt`) y `version.properties`: la versión (`versionName` a mano, `versionCode` = commits) y de dónde sale la firma.
- De `app/build.gradle.kts`, lo que usa lo anterior: la versión, la firma, `RELEASE_SIGNING_REQUIRED`, el nombre del APK y, en `testOptions`, `user.timezone=UTC` y el idioma inglés fijos para los tests.
- `app/src/main/java/com/trastealabs/tiempo/update/` (el actualizador), su parte en Ajustes (`UpdateSection` en `SettingsScreen.kt`, con sus textos) y los estados de la demo (`app/src/debug/.../demo/DemoUpdate.kt`).
- Esqueletos: `README.md`, `PRIVACY.md`, `SECURITY.md`, `CONTRIBUTING.md`, `LICENSE` (GPL-3.0, sin tocar) y `.github/ISSUE_TEMPLATE/`.
- Las reglas `deny` de `.claude/settings.json` que protegen la carpeta de la clave.

## Qué se cambia en cada app

- Identificador: `applicationId` y `namespace` en `app/build.gradle.kts` (y el paquete del actualizador, si se mueve).
- Repo y nombre del APK: en `update/UpdateConfig.kt`, `OWNER`, `REPO`, `APK_SLUG` y `CONTACT_EMAIL`; y `AppVersion.apkName("tiempo", …)` en `app/build.gradle.kts`.
- Carpeta de la clave: `~/.<app>/`. Cambia en la ruta de `keystore.properties` de `app/build.gradle.kts`, en las reglas `deny` y en los valores por defecto de `tools/subir-secretos.ps1` (`-KeyDir`, `-Repo`, `-Description`). `tools/crear_clave.ps1` tiene la carpeta, el nombre del `.jks`, el alias y el `dname` escritos dentro: cambiarlos.
- En `beta.yml` y `estable.yml`: el patrón `tiempo-*.apk`, la ruta `app/build/outputs/apk/release/tiempo-…apk` y los títulos de las releases («Tiempo …»).
- La orden del `check` en `check.yml` y en `beta.yml`, si la app no usa la misma.
- Las rutas que disparan una beta: en `beta.yml` (`paths`) y en `tools/publicar.sh` (`APP_PATHS`). Las dos a la vez.
- `firma.sha256`: la huella de la clave de esa app. Sale de `apksigner verify --print-certs` sobre una release firmada en el PC.
- Textos: README, PRIVACY, SECURITY, Acerca de y las URL de `.github/ISSUE_TEMPLATE/config.yml`.
- El User-Agent, que sale de `UpdateConfig.userAgent` (`Tiempo/<versión> (+<repo>)`).

## Ajustes del repo

Los hizo Claude con `gh api`. Cambia `<r>` por `<dueño>/<repo>`.

```
gh api -X PATCH repos/<r> -f description="…" -F has_wiki=false -F has_issues=true -F has_projects=false -F has_discussions=false
gh api -X PUT repos/<r>/topics -f "names[]=android" -f "names[]=…"
gh api -X PATCH repos/<r> --input - <<< '{"security_and_analysis":{"secret_scanning":{"status":"enabled"},"secret_scanning_push_protection":{"status":"enabled"}}}'
gh api -X PUT repos/<r>/private-vulnerability-reporting
gh api -X PUT repos/<r>/vulnerability-alerts
gh api -X PUT repos/<r>/actions/permissions/workflow -f default_workflow_permissions=read -F can_approve_pull_request_reviews=false
gh api -X PUT repos/<r>/actions/permissions/fork-pr-contributor-approval -f approval_policy=all_external_contributors
gh api -X PUT repos/<r>/actions/permissions -F enabled=true -f allowed_actions=all -F sha_pinning_required=true
```

Dos rulesets, cada uno con `gh api -X POST repos/<r>/rulesets --input -`:

- Rama por defecto: destino `~DEFAULT_BRANCH`, reglas `deletion` y `non_fast_forward`.
- Etiquetas: destino `refs/tags/v*`, reglas `deletion`, `non_fast_forward` y `update`.

## Pasos que son del usuario

Claude no puede hacerlos.

1. Crear la clave con `tools/crear_clave.ps1` y guardar una copia fuera del PC.
2. Crear el repo en la organización, o dejar que lo cree `subir-secretos.ps1`.
3. Ejecutar `powershell -ExecutionPolicy Bypass -File tools/subir-secretos.ps1 -KeyDir … -Repo …`.
4. Decir «publica la estable» cuando la beta esté bien.
5. En cada móvil: la primera instalación desde Releases y el permiso de «instalar apps desconocidas».

## Cómo se hace el corte limpio

Lo que hizo Tiempo al pasar del repo privado al público:

1. Repaso de lo que se publica: búsquedas de nombre, correo, rutas del PC y claves, y cada imagen mirada.
2. El `.git` privado se aparta a `../<App>-privado.git`.
3. `git init -b main` nuevo, con identidad local «Trastea Labs <hola@trastealabs.com>».
4. Un único commit: «<App> X.Y.Z: primera versión pública».
5. Se comprueba que el árbol es idéntico: `git write-tree` igual al `HEAD^{tree}` del privado.
6. Las etiquetas `antes-*` se quedan en el privado.
7. Al final, se archiva el repo privado.

## Trampas encontradas

1. En Windows, `mv .git` da «Permission denied» (algo vigila la carpeta `.git`). `Move-Item` de PowerShell mueve el contenido pero deja `.git` vacía y da error: comprobar la copia con `git --git-dir=<copia> fsck` y hacer `git init` sobre la vacía.
2. Los runners de GitHub van en UTC y en inglés: un test que dependía de la zona del aparato pasaba en el PC y fallaba en GitHub. Por eso `app/build.gradle.kts` fija `user.timezone=UTC` y el idioma en los tests: así falla también en el PC.
3. Desde Windows, `gradlew` y los `.sh` entran sin permiso de ejecución y en GitHub dan «Permission denied»: `git update-index --chmod=+x gradlew tools/*.sh`.
4. Con `minSdk` 24 o más no hay firma v1 y `apksigner` escribe «V2 Signer: certificate SHA-256 digest: …», no «Signer #1 …»: `beta.yml` acepta las dos.
5. Las etiquetas que crea el flujo con `GITHUB_TOKEN` no disparan otros flujos: por eso la estable la dispara la etiqueta que sube `tools/publicar.sh` con la sesión del usuario.
6. `versionCode` = número de commits: hace falta `fetch-depth: 0` en el `checkout`; en el repo nuevo vuelve a empezar desde 1.
7. `subir-secretos.ps1`, antes de guardarse con BOM, dejó la descripción del repo con acentos rotos en PowerShell 5.1; se corrigió con `gh api -X PATCH`.
8. La organización se llama `TrasteaLabs` en GitHub: con `trastealabs` en minúsculas funciona todo, pero `git push` avisa de «This repository moved»; el remoto se deja con el nombre exacto.
9. La app enseña las notas de la release tal cual, sin Markdown: nada de comillas invertidas ni tablas en las notas.
10. Al subir una etiqueta, GitHub ejecuta el `estable.yml` del commit etiquetado (el de la beta), no el de la punta de `main`: un cambio solo en `estable.yml` no se usa hasta la siguiente beta.
11. Cada cambio de la app sale como beta, así que la estable puede quedar fuera de la primera página de releases: el actualizador pide 100 y, si no ve ninguna estable, pregunta también por `releases/latest`.
12. Con la estable de una versión publicada, `beta.yml` falla hasta que se sube `versionName` (aviso claro en el registro de Actions).
