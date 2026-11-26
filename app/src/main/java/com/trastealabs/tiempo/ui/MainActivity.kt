package com.trastealabs.tiempo.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.lifecycleScope
import com.trastealabs.tiempo.R
import com.trastealabs.tiempo.WeatherApp
import com.trastealabs.tiempo.domain.PlaceRef
import com.trastealabs.tiempo.refresh.RefreshScheduler
import com.trastealabs.tiempo.ui.scene.AndroidMotionSettings
import com.trastealabs.tiempo.ui.scene.rememberSkyClock
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    internal val viewModel: MainViewModel by viewModels {
        viewModelFactory {
            initializer {
                MainViewModel(
                    repository = (application as WeatherApp).repository,
                    reliability = (application as WeatherApp).reliability,
                    hasLocationPermission = ::hasLocationPermission,
                    usage = (application as WeatherApp).calls.usage,
                    refreshLog = (application as WeatherApp).refreshLog.entries,
                    saved = (application as WeatherApp).saved,
                    placeSearch = (application as WeatherApp).placeSearch,
                    hereName = getString(R.string.place_here),
                    reports = (application as WeatherApp).reports,
                    reportStore = (application as WeatherApp).reportStore,
                    aiSettings = (application as WeatherApp).aiSettings,
                    keyVault = (application as WeatherApp).keyVault,
                    morning = (application as WeatherApp).morningSettings,
                    notificationsAllowed = ::hasNotificationPermission,
                    onMorningChanged = { RefreshScheduler.scheduleMorningAlarm(applicationContext) },
                )
            }
        }
    }

    private var askedForPermission = false

    /** El consejo de batería sale una vez en la vida de la app (C8: preferencia "diálogo visto"). */
    private val prefs by lazy { getSharedPreferences(PREFS, MODE_PRIVATE) }
    private var batteryTipPending by mutableStateOf(false)

    /** La última temperatura vista de «donde estoy» (012 P9, C8): se lee al crear, antes de que `onStop` la sustituya. */
    private val lastSeen by lazy { LastSeenTemp(prefs) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        batteryTipPending = !prefs.getBoolean(BATTERY_TIP_SEEN, false)
        val lastSeenTemp = lastSeen.get()
        // Un ViewModel nuevo ya empieza en «donde estoy» (H9); el extra del widget lo dice también aquí.
        openHereIfAsked(intent)
        setContent {
            val state = viewModel.uiState.collectAsStateWithLifecycle().value
            val cards = viewModel.placeCards.collectAsStateWithLifecycle().value
            val search = viewModel.search.collectAsStateWithLifecycle().value
            val askState = viewModel.askState.collectAsStateWithLifecycle().value
            val update = (application as WeatherApp).updater.state.collectAsStateWithLifecycle().value
            val motionSettings = remember { AndroidMotionSettings(this) }
            val skyClock = rememberSkyClock(motionSettings)
            // La entrada al abrir en frío (007, H15): se decide con el primer estado que no sea la carga.
            val intro = rememberMainIntro(state, (application as WeatherApp).introGate, motionSettings)
            MainScreen(
                state = state,
                onRefresh = viewModel::refresh,
                onRetry = viewModel::refresh,
                onRequestPermission = ::requestLocationPermission,
                showBatteryTip = batteryTipPending,
                onBatteryTipSettings = {
                    batteryTipSeen()
                    openAppSettings()
                },
                onBatteryTipDismiss = ::batteryTipSeen,
                placeCards = cards,
                search = search,
                onPlacesOpen = ::loadPlaces,
                onSelectPlace = viewModel::select,
                onQueryChange = viewModel::setQuery,
                onSaveAndOpen = viewModel::saveAndOpen,
                onDeletePlace = viewModel::delete,
                onAi = viewModel::onAi,
                onMorningToggle = viewModel::setMorning,
                onMorningTime = viewModel::setMorningTime,
                askState = askState,
                onAsk = viewModel::ask,
                onCancelAsk = viewModel::cancelAsk,
                onCloseAsk = viewModel::closeAsk,
                skyClock = skyClock,
                intro = intro,
                lastSeenTemp = lastSeenTemp,
                update = update,
                onUpdate = ::onUpdate,
            )
        }
        if (!hasLocationPermission()) requestLocationPermission()
    }

    /** Tocar el widget con la actividad ya abierta (en un guardado): vuelve a «donde estoy» (H9). */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openHereIfAsked(intent)
    }

    private fun openHereIfAsked(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_HERE, false) == true) viewModel.select(PlaceRef.Here)
    }

    /** La pantalla Sitios carga la lista de lugares al abrirse (una sola vez por proceso), para que buscar no espere. */
    private fun loadPlaces() {
        val index = (application as WeatherApp).placeIndex
        if (!index.isLoaded) lifecycleScope.launch { runCatching { index.load() } }
    }

    override fun onStart() {
        super.onStart()
        viewModel.onStart()
        viewModel.morningPermissionMayHaveChanged()
        // 021: ¿hay versión nueva? Solo al abrir la app y como mucho una vez al día; sin red, en silencio.
        val app = application as WeatherApp
        app.appScope.launch { app.updater.checkIfDue() }
    }

    /** Lo del actualizador (021) va en el ámbito de la app: una bajada sigue aunque se gire la pantalla. */
    private fun onUpdate(action: UpdateAction) {
        val app = application as WeatherApp
        val updater = app.updater
        when (action) {
            UpdateAction.CheckNow -> app.appScope.launch { updater.checkNow() }
            is UpdateAction.SetBeta -> {
                updater.setBeta(action.on)
                app.appScope.launch { updater.checkNow() }
            }
            UpdateAction.Download -> app.appScope.launch { updater.downloadAndInstall() }
            UpdateAction.Install -> app.appScope.launch { updater.install() }
            UpdateAction.Retry -> app.appScope.launch { updater.retry() }
            UpdateAction.Dismiss -> updater.dismiss()
        }
    }

    /** Lo que enseñaba la principal para «donde estoy» es el punto de partida de la cifra la próxima vez que se abra en frío. */
    override fun onStop() {
        super.onStop()
        saveLastSeen()
    }

    internal fun saveLastSeen() {
        lastSeenToSave(viewModel.uiState.value)?.let(lastSeen::put)
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST) viewModel.refresh()
    }

    /** El permiso de notificaciones (018 P2): el interruptor de la mañana lo refleja al volver a la app. */
    private fun hasNotificationPermission(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun hasLocationPermission(): Boolean = listOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    ).any { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }

    /** Primera vez y mientras Android deje preguntar: el diálogo del sistema. Si ya no deja, los ajustes de la app. */
    private fun requestLocationPermission() {
        val canAsk = !askedForPermission ||
            shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) ||
            shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (canAsk) {
            askedForPermission = true
            requestPermissions(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                PERMISSION_REQUEST,
            )
        } else {
            openAppSettings()
        }
    }

    private fun batteryTipSeen() {
        batteryTipPending = false
        prefs.edit { putBoolean(BATTERY_TIP_SEEN, true) }
    }

    /** Ajustes de la app en el móvil: ahí están la ubicación "todo el tiempo" y la batería de ColorOS. */
    private fun openAppSettings() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
    }

    companion object {
        /** Lo pone el clic del widget: abrir siempre en «donde estoy» (H9). */
        const val EXTRA_OPEN_HERE = "com.trastealabs.tiempo.OPEN_HERE"

        private const val PERMISSION_REQUEST = 1
        private const val PREFS = "prefs"
        private const val BATTERY_TIP_SEEN = "battery_tip_seen"
    }
}
