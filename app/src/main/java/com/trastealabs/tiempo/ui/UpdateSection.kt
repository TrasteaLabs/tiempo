package com.trastealabs.tiempo.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.trastealabs.tiempo.R
import com.trastealabs.tiempo.update.ApkProblem
import com.trastealabs.tiempo.update.AppRelease
import com.trastealabs.tiempo.update.DownloadState
import com.trastealabs.tiempo.update.UpdateState
import com.trastealabs.tiempo.ui.glass.GlassButton
import com.trastealabs.tiempo.ui.glass.WhiteButton
import com.trastealabs.tiempo.ui.glass.sceneText
import com.trastealabs.tiempo.ui.glass.translucent
import com.trastealabs.tiempo.ui.theme.CristalType
import com.trastealabs.tiempo.ui.theme.OnScreen
import com.trastealabs.tiempo.ui.theme.OnSky
import com.trastealabs.tiempo.ui.theme.Tabular
import com.trastealabs.tiempo.ui.theme.cristalSwitchColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Lo que se puede hacer con el actualizador (021) desde la principal y desde Ajustes. */
sealed interface UpdateAction {
    data object CheckNow : UpdateAction
    data class SetBeta(val on: Boolean) : UpdateAction

    /** «Bajar e instalar»: baja, comprueba y, si todo cuadra, abre el instalador de Android. */
    data object Download : UpdateAction
    data object Install : UpdateAction
    data object Retry : UpdateAction
    data object Dismiss : UpdateAction
}

/**
 * La línea discreta de la principal (021): hay versión nueva, se toca y lleva a Ajustes; la cruz la descarta para esa
 * versión. Nunca un diálogo.
 */
@Composable
fun UpdateNotice(release: AppRelease, onOpen: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val dismissLabel = stringResource(R.string.update_notice_dismiss)
    Row(
        modifier
            .fillMaxWidth()
            .testTag("update_notice")
            .translucent(RoundedCornerShape(22.dp))
            .clip(RoundedCornerShape(22.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button, onClick = onOpen)
                .padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Image(painterResource(R.drawable.ic_wx_update), contentDescription = null, Modifier.size(18.dp))
            Text(
                stringResource(
                    if (release.prerelease) R.string.update_notice_beta else R.string.update_notice,
                    release.versionName,
                ),
                color = Color.White,
                style = sceneText(CristalType.bodyMedium),
                modifier = Modifier.weight(1f),
            )
            Text(stringResource(R.string.update_notice_open), color = Color.White, style = sceneText(CristalType.labelLarge))
        }
        Box(
            Modifier
                .testTag("update_notice_dismiss")
                .minimumInteractiveComponentSize()
                .clickable(role = Role.Button, onClick = onDismiss)
                .semantics { contentDescription = dismissLabel },
            contentAlignment = Alignment.Center,
        ) {
            Image(painterResource(R.drawable.ic_wx_close), contentDescription = null, Modifier.size(16.dp))
        }
    }
}

/**
 * «Versión de la app» en Ajustes (021): la instalada, si hay una nueva (con sus notas y el botón de bajarla), cuándo se
 * comprobó, «Buscar ahora» y el interruptor de betas. Se llama así para no confundirla con «Refrescos», que es el dato
 * del tiempo. En la versión de desarrollo solo dice que no se actualiza.
 */
@Composable
fun UpdateSection(state: UpdateState, now: Long, zone: ZoneId, onUpdate: (UpdateAction) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .testTag("update_card")
            .translucent(RoundedCornerShape(28.dp))
            .padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            stringResource(R.string.update_installed, state.installedName, state.installedCode),
            color = OnScreen.Text,
            style = sceneText(CristalType.titleSmall.merge(Tabular)),
        )
        if (!state.enabled) {
            Text(
                stringResource(R.string.update_dev),
                color = OnScreen.Secondary,
                style = sceneText(CristalType.bodyMedium),
                modifier = Modifier.padding(bottom = 8.dp).testTag("update_dev"),
            )
            return@Column
        }
        val available = state.available
        if (available != null) {
            Available(available, state.download, onUpdate)
        } else {
            CheckStatus(state, now, zone)
        }
        if (available != null) CheckStatus(state, now, zone, compact = true)
        GlassButton(
            text = stringResource(if (state.checking) R.string.update_checking else R.string.update_check_now),
            onClick = { if (!state.checking) onUpdate(UpdateAction.CheckNow) },
            modifier = Modifier.testTag("update_check_now"),
        )
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.14f)))
        BetaSwitch(state.beta) { onUpdate(UpdateAction.SetBeta(it)) }
    }
}

/** Al día, sin comprobar o no se ha podido; con [compact], solo la línea de cuándo (debajo de una versión nueva). */
@Composable
private fun CheckStatus(state: UpdateState, now: Long, zone: ZoneId, compact: Boolean = false) {
    val whenText = state.lastCheck.takeIf { it > 0 }?.let { checkedAt(it, now, zone) }
    when {
        state.lastCheck <= 0 -> if (!compact) {
            Text(stringResource(R.string.update_never), color = OnScreen.Secondary, style = sceneText(CristalType.bodyMedium), modifier = Modifier.testTag("update_never"))
        }
        state.lastFailure != null -> Column(Modifier.testTag("update_failed").semantics(mergeDescendants = true) {}) {
            Text(stringResource(R.string.update_failed), color = Color.White, style = sceneText(CristalType.bodyLarge))
            Text(
                stringResource(R.string.update_failed_hint, whenText.orEmpty()),
                color = OnScreen.Secondary,
                style = sceneText(CristalType.bodyMedium),
            )
        }
        compact -> Text(
            stringResource(R.string.update_checked_at, whenText.orEmpty()),
            color = OnScreen.Secondary,
            style = sceneText(CristalType.bodySmall),
        )
        else -> Column(Modifier.testTag("update_current").semantics(mergeDescendants = true) {}) {
            Text(stringResource(R.string.update_up_to_date), color = Color.White, style = sceneText(CristalType.bodyLarge))
            Text(stringResource(R.string.update_checked_at, whenText.orEmpty()), color = OnScreen.Secondary, style = sceneText(CristalType.bodyMedium))
        }
    }
}

@Composable
private fun Available(release: AppRelease, download: DownloadState, onUpdate: (UpdateAction) -> Unit) {
    val uri = LocalUriHandler.current
    Column(Modifier.testTag("update_available"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            stringResource(if (release.prerelease) R.string.update_available_beta else R.string.update_available, release.versionName),
            color = Color.White,
            style = sceneText(CristalType.titleMedium),
        )
        val notes = plainNotes(release.notes)
        if (notes.isNotEmpty()) {
            Text(
                notes,
                color = OnScreen.Secondary,
                style = sceneText(CristalType.bodyMedium),
                maxLines = 8,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("update_notes"),
            )
        }
        if (release.pageUrl.startsWith("https://")) {
            Text(
                stringResource(R.string.update_notes_link),
                color = Color.White,
                style = sceneText(CristalType.labelLarge),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clickable(role = Role.Button) { runCatching { uri.openUri(release.pageUrl) } }
                    .padding(vertical = 14.dp),
            )
        }
    }
    when (download) {
        DownloadState.Idle -> WhiteButton(
            stringResource(R.string.update_download, megabytes(release.apkSize)),
            { onUpdate(UpdateAction.Download) },
            Modifier.testTag("update_download"),
        )
        is DownloadState.Downloading -> Column(Modifier.testTag("update_downloading"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(R.string.update_downloading, megabytes(download.done), megabytes(download.total.takeIf { it > 0 } ?: release.apkSize)),
                color = Color.White,
                style = sceneText(CristalType.bodyMedium.merge(Tabular)),
            )
            ProgressBar(download.fraction ?: 0f)
        }
        DownloadState.Verifying -> Text(
            stringResource(R.string.update_verifying),
            color = Color.White,
            style = sceneText(CristalType.bodyMedium),
            modifier = Modifier.testTag("update_verifying").padding(vertical = 12.dp),
        )
        is DownloadState.Ready -> WhiteButton(stringResource(R.string.update_install), { onUpdate(UpdateAction.Install) }, Modifier.testTag("update_install"))
        is DownloadState.NeedsPermission -> {
            Hint(stringResource(R.string.update_permission), "update_permission")
            WhiteButton(stringResource(R.string.update_install), { onUpdate(UpdateAction.Install) }, Modifier.testTag("update_install"))
        }
        is DownloadState.Installing -> {
            Hint(stringResource(R.string.update_installing), "update_installing")
            GlassButton(stringResource(R.string.update_install), { onUpdate(UpdateAction.Install) }, Modifier.testTag("update_install"))
        }
        is DownloadState.Failed -> {
            Problem(stringResource(if (download.problem == ApkProblem.DOWNLOAD) R.string.update_download_failed else R.string.update_invalid), "update_error")
            WhiteButton(stringResource(R.string.update_retry), { onUpdate(UpdateAction.Retry) }, Modifier.testTag("update_retry"))
        }
        DownloadState.InstallFailed -> {
            Problem(stringResource(R.string.update_install_failed), "update_error")
            WhiteButton(stringResource(R.string.update_retry), { onUpdate(UpdateAction.Retry) }, Modifier.testTag("update_retry"))
        }
    }
}

@Composable
private fun Hint(text: String, tag: String) {
    Text(text, color = OnScreen.Secondary, style = sceneText(CristalType.bodyMedium), modifier = Modifier.testTag(tag))
}

/** Un fallo de la bajada: blanco con el icono de aviso, sin rojo (el rojo es de los avisos de AEMET). */
@Composable
private fun Problem(text: String, tag: String) {
    // TalkBack lo dice al aparecer: llega sin que nadie lo pida, cuando falla la bajada o la comprobación.
    Row(Modifier.testTag(tag).semantics { liveRegion = LiveRegionMode.Polite }, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        Image(painterResource(R.drawable.ic_wx_warning), contentDescription = null, Modifier.padding(top = 2.dp).size(16.dp))
        Text(text, color = Color.White, style = sceneText(CristalType.bodyMedium))
    }
}

@Composable
private fun ProgressBar(fraction: Float) {
    Box(
        Modifier
            .fillMaxWidth()
            .progressSemantics(fraction.coerceIn(0f, 1f))
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(Color.White.copy(alpha = 0.18f)),
    ) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(6.dp).background(OnSky.Control))
    }
}

@Composable
private fun BetaSwitch(on: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .testTag("update_beta")
            .toggleable(value = on, role = Role.Switch, onValueChange = onToggle)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.update_beta), color = Color.White, style = sceneText(CristalType.titleSmall))
            Text(stringResource(R.string.update_beta_hint), color = OnScreen.Secondary, style = sceneText(CristalType.bodyMedium))
        }
        Switch(
            checked = on,
            onCheckedChange = null,
            thumbContent = if (on) {
                { Icon(painterResource(R.drawable.ic_wx_check), null, Modifier.size(16.dp)) }
            } else {
                null
            },
            colors = cristalSwitchColors(),
        )
    }
}

private val CHECK_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("H:mm", Locale.ROOT)
private val CHECK_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("d/M", Locale.ROOT)

/** «hoy a las 10:15», «ayer a las 22:40» o «el 24/11». */
@Composable
private fun checkedAt(at: Long, now: Long, zone: ZoneId): String {
    val time = Instant.ofEpochSecond(at).atZone(zone)
    val today = Instant.ofEpochSecond(now).atZone(zone).toLocalDate()
    return when (time.toLocalDate()) {
        today -> stringResource(R.string.update_today_at, CHECK_TIME.format(time))
        today.minusDays(1) -> stringResource(R.string.update_yesterday_at, CHECK_TIME.format(time))
        else -> stringResource(R.string.update_on_day, CHECK_DATE.format(time))
    }
}

/** «8,6 MB» (un decimal, coma). */
internal fun megabytes(bytes: Long): String = String.format(Locale.forLanguageTag("es-ES"), "%.1f MB", bytes / 1_000_000.0)

/**
 * Las notas de GitHub vienen en Markdown: se enseñan como texto, sin almohadillas de título, con viñetas «•» y sin
 * asteriscos ni comillas invertidas.
 */
internal fun plainNotes(markdown: String): String =
    markdown.lineSequence()
        .map { it.trimEnd() }
        .map { line ->
            line.replace(Regex("""^\s*#{1,6}\s*"""), "")
                .replace(Regex("""^(\s*)[-*+]\s+"""), "$1• ")
                .replace("**", "")
                .replace("`", "")
        }
        .joinToString("\n")
        .replace(Regex("""\n{3,}"""), "\n\n")
        .trim()
