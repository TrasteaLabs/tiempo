// Generado por tools/build_looks.py: no se edita a mano. Para cambiarlo, cambia el script y vuelve a ejecutarlo.
package com.trastealabs.tiempo.ui.scene

import com.trastealabs.tiempo.domain.SkyPhoto

/**
 * Cómo se lee el texto sobre cada foto en las pantallas de la 010 (medido por el script con los cálculos de la maqueta).
 * [sceneVeil]: alfa del velo de tinta #0A1028 sobre la foto desenfocada de Ajustes y Sitios (≥ 0,20).
 * [screenTint]: tono del cristal de encima (0 = cristal claro). [sceneContrast]: texto al 80 % sobre la escena (≥ 4,5).
 * Los demás campos son de las postales de Sitios (P2): [crop] es el centro de la franja de la foto que se ve (fracción de
 * su alto, tabla FRANJA de la maqueta), [postalVeil] y [herePostalVeil] el velo de tinta uniforme de la postal de 112 dp y
 * el de la de «Donde estoy» (148 dp), y [postalContrast] el peor texto de las dos (≥ 4,5).
 */
data class ScreenLook(
    val sceneVeil: Float,
    val screenTint: Float,
    val sceneContrast: Float,
    val crop: Float,
    val postalVeil: Float,
    val herePostalVeil: Float,
    val postalContrast: Float,
)

val SCREEN_LOOKS: Map<SkyPhoto, ScreenLook> = mapOf(
    SkyPhoto.CLEAR_COOL to ScreenLook(0.54f, 0.37f, 4.62f, 0.14f, 0.21f, 0.26f, 4.60f),
    SkyPhoto.CLEAR_MILD to ScreenLook(0.57f, 0.38f, 4.61f, 0.18f, 0.33f, 0.33f, 4.62f),
    SkyPhoto.CLEAR_HOT to ScreenLook(0.55f, 0.37f, 4.64f, 0.40f, 0.51f, 0.52f, 4.62f),
    SkyPhoto.SCORCHING to ScreenLook(0.49f, 0.37f, 4.60f, 0.46f, 0.53f, 0.53f, 4.61f),
    SkyPhoto.CLOUDS to ScreenLook(0.63f, 0.38f, 4.60f, 0.34f, 0.60f, 0.59f, 4.63f),
    SkyPhoto.FOG to ScreenLook(0.56f, 0.37f, 4.61f, 0.30f, 0.53f, 0.53f, 4.63f),
    SkyPhoto.RAIN to ScreenLook(0.38f, 0.38f, 4.63f, 0.20f, 0.22f, 0.21f, 4.61f),
    SkyPhoto.STORM to ScreenLook(0.20f, 0.35f, 4.89f, 0.26f, 0.00f, 0.00f, 7.47f),
    SkyPhoto.SNOW to ScreenLook(0.57f, 0.38f, 4.61f, 0.30f, 0.54f, 0.54f, 4.64f),
    SkyPhoto.GOLDEN_CLEAR to ScreenLook(0.54f, 0.37f, 4.62f, 0.36f, 0.43f, 0.43f, 4.62f),
    SkyPhoto.GOLDEN_CLOUDS to ScreenLook(0.48f, 0.36f, 4.64f, 0.30f, 0.28f, 0.29f, 4.62f),
    SkyPhoto.NIGHT_CLEAR to ScreenLook(0.20f, 0.02f, 9.77f, 0.30f, 0.00f, 0.00f, 10.35f),
    SkyPhoto.NIGHT_CLOUDS to ScreenLook(0.20f, 0.23f, 6.97f, 0.26f, 0.00f, 0.00f, 5.98f),
    SkyPhoto.NIGHT_RAIN to ScreenLook(0.20f, 0.16f, 7.92f, 0.30f, 0.00f, 0.00f, 7.53f),
)
