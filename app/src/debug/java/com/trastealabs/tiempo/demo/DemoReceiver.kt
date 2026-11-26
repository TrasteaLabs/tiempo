package com.trastealabs.tiempo.demo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.glance.appwidget.updateAll
import com.trastealabs.tiempo.WeatherApp
import com.trastealabs.tiempo.data.SnapshotStore
import com.trastealabs.tiempo.domain.AskResult
import com.trastealabs.tiempo.domain.MorningText
import com.trastealabs.tiempo.domain.Scene
import com.trastealabs.tiempo.domain.StoredState
import com.trastealabs.tiempo.domain.WarningLevel
import com.trastealabs.tiempo.refresh.MorningNotifier
import com.trastealabs.tiempo.refresh.WeatherRepository
import com.trastealabs.tiempo.widget.WeatherWidget
import com.trastealabs.tiempo.widget.WidgetDemo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Solo debug. Escribe un snapshot inventado y repinta los widgets; el siguiente refresco real lo sustituye:
 * `adb shell am broadcast -a com.trastealabs.tiempo.debug.DEMO -n com.trastealabs.tiempo.dev/com.trastealabs.tiempo.demo.DemoReceiver --es scene STORM [--es badge WARNING|OFFLINE|EMPTY]`
 * `[--ef moon 0.84] [--ez south true]` (007): la fase de la luna de hoy y un sitio inventado del hemisferio sur.
 * `[--ez long true]` (010): el sitio «Sanlúcar de Barrameda»; `[--ez bare true]`: el 4×2 sin textos (para `tools/contraste.py`);
 * `[--es level RED]`: el aviso de ese nivel (con `long`, «Temperaturas máximas»).
 * 018: `--es ask "<pregunta>"` (solo eso) hace la pregunta a la IA con la previsión guardada de esta app y escribe el
 * resultado en logcat (etiqueta `AskDemo`); no toca la previsión ni los widgets. Es la herramienta de la batería de H17.
 * 018 P3: `[--es phrase short|long|none|"<texto>"]` además de lo anterior: guarda un parte de hoy con esa frase para el 4×2
 * (`long` es la de 55 caracteres; `none`, un parte sin frase). Sin `phrase` no se toca el parte guardado.
 * 018 P2: `--es morning now` (solo eso) publica la notificación de la mañana con datos inventados (etiqueta `MorningDemo`).
 */
class DemoReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        intent.getStringExtra("ask")?.let { return ask(context, it) }
        if (intent.getStringExtra("morning") == "now") return morning(context)
        val scene = demoScene(intent.getStringExtra("scene"))
        val badge = demoBadge(intent.getStringExtra("badge"))
        val moon = if (intent.hasExtra("moon")) intent.getFloatExtra("moon", 0.5f).toDouble() else null
        val place = when {
            intent.getBooleanExtra("long", false) -> demoLongPlace // «Sanlúcar de Barrameda» (010, P3)
            intent.getBooleanExtra("south", false) -> demoSouthPlace
            else -> demoPlace
        }
        WidgetDemo.bare = intent.getBooleanExtra("bare", false) // sin textos, para medir el contraste (010, P3)
        val level = WarningLevel.entries.firstOrNull { it.name == intent.getStringExtra("level")?.uppercase() }
        val long = intent.getBooleanExtra("long", false)
        val phraseKind = intent.getStringExtra("phrase")
        val app = context.applicationContext as WeatherApp
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val state = demoState(scene, badge, System.currentTimeMillis() / 1000, place = place, moonPhase = moon)
                val shown = withLevel(state, level, long)
                storeOf(app.repository).write(shown)
                // 018 P3: `--es phrase short|long|none|<texto>` guarda un parte con esa frase (el 4×2 la enseña); sin la orden, no se toca.
                if (phraseKind != null) shown.snapshot?.let { s -> demoWidgetReport(demoPhrase(phraseKind), s, System.currentTimeMillis() / 1000)?.let { app.reportStore.write(it) } }
                WeatherWidget().updateAll(app)
                Log.i(TAG, "Demo: $scene ${badge ?: ""}")
            } finally {
                pending.finish()
            }
        }
    }

    /** 018: la pregunta por adb. Lo único que se escribe es en logcat y solo aquí, en debug: la release no tiene este receptor. */
    private fun ask(context: Context, question: String) {
        val app = context.applicationContext as WeatherApp
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val started = System.currentTimeMillis()
                val stored = app.repository.state.first().snapshot
                // Sin previsión guardada se usa una inventada, solo para esta pregunta: no se escribe en ninguna parte.
                val snapshot = stored ?: checkNotNull(demoState(Scene.SUN_MILD, null, started / 1000).snapshot)
                val result = app.reports.ask(snapshot, question)
                val ms = System.currentTimeMillis() - started
                val source = if (stored == null) "previsión de demo" else "previsión guardada"
                when (result) {
                    is AskResult.Ok -> Log.i(ASK_TAG, "OK ${result.answer.kind} ($ms ms, $source): ${result.answer.text}")
                    is AskResult.Failed -> Log.i(ASK_TAG, "${result.failure} ($ms ms, $source)")
                }
            } catch (e: Exception) {
                Log.i(ASK_TAG, "ERROR ${e.javaClass.simpleName}")
            } finally {
                pending.finish()
            }
        }
    }

    /**
     * 018 P2: `--es morning now` publica la notificación de la mañana con datos inventados, por el mismo camino que la
     * real (canal, icono, toque). No toca los ajustes, ni el registro, ni marca el día como avisado.
     */
    private fun morning(context: Context) {
        val text = MorningText(
            title = "Hoy 12° a 26° · Aviso amarillo por viento",
            body = "Mañana fresca con 12° a las 07:00; sol y 26° hacia las 16:00. Viento fuerte de poniente por la tarde, hasta las 20:00.",
            withAi = true,
        )
        Log.i(MORNING_TAG, if (MorningNotifier.post(context, text)) "PUBLICADA" else "SIN_PERMISO")
    }

    /** Con `--es level RED` el aviso es de ese nivel; con `--ez long true`, el fenómeno largo de la maqueta (010, P3). */
    private fun withLevel(state: StoredState, level: WarningLevel?, long: Boolean): StoredState {
        val snapshot = state.snapshot ?: return state
        if (snapshot.warnings.isEmpty() || (level == null && !long)) return state
        val warnings = snapshot.warnings.map {
            it.copy(level = level ?: it.level, phenomenon = if (long) "temperaturas máximas" else it.phenomenon)
        }
        return state.copy(snapshot = snapshot.copy(warnings = warnings))
    }

    /**
     * El almacén del repositorio, el que leen la app y el widget (con su caché): escribir en otro
     * `SnapshotStore` del mismo fichero no se vería hasta reiniciar el proceso. Solo para esta herramienta.
     */
    private fun storeOf(repository: WeatherRepository): SnapshotStore =
        WeatherRepository::class.java.getDeclaredField("store").run {
            isAccessible = true
            get(repository) as SnapshotStore
        }

    private companion object {
        const val TAG = "DemoReceiver"
        const val ASK_TAG = "AskDemo"
        const val MORNING_TAG = "MorningDemo"
    }
}
