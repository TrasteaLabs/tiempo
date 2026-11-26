# Diseño de El Tiempo

Cristal aplicado a esta app. El manual completo es `estilo/cristal.md` del plugin Becario (versión 1.6; lo nuevo de la 1.6, el movimiento de las webs, no toca Android); aquí va solo lo que decide cómo se ve y se mueve **esta** app. Lo leen la construcción y, si se usa, Impeccable (este fichero manda sobre su gusto). La referencia es la app en marcha, no las maquetas antiguas.

## Naturaleza
- Qué es: app del tiempo para Android, probada sobre todo en ColorOS (OPPO Find X8 Pro); se mira varias veces al día, de un vistazo, y sobre todo en el widget.
- Escena: el cielo según la hora y el tiempo, con 14 fotos (009) elegidas por `ui/scene/` y la luna real. Quieta: solo cambia con el dato, con fundido. En Ajustes y Sitios, la misma foto desenfocada y velada (`SecondaryBackdrop.kt`).
- Color: azul de cielo y ámbar (`OnSky.Temperature` `#FFD27A`); lluvia en azul claro en las barras (`OnSky.Rain`); sus cifras, en blanco sobre la escena (019). La interfaz es blanca con opacidad sobre la escena; sin color dinámico de Android.
- Protagonista: la temperatura de ahora (112 sp, `AppFigures.mainTemp`) con su referencia («2° más que ayer»). Momento especial: el astro sube detrás del panel y la temperatura viaja desde la última vista (`TravelingNumber.kt`).

## Letra, material y forma
- Google Sans Flex en cinco pesos; la escala de M3 en `ui/theme/Type.kt` (`CristalType`) más las cifras propias de `AppFigures`. Etiquetas de datos a 12 sp como mínimo; 11 (`labelSmall`) solo en ejes.
- Niveles de texto (019), medidos con `tools/medir_niveles.py` a **4,5:1 sobre el punto más claro** de las 14 fotos con su velo y vigilados por `TextLevelsTest` (ningún alfa de texto escrito a mano fuera de `Type.kt`):
  - Principal y franja del día (`OnSky`): principal, secundario y terciario al **100 %**. La cabecera pide 0,97 y las tarjetas 0,98: no hay sitio para el gris, la jerarquía sale del tamaño y el peso.
  - Ajustes y Sitios (`OnScreen`): principal 100 %, secundario, terciario y ayuda de los campos al **85 %** (el peor punto, una postal, pide 0,81).
  - Hojas, ventana del día y diálogo (`OnSheet`): principal 100 %, secundario **78 %**, terciario y ayuda **62 %** (5,4:1 sobre el panel más claro de la hoja).
  - Lo atenuado (horas pasadas, modelos que se apartan, «nada» del polen) apaga el dibujo (barras, línea, iconos, fondo), no el texto.
  - Texto de color sobre la escena: no llega (el azul claro de la lluvia daba 3,6:1 en las tarjetas de día). Las cifras de lluvia van en blanco y el azul lo llevan las barras.
  - La franja de la foto del día lleva un oscurecido extra por foto (`DAY_BAND_SHADE`) en las siete de día que no llegaban ni con el texto al 100 %.
- Cristal (desenfoque) solo en la barra superior de Ajustes y Sitios (`glass/GlassTopBar.kt`). Con ahorro de batería, esa barra es opaca (`CristalColors.SheetSurface`). Tarjetas, chips y campos: `Modifier.translucent` sin desenfoque, con el tono de cada foto (`GlassTone.forPhoto`). Hojas, ventana del día y diálogos: opacos (`CristalColors.SheetSurface`).
- Radios y espaciado de Cristal (tarjeta 28, pasos de 4, margen lateral 16). Zona táctil de 48 dp en todo lo pulsable.
- Widget: bitmap pintado por `widget/WidgetPainter.kt` con velo medido (`WidgetVeil.kt`, objetivo 5:1 en el percentil 1).

## Movimiento
- El de Material 3 (`MotionScheme.standard()`), sin rebotes. Firma: el astro que sube y la cifra que viaja.
- Pasar de pantalla: hoy con el sistema propio de `ui/motion/ScreenHost.kt`. **Pendiente:** cambiarlo por Navigation 3 (planificado en `ESTADO.md`); hasta entonces no se toca.
- Con «Quitar animaciones» o ahorro de batería (`SkyClock.allowed`): fundidos de 150 ms, sin reloj de cielo, sin intro y sin cuenta de temperatura.

## Componentes y estados
- Principal: cabecera con la cifra, resumen (IA o `Summary.kt`), horas (`HourlyChart.kt`), días (`DailyList.kt`), aire y polen, avisos. Ventana del día como hoja. Pregunta a la IA en su hoja.
- Estados: sin datos, sin permiso, sin conexión (píldora) y aviso de AEMET, en `MessageScreen.kt` o banners.
- Esperas (020), con esqueleto del tamaño de lo que viene y nunca «Cargando…»: la principal (`MainSkeleton.kt`: sitio, cifra, resumen con la frase de qué se hace, 24 horas y 10 días; leyendo lo guardado solo sale pasados 400 ms), la búsqueda de sitios (`PlaceSearchUi.kt`) y la pregunta a la IA (`AskSheet.kt`: indicador, frase y tres barras del tamaño de la respuesta).
- Piezas de M3 en lo que se elige (020): el proveedor de IA es un `SegmentedButton`; la tira de días de la ventana del día, `Tab`. Lo blanco lleva la tinta `onPrimary` del tema (`WhiteInk`) y el interruptor, `cristalSwitchColors()` (`ui/theme/Controls.kt`); `D24GuardTest` vigila que no vuelvan a mano.
- Botones con verbo: el diálogo de la hora dice «Dejar como está» y «Poner hora».
- Lector de pantalla: cada bloque se lee como una frase; la cifra que viaja se anuncia una sola vez con su valor final y su referencia.

## Lo que no hacemos
- Cristal sobre lo que se mueve (`D24GuardTest`), cristal sobre cristal, escena en movimiento continuo.
- Rojo para algo que no sea un aviso. Solo color para decir algo (lluvia, nivel de aire): siempre va con cifra o palabra.
- Filas de valores vacíos repetidos («0 %» hora tras hora): las horas enseñan la probabilidad solo desde el 20 % (`hourRainRow`) y, si no llega ninguna, «Sin lluvia en las próximas 24 h».
- Pantalla de bienvenida con el parte en grande: descartada.

## Excepciones aceptadas
- Tema solo oscuro: la escena siempre se vela, no hay versión clara de la app.
- Sin dinero en la app: la regla de importes de D25 no aplica (el gasto de IA se cuenta en llamadas y tokens).
- Foco con teclado (D25 §12): no aplica en el móvil; los campos de texto sí cuidan la acción del teclado.
- El widget es un bitmap: su texto no sigue la letra grande del sistema y TalkBack lee la descripción del widget entero.
- Cifras fuera de la escala de letra (`AppFigures` en `ui/theme/Type.kt`, decidido el 10/11): la temperatura de ahora a 112 sp, la de «donde estoy» en Sitios a 68 y la del gasto de IA a 64, todas en Light. Es la decisión 9 de Cristal: aquí la cifra es la pantalla.
- Colores de nivel (`ui/AirPollenTiles.kt`), siempre con la palabra del nivel al lado, nunca solos. Aire, escala europea aclarada para el cristal: buena `#6EE7DC`, razonablemente buena `#7FD9A8`, regular `#F2E36A`, desfavorable `#FF7B6E`, muy desfavorable `#E8577F`, extremadamente desfavorable `#B98AF0`. Polen: bajo `#8FDDA0`, moderado `#F5D36B`, alto `#FF8A6B`; «nada» va apagado, sin color.

## Tests de diseño (Cristal §14)
- Voz: `VoiceTest` (nada de «Cargando…», «¡Ups!», «¿Estás seguro?», «pronto» ni el «nosotros» de empresa en `strings.xml`).
- Contraste: `TextLevelsTest`. Lo prohibido: `D24GuardTest`. Zona táctil de 48 dp: en los tests de cada pantalla (`assertTouchAtLeast48`).
