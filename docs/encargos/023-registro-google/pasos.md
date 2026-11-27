# 023 · Registrar Tiempo en Google: los pasos de quien mantiene

Releído en las páginas de Google el 27-11-2026. Las pantallas cambian: si algo no coincide, para y pregunta (pega el texto, sin datos personales).

**Tiempo total:** unos 20 minutos con todo a mano, más lo que tarde Google en revisar (no dice cuánto; pueden ser días).

## Antes de empezar, ten a mano
- El **DNI o el pasaporte** (foto de las dos caras si es el DNI).
- Un **justificante de domicilio** reciente a tu nombre: recibo de luz, agua, gas o internet, o un extracto del banco o de la tarjeta.
- Una **tarjeta** de crédito o débito a tu nombre (no prepago) para los **25 USD**, un solo pago.
- El **móvil** para los códigos por SMS y, quizá, para instalar la app «Google Play Console».
- La **cuenta de Google del estudio** abierta en el navegador (no tu cuenta personal).

## 1. Crear la cuenta de Play Console
1. Entra en https://play.google.com/console/signup con la cuenta del estudio.
2. Tipo de cuenta: **«Personal»** (o «Yourself / Individual»). No «Organización»: Trastea Labs no es una empresa y Google pediría un D-U-N-S y documentos que no hay.
3. Lee y acepta el «Acuerdo de distribución para desarrolladores» si estás de acuerdo (eso es tuyo).

## 2. El perfil de pagos — PARA y lee
La consola te pide un **perfil de pagos de Google**. Es lo que representa tu identidad real.
- Tiene que ser de **tipo individual** y con **tu nombre legal y tu dirección reales**, los mismos del DNI y del justificante.
- **La cuenta queda unida a ese perfil para siempre.** Si eliges uno de empresa o con otro nombre, no se arregla después. Si la pantalla ofrece uno que ya existe y no sabes de dónde salió, crea uno nuevo.
- Si dudas aquí, para y pregunta antes de pagar.

## 3. Los datos de la cuenta
| Campo | Qué poner | ¿Se ve en público? |
|---|---|---|
| Nombre de desarrollador | **Trastea Labs** (sin comprobar que la consola lo acepte en una cuenta personal: si no lo deja, para y pregunta) | Sí |
| Nombre legal y dirección | Los tuyos (salen del perfil de pagos) | En Play Store: nombre legal y país. La dirección completa, solo si cobras por algo o te declaras «trader» |
| Correo y teléfono de contacto | Los tuyos, privados | No: «Google no los muestra». El teléfono sí, si te declaras «trader» (punto 4) |
| Correo de desarrollador | **hola@trastealabs.com** | Sí |

Los tres (correo de contacto, teléfono y correo de desarrollador) se verifican con un **código** que llega por correo o SMS, y tienen que seguir funcionando mientras exista la cuenta.

Lo de «se ve en público» es para cuando haya una app **publicada en Play Store** (encargo 024). Por registrar un paquete que se reparte fuera de Play, Google no dice qué enseña. **Si alguna pantalla avisa de que algo tuyo será público, para y cópialo antes de confirmar.**

## 4. ¿Eres «trader» (comerciante)? — es tu declaración
La consola pregunta si actúas como comerciante en la UE. Es una declaración legal tuya; aquí solo va lo que dice Google en su página de «trader» (leída el 27-11-2026):
- **«Trader»** es quien actúa «con fines relacionados con su comercio, negocio, oficio o profesión». Google da como indicios ganar dinero con las apps (pago, compras dentro, anuncios), mucho volumen, llevarlo como negocio o tener un D-U-N-S.
- **Si dices que sí:** desde el 1-3-2027 se enseñan tus datos de contacto en todo el mundo y, en cuentas personales, **tu teléfono y tu dirección**.
- **Si dices que no:** Google avisa a los usuarios de la UE y el Reino Unido de que las leyes de consumo no se aplican a lo que hagan contigo. Declararse «no trader» siendo trader está prohibido y puede costar la cuenta.
- Se puede **cambiar después** en la consola si cambia lo que haces.
- Hoy Tiempo es gratis, sin anuncios ni donaciones. Si dudas, gestor.

## 5. Identificarte y pagar
1. Sube la foto del documento y el justificante cuando lo pida.
2. Paga los 25 USD con la tarjeta. Si los datos no son válidos, **Google no devuelve el pago**: revisa que el nombre coincide en todo.
3. Puede pedirte **verificar un móvil Android** con la app «Google Play Console» (https://play.google.com/store/apps/details?id=com.google.android.apps.playconsole). Hazlo con tu OPPO.
4. Espera el correo de que la identidad está verificada. Google no da plazo.

## 6. Registrar el paquete de Tiempo
Con la identidad verificada:
1. En el menú de la izquierda, **«Verificación de desarrolladores de Android»** («Android developer verification») › pestaña **«Nombres de paquete»** › botón **«Registrar nombre de paquete»**.
2. Nombre del paquete: `com.trastealabs.tiempo`
3. Clave: elige o pega esta huella SHA-256 (es pública, está en `firma.sha256` del repo):
   `9C:91:D3:97:B5:25:34:CB:1D:A2:30:75:CB:E7:8D:78:AB:05:DF:5F:A1:BE:89:61:1A:B4:A5:2F:BD:46:A0:E1`
   Si sale en la lista de «claves aptas», selecciónala y pulsa **«Añadir clave»**. Si en la lista hay otra huella distinta o te avisa de que el paquete es de otro, **para y pregunta**: no elijas otra.
4. Si después pone **«Firmar y subir un APK»** (prueba de propiedad):
   - Copia el texto que te da y **pégalo en el chat con Claude**. No es secreto (viajará dentro de un APK público).
   - Claude lo pone en la app, sale una beta nueva en GitHub en unos minutos y te dice cuál es.
   - Baja ese APK de https://github.com/trastealabs/tiempo/releases y súbelo en esa misma pantalla. Solo sirve para demostrar que la clave es tuya; no se publica en Play.
5. El estado pasa a **«En revisión»** y luego a **«Registrado»**, con un correo. Avisa a Claude cuando lo veas.

## Dónde parar y preguntar (resumen)
- El perfil de pagos no es individual o no está a tu nombre.
- Una pantalla dice que algo tuyo (nombre legal, dirección, teléfono) se hará público y no lo esperabas.
- La pregunta de «trader», si dudas.
- La huella de la lista no es la de arriba, o dice que el paquete es de otro.
- Te pide algo que no está aquí (otro documento, otra verificación).

**No hagas capturas de la consola para el repo:** salen tu nombre y tus datos.

## Después
Con el paquete en «Registrado», en el OPPO aceptas la siguiente actualización (con «Recibir versiones beta» encendido) y cuentas qué sale, siguiendo `linea-base.md`; en el emulador no se puede (no tiene Play Store). Si Play Protect sigue bloqueando, está preparada la apelación (`apelacion.md`).

Fuentes: [Play Console, crear cuenta](https://support.google.com/googleplay/android-developer/answer/6112435) · [datos de la cuenta y qué se ve](https://support.google.com/googleplay/android-developer/answer/13628312) · [declaración de «trader»](https://support.google.com/googleplay/android-developer/answer/17594752) · [verificación en Play Console](https://developer.android.com/developer-verification/guides/google-play-console) · [guía en PDF de Google, marzo de 2026](https://developer.android.com/developer-verification/guides/pdf-guides/pdc-guide.pdf) · [cuenta completa: documentos](https://developer.android.com/developer-verification/guides/full-distribution)
