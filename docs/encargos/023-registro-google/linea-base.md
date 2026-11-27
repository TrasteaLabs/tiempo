# 023 · Línea base del aviso de Play Protect

## En el emulador: no se puede (27-11-2026)
Los cuatro emuladores de este PC (`BalooMovil`, `BalooTVPublica`, `BalooTablet`, `PersonalOS`) usan la imagen `android-36/google_apis/x86_64`: Google APIs **sin Play Store** (`PlayStore.enabled=no`). Sin Play Store no hay Play Protect, así que una instalación ahí no enseña el aviso y no sirve de comparación.

Para tenerlo haría falta bajar la imagen `google_apis_playstore` (~1,5 GB) y entrar en el emulador con una cuenta de Google, porque Play Protect solo escanea con una cuenta puesta. Entrar con una cuenta es cosa de quien mantiene, no de Claude, y un emulador con cuenta nueva no se parece a un móvil de verdad en lo que mira Play Protect (reputación del aparato y de la cuenta). Decisión: **la comparación antes/después se hace solo en el OPPO**, como prevé el plan.

## En el OPPO: lo visto el 27-11-2026 (encargo 022)
Actualizando Tiempo de la 1.0.0 a la 1.0.1 beta 10 desde el actualizador de la app (el mismo instalador de Android que al abrir un APK desde Archivos):

1. ColorOS: «Instalar aplicaciones desconocidas › Autorizar de esta fuente».
2. Android: «¿Quieres actualizar esta aplicación?».
3. **Google Play Protect:** «Aplicación bloqueada para proteger tu dispositivo» · «Play Protect no conoce ninguna otra aplicación de este desarrollador». Solo sigue con «Más detalles › Instalar de todas formas».

No hay captura de ese momento (la pantalla la tenía Álvaro). La primera instalación desde el navegador dio el mismo aviso.

## Cómo repetirlo después del registro
En el OPPO, con Play Protect activo (Play Store › foto de perfil › Play Protect › Ajustes › «Analizar apps con Play Protect» encendido):

1. Esperar a que GitHub publique una beta nueva (cualquier cambio de la app en `main`) o usar la que traiga el fichero de la prueba de propiedad.
2. Tiempo › Ajustes › «Buscar ahora» › «Instalar».
3. Apuntar cada pantalla que sale, con el texto exacto, y hacer captura **solo** de la de Play Protect (sin la barra de notificaciones).
4. Si el aviso sigue, repetir con la siguiente beta a los pocos días: Play Protect puede tardar en enterarse del registro.

Prueba que **no vale**: `adb install`, que se salta Play Protect.
