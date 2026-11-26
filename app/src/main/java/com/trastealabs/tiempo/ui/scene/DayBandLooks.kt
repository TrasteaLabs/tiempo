// Generado por tools/medir_niveles.py: no se edita a mano. Para cambiarlo, cambia el script y vuelve a ejecutarlo.
package com.trastealabs.tiempo.ui.scene

import com.trastealabs.tiempo.domain.SkyPhoto

/**
 * La fuerza del oscurecido extra de la franja de la ventana del día (019): 0 si el de siempre ya deja el texto blanco a
 * 4,6:1 sobre el punto más claro; si no, la más suave que llega (ver `bandShadeAt` en `DayPage.kt`).
 */
val DAY_BAND_SHADE: Map<SkyPhoto, Float> = mapOf(
    SkyPhoto.CLEAR_COOL to 0.00f,
    SkyPhoto.CLEAR_MILD to 0.20f,
    SkyPhoto.CLEAR_HOT to 0.20f,
    SkyPhoto.SCORCHING to 0.26f,
    SkyPhoto.CLOUDS to 0.56f,
    SkyPhoto.FOG to 0.36f,
    SkyPhoto.RAIN to 0.00f,
    SkyPhoto.STORM to 0.00f,
    SkyPhoto.SNOW to 0.38f,
    SkyPhoto.GOLDEN_CLEAR to 0.30f,
    SkyPhoto.GOLDEN_CLOUDS to 0.32f,
    SkyPhoto.NIGHT_CLEAR to 0.00f,
    SkyPhoto.NIGHT_CLOUDS to 0.00f,
    SkyPhoto.NIGHT_RAIN to 0.00f,
)
