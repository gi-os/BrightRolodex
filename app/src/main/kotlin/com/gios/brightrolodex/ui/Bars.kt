package com.gios.brightrolodex.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gios.brightrolodex.face.Face
import com.gios.brightrolodex.face.FaceView
import com.gios.brightrolodex.ui.theme.LightText
import com.gios.brightrolodex.ui.theme.LightTextVariant
import com.gios.brightrolodex.ui.theme.LightThemeTokens
import com.gios.brightrolodex.ui.theme.gridUnitsAsDp
import com.gios.brightrolodex.ui.theme.lightClickable
import com.gios.brightrolodex.ui.theme.lightTextStyle

/**
 * The chrome, at the sizes the SDK uses: a three-unit top bar, a four-unit bottom bar, a
 * one-unit horizontal inset. Everything is grid units rather than dp, so it scales with the
 * panel instead of being tuned to one.
 */

@Composable
fun LightTopBar(
    title: String,
    modifier: Modifier = Modifier,
    left: String? = null,
    right: String? = null,
    onLeft: (() -> Unit)? = null,
    onRight: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(3f.gridUnitsAsDp())
            .padding(horizontal = lightInset()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Both edges reserve the same width whether or not they hold anything, so the title
        // stays centred on the screen rather than centred in the space left over.
        Box(Modifier.width(6f.gridUnitsAsDp()), contentAlignment = Alignment.CenterStart) {
            if (left != null) {
                LightText(
                    text = left,
                    variant = LightTextVariant.Superfine,
                    lighten = true,
                    maxLines = 1,
                    modifier = if (onLeft != null) {
                        Modifier.lightClickable(onClick = onLeft)
                    } else {
                        Modifier
                    },
                )
            }
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            LightText(
                text = title,
                variant = LightTextVariant.Fine,
                align = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(Modifier.width(6f.gridUnitsAsDp()), contentAlignment = Alignment.CenterEnd) {
            if (right != null) {
                LightText(
                    text = right,
                    variant = LightTextVariant.Superfine,
                    lighten = true,
                    maxLines = 1,
                    modifier = if (onRight != null) {
                        Modifier.lightClickable(onClick = onRight)
                    } else {
                        Modifier
                    },
                )
            }
        }
    }
}

/**
 * LightOS's action bar: up to five icon items, but **three** if any item is text.
 *
 * All of them are text here, so three is the cap and the app's shape follows from it — the
 * verbs live in the bar and everything else hangs off the card or the top-right menu.
 */
@Composable
fun LightBottomBar(
    items: List<BarItem>,
    modifier: Modifier = Modifier,
) {
    val colors = LightThemeTokens.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(4f.gridUnitsAsDp())
            .background(colors.background)
            .padding(horizontal = lightInset()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        items.forEach { item ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .lightClickable(enabled = item.enabled, onClick = item.onClick),
                contentAlignment = Alignment.Center,
            ) {
                LightText(
                    text = item.label,
                    variant = LightTextVariant.Button,
                    align = TextAlign.Center,
                    maxLines = 1,
                    color = if (item.enabled) colors.content else colors.contentFaint,
                )
            }
        }
    }
}

data class BarItem(
    val label: String,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

/**
 * A list row: face, name, one detail line.
 *
 * The avatar falls back to initials rather than to an empty head outline. Forty identical ovals
 * identify nobody, and the point of the drawing is that it identifies somebody at a glance.
 */
@Composable
fun PersonRow(
    name: String,
    detail: String,
    face: Face?,
    initials: String,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    onClick: () -> Unit,
) {
    val colors = LightThemeTokens.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .lightClickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(3.4f.gridUnitsAsDp()),
            contentAlignment = Alignment.Center,
        ) {
            if (face != null && face.isDrawn) {
                FaceView(face = face, modifier = Modifier.size(3.4f.gridUnitsAsDp()))
            } else {
                LightText(initials, LightTextVariant.Detail, lighten = true)
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 0.6f.gridUnitsAsDp()),
        ) {
            LightText(name, LightTextVariant.Copy, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (detail.isNotBlank()) {
                LightText(
                    text = detail,
                    variant = LightTextVariant.Detail,
                    lighten = true,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            LightText(trailing, LightTextVariant.Superfine, color = colors.contentSecondary)
        }
    }
}

/**
 * A labelled text field.
 *
 * `BasicTextField` and a rule, not Material's `TextField`: a filled container with a floating
 * label appears nowhere in LightOS. The rule is 3 design pixels at 80% width, which is what the
 * SDK's own field draws.
 */
@Composable
fun LightField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Done,
    singleLine: Boolean = true,
    placeholder: String = "",
) {
    val colors = LightThemeTokens.colors
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        LightText(label.uppercase(), LightTextVariant.Micro, lighten = true)
        Box(contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty() && placeholder.isNotEmpty()) {
                LightText(placeholder, LightTextVariant.Copy, color = colors.contentFaint)
            }
            BasicTextField(
                value = value,
                onValueChange = onChange,
                textStyle = lightTextStyle(LightTextVariant.Copy).copy(color = colors.content),
                cursorBrush = SolidColor(colors.content),
                singleLine = singleLine,
                keyboardOptions = KeyboardOptions(
                    keyboardType = keyboardType,
                    imeAction = imeAction,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Box(
            Modifier
                .fillMaxWidth(0.8f)
                .height(1.5.dp)
                .background(colors.contentSecondary),
        )
    }
}
