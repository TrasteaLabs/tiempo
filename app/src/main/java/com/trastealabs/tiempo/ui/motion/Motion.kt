package com.trastealabs.tiempo.ui.motion

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * ¿Puede animarse la app? `false` con «Quitar animaciones» o el ahorro de batería (la principal lo da desde su reloj del
 * cielo): entonces pantallas, hojas y diálogo aparecen y se van con un fundido corto, sin moverse. Los muelles salen del
 * tema (`MaterialTheme.motionScheme`, 012 P7); aquí solo queda esta bandera.
 */
val LocalWindowMotion = staticCompositionLocalOf { true }
