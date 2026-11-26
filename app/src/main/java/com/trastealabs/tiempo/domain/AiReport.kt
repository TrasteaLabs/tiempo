package com.trastealabs.tiempo.domain

import kotlinx.serialization.Serializable

// 016: el parte del día que escribe un modelo de IA. Se guarda aparte de la previsión (ai_report.json).

@Serializable
enum class AiProvider { CLAUDE, GEMINI, OPENAI }

/** Lo que decide si la previsión «ha cambiado de verdad» (H10). Cifras de pantalla. */
@Serializable
data class ReportBasis(
    /** «Hoy» local del sitio, YYYY-MM-DD. */
    val date: String,
    val maxC: Int,
    val minC: Int,
    val wetToday: Boolean,
    /** Primera hora con lluvia desde ahora, 0–47 contando desde las 00:00 de hoy; una lluvia en curso cuenta desde que empezó. */
    val rainStartHour: Int? = null,
    /** `Warning.id` en vigor o en 24 h, ordenados. */
    val warningIds: List<String> = emptyList(),
)

@Serializable
data class AiReport(
    /** Epoch s. */
    val generatedAt: Long,
    val provider: AiProvider,
    val basis: ReportBasis,
    /** null = esa parte no pasó la comprobación. */
    val today: String? = null,
    /** Fecha → frase. */
    val days: Map<String, String> = emptyMap(),
    /** `Warning.id` → explicación. */
    val warnings: Map<String, String> = emptyMap(),
    /** 018: la frase corta del widget 4×2 (55 caracteres como mucho). null = sin frase (parte viejo o no pasó la comprobación). */
    val widget: String? = null,
)
