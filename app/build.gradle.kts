import com.android.build.api.variant.impl.VariantOutputImpl

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.androidx.baselineprofile)
}

// Versión (021): nombre en version.properties, código = commits (o -PversionCode / VERSION_CODE). Ver buildSrc.
val appVersionName = AppVersion.versionName(rootProject.file("version.properties").takeIf { it.isFile }?.readText())
val appVersionCode = AppVersion.versionCode(
    property = providers.gradleProperty("versionCode").orNull,
    env = providers.environmentVariable("VERSION_CODE").orNull,
    gitCount = {
        providers.exec {
            commandLine("git", "rev-list", "--count", "HEAD")
            workingDir = rootDir
            isIgnoreExitValue = true
        }.standardOutput.asText.get()
    },
)

// Firma (021): las variables RELEASE_* si están las cuatro (GitHub Actions); si no, la clave del PC, fuera del repo.
// Sin ninguna, assembleRelease sigue funcionando pero sin firmar, y lo avisa; con RELEASE_SIGNING_REQUIRED=true
// (GitHub Actions) falla en vez de sacar un APK sin firmar.
val (releaseSigning, signingWarning) = ReleaseSigning.resolve(
    env = { providers.environmentVariable(it).orNull },
    propertiesFile = File(System.getProperty("user.home"), ".weatherapp/keystore.properties"),
)
if (signingWarning != null && gradle.startParameter.taskNames.any { it.contains("release", ignoreCase = true) }) {
    if (releaseSigning == null && providers.environmentVariable("RELEASE_SIGNING_REQUIRED").orNull == "true") {
        throw GradleException("Firma de la release: $signingWarning")
    }
    logger.warn("Firma de la release: $signingWarning")
}

android {
    namespace = "com.trastealabs.tiempo"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.trastealabs.tiempo"
        minSdk = 31
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
    }

    signingConfigs {
        if (releaseSigning != null) {
            create("release") {
                storeFile = releaseSigning.storeFile
                storePassword = releaseSigning.storePassword
                keyAlias = releaseSigning.keyAlias
                keyPassword = releaseSigning.keyPassword
            }
        }
    }

    buildTypes {
        // 017: la versión de desarrollo es otra app («Tiempo dev», com.trastealabs.tiempo.dev) junto a la release de uso diario.
        debug {
            applicationIdSuffix = ".dev"
        }
        release {
            // 008 (H10): R8 y recursos. Las librerías traen sus reglas; las nuestras, en proguard-rules.pro.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
        // Para medir (008): el código de la release, sin depuración, con la demo y la clave de debug,
        // así se instala encima de la debug sin perder nada. Va después de `release` para copiarla.
        create("perf") {
            initWith(getByName("release"))
            proguardFile("proguard-perf.pro")   // lo que la demo lee por reflexión
            signingConfig = signingConfigs.getByName("debug")
            isDebuggable = false
            isProfileable = true
            applicationIdSuffix = ".dev"
            matchingFallbacks += "release"
        }
    }

    // También en los tipos que crea el plugin de perfiles a partir de `perf` (012): llevan la demo, que es lo que se mide.
    sourceSets.configureEach {
        if (name == "perf" || name == "benchmarkPerf" || name == "nonMinifiedPerf") {
            kotlin.directories.add("src/debug/java")
            manifest.srcFile("src/debug/AndroidManifest.xml")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true   // 021: versión y repo para el actualizador y el User-Agent
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
            // 008 (H12): Robolectric en varios procesos.
            all {
                it.maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
                // 022: los tests corren con la zona y el idioma fijos, igual en este PC que en GitHub (UTC, inglés).
                // Un test que dependa de la zona del aparato falla aquí también, no solo en GitHub.
                it.systemProperty("user.timezone", "UTC")
                it.systemProperty("user.language", "en")
                it.systemProperty("user.country", "US")
            }
        }
    }

    lint {
        abortOnError = true
        warningsAsErrors = false
    }
}

// 021: el APK de release se llama tiempo-<versionName>-<versionCode>.apk; el actualizador lee el código del nombre.
androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        variant.outputs.forEach { output ->
            (output as? VariantOutputImpl)?.outputFileName?.set(AppVersion.apkName("tiempo", appVersionName, appVersionCode))
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.play.services.location)
    implementation(libs.glance.appwidget)
    implementation(libs.work.runtime.ktx)
    implementation(libs.androidx.profileinstaller)
    baselineProfile(project(":benchmark"))

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.glance.appwidget.testing)
    testImplementation(libs.work.testing)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}

// 012 (H1): el perfil de base se genera a mano con `sh tools/bench.sh perfil`, solo en el emulador, y el fichero va al repo.
baselineProfile {
    mergeIntoMain = true
    automaticGenerationDuringBuild = false
}
