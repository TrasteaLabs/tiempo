# 023 · Registro en Google (Play Protect)

Aprobado: 2026-11-27 · Uso semanal al empezar: 52 %

## Qué se quiere y por qué
Al instalar o actualizar Tiempo con Play Protect activo, Google la bloquea («Aplicación bloqueada para proteger tu dispositivo · Play Protect no conoce ninguna otra aplicación de este desarrollador») y solo se sigue con «Más detalles › Instalar de todas formas». Le pasa a cualquiera, en la primera instalación y en cada actualización (visto en el OPPO el 27-11-2026, encargo 022).

Se quiere que Google conozca a quien hace Tiempo: una cuenta de desarrollador verificada y el paquete `com.trastealabs.tiempo` registrado con su clave de firma. Esto es obligatorio de todos modos: en 2027 (Google no ha dado mes) un móvil certificado no instalará ni actualizará una app sin registrar, salvo con un rodeo de 24 horas por móvil.

**No está comprobado que el registro quite el aviso de hoy.** Ninguna página de Google lo dice: Play Protect y la verificación de desarrollador son dos sistemas. Por eso el encargo termina con una prueba: instalar antes y después y contar lo que se ve. Si el aviso sigue, se envía la apelación de Play Protect y el README se queda como está.

Decidido con quien mantiene (27-11-2026): se registra como persona, a su nombre (Trastea Labs no es una entidad legal y no puede ser organización); en **Play Console**, porque el mismo pago de 25 USD sirve después para Play Store; nombre visible «Trastea Labs». Publicar en Play Store es el encargo 024, no este.

## Qué no entra
- Publicar en Play Store (024): prueba cerrada con 12 personas 14 días, versión sin actualizador, justificación de permisos, ficha.
- Las demás apps del estudio: se registran cuando se publiquen, con la sección nueva de la plantilla.
- Automatizar el registro con la API de Google.
- Cambiar la firma, el paquete o el actualizador.

## Cómo se hace
Lo leído el 27-11-2026 en fuentes de Google (con enlaces, y qué está comprobado y qué no) está en `investigacion.md`, junto a este plan.

**Lo que hace quien mantiene, no Claude:** crear la cuenta, aceptar condiciones, identificarse, pagar, declarar si es «trader», rellenar y enviar formularios y subir ficheros a Google o a VirusTotal. Claude prepara los pasos, los textos y los ficheros, y puede leer lo que la consola enseña si se le pega o se le enseña.

**Cómo es el registro (Play Console › «Verificación de desarrolladores de Android» › «Nombres de paquete» › «Registrar nombre de paquete»):**
1. Nombre del paquete: `com.trastealabs.tiempo`.
2. Clave: la huella SHA-256 del certificado, que es pública y está en `firma.sha256`.
3. Si Google ya conoce instalaciones del paquete, pide prueba de propiedad: la consola da un texto que va en `app/src/main/assets/adi-registration.properties`, se publica un APK firmado con la clave de siempre y se sube ese APK a la consola. Con menos de 50 instalaciones conocidas puede que no lo pida. El texto no es secreto (viaja dentro de un APK público).
4. Estado «Registrado» y aviso por correo. Google no dice cuánto tarda.

El APK firmado solo lo produce GitHub (`beta.yml`): el fichero de la prueba de propiedad entra por un commit normal a `main`, sale una beta y esa beta es la que se sube. No se compila una release aquí ni se toca `~/.weatherapp/`.

**Alternativa descartada:** Android Developer Console (solo fuera de Play). Mismo precio y sin ficha pública, pero no está escrito que la cuenta sirva luego para Play Store. La cuenta «limitada» (gratis, sin DNI) es para 20 aparatos por invitación: no vale para una app pública.

**La apelación de Play Protect** (https://support.google.com/googleplay/android-developer/contact/protectappeals) pide correo, nombre de desarrollador, paquete, el SHA-256 de un APK subido a VirusTotal y una explicación. Google avisa de que no responde y de que para apps «desconocidas» no quita el mensaje. Se deja preparada y solo se envía si, registrada la app, el aviso sigue.

## Bloques
Construcción: Opus, esfuerzo medio. Una sesión. Es corta: casi todo lo demás depende de pasos de quien mantiene y se hace después con él, como retoques.

1. **Línea base del aviso** · normal · En un emulador con Google Play y Play Protect activo, instalar la última APK de Releases **como lo haría un usuario** (bajada con el navegador y abierta desde Archivos, no con `adb install`, que se salta Play Protect). Apuntar el texto exacto que sale y una captura. Si en el emulador no sale el aviso, decirlo: la comparación se hará solo en el OPPO.
2. **Los pasos de quien mantiene** · normal · `docs/encargos/023-registro-google/pasos.md`: una página, paso a paso y con enlaces, en lenguaje llano. Cuenta de Play Console personal con la cuenta de Google del estudio; nombre de desarrollador «Trastea Labs»; correo de desarrollador `hola@trastealabs.com`; qué documentos tener a mano; el registro del paquete con la huella copiada de `firma.sha256`; y **dónde parar y preguntar** (ver «Trampas»). Antes de escribirlo, releer las páginas de ayuda de Google de ese día: las pantallas cambian.
3. **Apelación preparada y plantilla** · rutinario · `apelacion.md` en la carpeta del encargo, con el texto en inglés para el formulario (app de código abierto, repo, licencia, qué permisos usa y por qué, huella de la firma) y los pasos para sacar el SHA-256 del APK. Añadir a `docs/plantilla-publicacion.md` la sección «Registro en Google» para las demás apps.
**Después, con quien mantiene (retoques, fuera de la sesión de construcción):**
- Él sigue `pasos.md`. Si la consola pide prueba de propiedad, pega el texto en el chat; se añade `adi-registration.properties`, `check`, commit a `main`, sale la beta y él sube ese APK.
- Con el paquete en «Registrado»: repetir el bloque 1 en el emulador y, en el OPPO, aceptar la siguiente actualización. Si no salta a la primera, repetir a los pocos días antes de dar el resultado por malo.
- Según lo visto: si el aviso desaparece, quitar del README el paso de «Instalar de todas formas»; si sigue, él envía la apelación y el README no cambia. En los dos casos, `entrega.md` cuenta lo que se vio, con fecha, y `ESTADO.md` y la trampa de Play Protect de `CLAUDE.local.md` se actualizan.

## Cómo se comprueba
- `check` en verde (solo cambia código si hace falta el fichero de la prueba de propiedad).
- Sin tests nuevos: no hay lógica nueva.
- En el emulador y en el OPPO: el mismo recorrido de instalación antes y después del registro, con el texto exacto del aviso (o su ausencia) y captura. La entrega dice «comprobado» solo de lo que se vio; lo demás, «sin comprobar».
- El paquete aparece como «Registrado» en la consola (lo confirma quien mantiene).

## Ayudas externas
Ninguna.

## Trampas
- **El repo es público.** Nada personal en `docs/`, commits ni capturas: ni nombre legal, ni dirección, ni teléfono, ni documentos, ni la cuenta de Google del estudio, ni pantallas de la consola con esos datos.
- **Perfil de pagos:** la cuenta queda unida para siempre al perfil de pagos elegido al crearla. Tiene que ser de tipo individual y a su nombre real. `pasos.md` lo dice antes del pago.
- **«Trader» (UE):** la consola pregunta si actúa como comerciante. Si responde que sí, su teléfono y su dirección pasan a ser públicos. Es una declaración legal suya: `pasos.md` explica qué significa y qué se publica en cada caso, sin recomendar; si duda, gestor. Hoy la app es gratis y no hay donaciones.
- **Qué se ve de él:** en Play Store, nombre legal, país y correo de desarrollador, cuando haya una app publicada (024). No está escrito qué se enseña por un paquete registrado y repartido fuera de Play: parar y mirar lo que diga la consola antes de confirmar.
- **Puede pedir más:** verificar un móvil Android con la app de Play Console, o esperar días a la revisión de identidad. No se promete plazo.
- **`adb install` no pasa por Play Protect:** una prueba así no demuestra nada.
- El fichero de la prueba de propiedad es un cambio de la app: dispara beta. Hoy no hay estable de la 1.0.1, así que `beta.yml` no pide subir versión.
- No abrir la app antigua del OPPO ni instalar encima de la nueva nada compilado aquí (`CLAUDE.local.md`).
- Sin comprobar y que puede cambiar el plan: que el registro quite el aviso; plazo de las revisiones; mes de la obligatoriedad de 2027.

## Del usuario
- Antes de empezar: DNI o pasaporte, un justificante de domicilio reciente (recibo o extracto) y una tarjeta para los 25 USD.
- Crear la cuenta, identificarse, pagar y registrar el paquete, siguiendo `pasos.md`.
- Si la consola lo pide: pegar el texto de la prueba de propiedad y subir el APK de la beta.
- Aceptar una actualización en el OPPO y decir qué sale.
- Si el aviso sigue: subir el APK a VirusTotal y enviar la apelación.
