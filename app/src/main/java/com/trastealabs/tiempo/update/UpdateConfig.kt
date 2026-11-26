package com.trastealabs.tiempo.update

import com.trastealabs.tiempo.BuildConfig

/**
 * 021: dónde vive la app en GitHub, en un solo sitio. De aquí salen la consulta de versiones, los enlaces de
 * «Acerca de» y el User-Agent. Para otra app de la casa basta con cambiar [OWNER], [REPO] y [APK_SLUG].
 */
object UpdateConfig {
    const val OWNER = "trastealabs"
    const val REPO = "tiempo"

    /** Prefijo del APK de release: `tiempo-<versionName>-<versionCode>.apk` (lo fija `app/build.gradle.kts`). */
    const val APK_SLUG = "tiempo"

    const val REPO_URL = "https://github.com/$OWNER/$REPO"
    const val RELEASES_API_URL = "https://api.github.com/repos/$OWNER/$REPO/releases?per_page=30"
    const val NEW_ISSUE_URL = "$REPO_URL/issues/new/choose"
    const val PRIVACY_URL = "$REPO_URL/blob/main/PRIVACY.md"
    const val LICENSE_URL = "$REPO_URL/blob/main/LICENSE"

    /** 022: el correo del estudio, junto a los issues de GitHub en «Acerca de», el README, PRIVACY y SECURITY. */
    const val CONTACT_EMAIL = "hola@trastealabs.com"

    /** Como mucho una consulta cada 24 h al abrir la app (GitHub sin clave admite 60 por hora y por IP). */
    const val CHECK_INTERVAL_SECONDS = 24 * 60 * 60L

    /**
     * La versión de desarrollo (y la de medir, `.dev` también) no se actualiza nunca: su `versionCode` no es el de
     * las releases y no está firmada con la clave de release.
     */
    val enabled: Boolean get() = !BuildConfig.APPLICATION_ID.endsWith(".dev")

    /** User-Agent de todas las llamadas: identificable para Open-Meteo y GitHub, sin nada del aparato. */
    val userAgent: String get() = "Tiempo/${BuildConfig.VERSION_NAME} (+$REPO_URL)"
}
