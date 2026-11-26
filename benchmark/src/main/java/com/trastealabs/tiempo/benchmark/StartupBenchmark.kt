package com.trastealabs.tiempo.benchmark

import android.content.Intent
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

const val APP_PACKAGE = "com.trastealabs.tiempo.dev"   // «Tiempo dev» (017)

/** La demo con un cielo fijo y nada más (plan 012): la app real no se abre ni se toca. */
fun demoIntent(): Intent = Intent()
    .setClassName(APP_PACKAGE, "com.trastealabs.tiempo.demo.DemoActivity")
    .putExtra("scene", "RAIN")

/** M1: arranque en frío de la demo, 10 iteraciones (`sh tools/bench.sh arranque`). */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun arranque() = rule.measureRepeated(
        packageName = APP_PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        iterations = 10,
        startupMode = StartupMode.COLD,
        setupBlock = {
            // ColorOS retiene el proceso al matarlo: repetir hasta que no quede ninguno (si no, el arranque no es en frío).
            // Hasta ver el proceso parado dos veces seguidas, con un segundo entre ellas.
            var tries = 0
            var quiet = 0
            while (quiet < 2 && tries++ < 30) {
                pressHome()
                killProcess()
                Thread.sleep(1000)
                quiet = if (device.executeShellCommand("pidof $APP_PACKAGE").isBlank()) quiet + 1 else 0
            }
        },
    ) {
        startActivityAndWait(demoIntent())
    }
}
