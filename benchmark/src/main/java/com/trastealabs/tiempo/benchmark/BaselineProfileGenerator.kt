package com.trastealabs.tiempo.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Perfil de base: arranque y desplazar la principal de la demo (`sh tools/bench.sh perfil`, solo en el emulador). */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(
        packageName = APP_PACKAGE,
        includeInStartupProfile = false,
    ) {
        pressHome()
        startActivityAndWait(demoIntent())
        device.waitForIdle()
        scrollMain(device)
    }
}
