package com.linzin.linzindeskclock.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer

@Composable
fun AutoSizedClockText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    maxHeightRatio: Float = 0.65f, // 最大高度佔比
    maxWidthRatio: Float = 0.88f   // 最大寬度佔比（保留邊距防切邊）
) {
    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        val density = LocalDensity.current
        val textMeasurer = rememberTextMeasurer()

        val availableWidth = constraints.maxWidth * maxWidthRatio
        val availableHeight = constraints.maxHeight * maxHeightRatio

        val baseFontSize = with(density) { availableHeight.toSp() }

        val measuredWidth = remember(text, baseFontSize, style) {
            textMeasurer.measure(
                text = text,
                style = style.copy(fontSize = baseFontSize),
                softWrap = false,
                maxLines = 1
            ).size.width
        }

        val finalFontSize = if (measuredWidth > availableWidth && measuredWidth > 0) {
            baseFontSize * (availableWidth / measuredWidth.toFloat())
        } else {
            baseFontSize
        }

        Text(
            text = text,
            style = style.copy(fontSize = finalFontSize),
            softWrap = false,
            maxLines = 1
        )
    }
}