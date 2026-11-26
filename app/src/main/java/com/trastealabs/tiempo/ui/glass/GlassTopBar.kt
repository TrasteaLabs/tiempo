package com.trastealabs.tiempo.ui.glass

import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.trastealabs.tiempo.R
import com.trastealabs.tiempo.ui.theme.CristalColors
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * La barra superior de Ajustes y Sitios (012 P4, Cristal 1.3 §3): una `LargeTopAppBar` de Material 3 que se pliega con
 * [scrollBehavior] (el título grande de `headlineMedium` se va y queda el pequeño de `titleLarge` junto a la flecha) y,
 * **detrás, el único cristal en vivo de la app**: la escena desenfocada, que solo se ve cuando la barra se ha plegado
 * (sin plegar, el título grande va sobre la escena sin franja). La usan `SettingsScreen` y `PlacesScreen` dentro de un
 * `Scaffold` con `nestedScroll(scrollBehavior.nestedScrollConnection)`.
 *
 * Sin márgenes de ventana: las pantallas secundarias ya van dentro de `safeDrawingPadding`. [actions] va a la derecha
 * («Editar» en Sitios). Con [opaque] (el ahorro de batería, Cristal 1.4 §5; 019) la franja es la superficie opaca de las
 * hojas, sin desenfoque en vivo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassTopBar(
    title: String,
    onBack: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    opaque: Boolean = rememberPowerSave(),
) {
    Box(modifier.fillMaxWidth().testTag("glass_top_bar")) {
        // El cristal asoma al plegarse: la lectura de la fracción va dentro de la capa (dibujo, no composición).
        Box(
            Modifier
                .matchParentSize()
                .testTag(if (opaque) "glass_top_bar_opaque" else "glass_top_bar_glass")
                .graphicsLayer { alpha = scrollBehavior.state.collapsedFraction }
                .then(if (opaque) Modifier.background(CristalColors.SheetSurface) else Modifier.glass(RectangleShape)),
        )
        LargeTopAppBar(
            title = { Text(title, color = Color.White) },
            navigationIcon = { BackButton(onBack) },
            actions = actions,
            windowInsets = WindowInsets(0, 0, 0, 0),
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent,
                navigationIconContentColor = Color.White,
                titleContentColor = Color.White,
                actionIconContentColor = Color.White,
            ),
            scrollBehavior = scrollBehavior,
        )
    }
}

/** La flecha de volver: el círculo translúcido de siempre, con la onda de Material al pulsar. */
@Composable
private fun BackButton(onBack: () -> Unit) {
    Box(
        Modifier
            .padding(start = 4.dp)
            .minimumInteractiveComponentSize()
            .size(42.dp)
            .translucent(CircleShape)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onBack),
        contentAlignment = Alignment.Center,
    ) {
        Image(painterResource(R.drawable.ic_wx_back), stringResource(R.string.back), Modifier.size(22.dp))
    }
}

/** ¿Está el ahorro de batería puesto? Se vuelve a mirar en cada `ON_START` y al momento si cambia con la pantalla abierta. */
@Composable
fun rememberPowerSave(): Boolean {
    val context = LocalContext.current
    val power = remember(context) { context.applicationContext.getSystemService(PowerManager::class.java) }
    var saving by remember(power) { mutableStateOf(power?.isPowerSaveMode == true) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, power) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) saving = power?.isPowerSaveMode == true
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    DisposableEffect(context, power) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                saving = power?.isPowerSaveMode == true
            }
        }
        val filter = IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }
    return saving
}
