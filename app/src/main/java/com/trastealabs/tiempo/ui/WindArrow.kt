package com.trastealabs.tiempo.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import com.trastealabs.tiempo.R

/** Flecha del viento girada hacia donde sopla ([towardsDeg], 0 = norte, en sentido horario). La describe quien la usa. */
@Composable
fun WindArrow(towardsDeg: Int, size: Dp, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.ic_wx_arrow),
        contentDescription = null,
        modifier = modifier.size(size).rotate(towardsDeg.toFloat()),
    )
}
