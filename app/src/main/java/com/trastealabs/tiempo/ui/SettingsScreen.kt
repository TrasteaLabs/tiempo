package com.trastealabs.tiempo.ui

import androidx.compose.foundation.layout.imePadding
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import com.trastealabs.tiempo.BuildConfig
import com.trastealabs.tiempo.R
import com.trastealabs.tiempo.data.RefreshLogEntry
import com.trastealabs.tiempo.data.RefreshOutcome
import com.trastealabs.tiempo.domain.ApiUsage
import com.trastealabs.tiempo.domain.DAILY_CALL_LIMIT
import com.trastealabs.tiempo.domain.MONTHLY_CALL_LIMIT
import com.trastealabs.tiempo.domain.gapText
import com.trastealabs.tiempo.domain.groupedThousands
import com.trastealabs.tiempo.domain.kbText
import com.trastealabs.tiempo.domain.monthCalls
import com.trastealabs.tiempo.domain.refreshSummary
import com.trastealabs.tiempo.domain.todayCalls
import com.trastealabs.tiempo.domain.usagePercent
import com.trastealabs.tiempo.refresh.AemetOutcome
import com.trastealabs.tiempo.refresh.RefreshOrigin
import com.trastealabs.tiempo.ui.glass.GlassTopBar
import com.trastealabs.tiempo.ui.glass.translucent
import com.trastealabs.tiempo.ui.glass.sceneText
import com.trastealabs.tiempo.ui.theme.OnScreen
import com.trastealabs.tiempo.ui.theme.AppFigures
import com.trastealabs.tiempo.ui.theme.CristalType
import com.trastealabs.tiempo.ui.theme.Tabular
import com.trastealabs.tiempo.update.UpdateState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import com.trastealabs.tiempo.update.UpdateConfig
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Ajustes (010, Cristal; H11 del 003 y H3): el consumo de Open-Meteo de hoy y del mes sobre la escena desenfocada,
 * los refrescos de «donde estoy» (015 P2, H11) y, al final, «Acerca de». Se calcula con `now` al pintar: pasada la
 * medianoche «hoy» sale 0 aunque no haya salido ninguna petición.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    usage: ApiUsage,
    now: Long,
    onBack: () -> Unit,
    zone: ZoneId = ZoneId.systemDefault(),
    refreshes: List<RefreshLogEntry> = emptyList(),
    /** La sección «IA» (016 P3). */
    ai: AiPanel = AiPanel(),
    onAi: (AiAction) -> Unit = {},
    /** «Parte de la mañana» (018 P2). */
    morning: MorningPanel = MorningPanel(),
    onMorningToggle: (Boolean) -> Unit = {},
    onMorningTime: (Int) -> Unit = {},
    /** «Versión de la app» (021). Null: no se enseña. */
    update: UpdateState? = null,
    onUpdate: (UpdateAction) -> Unit = {},
    /** Se ha abierto desde la línea de versión nueva de la principal: baja hasta la sección. */
    focusUpdate: Boolean = false,
) {
    val today = usage.todayCalls(now, zone)
    val updateRequester = remember { BringIntoViewRequester() }
    LaunchedEffect(focusUpdate, update != null) {
        if (focusUpdate && update != null) {
            // Tras la entrada de lado (011): pedirlo antes de medir no mueve nada.
            kotlinx.coroutines.delay(350)
            updateRequester.bringIntoView()
        }
    }
    val month = usage.monthCalls(now, zone)
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.testTag("settings_screen").nestedScroll(scroll.nestedScrollConnection),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { GlassTopBar(title = stringResource(R.string.settings), onBack = onBack, scrollBehavior = scroll) },
    ) { inner ->
        Column(
            Modifier
                .padding(inner)
                // El teclado empuja el contenido: el campo de la clave no queda tapado.
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 12.dp, end = 12.dp, bottom = 26.dp),
        ) {
            Column(Modifier.testTag("settings_section")) {
                Section(stringResource(R.string.usage_title))
                Figure(
                    label = stringResource(R.string.usage_today),
                    calls = today,
                    detail = stringResource(
                        R.string.usage_today_detail,
                        groupedThousands(DAILY_CALL_LIMIT),
                        usagePercent(today, DAILY_CALL_LIMIT),
                    ),
                    fraction = today.toFloat() / DAILY_CALL_LIMIT,
                )
                Figure(
                    label = stringResource(R.string.usage_month),
                    calls = month,
                    detail = stringResource(
                        R.string.usage_month_detail,
                        groupedThousands(MONTHLY_CALL_LIMIT),
                        usagePercent(month, MONTHLY_CALL_LIMIT),
                    ),
                    fraction = month.toFloat() / MONTHLY_CALL_LIMIT,
                )
                Text(
                    text = stringResource(R.string.usage_note),
                    color = OnScreen.Secondary,
                    style = sceneText(CristalType.bodySmall),
                    modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 12.dp),
                )
            }
            Section(stringResource(R.string.ai_title))
            AiSection(ai, now, zone, onAi)
            Section(stringResource(R.string.morning_title_section))
            MorningSection(morning, now, zone, onMorningToggle, onMorningTime)
            Section(stringResource(R.string.refresh_title))
            Refreshes(refreshes, now, zone)
            if (update != null) {
                Column(Modifier.bringIntoViewRequester(updateRequester)) {
                    Section(stringResource(R.string.update_title))
                    UpdateSection(update, now, zone, onUpdate)
                }
            }
            Section(stringResource(R.string.about_title))
            About()
        }
    }
}

@Composable
private fun Section(text: String) {
    Text(
        text,
        color = Color.White,
        style = sceneText(CristalType.titleMedium),
        modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 24.dp, bottom = 8.dp),
    )
}

/** Una cifra sobre la escena: el nombre, la cifra grande con «llamadas» al 50 %, «de 10.000 · 2 % · …» y la barra. */
@Composable
private fun Figure(label: String, calls: Long, detail: String, fraction: Float) {
    Column(Modifier.padding(start = 6.dp, end = 6.dp, top = 14.dp).fillMaxWidth()) {
        Text(label, color = OnScreen.Text, style = sceneText(CristalType.titleSmall))
        val unit = stringResource(R.string.usage_unit)
        // Una sola línea siempre (017 H2): si la cifra con su «llamadas» no cabe, todo encoge un poco hasta que cabe.
        var shrink by remember(calls) { mutableFloatStateOf(1f) }
        val base = AppFigures.usageFigure.merge(Tabular)
        Text(
            text = buildAnnotatedString {
                append(groupedThousands(calls))
                withStyle(SpanStyle(fontSize = 32.sp * shrink, fontWeight = FontWeight.Normal, color = OnScreen.Tertiary)) {
                    append(" ")
                    append(unit)
                }
            },
            color = Color.White,
            maxLines = 1,
            softWrap = false,
            onTextLayout = { if (it.hasVisualOverflow && shrink > 0.5f) shrink *= 0.92f },
            style = sceneText(base.copy(fontSize = base.fontSize * shrink, lineHeight = base.lineHeight * shrink)),
        )
        Text(detail, color = OnScreen.Secondary, style = sceneText(CristalType.bodyMedium.merge(Tabular)))
        UsageBar(fraction)
    }
}

/**
 * «Refrescos» (015 P2, H11, H12) en una tarjeta translúcida: el último refresco de «donde estoy», el peor hueco de las
 * últimas 24 h («el dato llegó a tener 41 min») y la lista de esas 24 h, la más reciente arriba. Sin ninguna línea,
 * solo el aviso de que aún no hay.
 */
@Composable
private fun Refreshes(refreshes: List<RefreshLogEntry>, now: Long, zone: ZoneId) {
    val summary = remember(refreshes, now) { refreshSummary(refreshes, now) }
    val last = summary.last
    Column(
        Modifier
            .fillMaxWidth()
            .testTag("refresh_card")
            .translucent(RoundedCornerShape(28.dp))
            .padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 10.dp),
    ) {
        if (last == null) {
            Text(
                stringResource(R.string.refresh_empty),
                color = OnScreen.Secondary,
                style = sceneText(CristalType.bodyMedium),
                modifier = Modifier.padding(bottom = 6.dp),
            )
            return@Column
        }
        Column(Modifier.testTag("refresh_last").semantics(mergeDescendants = true) {}) {
            Text(
                stringResource(R.string.refresh_last),
                color = OnScreen.Text,
                style = sceneText(CristalType.titleSmall),
            )
            Text(
                refreshTime(last.at, now, zone),
                color = Color.White,
                style = sceneText(CristalType.titleLarge.merge(Tabular)),
            )
            Text(refreshWhat(last, zone), color = OnScreen.Secondary, style = sceneText(CristalType.bodyMedium))
        }
        summary.worstGapSeconds?.let { gap ->
            Text(
                stringResource(R.string.refresh_worst),
                color = OnScreen.Text,
                style = sceneText(CristalType.titleSmall),
                modifier = Modifier.padding(top = 14.dp),
            )
            Text(
                stringResource(R.string.refresh_worst_value, gapText(gap)),
                color = Color.White,
                style = sceneText(CristalType.bodyMedium.merge(Tabular)),
            )
        }
        Box(Modifier.padding(top = 14.dp).fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.14f)))
        if (summary.last24h.isEmpty()) {
            Text(
                stringResource(R.string.refresh_none_24h),
                color = OnScreen.Secondary,
                style = sceneText(CristalType.bodyMedium),
                modifier = Modifier.padding(vertical = 10.dp),
            )
        } else {
            Text(
                stringResource(R.string.refresh_list),
                color = OnScreen.Text,
                style = sceneText(CristalType.titleSmall),
                modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
            )
            summary.last24h.forEachIndexed { index, entry ->
                if (index > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.10f)))
                RefreshRow(entry, now, zone)
            }
        }
    }
}

@Composable
private fun RefreshRow(entry: RefreshLogEntry, now: Long, zone: ZoneId) {
    Row(
        Modifier.fillMaxWidth().testTag("refresh_row").semantics(mergeDescendants = true) {}.padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            refreshTime(entry.at, now, zone),
            color = Color.White,
            style = sceneText(CristalType.titleSmall.merge(Tabular)),
            modifier = Modifier.width(92.dp),
        )
        Column {
            Text(refreshWhat(entry, zone), color = Color.White, style = sceneText(CristalType.bodyMedium))
            val aemet = aemetText(entry)
            if (aemet != null) {
                Text(aemet, color = OnScreen.Secondary, style = sceneText(CristalType.bodySmall.merge(Tabular)))
            }
        }
    }
}

private val HOUR_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)
private val DATE_HOUR_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM HH:mm", Locale.ROOT)

/** La hora local del refresco: «10:15», «ayer 23:50» o, si es más vieja, «08/06 12:00». */
@Composable
private fun refreshTime(at: Long, now: Long, zone: ZoneId): String {
    val time = Instant.ofEpochSecond(at).atZone(zone)
    val today = Instant.ofEpochSecond(now).atZone(zone).toLocalDate()
    return when (time.toLocalDate()) {
        today -> HOUR_FORMAT.format(time)
        today.minusDays(1) -> stringResource(R.string.refresh_yesterday, HOUR_FORMAT.format(time))
        else -> DATE_HOUR_FORMAT.format(time)
    }
}

/**
 * «Segundo plano · Bien»: quién lo pidió y cómo acabó. Una línea de aviso de la mañana (018 P2) dice, en cambio,
 * «Parte de la mañana · pedido 7:30 · con IA».
 */
@Composable
private fun refreshWhat(entry: RefreshLogEntry, zone: ZoneId): String {
    entry.morning?.let { mark ->
        val asked = Instant.ofEpochSecond(mark.askedAt).atZone(zone)
        val how = stringResource(if (mark.withAi) R.string.morning_last_ai else R.string.morning_last_rules)
        return stringResource(R.string.refresh_morning_notice, morningClock(asked.hour * 60 + asked.minute), how)
    }
    val origin = stringResource(
        when (entry.origin) {
            RefreshOrigin.WORKER -> R.string.refresh_origin_worker
            RefreshOrigin.ALARM -> R.string.refresh_origin_alarm
            RefreshOrigin.APP -> R.string.refresh_origin_app
            RefreshOrigin.MORNING -> R.string.refresh_origin_morning
        },
    )
    val outcome = stringResource(
        when (entry.outcome) {
            RefreshOutcome.OK -> R.string.refresh_ok
            RefreshOutcome.OFFLINE -> R.string.refresh_offline
            RefreshOutcome.NO_LOCATION -> R.string.refresh_no_location
        },
    )
    return "$origin · $outcome"
}

/** Lo de AEMET en ese refresco; null si no aplica (sin conexión, sin ubicación o fuera de España). */
@Composable
private fun aemetText(entry: RefreshLogEntry): String? = when {
    entry.aemet == AemetOutcome.NOT_APPLICABLE -> null
    entry.aemet == AemetOutcome.FAILED -> stringResource(R.string.refresh_aemet_failed)
    // Una consulta que no bajó nada es «sin cambios»: «nuevos» solo si llegó algo (decisión de la coordinadora, 015 P2).
    entry.aemet == AemetOutcome.NEW && entry.aemetBytes > 0 ->
        stringResource(R.string.refresh_aemet_new, kbText(entry.aemetBytes))
    // Sin cambios pero con algo de red (el RSS cambió y enlazaba el mismo tar.gz): se dice cuánto.
    entry.aemetBytes > 0 -> stringResource(R.string.refresh_aemet_unchanged_kb, kbText(entry.aemetBytes))
    else -> stringResource(R.string.refresh_aemet_unchanged)
}

/** «Acerca de» en una tarjeta de cristal: icono, nombre y versión, la luna, las fuentes, la letra y el sello. */
@Composable
private fun About() {
    val context = LocalContext.current
    val icon = remember(context) { launcherIcon(context) }
    Column(
        Modifier
            .fillMaxWidth()
            .testTag("about_card")
            .translucent(RoundedCornerShape(28.dp))
            .padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            if (icon != null) {
                Image(icon.asImageBitmap(), null, Modifier.size(56.dp).clip(RoundedCornerShape(30)))
            }
            Column {
                Text(stringResource(R.string.app_name), color = Color.White, style = sceneText(CristalType.titleLarge))
                Text(
                    stringResource(R.string.about_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                    color = OnScreen.Secondary,
                    style = sceneText(CristalType.bodyMedium.merge(Tabular)),
                )
            }
        }
        // 021: las atribuciones que piden Open-Meteo (enlace junto a los datos, CC BY 4.0) y AEMET (nota legal: servicio
        // con valor añadido, «información elaborada utilizando, entre otras…», sin dar a entender que la patrocina).
        Column(Modifier.padding(top = 14.dp)) {
            AboutRow(R.drawable.ic_wx_cloudy, null, stringResource(R.string.about_openmeteo), url = OPEN_METEO_URL, tag = "about_openmeteo")
            AboutRow(R.drawable.ic_wx_warning, null, stringResource(R.string.about_aemet), url = AEMET_URL, tag = "about_aemet")
            AboutRow(R.drawable.ic_wx_air, null, stringResource(R.string.about_air))
            AboutRow(R.drawable.ic_wx_location, null, stringResource(R.string.about_places))
            AboutRow(R.drawable.ic_wx_clear_night, null, stringResource(R.string.about_moon))
            AboutRow(null, "Aa", stringResource(R.string.about_font))
        }
        Column {
            AboutRow(R.drawable.ic_wx_code, null, stringResource(R.string.about_source_code), url = UpdateConfig.REPO_URL, tag = "about_code")
            AboutRow(R.drawable.ic_wx_block, null, stringResource(R.string.about_privacy), url = UpdateConfig.PRIVACY_URL, tag = "about_privacy")
            AboutRow(R.drawable.ic_wx_chat, null, stringResource(R.string.about_feedback), url = UpdateConfig.NEW_ISSUE_URL, tag = "about_feedback")
            // 022: el correo, como texto que se lee entero; tocar abre el correo y mantener pulsado lo copia.
            val copyEmail = rememberCopyText(UpdateConfig.CONTACT_EMAIL)
            AboutRow(
                R.drawable.ic_wx_mail,
                null,
                stringResource(R.string.about_email, UpdateConfig.CONTACT_EMAIL),
                url = "mailto:${UpdateConfig.CONTACT_EMAIL}",
                tag = "about_email",
                onLongClick = copyEmail,
                onLongClickLabel = stringResource(R.string.about_email_copy),
            )
            ThirdPartyRow()
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.14f)))
        Text(
            stringResource(R.string.about_seal),
            color = OnScreen.Secondary,
            style = sceneText(CristalType.bodySmall.copy(textAlign = TextAlign.Center)),
            modifier = Modifier.padding(top = 14.dp).fillMaxWidth(),
        )
    }
}

private const val OPEN_METEO_URL = "https://open-meteo.com/"
private const val AEMET_URL = "https://www.aemet.es/"

/** «Licencias de terceros»: se despliega dentro de la tarjeta con la lista de lo que va dentro de la app. */
@Composable
private fun ThirdPartyRow() {
    var open by rememberSaveable { mutableStateOf(false) }
    Column {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.14f)))
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("about_licenses")
                .clickable(role = Role.Button) { open = !open }
                .padding(vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.width(20.dp), contentAlignment = Alignment.Center) {
                Image(painterResource(R.drawable.ic_wx_expand), null, Modifier.size(20.dp).rotate(if (open) 180f else 0f), alpha = 0.85f)
            }
            Text(stringResource(R.string.about_licenses), color = Color.White, style = sceneText(CristalType.bodyMedium), modifier = Modifier.weight(1f))
        }
        if (open) {
            Column(Modifier.padding(start = 30.dp, bottom = 10.dp).testTag("about_licenses_list"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                THIRD_PARTY.filter { it.shipped }.forEach {
                    Text(
                        stringResource(R.string.about_license_line, it.name, it.license),
                        color = OnScreen.Secondary,
                        style = sceneText(CristalType.bodySmall),
                    )
                }
            }
        }
    }
}

@Composable
private fun AboutRow(
    icon: Int?,
    glyph: String?,
    text: String,
    url: String? = null,
    tag: String? = null,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
) {
    val uri = LocalUriHandler.current
    Column {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.14f)))
        Row(
            Modifier
                .fillMaxWidth()
                .then(if (tag != null) Modifier.testTag(tag) else Modifier)
                .then(
                    if (url != null) {
                        Modifier.heightIn(min = 48.dp).combinedClickable(
                            role = Role.Button,
                            onLongClick = onLongClick,
                            onLongClickLabel = onLongClickLabel,
                        ) {
                            // 022: si no hay app que lo abra (un móvil sin app de correo), se copia si la fila sabe copiar.
                            runCatching { uri.openUri(url) }.onFailure { onLongClick?.invoke() }
                        }
                    } else {
                        Modifier
                    },
                )
                .padding(vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.width(20.dp), contentAlignment = Alignment.Center) {
                if (icon != null) {
                    Image(painterResource(icon), null, Modifier.size(20.dp), alpha = 0.85f)
                } else if (glyph != null) {
                    Text(glyph, color = Color.White, style = sceneText(CristalType.titleSmall))
                }
            }
            Text(text, color = Color.White, style = sceneText(CristalType.bodyMedium), modifier = Modifier.weight(1f))
            // Abre en el navegador: la flecha lo dice sin otra palabra.
            if (url != null) Text("↗", color = OnScreen.Secondary, style = sceneText(CristalType.bodyMedium))
        }
    }
}

/** Copia [text] al portapapeles. Android 13+ ya enseña su propio aviso de «copiado»; en el 12, un aviso nuestro. */
@Composable
private fun rememberCopyText(text: String): () -> Unit {
    val context = LocalContext.current
    val copied = stringResource(R.string.about_email_copied)
    return remember(context, text, copied) {
        {
            val ok = runCatching {
                context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(text, text))
            }.isSuccess
            if (ok && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
            }
        }
    }
}

/** El icono del lanzador (adaptativo) como bitmap; `null` si el sistema no lo da. */
private fun launcherIcon(context: Context): Bitmap? = runCatching {
    ResourcesCompat.getDrawable(context.resources, R.mipmap.ic_launcher, context.theme)?.toBitmap(168, 168)
}.getOrNull()

/** 6 dp, blanca sobre blanco al 18 %; al menos 5 dp de largo para que se vea con un 0,3 %. */
@Composable
private fun UsageBar(fraction: Float) {
    Box(
        Modifier
            .padding(top = 10.dp)
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(Color.White.copy(alpha = 0.18f)),
    ) {
        Layout(
            content = {
                Box(Modifier.fillMaxSize().clip(RoundedCornerShape(3.dp)).background(Color.White))
            },
        ) { measurables, constraints ->
            val width = (constraints.maxWidth * fraction.coerceIn(0f, 1f)).toInt().coerceAtLeast(5.dp.roundToPx())
            val placeable = measurables.single().measure(constraints.copy(minWidth = width, maxWidth = width))
            layout(constraints.maxWidth, placeable.height) { placeable.place(0, 0) }
        }
    }
}
