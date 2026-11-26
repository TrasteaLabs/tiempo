package com.trastealabs.tiempo.domain

import java.time.ZoneId

/** Lo que pinta una postal de la pantalla Sitios (006). */
data class PlaceCardModel(
    val ref: PlaceRef,
    val name: String,
    /**
     * La foto de su fondo: la que le toca por su tiempo, momento y temperatura ([skyPhotoNow], la misma regla que la
     * principal). null = sin datos o sin permiso: postal de cristal sin foto.
     */
    val photo: SkyPhoto?,
    /** El cielo de ahora; la pantalla lo pasa a texto con los mismos textos que la principal. */
    val sky: Sky?,
    val temp: Int?,
    val max: Int?,
    val min: Int?,
    /** [dataAgeLabel]: solo si el dato tiene más de 30 min. */
    val ageLabel: String?,
    /** "01:20" si el sitio tiene otro desfase que el móvil; si no, null. */
    val localTime: String?,
    /** Solo en «donde estoy». */
    val noPermission: Boolean,
    /** El sitio que se ve: borde de 2 dp. */
    val isShown: Boolean,
)

fun placeCard(
    ref: PlaceRef,
    name: String,
    stored: StoredState,
    now: Long,
    phoneZone: ZoneId,
    shown: Boolean,
    noPermission: Boolean,
): PlaceCardModel {
    val snapshot = stored.snapshot
        ?: return PlaceCardModel(ref, name, null, null, null, null, null, null, null, noPermission, shown)
    val zone = ZoneId.of(snapshot.zoneId)
    val day = today(snapshot, now)
    return PlaceCardModel(
        ref = ref,
        name = name,
        photo = if (noPermission) null else skyPhotoNow(snapshot, now),
        sky = skyOf(snapshot.current.weatherCode),
        temp = shownDegrees(snapshot.current.temperatureC),
        max = day?.let { shownDegrees(it.maxC) },
        min = day?.let { shownDegrees(it.minC) },
        ageLabel = dataAgeLabel(snapshot.fetchedAt, now, zone),
        localTime = if (otherOffset(now, zone, phoneZone)) clockAt(now, zone) else null,
        noPermission = noPermission,
        isShown = shown,
    )
}
