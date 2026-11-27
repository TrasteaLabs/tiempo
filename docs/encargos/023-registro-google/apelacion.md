# 023 · Apelación de Play Protect (preparada, no enviada)

## Cuándo se usa
Solo si el paquete `com.trastealabs.tiempo` ya está **«Registrado»** y Play Protect **sigue bloqueando** la instalación. Antes no.

Google avisa de dos cosas (ver `investigacion.md`):
- No responde: «you will not receive a response».
- Para el aviso de app «desconocida», la apelación no es lo que lo quita: «Appeals are not relevant and won't remove this message».

Así que es un intento con pocas garantías. Lo envía quien mantiene.

## El formulario
https://support.google.com/googleplay/android-developer/contact/protectappeals

| Campo | Qué poner |
|---|---|
| Correo | hola@trastealabs.com |
| Nombre de desarrollador | Trastea Labs |
| Nombre del paquete | com.trastealabs.tiempo |
| SHA-256 del APK | El hash **del fichero APK** (no la huella del certificado de `firma.sha256`) |
| Explicación | El texto en inglés de más abajo |

## Sacar el SHA-256 del APK
Usa el APK exacto que bloquea Play Protect (el de la última release).
1. **En GitHub:** https://github.com/trastealabs/tiempo/releases. `beta.yml` solo sube el APK (`gh release create`) y no escribe el hash en las notas, pero GitHub calcula el SHA-256 de cada fichero adjunto y lo enseña junto a él, como `sha256:…`.
2. **En PowerShell, con el APK bajado:**
   `Get-FileHash .\tiempo-X.Y.Z-N.apk -Algorithm SHA256`
   (cambia `X.Y.Z-N` por la versión; el hash sale en la columna `Hash`).

Los dos tienen que coincidir. Antes de enviar, **sube ese mismo APK a https://www.virustotal.com** (lo hace quien mantiene) y comprueba que el hash que enseña VirusTotal es el mismo.

## Texto para el campo de explicación (inglés, para pegar)

```
Tiempo is a free, open-source weather app for Android (GPL-3.0). Source code: https://github.com/trastealabs/tiempo
It has no accounts, no analytics, no ads and no tracking. It is distributed only through GitHub Releases and updates itself from there.
Every release is signed with the same certificate. SHA-256 certificate fingerprint: 9C:91:D3:97:B5:25:34:CB:1D:A2:30:75:CB:E7:8D:78:AB:05:DF:5F:A1:BE:89:61:1A:B4:A5:2F:BD:46:A0:E1
The developer (Trastea Labs) is registered in Android developer verification, and the package com.trastealabs.tiempo is registered with that key.
Permissions and why:
- INTERNET: download the forecast (Open-Meteo) and check GitHub for new versions.
- ACCESS_COARSE_LOCATION / ACCESS_FINE_LOCATION: show the weather where the user is; coordinates are rounded to 2 decimals.
- ACCESS_BACKGROUND_LOCATION: keep the widget and "where I am" up to date without opening the app.
- RECEIVE_BOOT_COMPLETED: reschedule refreshes after a reboot.
- POST_NOTIFICATIONS: optional morning weather notification.
- USE_EXACT_ALARM: deliver that notification on time.
- REQUEST_INSTALL_PACKAGES: install a new version only when the user asks for it.
Privacy details: https://github.com/trastealabs/tiempo/blob/main/PRIVACY.md
This looks like a false positive caused by a new, little-seen developer and key. Please review and remove the block.
```

(Va por debajo de los 1500 caracteres. Si el formulario cuenta distinto, quita la última frase.)

Nota: la librería de trabajos en segundo plano (WorkManager) añade al manifiesto final estado de la red, mantener el móvil despierto y servicio en primer plano (lo dice `PRIVACY.md`); no están en nuestro `AndroidManifest.xml`, por eso no van en la lista.
