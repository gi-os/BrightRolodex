package com.gios.brightrolodex.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gios.brightrolodex.ui.theme.LightText
import com.gios.brightrolodex.ui.theme.LightTextVariant
import com.gios.brightrolodex.ui.theme.LightThemeTokens
import com.gios.brightrolodex.ui.theme.gridUnitsAsDp
import com.gios.brightrolodex.ui.theme.lightClickable

/** One grid unit of margin, so every screen starts at the same place. */
@Composable
fun lightInset(): Dp = 1f.gridUnitsAsDp()

/**
 * A text button.
 *
 * Selected state is carried by inversion — filled white with black text — rather than by an
 * accent colour, because the panel is greyscale and there is no accent colour to carry it.
 */
@Composable
fun LightButton(
    label: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val colors = LightThemeTokens.colors
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(2.dp))
            .background(if (selected) colors.content else Color.Transparent)
            .lightClickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        LightText(
            text = label,
            variant = LightTextVariant.Detail,
            align = TextAlign.Center,
            color = when {
                selected -> colors.background
                !enabled -> colors.contentFaint
                else -> colors.content
            },
        )
    }
}

/** A row of mutually exclusive [LightButton]s. */
@Composable
fun <T> LightSegmented(
    options: List<Pair<T, String>>,
    selected: T,
    modifier: Modifier = Modifier,
    onSelect: (T) -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        options.forEach { (value, label) ->
            LightButton(
                label = label,
                modifier = Modifier.weight(1f),
                selected = value == selected,
                onClick = { onSelect(value) },
            )
        }
    }
}

/**
 * A slider drawn as a bar, because Material's has a thumb, a ripple and a tick track.
 *
 * `weight(1f)` on the filled half rather than `fillMaxWidth(fraction)`: fractional fills
 * compound against the parent that already has one, so a bar inside a padded row ends up
 * shorter than the number it is reporting.
 */
@Composable
fun LightBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    height: Dp = 3.dp,
) {
    val colors = LightThemeTokens.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(colors.rule),
    ) {
        val f = fraction.coerceIn(0.001f, 1f)
        Box(Modifier.weight(f).height(height).background(colors.content))
        Box(Modifier.weight(1f - f).height(height))
    }
}
