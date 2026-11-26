package com.trastealabs.tiempo.data.ai

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.trastealabs.tiempo.domain.AiProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.ZoneId
import java.time.ZonedDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AiSettingsTest {
    private val zone = ZoneId.of("Europe/Madrid")
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val prefs = context.getSharedPreferences("ai_settings_test", Context.MODE_PRIVATE)
    private val reply = ReportReply("{}", inputTokens = 2500, outputTokens = 1500)

    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int = 0) = ZonedDateTime.of(y, m, d, h, min, 0, 0, zone).toEpochSecond()
    private fun settings() = AiSettings(prefs, zone = { zone })

    @Before fun setUp() {
        prefs.edit().clear().commit()
    }

    @Test fun defaultProviderIsClaudeAndChoiceIsRemembered() {
        assertEquals(AiProvider.CLAUDE, settings().provider.value)
        val s = settings()
        s.setProvider(AiProvider.GEMINI)
        assertEquals(AiProvider.GEMINI, s.provider.value)
        assertEquals(AiProvider.GEMINI, settings().provider.value)
    }

    @Test fun recordAddsCallsAndTokensPerScope() {
        val s = settings()
        val now = at(2026, 11, 12, 10)
        s.record(ReportScope.HERE, reply, now)
        s.record(ReportScope.HERE, reply, now + 60)
        s.record(ReportScope.SAVED, reply, now + 120)
        val u = s.usage.value
        assertEquals("2026-11-12", u.day)
        assertEquals(2, u.dayHere)
        assertEquals(1, u.daySaved)
        assertEquals("2026-11", u.month)
        assertEquals(3, u.monthCalls)
        assertEquals(7500L, u.monthInputTokens)
        assertEquals(4500L, u.monthOutputTokens)
        assertEquals(u, settings().usage.value)
    }

    @Test fun dayResetsAtMidnightButMonthKeeps() {
        val s = settings()
        s.record(ReportScope.HERE, reply, at(2026, 11, 12, 23, 59))
        s.record(ReportScope.HERE, reply, at(2026, 11, 13, 0, 1))
        val u = s.usage.value
        assertEquals("2026-11-13", u.day)
        assertEquals(1, u.dayHere)
        assertEquals(2, u.monthCalls)
        assertEquals(5000L, u.monthInputTokens)
    }

    @Test fun monthResetsOnTheFirst() {
        val s = settings()
        s.record(ReportScope.SAVED, reply, at(2026, 11, 30, 12))
        s.record(ReportScope.SAVED, reply, at(2026, 12, 1, 0, 5))
        val u = s.usage.value
        assertEquals("2026-12", u.month)
        assertEquals(1, u.monthCalls)
        assertEquals(2500L, u.monthInputTokens)
        assertEquals(1, u.daySaved)
    }

    @Test fun usageAtShowsZeroAfterMidnightWithoutRecording() {
        val s = settings()
        s.record(ReportScope.HERE, reply, at(2026, 11, 12, 22))
        assertEquals(1, s.usageAt(at(2026, 11, 12, 23)).dayHere)
        assertEquals(0, s.usageAt(at(2026, 11, 13, 8)).dayHere)
        assertEquals(1, s.usageAt(at(2026, 11, 13, 8)).monthCalls)
    }

    @Test fun clockChangeNightStillCountsTheSameLocalDay() {
        // 2026-10-25: Madrid retrasa el reloj a las 03:00 (hay dos 02:30).
        val s = settings()
        val first = ZonedDateTime.of(2026, 10, 25, 2, 30, 0, 0, zone).withEarlierOffsetAtOverlap().toEpochSecond()
        val second = ZonedDateTime.of(2026, 10, 25, 2, 30, 0, 0, zone).withLaterOffsetAtOverlap().toEpochSecond()
        s.record(ReportScope.HERE, reply, first)
        s.record(ReportScope.HERE, reply, second)
        assertEquals(2, s.usage.value.dayHere)
        assertEquals("2026-10-25", s.usage.value.day)
    }

    @Test fun statusKeepsTheLastAttempt() {
        val s = settings()
        assertNull(s.status.value)
        s.setStatus(1000, AiFailure.BAD_KEY)
        assertEquals(AiStatus(1000, AiFailure.BAD_KEY), s.status.value)
        assertEquals(AiStatus(1000, AiFailure.BAD_KEY), settings().status.value)
        s.setStatus(2000, null)
        assertEquals(AiStatus(2000, null), settings().status.value)
    }

    @Test fun fileNeverHoldsAKey() {
        val s = settings()
        s.setProvider(AiProvider.OPENAI)
        s.record(ReportScope.HERE, reply, at(2026, 11, 12, 10))
        s.setStatus(5, AiFailure.OFFLINE)
        val keys = prefs.all.keys
        assertTrue(keys.none { it.startsWith("key_") })
        assertFalse(prefs.all.values.joinToString("|").contains("clave"))
    }

    @Test fun sharingTheFileWithTheVaultKeepsBothIntact() {
        val vault = KeystoreKeyVault(prefs, object : KeyCipher {
            override fun encrypt(plain: ByteArray) = plain.reversedArray()
            override fun decrypt(data: ByteArray) = data.reversedArray()
        })
        val s = settings()
        vault.put(AiProvider.CLAUDE, "clave-de-prueba-1234")
        s.setProvider(AiProvider.GEMINI)
        s.record(ReportScope.HERE, reply, at(2026, 11, 12, 10))
        s.setStatus(5, null)
        assertEquals("clave-de-prueba-1234", vault.get(AiProvider.CLAUDE))
        assertEquals(AiProvider.GEMINI, settings().provider.value)
        assertEquals(1, settings().usage.value.monthCalls)
        val plain = prefs.all.filterKeys { !it.startsWith("key_") }.values.joinToString("|")
        assertFalse(plain.contains("1234") && plain.contains("clave-de-prueba"))
    }

    @Test fun askCapAndLimitsAreFixed() {
        assertEquals(20, AI_DAILY_ASK)
        assertEquals(200, ASK_MAX_CHARS)
    }

    @Test fun askCountsItsOwnDayAndTheMonthButNotThePartes() {
        val s = settings()
        val now = at(2026, 11, 12, 10)
        s.record(ReportScope.ASK, reply, now)
        s.recordAttempt(ReportScope.ASK, now + 60)
        s.record(ReportScope.HERE, reply, now + 120)
        val u = s.usage.value
        assertEquals(2, u.dayAsk)
        assertEquals(1, u.dayHere)
        assertEquals(0, u.daySaved)
        assertEquals(3, u.monthCalls)
        assertEquals(5000L, u.monthInputTokens)
        assertEquals(2, settings().usage.value.dayAsk)
    }

    @Test fun dayAskGoesBackToZeroAtLocalMidnight() {
        val s = settings()
        s.record(ReportScope.ASK, reply, at(2026, 11, 12, 23, 59))
        assertEquals(1, s.usageAt(at(2026, 11, 12, 23, 59)).dayAsk)
        assertEquals(0, s.usageAt(at(2026, 11, 13, 0, 1)).dayAsk)
        s.record(ReportScope.ASK, reply, at(2026, 11, 13, 0, 1))
        assertEquals(1, s.usage.value.dayAsk)
        assertEquals(2, s.usage.value.monthCalls)
    }

    @Test fun oldPreferencesWithoutUsageDayAskReadAsZero() {
        prefs.edit()
            .putString("provider", "GEMINI")
            .putString("usage_day", "2026-11-12")
            .putInt("usage_day_here", 4)
            .putInt("usage_day_saved", 2)
            .putString("usage_month", "2026-11")
            .putInt("usage_month_calls", 30)
            .putLong("usage_month_in", 1000)
            .putLong("usage_month_out", 500)
            .commit()
        val s = settings()
        assertEquals(AiProvider.GEMINI, s.provider.value)
        val u = s.usage.value
        assertEquals(0, u.dayAsk)
        assertEquals(4, u.dayHere)
        assertEquals(2, u.daySaved)
        assertEquals(30, u.monthCalls)
        s.record(ReportScope.ASK, reply, at(2026, 11, 12, 11))
        assertEquals(1, s.usage.value.dayAsk)
        assertEquals(4, s.usage.value.dayHere)
    }

    @Test fun dailyCapsAreTwelve() {
        assertEquals(12, AI_DAILY_HERE)
        assertEquals(12, AI_DAILY_SAVED)
    }

    @Test fun manifestForbidsBackups() {
        val manifest = listOf("app/src/main/AndroidManifest.xml", "src/main/AndroidManifest.xml")
            .map { File(it) }.first { it.exists() }.readText()
        assertTrue(manifest.contains("android:allowBackup=\"false\""))
    }
}
