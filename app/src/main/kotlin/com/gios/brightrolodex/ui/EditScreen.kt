package com.gios.brightrolodex.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.gios.brightrolodex.data.Fact
import com.gios.brightrolodex.data.Person
import com.gios.brightrolodex.ui.theme.LightText
import com.gios.brightrolodex.ui.theme.LightTextVariant
import com.gios.brightrolodex.ui.theme.LightThemeTokens
import com.gios.brightrolodex.ui.theme.gridUnitsAsDp
import com.gios.brightrolodex.ui.theme.lightClickable
import java.util.UUID

/**
 * Editing a person.
 *
 * The field order is the order the answers arrive in when you are standing on the stairs having
 * just been said hello to: who they are, then where, then a number if there is one. The name is
 * first because it is the only required field, and `howIKnow` is second because on most cards it
 * is the field that does the work.
 *
 * Everything is committed when the screen leaves — including via the hardware back button, which
 * is what `DisposableEffect` is for. The alternative, saving on every keystroke, rewrites the
 * whole store and re-renders a contact photo per character typed.
 */
@Composable
fun EditScreen(
    person: Person,
    onCommit: (Person) -> Unit,
    onDrawFace: () -> Unit,
    onNote: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = LightThemeTokens.colors

    var name by remember(person.id) { mutableStateOf(person.name) }
    var howIKnow by remember(person.id) { mutableStateOf(person.howIKnow) }
    var where by remember(person.id) { mutableStateOf(person.where) }
    var number by remember(person.id) { mutableStateOf(person.phones.firstOrNull().orEmpty()) }
    var birthday by remember(person.id) { mutableStateOf(person.birthday) }
    var tags by remember(person.id) { mutableStateOf(person.tags.joinToString(", ")) }
    var facts by remember(person.id) { mutableStateOf(person.facts) }
    var newFact by remember(person.id) { mutableStateOf("") }

    fun assembled(): Person = person.copy(
        name = name.trim().ifBlank { person.name.ifBlank { "Someone" } },
        howIKnow = howIKnow.trim(),
        where = where.trim(),
        // One number in the UI, a list in the model. A second number is rare enough on this
        // phone that a field for it would cost every card more than it earns; the model keeps
        // the list so an adopted contact's extra numbers are not thrown away.
        phones = if (number.isBlank()) {
            person.phones.drop(1)
        } else {
            listOf(number.trim()) + person.phones.drop(1)
        },
        birthday = birthday.trim(),
        tags = tags.split(',').map { it.trim() }.filter { it.isNotEmpty() },
        facts = facts,
    )

    // rememberUpdatedState, or onDispose would commit the values as they were when this screen
    // first appeared — which is to say, discard every edit. The assembled person is recomputed
    // each recomposition, which is a handful of string trims.
    val snapshot by rememberUpdatedState(assembled())
    val commit by rememberUpdatedState(onCommit)
    DisposableEffect(person.id) {
        onDispose { commit(snapshot) }
    }

    Column(Modifier.fillMaxSize().background(colors.background)) {
        LightTopBar(title = "EDIT", left = "DONE", onLeft = onBack)

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = lightInset()),
        ) {
            LightField("name", name, { name = it }, imeAction = ImeAction.Next)
            LightField(
                label = "how you know them",
                value = howIKnow,
                onChange = { howIKnow = it },
                placeholder = "the super",
                imeAction = ImeAction.Next,
            )
            LightField(
                label = "where",
                value = where,
                onChange = { where = it },
                placeholder = "4B",
                imeAction = ImeAction.Next,
            )
            LightField(
                label = "number",
                value = number,
                onChange = { number = it },
                keyboardType = KeyboardType.Phone,
                placeholder = "leave empty if you don't have one",
                imeAction = ImeAction.Next,
            )
            LightField(
                label = "birthday (MM-DD)",
                value = birthday,
                onChange = { birthday = it },
                placeholder = "03-14",
                imeAction = ImeAction.Next,
            )
            LightField(
                label = "tags, comma separated",
                value = tags,
                onChange = { tags = it },
                placeholder = "neighbours, building",
            )

            Spacer(Modifier.height(1f.gridUnitsAsDp()))
            LightText("FACTS", LightTextVariant.Micro, lighten = true)

            facts.forEach { fact ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LightText(
                        text = fact.text,
                        variant = LightTextVariant.Copy,
                        modifier = Modifier.weight(1f),
                    )
                    LightText(
                        text = "REMOVE",
                        variant = LightTextVariant.Micro,
                        color = colors.contentFaint,
                        modifier = Modifier.lightClickable {
                            facts = facts.filterNot { it.id == fact.id }
                        },
                    )
                }
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Box(Modifier.weight(1f)) {
                    LightField(
                        label = "one thing you want to remember",
                        value = newFact,
                        onChange = { newFact = it },
                        placeholder = "has a beagle named Pete",
                        imeAction = ImeAction.Done,
                    )
                }
                LightText(
                    text = "ADD",
                    variant = LightTextVariant.Detail,
                    color = if (newFact.isBlank()) colors.contentFaint else colors.content,
                    modifier = Modifier
                        .lightClickable(enabled = newFact.isNotBlank()) {
                            facts = facts + Fact(
                                id = UUID.randomUUID().toString(),
                                text = newFact.trim(),
                                createdAt = System.currentTimeMillis(),
                            )
                            newFact = ""
                        }
                        .padding(start = 8.dp, bottom = 10.dp),
                )
            }

            Spacer(Modifier.height(2f.gridUnitsAsDp()))
        }

        LightBottomBar(
            items = listOf(
                BarItem("DONE") { onBack() },
                BarItem("FACE") { onDrawFace() },
                BarItem("NOTE") { onNote() },
            ),
        )
    }
}
