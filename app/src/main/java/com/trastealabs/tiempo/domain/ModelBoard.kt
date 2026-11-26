package com.trastealabs.tiempo.domain

// Modelos en la página del día (H9 y H10 de la 004; cambia H4 y H5 del 003).

private val ModelDay.wet get() = precipMm >= RAIN_MM

/** H9. Divididos, con la regla de la frase del 003: lluvia empatada (n ≥ 2), 3 o más que se apartan, o todos (n ≥ 2). */
fun modelsDivided(verdicts: List<ModelVerdict>): Boolean {
    val n = verdicts.size
    val wet = verdicts.count { it.day.wet }
    val apart = verdicts.count { it.off }
    return (n >= 2 && wet * 2 == n) || apart >= 3 || (n >= 2 && apart == n)
}

/** H9. Cada modelo con si va atenuado: se aparta y no van divididos. En el orden de [models]. */
fun dimmedModels(models: List<ModelDay>): List<Pair<ModelVerdict, Boolean>> {
    val verdicts = modelVerdicts(models)
    val divided = modelsDivided(verdicts)
    return verdicts.map { it to (it.off && !divided) }
}

/** H10. El marcador: solo los modelos que llegan a ese día. */
data class ModelBoard(
    val models: Int,
    val dryModels: Int,
    val wetModels: Int,
    /** Con [shownDegrees]; sin modelos, 0. */
    val maxLow: Int,
    val maxHigh: Int,
    val scenarios: Int,
    /** % del lado que se dice, truncado (como el 003). */
    val scenarioPct: Int,
    /** Secos >= con lluvia: al 50 % dice «secos» (diseño). */
    val scenariosDry: Boolean,
)

fun modelBoard(day: DayReliability): ModelBoard {
    val wet = day.models.count { it.wet }
    val maxima = day.models.map { shownDegrees(it.maxC) }
    val dryScenarios = day.scenarios - day.wetScenarios
    val dry = dryScenarios >= day.wetScenarios
    val side = if (dry) dryScenarios else day.wetScenarios
    return ModelBoard(
        models = day.models.size,
        dryModels = day.models.size - wet,
        wetModels = wet,
        maxLow = maxima.minOrNull() ?: 0,
        maxHigh = maxima.maxOrNull() ?: 0,
        scenarios = day.scenarios,
        scenarioPct = if (day.scenarios > 0) side * 100 / day.scenarios else 0,
        scenariosDry = dry,
    )
}

private fun modelsCount(n: Int) = if (n == 1) "1 modelo da" else "$n modelos dan"

/** Lo que lee TalkBack: «5 modelos dan el día seco y 1 con lluvia. Máximas de 17 a 21 grados. De 122 escenarios, el 72 % salen secos.» */
fun ModelBoard.spoken(): String {
    val modelsText = when {
        models == 0 -> null
        dryModels > 0 && wetModels > 0 -> "${modelsCount(dryModels)} el día seco y $wetModels con lluvia."
        wetModels == 0 -> "${modelsCount(dryModels)} el día seco."
        else -> "${modelsCount(wetModels)} el día con lluvia."
    }
    val maximaText = when {
        models == 0 -> null
        maxLow != maxHigh -> "Máximas de $maxLow a $maxHigh grados."
        models == 1 -> "Máxima de $maxLow grados."
        else -> "Máximas de $maxLow grados."
    }
    val scenariosText = if (scenarios > 0) {
        "De $scenarios escenarios, el $scenarioPct % ${if (scenariosDry) "salen secos" else "salen con lluvia"}."
    } else {
        null
    }
    return listOfNotNull(modelsText, maximaText, scenariosText).joinToString(" ")
}
