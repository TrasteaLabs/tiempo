package com.trastealabs.tiempo.domain

import com.trastealabs.tiempo.domain.WeatherModel.ECMWF
import com.trastealabs.tiempo.domain.WeatherModel.GEM
import com.trastealabs.tiempo.domain.WeatherModel.GFS
import com.trastealabs.tiempo.domain.WeatherModel.ICON
import com.trastealabs.tiempo.domain.WeatherModel.METEO_FRANCE
import com.trastealabs.tiempo.domain.WeatherModel.UKMO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** H9 y H10 de la 004: atenuados y marcador de los modelos, con datos inventados (C5). */
class ModelBoardTest {
    private fun m(model: WeatherModel, maxC: Double, mm: Double = 0.0) = ModelDay(model, 2, maxC, mm)

    private fun day(models: List<ModelDay>, scenarios: Int = 122, wet: Int = 0) =
        DayReliability("2026-11-05", scenarios, wet, 19.0, 21.0, models)

    private fun dimmed(models: List<ModelDay>) = dimmedModels(models).filter { it.second }.map { it.first.day.model }

    private val agree = listOf(m(ECMWF, 21.0), m(ICON, 22.0), m(GFS, 21.0, 0.1), m(METEO_FRANCE, 20.0, 0.2), m(UKMO, 21.0), m(GEM, 22.0))

    /** El ejemplo de la spec: el francés ve lluvia y 17°. */
    private val oneApart = listOf(m(ECMWF, 20.0, 0.1), m(ICON, 21.0), m(GFS, 20.0, 0.2), m(METEO_FRANCE, 17.0, 2.8), m(UKMO, 20.0), m(GEM, 19.0, 0.4))

    /** GFS, ICON y GEM se apartan: tres o más → divididos. */
    private val split = listOf(m(ECMWF, 20.0, 3.4), m(ICON, 23.0, 0.2), m(GFS, 18.0, 6.1), m(UKMO, 21.0, 1.2), m(GEM, 22.0, 0.4))

    /** Mediana 17: los dos están a 3°. */
    private val twoOfTwo = listOf(m(ECMWF, 14.0), m(ICON, 20.0))

    // ---------- H9 · atenuados ----------

    @Test fun `si coinciden no se atenua ninguno (H9)`() {
        assertTrue(dimmed(agree).isEmpty())
        assertEquals(6, dimmedModels(agree).size)
    }

    @Test fun `si uno se aparta se atenua ese (H9)`() {
        assertEquals(listOf(METEO_FRANCE), dimmed(oneApart))
        assertFalse(modelsDivided(modelVerdicts(oneApart)))
    }

    @Test fun `si van divididos no se atenua ninguno (H9)`() {
        assertTrue(modelsDivided(modelVerdicts(split)))
        assertEquals(3, modelVerdicts(split).count { it.off })
        assertTrue(dimmed(split).isEmpty())
        // Lluvia empatada también es ir divididos.
        val tied = listOf(m(ECMWF, 20.0, 3.4), m(ICON, 21.0), m(GFS, 20.0, 6.1), m(GEM, 21.0))
        assertTrue(modelsDivided(modelVerdicts(tied)))
    }

    @Test fun `con 2 de 2 que se apartan no se atenua ninguno (H9)`() {
        assertEquals(2, modelVerdicts(twoOfTwo).count { it.off })
        assertTrue(dimmed(twoOfTwo).isEmpty())
    }

    @Test fun `el orden de los modelos se mantiene`() {
        assertEquals(oneApart.map { it.model }, dimmedModels(oneApart).map { it.first.day.model })
    }

    // ---------- H10 · marcador y TalkBack ----------

    @Test fun `el ejemplo de la spec palabra por palabra (H10)`() {
        // 34 de 122 con lluvia: 88 secos, el 72,1 %.
        val board = modelBoard(day(oneApart, wet = 34))
        assertEquals(ModelBoard(6, 5, 1, 17, 21, 122, 72, true), board)
        assertEquals(
            "5 modelos dan el día seco y 1 con lluvia. Máximas de 17 a 21 grados. De 122 escenarios, el 72 % salen secos.",
            board.spoken(),
        )
    }

    @Test fun `todos coinciden`() {
        val board = modelBoard(day(agree))
        assertEquals(ModelBoard(6, 6, 0, 20, 22, 122, 100, true), board)
        assertEquals("6 modelos dan el día seco. Máximas de 20 a 22 grados. De 122 escenarios, el 100 % salen secos.", board.spoken())
    }

    @Test fun `divididos, con la mayoria de escenarios con lluvia`() {
        // 66 de 122 con lluvia: el 54,1 %.
        val board = modelBoard(day(split, wet = 66))
        assertEquals(ModelBoard(5, 2, 3, 18, 23, 122, 54, false), board)
        assertEquals(
            "2 modelos dan el día seco y 3 con lluvia. Máximas de 18 a 23 grados. De 122 escenarios, el 54 % salen con lluvia.",
            board.spoken(),
        )
        val oneDry = modelBoard(day(listOf(m(ECMWF, 20.0), m(GFS, 20.0, 3.0), m(GEM, 21.0, 2.0)), wet = 66))
        assertTrue(oneDry.spoken(), oneDry.spoken().startsWith("1 modelo da el día seco y 2 con lluvia."))
    }

    @Test fun `2 de 2`() {
        val board = modelBoard(day(twoOfTwo))
        assertEquals(2, board.models)
        assertEquals("2 modelos dan el día seco. Máximas de 14 a 20 grados. De 122 escenarios, el 100 % salen secos.", board.spoken())
    }

    @Test fun `un solo modelo`() {
        val board = modelBoard(day(listOf(m(GFS, 18.0, 4.0)), scenarios = 82, wet = 82))
        assertEquals(ModelBoard(1, 0, 1, 18, 18, 82, 100, false), board)
        assertEquals("1 modelo da el día con lluvia. Máxima de 18 grados. De 82 escenarios, el 100 % salen con lluvia.", board.spoken())
    }

    @Test fun `varios modelos con la misma maxima`() {
        val board = modelBoard(day(listOf(m(ECMWF, 20.2), m(GFS, 19.8)), wet = 24))
        assertEquals(20, board.maxLow)
        assertEquals(20, board.maxHigh)
        assertTrue(board.spoken(), board.spoken().contains("Máximas de 20 grados."))
    }

    @Test fun `escenarios al 50 por ciento dicen secos (diseno)`() {
        val board = modelBoard(day(agree, scenarios = 100, wet = 50))
        assertTrue(board.scenariosDry)
        assertEquals(50, board.scenarioPct)
        assertTrue(board.spoken(), board.spoken().endsWith("De 100 escenarios, el 50 % salen secos."))
    }

    @Test fun `el porcentaje se trunca (89,6 da 89)`() {
        assertEquals(89, modelBoard(day(agree, scenarios = 125, wet = 13)).scenarioPct)
    }

    @Test fun `sin modelos solo los escenarios`() {
        val board = modelBoard(day(emptyList(), wet = 24))
        assertEquals(ModelBoard(0, 0, 0, 0, 0, 122, 80, true), board)
        assertEquals("De 122 escenarios, el 80 % salen secos.", board.spoken())
    }
}
