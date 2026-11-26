package com.trastealabs.tiempo.benchmark

import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.UiDevice
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** M2: desplazar la principal de la demo, `frameOverrunMs` P50/P90 (`sh tools/bench.sh desplazar`). */
@RunWith(AndroidJUnit4::class)
class ScrollBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun desplazar() = rule.measureRepeated(
        packageName = APP_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.WARM,
        setupBlock = {
            pressHome()
            startActivityAndWait(demoIntent())
            device.waitForIdle()
        },
    ) {
        scrollMain(device)
    }
}

/** Dos pasadas abajo y dos arriba por el centro de la pantalla, como `medir.sh desplazar`. */
fun scrollMain(device: UiDevice) {
    val x = device.displayWidth / 2
    val low = device.displayHeight * 75 / 100
    val high = device.displayHeight * 30 / 100
    repeat(2) {
        device.swipe(x, low, x, high, 40)   // 40 pasos ≈ 300 ms
        device.waitForIdle()
    }
    repeat(2) {
        device.swipe(x, high, x, low, 40)
        device.waitForIdle()
    }
}
