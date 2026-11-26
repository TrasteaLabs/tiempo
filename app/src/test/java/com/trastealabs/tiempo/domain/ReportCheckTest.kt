package com.trastealabs.tiempo.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class ReportCheckTest {
    private val now = madridAt(2026, 11, 12, 8, 15)
    private val zone = ZoneId.of("Europe/Madrid")

    private fun day(
        date: String = "2026-11-12",
        label: String = "hoy",
        minC: Int = 6,
        minAt: String? = "06:00",
        maxC: Int = 19,
        maxAt: String? = "16:00",
        rainMm: Int = 0,
        rain: List<Span> = emptyList(),
        rainAllDay: Boolean = false,
        storm: List<String> = emptyList(),
    ) = DayFacts(date, label, minC, minAt, maxC, maxAt, rainMm, rain, rainAllDay, storm, null, null, null, null)

    private fun basis(
        date: String = "2026-11-12",
        maxC: Int = 20,
        minC: Int = 10,
        wet: Boolean = false,
        rainStart: Int? = null,
        ids: List<String> = emptyList(),
    ) = ReportBasis(date, maxC, minC, wet, rainStart, ids)

    private fun factsOf(today: DayFacts, days: List<DayFacts> = listOf(today), warnings: List<WarningFacts> = emptyList()) =
        ReportFacts(basis(), today, days, warnings)

    private fun reply(today: String?, days: List<Pair<String, String>> = emptyList(), warnings: List<Pair<String, String>> = emptyList()): String {
        fun q(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""
        val t = if (today == null) "" else "\"today\":${q(today)},"
        val d = days.joinToString(",") { "{\"date\":${q(it.first)},\"text\":${q(it.second)}}" }
        val w = warnings.joinToString(",") { "{\"key\":${q(it.first)},\"text\":${q(it.second)}}" }
        return "{$t\"days\":[$d],\"warnings\":[$w]}"
    }

    private fun check(json: String, facts: ReportFacts) = checkReply(json, facts, AiProvider.CLAUDE, now)

    private val warningFacts = WarningFacts(
        key = "a1", level = "naranja", phenomenon = "Tormentas", from = "hoy 15:00", to = "hoy 21:00",
        probability = "40%-70%", description = "Acumulación de precipitación en una hora: 20 mm", id = "id-secreto",
    )

    // ---------- los tres ejemplos de la spec ----------

    @Test fun `ejemplo 1 seco`() {
        val f = factsOf(day())
        val r = check(reply("Hoy entre 6° al amanecer y 19° hacia las 16:00. Sin lluvia."), f)!!
        assertEquals("Hoy entre 6° al amanecer y 19° hacia las 16:00. Sin lluvia.", r.today)
        assertEquals(now, r.generatedAt)
        assertEquals(AiProvider.CLAUDE, r.provider)
        assertEquals(f.basis, r.basis)
    }

    @Test fun `ejemplo 2 lluvia debil con tormenta, con 7 00 por 07 00`() {
        val f = factsOf(day(minC = 9, minAt = "07:00", maxC = 21, rain = listOf(Span("17:00", "21:00", 5)), rainMm = 5, storm = listOf("18:00")))
        val text = "Hoy entre 9° a las 7:00 y 21° hacia las 16:00. Lluvia débil sobre todo de 17:00 a 21:00, con posible tormenta hacia las 18:00."
        assertEquals(text, check(reply(text), f)!!.today)
    }

    @Test fun `ejemplo 3 lluvia todo el dia`() {
        val f = factsOf(day(minC = 8, maxC = 13, rainMm = 22, rain = listOf(Span("11:00", "15:00", 15)), rainAllDay = true))
        val text = "Hoy no pasa de 13°, con mínima de 8° de madrugada. Lluvia todo el día, más fuerte entre las 11:00 y las 15:00; cae bastante, unos 22 mm."
        assertEquals(text, check(reply(text), f)!!.today)
    }

    // ---------- H7 ----------

    @Test fun `una cifra que no existe descarta el hoy`() {
        val r = check(reply("Hoy entre 6° y 25° hacia las 16:00. Sin lluvia.", days = listOf("2026-11-12" to "Máxima de 19°.")), factsOf(day()))!!
        assertNull(r.today)
        assertEquals("Máxima de 19°.", r.days["2026-11-12"])
    }

    // 017 (H10): «7:00», «7.00», «a las 7» y «a las 7h» son la misma hora y se comprueban contra los hechos.
    private fun withMinAt7() = factsOf(day(minC = 9, minAt = "07:00", maxC = 21, maxAt = "16:00"))
    private fun today(text: String, facts: ReportFacts) = check(reply(text), facts)!!.today

    @Test fun `la hora escrita de cualquier forma vale si esta en los hechos`() {
        for (t in listOf("7:00", "07:00", "7h", "7 h", "7.00", "a las 7")) {
            val text = "Hoy mínima de 9° a las $t y máxima de 21°."
            assertEquals(t, text, today(text, withMinAt7()))
        }
    }

    @Test fun `la hora escrita de cualquier forma se rechaza si no esta en los hechos`() {
        val facts = factsOf(day(minC = 9, minAt = "06:00", maxC = 21, maxAt = "16:00"))
        for (t in listOf("7:00", "7h", "7 h", "7.00", "a las 7"))
            assertNull(t, today("Hoy mínima de 9° a las $t y máxima de 21°.", facts))
    }

    @Test fun `7h30 es la hora 7 30 y 7h no es el numero 7`() {
        val facts = factsOf(day(minC = 9, minAt = "07:30", maxC = 21, maxAt = "16:00"))
        assertNotNull(today("Hoy mínima de 9° a las 7h30 y máxima de 21°.", facts))
        // el 7 de «7h» no puede colarse como cifra aunque 7 sea la mínima
        assertNull(today("Hoy mínima de 7° a las 7h y máxima de 21°.", factsOf(day(minC = 7, minAt = "06:00", maxC = 21))))
    }

    // «Nh» y «N» solo son hora con «las» delante; sueltos («3 h de lluvia») son cifras de siempre.
    @Test fun `con las delante cuenta como hora, desde y hasta incluidos`() {
        val facts = withMinAt7()
        for (t in listOf("desde las 7", "hasta las 7.00", "a las 7h", "de las 7 a las 16", "sobre las 7:00")) {
            val text = "Hoy mínima de 9° $t y máxima de 21°.".replace("de las 7 a las 16", "de las 7 a las 16")
            assertNotNull(t, today(text, facts))
        }
        val other = factsOf(day(minC = 9, minAt = "06:00", maxC = 21, maxAt = "16:00"))
        for (t in listOf("desde las 7", "hasta las 7.00", "a las 7h", "sobre las 7:00"))
            assertNull(t, today("Hoy mínima de 9° $t y máxima de 21°.", other))
    }

    @Test fun `sin las delante 3 h, 3 horas y 7h de sol son cifras`() {
        val facts = factsOf(day(minC = 6, maxC = 19, rainMm = 3).copy(rainAllDay = false))
        assertNotNull(today("Hoy entre 6° y 19°, con unas 3 h de lluvia.", facts))
        assertNotNull(today("Hoy entre 6° y 19°, con unas 3 horas de lluvia.", facts))
        assertNotNull(today("Hoy entre 6° y 19°, con 3h de lluvia.", facts))
        // si la cifra no está en los hechos, se rechaza como cifra (no como hora)
        assertNull(today("Hoy entre 6° y 19°, con unas 4 h de lluvia.", facts))
        assertNotNull(today("Hoy entre 6° y 19°, con 6h de sol.", facts))
        assertNull(today("Hoy entre 6° y 19°, con 7h de sol.", facts))
    }

    @Test fun `un decimal con unidad o km por hora no es una hora`() {
        val facts = factsOf(day(minC = 6, maxC = 19, rainMm = 12))
        assertNotNull(today("Hoy entre 6° y 19°, con rachas de hasta 12 km/h.", factsOf(day(minC = 6, maxC = 19).copy(gustKmh = 12))))
        assertNotNull(today("Hoy entre 6° y 19°, unos 12 mm.", facts))
        // un decimal pegado a su unidad sigue siendo número (no «07:00»): si no está en los hechos, se rechaza como número, no como hora
        assertNotNull(today("Hoy entre 6.00° y 19°.", facts))
        assertNotNull(today("Hoy entre 6° y 19°, 12.00 mm.", facts))
        assertNull(today("Hoy entre 6° y 19°, 7.00 mm.", facts))
    }

    @Test fun `una hora que no existe descarta el texto`() {
        assertNull(check(reply("Hoy entre 6° y 19° hacia las 17:00."), factsOf(day()))!!.today)
        assertNull(check(reply("Hoy entre 6° y 19° hacia las 5:30."), factsOf(day()))!!.today)
    }

    @Test fun `un dia malo no tira los demas`() {
        val d0 = day()
        val d1 = day("2026-11-13", "mañana", 7, "05:00", 18, "15:00")
        val d2 = day("2026-11-14", "sábado 14", 8, "05:00", 17, "15:00")
        val f = factsOf(d0, listOf(d0, d1, d2))
        val r = check(
            reply("Hoy entre 6° y 19°.", days = listOf(
                "2026-11-12" to "Máxima de 19° hacia las 16:00.",
                "2026-11-13" to "Máxima de 30°.",
                "2026-11-14" to "Máxima de 17° hacia las 15:00.",
            )),
            f,
        )!!
        assertEquals(setOf("2026-11-12", "2026-11-14"), r.days.keys)
    }

    @Test fun `cada dia se comprueba con sus propios datos`() {
        val d0 = day()
        val d1 = day("2026-11-13", "mañana", 7, "05:00", 18, "15:00")
        val r = check(reply("Hoy 19°.", days = listOf("2026-11-13" to "Máxima de 19°.")), factsOf(d0, listOf(d0, d1)))!!
        assertTrue(r.days.isEmpty()) // 19 es de hoy, no de mañana
    }

    @Test fun `la fecha de un dia puede venir en la etiqueta del dia`() {
        val d0 = day()
        val d2 = day("2026-11-14", "sábado 14", 8, "05:00", 17, "15:00")
        val r = check(reply("Hoy 19°.", days = listOf("2026-11-14" to "El sábado 14, máxima de 17°.")), factsOf(d0, listOf(d0, d2)))!!
        assertEquals("El sábado 14, máxima de 17°.", r.days["2026-11-14"])
    }

    @Test fun `una fecha de dia desconocida se descarta`() {
        val r = check(reply("Hoy 19°.", days = listOf("2026-12-25" to "Sin lluvia.")), factsOf(day()))!!
        assertTrue(r.days.isEmpty())
    }

    @Test fun `aviso con clave desconocida fuera, y la clave buena vuelve a su id`() {
        val f = factsOf(day(), warnings = listOf(warningFacts))
        val r = check(
            reply("Hoy 19°.", warnings = listOf(
                "a1" to "Puede llover muy fuerte en poco rato esta tarde, de 15:00 a 21:00.",
                "a7" to "Nada.",
                "id-secreto" to "Nada.",
            )),
            f,
        )!!
        assertEquals(setOf("id-secreto"), r.warnings.keys)
        assertEquals("Puede llover muy fuerte en poco rato esta tarde, de 15:00 a 21:00.", r.warnings["id-secreto"])
    }

    @Test fun `un aviso con una cifra que no esta en su descripcion se descarta`() {
        val f = factsOf(day(), warnings = listOf(warningFacts))
        val r = check(reply("Hoy 19°.", warnings = listOf("a1" to "Hasta 50 mm esta tarde.")), f)!!
        assertTrue(r.warnings.isEmpty())
        val ok = check(reply("Hoy 19°.", warnings = listOf("a1" to "Hasta 20 mm esta tarde, con probabilidad del 40%.")), f)!!
        assertEquals(1, ok.warnings.size)
    }

    // ---------- 018: la frase del widget ----------

    private fun withWidget(widget: String?, today: String = "Hoy entre 6° y 19°."): String {
        val w = if (widget == null) "" else ",\"widget\":\"$widget\""
        return "{\"today\":\"$today\",\"days\":[],\"warnings\":[]$w}"
    }

    @Test fun `la frase del widget correcta se guarda`() {
        val r = check(withWidget("Máxima de 19° hacia las 16:00"), factsOf(day()))!!
        assertEquals("Máxima de 19° hacia las 16:00", r.widget)
        assertEquals("Hoy entre 6° y 19°.", r.today)
    }

    @Test fun `la frase del widget con cifra falsa se descarta y el resto del parte sigue`() {
        val r = check(withWidget("Máxima de 25° hacia las 16:00"), factsOf(day()))!!
        assertNull(r.widget)
        assertEquals("Hoy entre 6° y 19°.", r.today)
    }

    @Test fun `la frase del widget con hora falsa se descarta y el resto del parte sigue`() {
        val r = check(withWidget("Lluvia desde las 17:00"), factsOf(day()))!!
        assertNull(r.widget)
        assertEquals("Hoy entre 6° y 19°.", r.today)
    }

    @Test fun `un hoy malo no tira la frase del widget`() {
        val r = check(withWidget("Máxima de 19°", today = "Hoy 31°."), factsOf(day()))!!
        assertNull(r.today)
        assertEquals("Máxima de 19°", r.widget)
    }

    @Test fun `55 caracteres valen y 56 no`() {
        assertEquals(55, WIDGET_MAX)
        val f = factsOf(day())
        assertEquals(55, check(withWidget("a".repeat(55)), f)!!.widget!!.length)
        assertNull(check(withWidget("a".repeat(56)), f)!!.widget)
    }

    @Test fun `la frase con enes, tildes y grados cuenta por caracteres y vale`() {
        val phrase = "Mañana niebla, máxima de 19°".padEnd(55, 'ñ')
        assertEquals(55, phrase.length)
        assertEquals(phrase, check(withWidget(phrase), factsOf(day()))!!.widget)
    }

    @Test fun `una frase del widget vacia, con salto de linea o que no es texto queda sin frase`() {
        val f = factsOf(day())
        assertNull(check(withWidget(""), f)!!.widget)
        assertNull(check(withWidget("   "), f)!!.widget)
        assertNull(check(withWidget("Hoy 19°.\\nSin lluvia"), f)!!.widget)
        assertNull(check("{\"today\":\"Hoy 19°.\",\"days\":[],\"warnings\":[],\"widget\":5}", f)!!.widget)
    }

    @Test fun `una respuesta sin campo widget se guarda sin frase`() {
        val r = check(withWidget(null), factsOf(day()))!!
        assertNull(r.widget)
        assertEquals("Hoy entre 6° y 19°.", r.today)
    }

    @Test fun `solo la frase del widget no rompe la comprobacion`() {
        val r = check("{\"widget\":\"Máxima de 19°\"}", factsOf(day()))!!
        assertEquals("Máxima de 19°", r.widget)
        assertNull(r.today)
    }

    // ---------- forma ----------

    @Test fun `json roto, vacio o con texto alrededor no es un parte`() {
        val f = factsOf(day())
        assertNull(check("", f))
        assertNull(check("   ", f))
        assertNull(check("{\"today\": \"Hoy 6°", f))
        assertNull(check("Aquí tienes: " + reply("Hoy 19°."), f))
        assertNull(check("```json\n" + reply("Hoy 19°.") + "\n```", f))
        assertNull(check("[]", f))
        assertNull(check("{}", f))
        assertNull(check("\"hola\"", f))
    }

    @Test fun `texto vacio, con salto de linea o demasiado largo se descarta`() {
        val d0 = day()
        val f = factsOf(d0)
        assertNull(check(reply(""), f)!!.today)
        assertNull(check(reply("   "), f)!!.today)
        assertNull(check(reply("Hoy 19°.\nSin lluvia."), f)!!.today)
        assertNull(check(reply("Hoy 19°. " + "x".repeat(320)), f)!!.today)
        assertNotNull(check(reply("Hoy 19°. " + "x".repeat(300)), f)!!.today)
        val longDay = "Máxima de 19°. " + "x".repeat(170)
        assertTrue(check(reply("Hoy 19°.", days = listOf("2026-11-12" to longDay)), f)!!.days.isEmpty())
        val longWarn = "Tormenta. " + "x".repeat(215)
        assertTrue(check(reply("Hoy 19°.", warnings = listOf("a1" to longWarn)), factsOf(d0, warnings = listOf(warningFacts)))!!.warnings.isEmpty())
    }

    @Test fun `un tope exacto vale y uno mas no`() {
        val f = factsOf(day())
        assertNotNull(check(reply("a".repeat(320)), f)!!.today)
        assertNull(check(reply("a".repeat(321)), f)!!.today)
        assertEquals(180, check(reply("Hoy 19°.", days = listOf("2026-11-12" to "a".repeat(180))), f)!!.days["2026-11-12"]!!.length)
        assertTrue(check(reply("Hoy 19°.", days = listOf("2026-11-12" to "a".repeat(181))), f)!!.days.isEmpty())
    }

    @Test fun `un hoy que falta no rompe el resto`() {
        val r = check(reply(null, days = listOf("2026-11-12" to "Máxima de 19°.")), factsOf(day()))!!
        assertNull(r.today)
        assertEquals(1, r.days.size)
    }

    @Test fun `un texto que no es una cadena se descarta sin tirar el parte`() {
        val r = check("""{"today":5,"days":[{"date":"2026-11-12","text":null},{"date":"2026-11-12","text":"Máxima de 19°."},7],"warnings":"x"}""", factsOf(day()))!!
        assertNull(r.today)
        assertEquals("Máxima de 19°.", r.days["2026-11-12"])
    }

    // ---------- reportDue ----------

    private fun report(generatedAt: Long = now, b: ReportBasis = basis()) =
        AiReport(generatedAt, AiProvider.CLAUDE, b, today = "Hoy 19°.")

    @Test fun `sin parte toca`() = assertTrue(reportDue(null, basis(), now))

    @Test fun `con 6 h menos un segundo no toca y con 6 h mas un segundo si`() {
        assertFalse(reportDue(report(), basis(), now + REPORT_MAX_AGE_S - 1))
        assertFalse(reportDue(report(), basis(), now + REPORT_MAX_AGE_S))
        assertTrue(reportDue(report(), basis(), now + REPORT_MAX_AGE_S + 1))
    }

    @Test fun `otro dia toca`() = assertTrue(reportDue(report(), basis(date = "2026-11-13"), now + 60))

    @Test fun `maxima mas 1 no toca y mas 2 si, igual con la minima y hacia abajo`() {
        assertFalse(reportDue(report(), basis(maxC = 21), now + 60))
        assertTrue(reportDue(report(), basis(maxC = 22), now + 60))
        assertTrue(reportDue(report(), basis(maxC = 18), now + 60))
        assertFalse(reportDue(report(), basis(minC = 11), now + 60))
        assertTrue(reportDue(report(), basis(minC = 8), now + 60))
    }

    @Test fun `lluvia que aparece o desaparece toca`() {
        assertTrue(reportDue(report(), basis(wet = true, rainStart = 15), now + 60))
        assertTrue(reportDue(report(b = basis(wet = true, rainStart = 15)), basis(), now + 60))
        assertTrue(reportDue(report(), basis(rainStart = 30), now + 60)) // mañana empieza a llover
    }

    @Test fun `lluvia que se mueve 1 h no toca y 2 h si`() {
        val old = report(b = basis(wet = true, rainStart = 15))
        assertFalse(reportDue(old, basis(wet = true, rainStart = 16), now + 60))
        assertFalse(reportDue(old, basis(wet = true, rainStart = 14), now + 60))
        assertTrue(reportDue(old, basis(wet = true, rainStart = 17), now + 60))
        assertTrue(reportDue(old, basis(wet = true, rainStart = 13), now + 60))
    }

    @Test fun `avisos distintos tocan`() {
        assertTrue(reportDue(report(b = basis(ids = listOf("a"))), basis(ids = listOf("a", "b")), now + 60))
        assertFalse(reportDue(report(b = basis(ids = listOf("a", "b"))), basis(ids = listOf("a", "b")), now + 60))
    }

    @Test fun `changedForReal es simetrico`() {
        val a = basis(maxC = 20)
        val b = basis(maxC = 22)
        assertEquals(changedForReal(a, b), changedForReal(b, a))
    }

    @Test fun `48 llamadas cada 30 min con la misma prevision dan como mucho 5 si`() {
        val snap = reportSnap(madridAt(2026, 11, 12, 7, 0), hour = { t, h ->
            if (t.toLocalDate().toString() == "2026-11-12" && t.hour in 15..17) h.copy(precipMm = 1.0) else h
        })
        var current: AiReport? = null
        var yes = 0
        repeat(48) { i ->
            val t = madridAt(2026, 11, 12, 7, 0) + i * 1800L
            val b = reportFacts(snap, t, zone)!!.basis
            if (reportDue(current, b, t)) {
                yes++
                current = AiReport(t, AiProvider.CLAUDE, b, today = "x")
            }
        }
        assertTrue("llamadas: $yes", yes <= 5)
    }

    // ---------- visibleReport ----------

    @Test fun `visibleReport sin parte o sin huella es null`() {
        assertNull(visibleReport(null, basis()))
        assertNull(visibleReport(report(), null))
    }

    @Test fun `pasada la medianoche el parte no se ve`() {
        val old = report(b = basis(date = "2026-11-12"))
        assertNull(visibleReport(old, basis(date = "2026-11-13")))
    }

    @Test fun `un parte de 7 h sin cambios se sigue viendo`() {
        val old = report(generatedAt = now - 7 * 3600)
        assertEquals(old, visibleReport(old, basis(maxC = 21)))
    }

    @Test fun `un cambio de verdad lo esconde`() {
        assertNull(visibleReport(report(), basis(maxC = 25)))
        assertNull(visibleReport(report(), basis(ids = listOf("nuevo"))))
    }

    // ---------- reportLabel (016 P4, H9) ----------

    private fun reportAt(epoch: Long) = AiReport(epoch, AiProvider.CLAUDE, basis(), today = "Hoy.")

    @Test fun `la marca lleva la hora del parte en la zona del sitio`() {
        assertEquals("IA · 08:15", reportLabel(reportAt(madridAt(2026, 11, 12, 8, 15)), zone))
        // El mismo instante, en un sitio con otra zona, sale con su hora.
        assertEquals("IA · 04:15", reportLabel(reportAt(madridAt(2026, 11, 12, 8, 15)), ZoneId.of("America/Argentina/Buenos_Aires")))
    }

    @Test fun `la marca el dia del cambio de hora usa el huso de ese momento`() {
        // 2026-10-25: a las 03:00 de verano se vuelve a las 02:00; 01:30 UTC es la segunda 02:30... y 00:30 UTC, las 02:30 de verano.
        val summer = java.time.ZonedDateTime.of(2026, 10, 25, 2, 30, 0, 0, zone).withEarlierOffsetAtOverlap().toEpochSecond()
        val winter = java.time.ZonedDateTime.of(2026, 10, 25, 2, 30, 0, 0, zone).withLaterOffsetAtOverlap().toEpochSecond()
        assertEquals(3600L, winter - summer)
        assertEquals("IA · 02:30", reportLabel(reportAt(summer), zone))
        assertEquals("IA · 02:30", reportLabel(reportAt(winter), zone))
        assertEquals("IA · 04:00", reportLabel(reportAt(java.time.ZonedDateTime.of(2026, 10, 25, 4, 0, 0, 0, zone).toEpochSecond()), zone))
    }
}
