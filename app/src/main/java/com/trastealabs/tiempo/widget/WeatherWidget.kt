package com.trastealabs.tiempo.widget

import android.content.Context
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import com.trastealabs.tiempo.WeatherApp
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

/**
 * Widget de tres tamaños (H13 de la 002). Nunca descarga nada (C6): pinta lo que hay en `snapshot.json`,
 * así que al añadirlo ya tiene datos y enseña lo mismo que la app.
 * `SizeMode.Exact`: Glance compone con el tamaño real (no un escalón), así la imagen de fondo
 * ([paintWidget]) no se estira (H14); [WidgetContent] decide la versión.
 */
class WeatherWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as WeatherApp
        // 018 P3: lo guardado (previsión y parte de la IA, los dos de disco); el widget no pide nada a internet.
        val states = combine(app.repository.state, app.reportStore.state) { state, report -> state to report }
        val initial = states.first()
        provideContent {
            val saved by states.collectAsState(initial)
            WidgetContent(saved.first, System.currentTimeMillis() / 1000, saved.second)
        }
    }
}
