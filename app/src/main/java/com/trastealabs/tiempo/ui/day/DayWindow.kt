package com.trastealabs.tiempo.ui.day

import androidx.compose.material3.Tab
import com.trastealabs.tiempo.ui.theme.OnSky
import com.trastealabs.tiempo.ui.theme.OnSheet
import com.trastealabs.tiempo.ui.theme.WhiteInk
import com.trastealabs.tiempo.ui.theme.CristalType
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.trastealabs.tiempo.R
import com.trastealabs.tiempo.domain.DayPoint
import com.trastealabs.tiempo.domain.dayPhoto
import com.trastealabs.tiempo.domain.daysFrom
import com.trastealabs.tiempo.domain.isSouth
import com.trastealabs.tiempo.domain.level
import com.trastealabs.tiempo.domain.skyPhotoNow
import com.trastealabs.tiempo.domain.today
import com.trastealabs.tiempo.ui.LocalMoonSouth
import com.trastealabs.tiempo.ui.UiState
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import com.trastealabs.tiempo.ui.theme.CristalColors
import com.trastealabs.tiempo.ui.scene.LocalBackdrop
import com.trastealabs.tiempo.ui.scene.PhotoCache
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.abs

/**
 * La ventana del día (009, H8 y H13; 012, P5): un `ModalBottomSheet` opaco de M3 que ocupa el alto, con el velo, el
 * movimiento y el atrás de M3. Se baja con el dedo desde el asa, la tira o el contenido si está arriba del todo, con el
 * velo y con «atrás»; [onClose] llega al acabar de bajar.
 *
 * Arriba, el asa y la tira de días; debajo, una página por día ([DayPage]) que se desliza de lado y se desplaza hacia
 * abajo sin cerrar la hoja (la hoja solo baja cuando la lista está arriba del todo). Va por [date], no por posición: si
 * pasa la medianoche con ella abierta sigue en la misma fecha (y si esa fecha ya no está, en la primera). Al asentarse una
 * página llama a [onDateChange].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayWindow(
    state: UiState.Content,
    date: String,
    onDateChange: (String) -> Unit,
    onClose: () -> Unit,
    photos: PhotoCache,
) {
    val snapshot = state.snapshot
    val days = daysFrom(snapshot, state.now)
    val latestOnClose by rememberUpdatedState(onClose)
    if (days.isEmpty()) {
        LaunchedEffect(Unit) { latestOnClose() }
        return
    }
    val paneLabel = stringResource(R.string.day_window_pane)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // Abierta y quieta en su sitio, tapa la principal: su cielo se para (P5, H9).
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheetState,
        modifier = Modifier.testTag("day_window").semantics { paneTitle = paneLabel },
        containerColor = CristalColors.SheetSurface,
        contentColor = Color.White,
        dragHandle = { BottomSheetDefaults.DragHandle(color = OnSky.Control) },
    ) {
        WindowContent(state, days, date, onDateChange, photos, Modifier.weight(1f))
    }
}

@Composable
private fun WindowContent(
    state: UiState.Content,
    days: List<DayPoint>,
    date: String,
    onDateChange: (String) -> Unit,
    photos: PhotoCache,
    modifier: Modifier = Modifier,
) {
    val snapshot = state.snapshot
    val dates = days.map { it.date }
    val target = dates.indexOf(date).coerceAtLeast(0)
    val pager = rememberPagerState(initialPage = target) { days.size }
    val latestDates by rememberUpdatedState(dates)
    val latestOnDateChange by rememberUpdatedState(onDateChange)
    // Cambia la lista (medianoche): de vuelta a la página de la fecha.
    LaunchedEffect(dates) {
        if (pager.currentPage != target) pager.scrollToPage(target)
        if (dates[target] != date) latestOnDateChange(dates[target])
    }
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { page -> latestDates.getOrNull(page)?.let { latestOnDateChange(it) } }
    }
    val todayDate = today(snapshot, state.now)?.date ?: dates.first()
    val reliability = state.reliability?.days.orEmpty().associateBy { it.date }
    val scope = rememberCoroutineScope()
    // La franja de cada día (H8): hoy, la foto de ahora; los demás, la de su cielo y su máxima, de día. Quieta.
    fun photoOf(d: String) = if (d == todayDate) skyPhotoNow(snapshot, state.now) else dayPhoto(snapshot, d, state.now)
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    // Dentro, nada de la escena: sin difuminar ni sombra en el texto (el cristal ya es oscuro).
    CompositionLocalProvider(
        LocalMoonSouth provides isSouth(snapshot),
        LocalBackdrop provides null,
        LocalTextStyle provides LocalTextStyle.current.copy(shadow = null),
    ) {
        Column(modifier.fillMaxWidth()) {
            WindowHead(
                days, todayDate, pager.currentPage,
                onSelect = { scope.launch { pager.scrollToPage(it) } },
            )
            HorizontalPager(
                state = pager,
                modifier = Modifier
                    .testTag("day_pager")
                    .weight(1f)
                    .fillMaxWidth(),
                // Las de al lado, compuestas por adelantado y por tramos mientras está quieta (P5, H9; 008 §3,
                // opciones 1 y 2): al empezar a deslizar ya están hechas. La de dos más allá solo pone su franja.
                beyondViewportPageCount = 1,
                key = { days[it].date },
                // Sin estirado de borde: al encajar el día, la velocidad sobrante iba al overscroll y hacía
                // un rebote de muelle hacia el lado del gesto.
                overscrollEffect = null,
            ) { page ->
                val day = days[page]
                // La que se va a ver (también al saltar desde la tira) nace entera, nunca por tramos.
                val ahead = remember { page != pager.settledPage && page != pager.currentPage }
                val prepare = abs(page - pager.settledPage) <= 1 || page == pager.currentPage
                DayPage(
                    snapshot = snapshot,
                    day = day,
                    today = todayDate,
                    now = state.now,
                    reliability = reliability[day.date]?.takeIf { it.level() != null },
                    scroll = rememberScrollState(),
                    photo = photoOf(day.date),
                    photos = photos,
                    bottomInset = bottom,
                    ahead = ahead,
                    prepare = prepare,
                    aiText = state.report?.days?.get(day.date),
                )
            }
        }
    }
}

/** La cabecera de la ventana: la tira de días. Se arrastra hacia abajo para bajarla (el asa es la de M3). */
@Composable
private fun WindowHead(
    days: List<DayPoint>,
    today: String,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val letters = stringArrayResource(R.array.weekdays_letter)
    val todayLabel = stringResource(R.string.today)
    val strip = rememberLazyListState()
    // La ficha del día que se ve, en el centro de la tira.
    LaunchedEffect(selected) {
        val info = strip.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.index == selected }
        if (item != null) {
            strip.animateScrollBy((item.offset + item.size / 2 - info.viewportSize.width / 2).toFloat())
        } else {
            strip.scrollToItem(selected)
        }
    }
    Column(modifier.testTag("day_window_head").fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 2.dp, bottom = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            LazyRow(
                state = strip,
                modifier = Modifier.testTag("day_strip").weight(1f),
                contentPadding = PaddingValues(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                itemsIndexed(days, key = { _, d -> d.date }) { i, day ->
                    val d = LocalDate.parse(day.date)
                    val name = if (day.date == today) todayLabel else letters[d.dayOfWeek.value - 1]
                    DayChip(name, d.dayOfMonth.toString(), selected = i == selected, onClick = { onSelect(i) })
                }
            }
        }
    }
}

/**
 * Un día de la tira (020): el `Tab` de M3 (papel de pestaña, elegido y onda), con el fondo blanco del elegido. La onda va
 * con el color del contenido: blanca sobre la hoja y de tinta sobre el blanco.
 */
@Composable
private fun DayChip(name: String, number: String, selected: Boolean, onClick: () -> Unit) {
    val ink = if (selected) WhiteInk else Color.White
    Tab(
        selected = selected,
        onClick = onClick,
        modifier = Modifier
            .testTag("day_chip")
            .widthIn(min = 48.dp)
            .height(52.dp)
            .clip(RoundedCornerShape(20.dp))
            .then(if (selected) Modifier.background(Color.White) else Modifier),
        selectedContentColor = ink,
        unselectedContentColor = Color.White,
    ) {
        // El margen va dentro: la zona que se toca y la onda llenan la ficha entera.
        val side = Modifier.padding(horizontal = 6.dp)
        Text(name, side, color = if (selected) ink else OnSheet.Secondary, style = CristalType.labelMedium)
        Text(number, side, color = ink, style = CristalType.labelLargeBig)
    }
}
