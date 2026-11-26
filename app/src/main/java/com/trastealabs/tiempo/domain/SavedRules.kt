package com.trastealabs.tiempo.domain

import java.time.Instant
import java.time.ZoneId

/** Un guardado se vuelve a pedir al abrirlo si lo suyo tiene más de esto (H11). */
const val SAVED_STALE_S = 30 * 60L

/** Al abrir un guardado: sin datos, con más de 30 min o con una edad negativa (reloj cambiado), se pide (H11). */
fun savedRefreshDue(stored: StoredState, now: Long): Boolean {
    val snapshot = stored.snapshot ?: return true
    val age = now - snapshot.fetchedAt
    return age < 0 || age > SAVED_STALE_S
}

/**
 * De cuándo es el dato de una postal, en la zona del sitio: null si tiene 30 min o menos; si no, «Datos de las 15:10»
 * (hoy), «Datos de ayer», «Datos del lunes» (2–6 días) o «Datos del 28 de octubre».
 */
fun dataAgeLabel(fetchedAt: Long, now: Long, zone: ZoneId): String? {
    if (now - fetchedAt <= SAVED_STALE_S) return null
    return when (val day = dayWord(fetchedAt, now, zone)) {
        null -> "Datos de las ${clockAt(fetchedAt, zone)}"
        else -> "Datos $day"
    }
}

/** El sitio tiene, en ese instante, otro desfase que el móvil (Canarias y Lisboa sí, desde Madrid; París no). */
fun otherOffset(now: Long, placeZone: ZoneId, phoneZone: ZoneId): Boolean {
    val instant = Instant.ofEpochSecond(now)
    return placeZone.rules.getOffset(instant) != phoneZone.rules.getOffset(instant)
}
