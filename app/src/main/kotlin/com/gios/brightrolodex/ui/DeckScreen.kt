package com.gios.brightrolodex.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gios.brightrolodex.data.Person
import com.gios.brightrolodex.data.Phone
import com.gios.brightrolodex.face.Face
import com.gios.brightrolodex.face.FaceView
import com.gios.brightrolodex.hw.WheelSteps
import com.gios.brightrolodex.ui.theme.LightText
import com.gios.brightrolodex.ui.theme.LightTextVariant
import com.gios.brightrolodex.ui.theme.LightThemeTokens
import com.gios.brightrolodex.ui.theme.gridUnitsAsDp
import com.gios.brightrolodex.ui.theme.lightClickable

/**
 * The deck: one full-screen card per person, and the wheel is the knob.
 *
 * The card is the navigation as well as the display. Tapping the face opens the face editor and
 * tapping the words opens the editor, which is what keeps the bottom bar free for the two verbs
 * that matter — LightOS caps a text action bar at three items, and a rolodex whose bar is spent
 * on EDIT and BACK is a rolodex you cannot text anybody from.
 */
@Composable
fun DeckScreen(
    person: Person?,
    face: Face,
    index: Int,
    total: Int,
    scopeLabel: String,
    onFlip: (Int) -> Unit,
    onScope: () -> Unit,
    onMenu: () -> Unit,
    onFind: () -> Unit,
    onFace: () -> Unit,
    onEdit: () -> Unit,
    onText: () -> Unit,
    onCall: () -> Unit,
    onSeen: () -> Unit,
    onAddSomeone: () -> Unit,
) {
    val colors = LightThemeTokens.colors

    WheelSteps { delta ->
        // A notch "up" moves forward through the deck, the same direction a physical rolodex
        // turns. Negated because the wheel reports up as +1.
        onFlip(-delta)
    }

    Column(Modifier.fillMaxSize().background(colors.background)) {

        LightTopBar(
            title = if (total > 0) (index + 1).toString() + " / " + total else "",
            left = scopeLabel,
            right = "MORE",
            onLeft = onScope,
            onRight = onMenu,
        )

        if (person == null) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = lightInset() * 2),
                ) {
                    LightText(
                        text = if (scopeLabel == ALL_LABEL) {
                            "Nobody in the rolodex yet"
                        } else {
                            "Nobody tagged " + scopeLabel
                        },
                        variant = LightTextVariant.Copy,
                        align = TextAlign.Center,
                    )
                    Spacer(Modifier.height(1f.gridUnitsAsDp()))
                    LightText(
                        text = "ADD SOMEONE",
                        variant = LightTextVariant.Button,
                        modifier = Modifier.lightClickable(onClick = onAddSomeone),
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = lightInset()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(11f.gridUnitsAsDp())
                        .lightClickable(onClick = onFace),
                    contentAlignment = Alignment.Center,
                ) {
                    if (face.isDrawn) {
                        FaceView(face = face, modifier = Modifier.fillMaxSize())
                    } else {
                        // Not the blank head outline: an untouched oval says nothing, and this
                        // says both who it is and what to do about it.
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            LightText(person.initials, LightTextVariant.Subtitle, lighten = true)
                            LightText("DRAW A FACE", LightTextVariant.Micro, lighten = true)
                        }
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth().lightClickable(onClick = onEdit),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    LightText(
                        text = person.name,
                        variant = LightTextVariant.Heading,
                        align = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )

                    val whoAndWhere = listOf(person.howIKnow, person.where)
                        .filter { it.isNotBlank() }
                        .joinToString(" · ")
                    if (whoAndWhere.isNotBlank()) {
                        LightText(
                            text = whoAndWhere,
                            variant = LightTextVariant.Detail,
                            lighten = true,
                            align = TextAlign.Center,
                        )
                    }

                    val subline = listOfNotNull(
                        person.phones.firstOrNull()?.let { Phone.pretty(it) },
                        seenLabel(person.lastSeenAt),
                    ).joinToString(" · ")
                    if (subline.isNotBlank()) {
                        LightText(
                            text = subline,
                            variant = LightTextVariant.Superfine,
                            lighten = true,
                            align = TextAlign.Center,
                        )
                    }
                }

                Spacer(Modifier.height(1f.gridUnitsAsDp()))

                if (person.facts.isNotEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        person.facts.forEach { fact ->
                            LightText(
                                text = fact.text,
                                variant = LightTextVariant.Paragraph,
                                align = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }

                if (person.tags.isNotEmpty()) {
                    Spacer(Modifier.height(0.6f.gridUnitsAsDp()))
                    LightText(
                        text = person.tags.joinToString("  ") { it.uppercase() },
                        variant = LightTextVariant.Micro,
                        lighten = true,
                        align = TextAlign.Center,
                    )
                }

                Spacer(Modifier.height(1f.gridUnitsAsDp()))
                LightText(
                    text = "+ ADD A FACT",
                    variant = LightTextVariant.Micro,
                    lighten = true,
                    modifier = Modifier.lightClickable(onClick = onEdit),
                )
                Spacer(Modifier.height(1f.gridUnitsAsDp()))
            }
        }

        LightBottomBar(
            items = if (person == null) {
                listOf(
                    BarItem("ADD") { onAddSomeone() },
                    BarItem("FIND") { onFind() },
                    BarItem("MORE") { onMenu() },
                )
            } else if (person.hasNumber) {
                listOf(
                    BarItem("TEXT") { onText() },
                    BarItem("CALL") { onCall() },
                    BarItem("FIND") { onFind() },
                )
            } else {
                // No number, so no TEXT and no CALL — and SEEN in their place, because for
                // somebody you only ever run into, "when did I last see them" is the only
                // thing there is to record.
                listOf(
                    BarItem("SEEN") { onSeen() },
                    BarItem("EDIT") { onEdit() },
                    BarItem("FIND") { onFind() },
                )
            },
        )
    }
}

const val ALL_LABEL = "ALL"

/**
 * "seen 2 days ago", or nothing at all.
 *
 * Nothing rather than "never": for most people the field has simply never been used, and a card
 * that announces "never seen" about your neighbour is both wrong and slightly bleak.
 */
fun seenLabel(at: Long): String? {
    if (at <= 0L) return null
    val days = ((System.currentTimeMillis() - at) / 86_400_000L).toInt()
    return when {
        days <= 0 -> "seen today"
        days == 1 -> "seen yesterday"
        days < 30 -> "seen " + days + " days ago"
        days < 60 -> "seen last month"
        days < 365 -> "seen " + (days / 30) + " months ago"
        else -> "seen over a year ago"
    }
}
