package com.trastealabs.tiempo.domain

import com.trastealabs.tiempo.TestData
import com.trastealabs.tiempo.data.RefreshLogEntry
import com.trastealabs.tiempo.data.RefreshOutcome
import com.trastealabs.tiempo.refresh.AemetOutcome
import com.trastealabs.tiempo.refresh.RefreshOrigin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Las reglas del resumen de refrescos (015 P2, H11, H12): último, peor hueco de 24 h y lista de 24 h. */
class RefreshSummaryTest {
    private val h = 3600L
    private val min = 60L

    private fun ok(at: Long, outcome: RefreshOutcome = RefreshOutcome.OK) =
        RefreshLogEntry(at, RefreshOrigin.WORKER, outcome, AemetOutcome.UNCHANGED, 0)

    private val now = TestData.at(2026, 11, 12, 12, 0)

    @Test fun `sin lineas no hay ultimo, ni hueco, ni lista (H12)`() {
        val s = refreshSummary(emptyList(), now)
        assertNull(s.last)
        assertNull(s.worstGapSeconds)
        assertEquals(emptyList<RefreshLogEntry>(), s.last24h)
    }

    @Test fun `solo fallos el ultimo es el fallo y no hay hueco`() {
        val a = ok(now - 2 * h, RefreshOutcome.OFFLINE)
        val b = ok(now - 1 * h, RefreshOutcome.NO_LOCATION)
        val s = refreshSummary(listOf(a, b), now)
        assertEquals(b, s.last)
        assertNull(s.worstGapSeconds)
        assertEquals(listOf(b, a), s.last24h)
    }

    @Test fun `un solo refresco bueno el hueco es desde entonces hasta ahora`() {
        val s = refreshSummary(listOf(ok(now - 41 * min)), now)
        assertEquals(41 * min, s.worstGapSeconds)
    }

    @Test fun `el mayor hueco puede estar en medio`() {
        val entries = listOf(
            ok(now - 10 * h), ok(now - 8 * h - 30 * min),
            ok(now - 5 * h),                                 // desde -8 h 30 min: 3 h 30 min
            ok(now - 3 * h - 30 * min), ok(now - 20 * min),  // 1 h 30 min y 3 h 10 min
        )
        assertEquals(3 * h + 30 * min, refreshSummary(entries, now).worstGapSeconds)
    }

    @Test fun `el mayor hueco puede ser el de ahora`() {
        val entries = listOf(ok(now - 5 * h), ok(now - 4 * h + 30 * min), ok(now - 3 * h))
        // 30 min, 1 h 30 min y, desde el último, 3 h hasta ahora.
        assertEquals(3 * h, refreshSummary(entries, now).worstGapSeconds)
    }

    @Test fun `el hueco que cruza el borde de las 24 horas cuenta entero`() {
        // El bueno anterior es de hace 35 h; el siguiente, de hace 22 h: el dato llegó a tener 13 h, dentro de la ventana.
        val entries = listOf(ok(now - 35 * h), ok(now - 22 * h), ok(now - 12 * h), ok(now - 2 * h), ok(now - 10 * min))
        assertEquals(13 * h, refreshSummary(entries, now).worstGapSeconds)
    }

    @Test fun `un hueco que acaba antes de la ventana no cuenta`() {
        // 60 h → 30 h (30 h) acaba fuera de la ventana; 30 h → 2 h (28 h) acaba dentro y cuenta.
        val entries = listOf(ok(now - 60 * h), ok(now - 30 * h), ok(now - 2 * h), ok(now - 1 * h))
        assertEquals(28 * h, refreshSummary(entries, now).worstGapSeconds)
    }

    @Test fun `los fallos no cuentan como refresco bueno para el hueco`() {
        val entries = listOf(
            ok(now - 3 * h), ok(now - 2 * h, RefreshOutcome.OFFLINE), ok(now - 1 * h, RefreshOutcome.OFFLINE),
            ok(now - 30 * min),
        )
        // 3 h → 30 min = 2 h 30 min aunque haya intentos fallidos en medio.
        val s = refreshSummary(entries, now)
        assertEquals(2 * h + 30 * min, s.worstGapSeconds)
        assertEquals(entries.last(), s.last)
    }

    @Test fun `la lista va con la mas reciente primero y sin nada de mas de 24 horas`() {
        val old = ok(now - 24 * h - 1)
        val edge = ok(now - 24 * h)
        val mid = ok(now - 5 * h)
        val last = ok(now)
        val s = refreshSummary(listOf(old, edge, mid, last), now)
        assertEquals(listOf(last, mid, edge), s.last24h)
        assertEquals(last, s.last)
    }

    @Test fun `el ultimo puede ser mas viejo que 24 horas y la lista queda vacia`() {
        val s = refreshSummary(listOf(ok(now - 30 * h)), now)
        assertEquals(now - 30 * h, s.last!!.at)
        assertEquals(emptyList<RefreshLogEntry>(), s.last24h)
        assertEquals(30 * h, s.worstGapSeconds)
    }

    @Test fun `la noche del cambio de hora el hueco sale en segundos reales`() {
        // 2026-10-25 en Madrid: a las 03:00 vuelven a ser las 02:00; entre 01:30 y 04:00 locales pasan 3 h reales.
        val dst = TestData.at(2026, 10, 25, 1, 30)
        val after = TestData.at(2026, 10, 25, 4, 0)
        assertEquals(3 * h + 30 * min, after - dst)
        val entries = listOf(ok(dst), ok(after))
        val s = refreshSummary(entries, after + 10 * min)
        assertEquals(3 * h + 30 * min, s.worstGapSeconds)
    }

    @Test fun `cruzando la medianoche el hueco sigue siendo el real`() {
        val before = TestData.at(2026, 11, 11, 23, 40)
        val past = TestData.at(2026, 11, 12, 0, 25)
        val s = refreshSummary(listOf(ok(before), ok(past)), past + 5 * min)
        assertEquals(45 * min, s.worstGapSeconds)
        assertEquals(listOf(ok(past), ok(before)), s.last24h)
    }

    @Test fun `el texto del hueco`() {
        assertEquals("menos de 1 min", gapText(0))
        assertEquals("menos de 1 min", gapText(59))
        assertEquals("1 min", gapText(60))
        assertEquals("41 min", gapText(41 * min))
        assertEquals("41 min", gapText(41 * min + 59))
        assertEquals("1 h", gapText(60 * min))
        assertEquals("2 h 05 min", gapText(2 * h + 5 * min))
        assertEquals("3 h", gapText(3 * h))
        assertEquals("23 h 59 min", gapText(23 * h + 59 * min))
    }

    @Test fun `el texto de los KB`() {
        assertEquals("1 KB", kbText(1))
        assertEquals("1 KB", kbText(1_024))
        assertEquals("2 KB", kbText(1_536))
        assertEquals("344 KB", kbText(352_256))
        assertEquals("1.200 KB", kbText(1_228_800))
    }

    @Test fun `la linea de aviso de la manana no es un refresco, ni ultimo ni hueco, pero sale en la lista`() {
        val refresh = ok(now - 3 * h)
        val notice = RefreshLogEntry(
            now - 10 * min, RefreshOrigin.MORNING, RefreshOutcome.OK, AemetOutcome.NOT_APPLICABLE, 0,
            com.trastealabs.tiempo.data.MorningMark(now - 40 * min, withAi = false),
        )
        val s = refreshSummary(listOf(refresh, notice), now)
        assertEquals(refresh, s.last)
        // Sin el aviso, el hueco es desde el último refresco bueno hasta ahora: 3 h, no 10 min.
        assertEquals(3 * h, s.worstGapSeconds)
        assertEquals(listOf(notice, refresh), s.last24h)
    }
}
