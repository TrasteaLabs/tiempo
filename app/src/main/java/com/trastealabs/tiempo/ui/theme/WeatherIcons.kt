package com.trastealabs.tiempo.ui.theme

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.trastealabs.tiempo.R
import com.trastealabs.tiempo.domain.Sky
import com.trastealabs.tiempo.domain.SummaryIcon

/** Iconos `ic_wx_*` (Material Symbols, en blanco). Solo despejado y poco nuboso cambian de noche. */
@DrawableRes
fun weatherIcon(sky: Sky, isDay: Boolean): Int = when (sky) {
    Sky.CLEAR -> if (isDay) R.drawable.ic_wx_clear_day else R.drawable.ic_wx_clear_night
    Sky.PARTLY_CLOUDY -> if (isDay) R.drawable.ic_wx_partly_cloudy_day else R.drawable.ic_wx_partly_cloudy_night
    Sky.CLOUDY -> R.drawable.ic_wx_cloudy
    Sky.FOG -> R.drawable.ic_wx_fog
    Sky.DRIZZLE -> R.drawable.ic_wx_drizzle
    Sky.RAIN -> R.drawable.ic_wx_rain
    Sky.SNOW -> R.drawable.ic_wx_snow
    Sky.STORM -> R.drawable.ic_wx_storm
}

@StringRes
fun skyLabel(sky: Sky): Int = when (sky) {
    Sky.CLEAR -> R.string.sky_clear
    Sky.PARTLY_CLOUDY -> R.string.sky_partly_cloudy
    Sky.CLOUDY -> R.string.sky_cloudy
    Sky.FOG -> R.string.sky_fog
    Sky.DRIZZLE -> R.string.sky_drizzle
    Sky.RAIN -> R.string.sky_rain
    Sky.SNOW -> R.string.sky_snow
    Sky.STORM -> R.string.sky_storm
}

@Composable
fun WeatherIcon(sky: Sky, isDay: Boolean, modifier: Modifier = Modifier, size: Dp = 24.dp) {
    Image(
        painter = painterResource(weatherIcon(sky, isDay)),
        contentDescription = stringResource(skyLabel(sky)),
        modifier = modifier.size(size),
    )
}

/** Icono de cada frase del Resumen (009, H12): los de siempre, sin dibujos nuevos. */
@DrawableRes
fun summaryIcon(icon: SummaryIcon): Int = when (icon) {
    SummaryIcon.RAIN -> R.drawable.ic_wx_rain
    SummaryIcon.RAIN_STOP -> R.drawable.ic_wx_partly_cloudy_day
    SummaryIcon.TEMP_UP -> R.drawable.ic_wx_trend_up
    SummaryIcon.TEMP_DOWN -> R.drawable.ic_wx_trend_down
    SummaryIcon.GUST -> R.drawable.ic_wx_wind
    SummaryIcon.FOG -> R.drawable.ic_wx_fog
    SummaryIcon.FROST -> R.drawable.ic_wx_thermometer
    SummaryIcon.DRY -> R.drawable.ic_wx_clear_day
}
