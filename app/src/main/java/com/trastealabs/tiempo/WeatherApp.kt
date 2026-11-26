package com.trastealabs.tiempo

import android.app.Application
import androidx.glance.appwidget.updateAll
import androidx.work.Configuration
import com.trastealabs.tiempo.data.AemetClient
import com.trastealabs.tiempo.data.AssetPlaceLookup
import com.trastealabs.tiempo.data.CallCounter
import com.trastealabs.tiempo.data.CallCountingInterceptor
import com.trastealabs.tiempo.data.FusedLocationSource
import com.trastealabs.tiempo.data.MorningSettings
import com.trastealabs.tiempo.data.OpenMeteoAirClient
import com.trastealabs.tiempo.data.OpenMeteoClient
import com.trastealabs.tiempo.data.OpenMeteoReliabilityClient
import com.trastealabs.tiempo.data.PlaceIndex
import com.trastealabs.tiempo.data.PlaceSearch
import com.trastealabs.tiempo.data.PlaceStores
import com.trastealabs.tiempo.data.RefreshLog
import com.trastealabs.tiempo.data.ReliabilityStore
import com.trastealabs.tiempo.data.ReportStore
import com.trastealabs.tiempo.data.ai.AiSettings
import com.trastealabs.tiempo.data.ai.ClaudeWriter
import com.trastealabs.tiempo.data.ai.GeminiWriter
import com.trastealabs.tiempo.data.ai.KeyVault
import com.trastealabs.tiempo.data.ai.KeystoreKeyVault
import com.trastealabs.tiempo.data.ai.OpenAiWriter
import com.trastealabs.tiempo.data.ai.ReportScope
import com.trastealabs.tiempo.domain.AiProvider
import com.trastealabs.tiempo.data.SavedPlacesStore
import com.trastealabs.tiempo.data.SnapshotStore
import com.trastealabs.tiempo.data.UserAgentInterceptor
import com.trastealabs.tiempo.data.aemetHttp
import com.trastealabs.tiempo.refresh.MorningNotice
import com.trastealabs.tiempo.refresh.MorningNotifier
import com.trastealabs.tiempo.refresh.RefreshOrigin
import com.trastealabs.tiempo.refresh.RefreshScheduler
import com.trastealabs.tiempo.refresh.ReportRepository
import com.trastealabs.tiempo.refresh.ReliabilityRepository
import com.trastealabs.tiempo.refresh.SavedPlacesRepository
import com.trastealabs.tiempo.refresh.Trigger
import com.trastealabs.tiempo.refresh.WeatherRepository
import com.trastealabs.tiempo.ui.IntroGate
import com.trastealabs.tiempo.update.AndroidApkInspector
import com.trastealabs.tiempo.update.GithubReleaseSource
import com.trastealabs.tiempo.update.HttpApkDownloader
import com.trastealabs.tiempo.update.SessionApkInstaller
import com.trastealabs.tiempo.update.UpdateConfig
import com.trastealabs.tiempo.update.UpdateSettings
import com.trastealabs.tiempo.update.Updater
import com.trastealabs.tiempo.widget.WeatherWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Crea las dependencias a mano (sin Hilt): son pocas y viven lo que la app.
 * También programa el refresco en segundo plano; WorkManager se inicia bajo demanda con [workManagerConfiguration].
 */
class WeatherApp : Application(), Configuration.Provider {
    lateinit var repository: WeatherRepository
        private set
    lateinit var reliability: ReliabilityRepository
        private set
    lateinit var calls: CallCounter
        private set
    /** El registro de refrescos de «donde estoy» (015 P2), para Ajustes. */
    lateinit var refreshLog: RefreshLog
        private set
    /** El parte del día con IA (016 P3): se pide después de un refresco bueno y nunca lo retrasa. */
    lateinit var reports: ReportRepository
        private set
    /** El parte de «donde estoy» (`files/ai_report.json`). */
    lateinit var reportStore: ReportStore
        private set
    lateinit var aiSettings: AiSettings
        private set
    lateinit var keyVault: KeyVault
        private set
    /** La notificación de la mañana (018 P2): interruptor, hora y último día avisado. */
    lateinit var morningSettings: MorningSettings
        private set
    /** Lo que avisa cada mañana (018 P2); lo llaman la alarma de la hora y, de red de seguridad, `RefreshWorker`. */
    lateinit var morningNotice: MorningNotice
        private set
    lateinit var saved: SavedPlacesRepository
        private set
    /**
     * 021: el actualizador. Perezoso: no se crea en el widget ni en el segundo plano, solo al abrir la app o cuando
     * avisa el instalador.
     */
    val updater: Updater by lazy { createUpdater() }

    /** Lo que debe seguir aunque se gire la pantalla o se cierre la actividad (la bajada de una versión nueva). */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var http: OkHttpClient

    /** La lista de lugares en memoria (006): una sola carga para el nombre de «donde estoy» y para la búsqueda. */
    lateinit var placeIndex: PlaceIndex
        private set
    lateinit var placeSearch: PlaceSearch
        private set

    /**
     * La entrada al abrir (007, H15), una por proceso: nace con él, así que sale al abrir en frío (también cuando
     * ColorOS ha cerrado la app en segundo plano) y no al volver con Inicio. La gasta `MainActivity`.
     */
    val introGate = IntroGate()

    override fun onCreate() {
        super.onCreate()
        // El fichero `prefs` de siempre (el del consejo de batería).
        calls = CallCounter(getSharedPreferences("prefs", MODE_PRIVATE))
        http = OkHttpClient.Builder()
            // 021: identificable en todas las llamadas (Open-Meteo, AEMET, la IA y GitHub), sin nada del aparato.
            .addInterceptor(UserAgentInterceptor(UpdateConfig.userAgent))
            // Toda petición a Open-Meteo suma su peso, también las de segundo plano (H12). De red: cuenta cada
            // intento que llega al servidor, también los reintentos y redirecciones de OkHttp.
            .addNetworkInterceptor(CallCountingInterceptor(calls))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
        // Uno solo para los dos repositorios: SnapshotStore guarda en memoria lo último que leyó o escribió.
        val snapshots = SnapshotStore(File(filesDir, "snapshot.json"))
        // Los guardados (006), aparte: «donde estoy» sigue en snapshot.json y reliability.json (H13, H14).
        saved = SavedPlacesRepository(
            store = SavedPlacesStore(File(filesDir, "saved_places.json")),
            stores = PlaceStores(File(filesDir, "places")),
        )
        // AGP descomprime el asset `places.tsv.gz` al empaquetar y le quita el .gz
        placeIndex = PlaceIndex { assets.open("places.tsv") }
        placeSearch = PlaceSearch(placeIndex)
        refreshLog = RefreshLog(File(filesDir, "refresh_log.jsonl"))
        repository = WeatherRepository(
            weather = OpenMeteoClient(http),
            // Solo AEMET lleva caché en disco (015): Open-Meteo, aire y fiabilidad van con `http`, sin ella.
            warnings = AemetClient(aemetHttp(http, cacheDir)),
            air = OpenMeteoAirClient(http),
            places = AssetPlaceLookup(placeIndex),
            location = FusedLocationSource(this),
            store = snapshots,
            onUpdated = { WeatherWidget().updateAll(this) },
            saved = saved,
            log = refreshLog,
        )
        reliability = ReliabilityRepository(
            source = OpenMeteoReliabilityClient(http),
            store = ReliabilityStore(File(filesDir, "reliability.json")),
            snapshots = snapshots,
            saved = saved,
        )
        // IA (016 P3): su propio cliente, con tope de 40 s por llamada. Va aparte del contador de Open-Meteo, que solo
        // cuenta sus peticiones por la dirección.
        val aiHttp = http.newBuilder().callTimeout(40, TimeUnit.SECONDS).build()
        val aiPrefs = getSharedPreferences("ai", MODE_PRIVATE)
        keyVault = KeystoreKeyVault(aiPrefs)
        aiSettings = AiSettings(aiPrefs)
        reportStore = ReportStore(File(filesDir, "ai_report.json"))
        reports = ReportRepository(
            writerFor = { provider, key ->
                when (provider) {
                    AiProvider.CLAUDE -> ClaudeWriter(aiHttp, key)
                    AiProvider.GEMINI -> GeminiWriter(aiHttp, key)
                    AiProvider.OPENAI -> OpenAiWriter(aiHttp, key)
                }
            },
            vault = keyVault,
            settings = aiSettings,
            nowSeconds = { System.currentTimeMillis() / 1000 },
            // 018: el widget 4×2 enseña la frase del parte; hay que repintarlo cuando llega uno nuevo.
            onSaved = { WeatherWidget().updateAll(this) },
        )
        morningSettings = MorningSettings.of(this)
        morningNotice = MorningNotice(
            settings = morningSettings,
            refresh = { repository.refresh(Trigger.BACKGROUND, RefreshOrigin.MORNING) },
            snapshot = { repository.state.first().snapshot },
            requestReport = { reports.refreshIfDue(it, reportStore, ReportScope.HERE) },
            reportStore = reportStore,
            log = refreshLog,
            canPost = { MorningNotifier.canPost(this) },
            post = { MorningNotifier.post(this, it) },
            strings = { MorningNotifier.strings(this) },
        )
        // Fuera del hilo principal: arranca WorkManager y pone las alarmas (~6 ms en el OPPO, 008 Z2-01).
        Thread({ RefreshScheduler.schedule(this) }, "refresh-schedule").start()
    }

    private fun createUpdater(): Updater {
        // La bajada del APK (~9 MB) puede tardar: sin tope por llamada, con el de lectura de siempre entre trozos.
        val updateHttp = http.newBuilder().build()
        return Updater(
            source = GithubReleaseSource(updateHttp),
            settings = UpdateSettings.of(this),
            downloader = HttpApkDownloader(updateHttp),
            inspector = AndroidApkInspector(this),
            installer = SessionApkInstaller(this),
            downloadDir = File(cacheDir, "updates"),
            installedCode = BuildConfig.VERSION_CODE,
            installedName = BuildConfig.VERSION_NAME,
            enabled = UpdateConfig.enabled,
            nowSeconds = { System.currentTimeMillis() / 1000 },
        )
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()
}
