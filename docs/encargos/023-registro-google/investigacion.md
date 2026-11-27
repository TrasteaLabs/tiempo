# 023 · Play Protect y verificación de desarrollador — investigación (27-11-2026)

Leído hoy en fuentes de Google. «Comprobado» = lo dice una página oficial; «sin comprobar» = no lo dice ninguna.

## Comprobado
- **Qué es:** la verificación de desarrollador de Android liga cada paquete y su clave de firma a una persona u organización identificada. Para quien reparte solo fuera de Play: Android Developer Console (ADC), https://android.google.com/developerconsole. Con cuenta de Play Console se hace desde ahí, también para apps de fuera de Play. [guía](https://developer.android.com/developer-verification/guides)
- **Calendario:** 30-09-2026 solo Brasil, Indonesia, Singapur y Tailandia y solo instalaciones desde tiendas participantes (Play, OPPO App Market, Galaxy Store, etc.). La instalación directa (APK de una web) **no está afectada todavía**. Global «en 2027», sin mes. [FAQ](https://developer.android.com/developer-verification/guides/faq), act. 15-07-2026 y 10-11-2026.
- **Después del 2027 sin registrar:** solo se instala o actualiza con ADB o con el «flujo avanzado» (modo desarrollador, reinicio, espera de 24 h, una vez por móvil) y aun así sale aviso con «Instalar de todas formas».
- **Cuenta completa («full distribution»):** 25 USD, pago único. Persona: documento oficial con foto + justificante de domicilio; correo y teléfono verificados por código, «not shown publicly». Unos 10 minutos con todo a mano. La identidad se verifica con un perfil de pagos de Google. [full distribution](https://developer.android.com/developer-verification/guides/full-distribution), [ayuda ADC](https://support.google.com/android-developer-console/answer/16641416)
- **Organización:** además D-U-N-S (gratis, hasta 28 días), web verificada en Search Console y documento oficial de la organización cuyo nombre coincida con D&B. Trastea Labs no es una entidad legal: hoy no puede.
- **Cuenta limitada:** gratis y sin DNI, máximo 20 aparatos por invitación. Se puede pasar a completa, no al revés. No vale para una app pública.
- **Registrar el paquete:** nombre de paquete + huella SHA-256 del certificado. Si el paquete ya tiene instalaciones conocidas, prueba de propiedad: fichero `adi-registration.properties` (texto que da la consola) en `assets/` de un APK firmado con la clave, y se sube ese APK. Se avisa por correo; estado «Registered». Hay API para automatizarlo. [ADC](https://developer.android.com/developer-verification/guides/android-developer-console), [open source](https://developer.android.com/developer-verification/guides/open-source-app-registration)
- **Apps de código abierto:** no hay vía gratuita ni exención; mismo registro.
- **Apelación de Play Protect:** https://support.google.com/googleplay/android-developer/contact/protectappeals. Campos: correo, nombre de desarrollador (opcional), paquete, SHA-256 del APK subido a VirusTotal, explicación. «Appeal decisions are final… you will not receive a response». Pensado para clasificaciones erróneas de app dañina; para el aviso «This app is unknown to Play Protect» la guía dice «Appeals are not relevant and won't remove this message». [guía de avisos](https://developers.google.com/android/play-protect/warning-dev-guidance)

## Sin comprobar (ninguna fuente oficial lo dice)
- **Que registrarse quite el aviso de hoy** («Play Protect no conoce ninguna otra aplicación de este desarrollador»). La guía de avisos de Play Protect no lista ese texto ni nombra la verificación; la de verificación no nombra Play Protect y promete solo que la experiencia del usuario «no cambia respecto a la de hoy». Son dos sistemas distintos. Solo se sabrá probando en el OPPO tras registrar.
- Qué datos de una persona ve el público en la ADC (nombre legal, país). En Play Console sí está escrito: nombre legal, país y correo; dirección completa si monetiza o se declara «trader».
- Plazo de revisión de identidad y de paquete.
- Mes de la obligatoriedad global de 2027.
- Si una cuenta ADC se convierte luego en Play Console sin pagar otros 25 USD.
- Si una persona puede poner «Trastea Labs» como nombre visible (la API tiene `displayName`).

## Terceros (no oficial)
- Blogs y foros: el aviso de «desarrollador desconocido» es de reputación (app/clave poco vista), no de malware; varios proyectos abiertos lo sufren igual en 2026 (issues de GitHub Castlegame #296, OpenAmpere #608).
