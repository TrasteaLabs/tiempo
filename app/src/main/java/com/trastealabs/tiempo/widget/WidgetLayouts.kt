package com.trastealabs.tiempo.widget

import android.content.ComponentName
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.cornerRadius
import androidx.glance.layout.Box
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import com.trastealabs.tiempo.domain.AiReport
import com.trastealabs.tiempo.domain.StoredState
import com.trastealabs.tiempo.ui.MainActivity
import com.trastealabs.tiempo.ui.scene.MoonCache
import com.trastealabs.tiempo.ui.scene.MoonLook

/**
 * El widget (010, P3 y P4): los tres tamaños son una imagen pintada por [paintWidget] (foto, velo, letra de la app, iconos,
 * luna y píldora). La versión sale del tamaño ([widgetLayout]); Glance solo la enseña, con lo que lee TalkBack, y el
 * toque que abre la app. Sin datos, la foto de nubes y «Abre la app» (H18). Nunca descarga nada (C6).
 */
@Composable
fun WidgetContent(state: StoredState, now: Long, report: AiReport? = null) {
    val context = LocalContext.current
    val size = LocalSize.current
    val widthDp = size.width.value
    val heightDp = size.height.value
    val density = context.resources.displayMetrics.density
    // 018 P3: la frase del parte guardado (nunca de la red), solo si la app enseñaría ese parte hoy; el 4×2 la usa y los demás la ignoran.
    val frame = widgetFrame(state, widgetLayout(widthDp, heightDp), now, context, widgetPhrase(state, report, now))
    val bare = WidgetDemo.bare
    val image = remember(frame, widthDp, heightDp, density, bare) {
        val app = context.applicationContext
        val moon = frame.moonPhase?.let { MoonLook(it, frame.south, MoonCache.litNow(app, it, frame.south)) }
        paintWidget(app, frame, widthDp, heightDp, density, moon, bare)
    }
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(WIDGET_CORNER.dp)
            .clickable(actionStartActivity(ComponentName(context, MainActivity::class.java))),
    ) {
        Image(
            provider = ImageProvider(image.bitmap),
            // TalkBack lee la frase solo si el pintor la enseñó (si no cabía, se ve y se lee la palabra del cielo).
            contentDescription = widgetDescription(if (image.phraseShown) frame else frame.copy(phrase = null), context),
            contentScale = ContentScale.Crop,
            modifier = GlanceModifier.fillMaxSize(),
        )
    }
}
