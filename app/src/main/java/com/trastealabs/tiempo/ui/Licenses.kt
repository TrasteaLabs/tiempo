package com.trastealabs.tiempo.ui

/**
 * 021: «Licencias de terceros» de «Acerca de». Una línea por cada librería de `gradle/libs.versions.toml` (la clave del
 * catálogo en [catalogKey]); `ThirdPartyLicensesTest` falla si el catálogo trae una que esta lista no, o al revés.
 * Las que solo sirven para compilar o probar ([shipped] = false) no van dentro de la app y no se enseñan.
 */
data class ThirdParty(val catalogKey: String?, val name: String, val license: String, val shipped: Boolean = true)

private const val APACHE = "Apache 2.0"

val THIRD_PARTY: List<ThirdParty> = listOf(
    // Dentro de la app
    ThirdParty("androidx-core-ktx", "AndroidX Core", APACHE),
    ThirdParty("androidx-activity-compose", "AndroidX Activity", APACHE),
    ThirdParty("androidx-lifecycle-viewmodel-compose", "AndroidX Lifecycle", APACHE),
    ThirdParty("androidx-lifecycle-runtime-compose", "AndroidX Lifecycle Runtime", APACHE),
    ThirdParty("compose-bom", "Jetpack Compose (lista de versiones)", APACHE, shipped = false),
    ThirdParty("compose-ui", "Jetpack Compose UI", APACHE),
    ThirdParty("compose-material3", "Material 3 para Compose", APACHE),
    ThirdParty("kotlinx-serialization-json", "kotlinx.serialization", APACHE),
    ThirdParty("kotlinx-coroutines-android", "kotlinx.coroutines", APACHE),
    ThirdParty("okhttp", "OkHttp", APACHE),
    ThirdParty("play-services-location", "Google Play services (ubicación)", "Android Software Development Kit License"),
    ThirdParty("glance-appwidget", "Jetpack Glance", APACHE),
    ThirdParty("work-runtime-ktx", "WorkManager", APACHE),
    ThirdParty("androidx-profileinstaller", "AndroidX ProfileInstaller", APACHE),
    // Solo para compilar, medir o probar
    ThirdParty("compose-ui-test-junit4", "Compose UI Test", APACHE, shipped = false),
    ThirdParty("compose-ui-test-manifest", "Compose UI Test Manifest", APACHE, shipped = false),
    ThirdParty("kotlinx-coroutines-test", "kotlinx.coroutines test", APACHE, shipped = false),
    ThirdParty("okhttp-mockwebserver", "MockWebServer", APACHE, shipped = false),
    ThirdParty("junit", "JUnit 4", "Eclipse Public License 1.0", shipped = false),
    ThirdParty("robolectric", "Robolectric", "MIT", shipped = false),
    ThirdParty("androidx-test-ext-junit", "AndroidX Test", APACHE, shipped = false),
    ThirdParty("glance-appwidget-testing", "Glance Testing", APACHE, shipped = false),
    ThirdParty("work-testing", "WorkManager Testing", APACHE, shipped = false),
    ThirdParty("androidx-benchmark-macro-junit4", "AndroidX Macrobenchmark", APACHE, shipped = false),
    ThirdParty("androidx-uiautomator", "UI Automator", APACHE, shipped = false),
    // Lo que no es una librería pero va dentro: letra, iconos y datos
    ThirdParty(null, "Google Sans Flex (letra)", "SIL Open Font License 1.1"),
    ThirdParty(null, "Material Symbols (iconos)", APACHE),
    ThirdParty(null, "GeoNames (nombres de lugares)", "CC BY 4.0"),
    ThirdParty(null, "Mapa de la Luna, NASA SVS (LRO)", "Dominio público"),
)
