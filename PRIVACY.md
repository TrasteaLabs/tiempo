# Privacidad de Tiempo

Última actualización: 26 de noviembre de 2026.

**Trastea Labs no recoge nada.** Tiempo no tiene cuentas, ni analíticas, ni anuncios, ni rastreo. El estudio no tiene ningún servidor al que la app envíe datos tuyos.

## A dónde se conecta la app y qué ve cada uno

- **Open-Meteo** (previsión, aire y polen): recibe tu ubicación redondeada a 2 decimales (alrededor de un kilómetro) para dar el tiempo de la zona. Como cualquier web, ve también la dirección IP de tu conexión. No recibe tu nombre, el nombre del sitio ni nada del aparato.
- **AEMET** (avisos): la app descarga los avisos enteros y los filtra en el móvil. AEMET no recibe nada tuyo, más allá de lo que ve cualquier web (la IP de la conexión).
- **GitHub** (versiones nuevas): solo cuando la app busca versión nueva, como mucho una vez al día al abrirla o cuando pulsas «Buscar ahora», y cuando bajas una versión nueva. GitHub ve la IP de la conexión y que se pide el listado de versiones; no recibe tu ubicación.
- **Tu proveedor de IA** (solo si pones una clave): recibe cifras y horas de la previsión, sin ubicación ni nombre del sitio, y la pregunta tal cual la escribes. Su trato de esos datos depende de las condiciones de ese proveedor, que son cosa tuya con él. Sin clave, no se conecta a ninguno.

Todas las llamadas llevan un User-Agent `Tiempo/<versión>` (con la dirección del proyecto) y no incluyen datos del aparato.

Los nombres de los sitios se calculan en el móvil, con una lista de lugares (GeoNames) que va dentro de la app.

## Permisos

- **Internet:** descargar el tiempo, los avisos y las versiones nuevas.
- **Ubicación aproximada y precisa:** saber dónde estás para dar el tiempo de la zona.
- **Ubicación en segundo plano:** mantener al día el widget y «donde estoy» sin tener la app abierta.
- **Recibir el aviso de arranque del móvil:** volver a programar las actualizaciones tras reiniciar.
- **Notificaciones:** el aviso de la mañana, si lo activas.
- **Alarmas exactas:** que el aviso de la mañana llegue a su hora.
- **Instalar paquetes:** instalar la versión nueva de Tiempo cuando tú lo pides.
- **Estado de la red, mantener el móvil despierto y servicio en primer plano:** los pide la librería de trabajos en segundo plano de Android (WorkManager) para refrescar cuando hay conexión y terminar un refresco empezado.

## Qué se guarda en el móvil

Solo en tu móvil, nada más:

- Por cada sitio (donde estoy y los guardados): la última previsión, los avisos, la fiabilidad, el aire y el polen, y el parte escrito por la IA.
- El último sitio de «donde estoy» y tu lista de sitios (nombre, provincia, país y coordenadas).
- Cuántas llamadas se han hecho a Open-Meteo y a la IA (hoy y este mes) y cuántas preguntas hoy.
- La última temperatura vista y tus preferencias.
- Una copia de los últimos avisos de AEMET descargados.
- Un registro de los refrescos de los últimos 7 días, sin ubicación ni nombre del sitio.
- Tus claves de IA, cifradas, y el proveedor elegido.
- La hora del aviso de la mañana y el día del último aviso.
- Del actualizador: la última comprobación y si falló, el interruptor de versiones beta, la versión que descartaste, la última versión encontrada y el fichero bajado (en caché; se borra al instalar).

Las preguntas a la IA y sus respuestas no se guardan.

## Cómo borrarlo

Ajustes de Android › Apps › Tiempo › Almacenamiento › Borrar datos, o desinstalar la app. No queda nada fuera de tu móvil que borrar, salvo lo que tu proveedor de IA conserve según sus condiciones.

## Contacto

Preguntas o dudas sobre privacidad: abre un issue en https://github.com/trastealabs/tiempo/issues o escribe a hola@trastealabs.com. Para un fallo de seguridad, mira [SECURITY.md](SECURITY.md).
