// Generado por tools/build_skies.py: no se edita a mano. Para cambiarlo, cambia la tabla del script y vuelve a
// ejecutarlo.
package com.trastealabs.tiempo.ui.scene

import com.trastealabs.tiempo.domain.SkyPhoto

/**
 * Cómo se lee el texto blanco sobre cada foto (009, H5), medido por el script como `solveGlass` de la maqueta.
 * [glassTint]: 0 = cristal claro de hoy; si no, alfa del velo de tinta rgb(16,22,46) de las tarjetas.
 * [headerVeil]: alfa del velo de lectura de la cabecera (tinta #0A1028), 0 si no hace falta.
 * [cardContrast] y [headerContrast]: el contraste del texto blanco que queda (≥ 4,5).
 */
data class PhotoLook(
    val glassTint: Float,
    val headerVeil: Float,
    val cardContrast: Float,
    val headerContrast: Float,
)

val PHOTO_LOOKS: Map<SkyPhoto, PhotoLook> = mapOf(
    SkyPhoto.CLEAR_COOL to PhotoLook(0.52f, 0.28f, 4.67f, 4.80f),
    SkyPhoto.CLEAR_MILD to PhotoLook(0.56f, 0.38f, 4.80f, 4.74f),
    SkyPhoto.CLEAR_HOT to PhotoLook(0.52f, 0.40f, 4.63f, 4.83f),
    SkyPhoto.SCORCHING to PhotoLook(0.46f, 0.42f, 4.75f, 4.87f),
    SkyPhoto.CLOUDS to PhotoLook(0.62f, 0.56f, 4.83f, 4.79f),
    SkyPhoto.FOG to PhotoLook(0.54f, 0.46f, 4.68f, 4.88f),
    SkyPhoto.RAIN to PhotoLook(0.34f, 0.10f, 4.75f, 4.74f),
    SkyPhoto.STORM to PhotoLook(0.10f, 0.00f, 5.09f, 9.73f),
    SkyPhoto.SNOW to PhotoLook(0.56f, 0.44f, 4.70f, 4.87f),
    SkyPhoto.GOLDEN_CLEAR to PhotoLook(0.52f, 0.38f, 4.75f, 4.81f),
    SkyPhoto.GOLDEN_CLOUDS to PhotoLook(0.46f, 0.26f, 4.78f, 4.86f),
    SkyPhoto.NIGHT_CLEAR to PhotoLook(0.00f, 0.00f, 5.13f, 17.93f),
    SkyPhoto.NIGHT_CLOUDS to PhotoLook(0.10f, 0.00f, 8.88f, 8.14f),
    SkyPhoto.NIGHT_RAIN to PhotoLook(0.10f, 0.00f, 9.95f, 13.33f),
)
