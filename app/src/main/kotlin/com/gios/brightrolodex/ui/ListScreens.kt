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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gios.brightrolodex.contacts.ContactRow
import com.gios.brightrolodex.data.Person
import com.gios.brightrolodex.data.Phone
import com.gios.brightrolodex.data.Search
import com.gios.brightrolodex.face.FaceJson
import com.gios.brightrolodex.hw.WheelScroll
import com.gios.brightrolodex.ui.theme.LightText
import com.gios.brightrolodex.ui.theme.LightTextVariant
import com.gios.brightrolodex.ui.theme.LightThemeTokens
import com.gios.brightrolodex.ui.theme.gridUnitsAsDp
import com.gios.brightrolodex.ui.theme.lightClickable

/**
 * FIND: the deck narrowed, not left behind.
 *
 * Search runs over every field, so this is where "beagle" finds Marco and "4B" finds whoever
 * lives there. Picking a result flips the deck to that person rather than opening a separate
 * detail page — there is only ever one card view in this app, which is what keeps the deck
 * metaphor from becoming a decoration.
 */
@Composable
fun FindScreen(
    query: String,
    results: List<Search.Result>,
    onQuery: (String) -> Unit,
    onOpen: (String) -> Unit,
    onNew: (String) -> Unit,
    onContacts: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = LightThemeTokens.colors
    val state = rememberLazyListState()
    WheelScroll(state)

    Column(Modifier.fillMaxSize().background(colors.background)) {
        LightTopBar(title = "FIND", left = "BACK", onLeft = onBack)

        Box(Modifier.padding(horizontal = lightInset())) {
            LightField(
                label = "name, place, or anything you remember",
                value = query,
                onChange = onQuery,
                imeAction = ImeAction.Search,
                placeholder = "beagle",
            )
        }

        LazyColumn(
            state = state,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = lightInset()),
        ) {
            items(results, key = { it.person.id }) { result ->
                PersonRow(
                    name = result.person.name,
                    // The snippet is the text that *matched*, not always `howIKnow`: a hit on a
                    // fact has to show the fact, or a search for "beagle" returns a row that
                    // gives no clue why it is there.
                    detail = result.snippet,
                    face = FaceJson.decode(result.person.faceJson),
                    initials = result.person.initials,
                    trailing = if (result.person.hasNumber) "phone" else null,
                    onClick = { onOpen(result.person.id) },
                )
            }
            if (results.isEmpty() && query.isNotBlank()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(top = 1f.gridUnitsAsDp())) {
                        LightText("Nobody matches that", LightTextVariant.Copy, lighten = true)
                        Spacer(Modifier.height(0.8f.gridUnitsAsDp()))
                        LightText(
                            text = "+ NEW PERSON CALLED " + query.uppercase(),
                            variant = LightTextVariant.Detail,
                            modifier = Modifier.lightClickable { onNew(query) },
                        )
                    }
                }
            }
        }

        LightBottomBar(
            items = listOf(
                BarItem("BACK") { onBack() },
                BarItem("NEW", enabled = query.isNotBlank()) { onNew(query) },
                BarItem("CONTACTS") { onContacts() },
            ),
        )
    }
}

/**
 * ADD FROM CONTACTS, and the reason it exists: an address book is not a rolodex.
 *
 * Nothing is imported automatically. Six hundred contacts accumulated over fifteen years would
 * make the deck useless, and the friend from third grade is not somebody you need a card for.
 * So every row here is a decision: ADD puts them in the deck, HIDE says they are not a rolodex
 * person and stops them being offered again.
 *
 * HIDE is not a delete. The contact stays in the phone, untouched, and the whole set can be
 * brought back from the bottom bar.
 */
@Composable
fun RosterScreen(
    candidates: List<ContactRow>,
    hiddenCount: Int,
    hasPermission: Boolean,
    onAdd: (ContactRow) -> Unit,
    onHide: (ContactRow) -> Unit,
    onUnhideAll: () -> Unit,
    onNew: () -> Unit,
    onGrant: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = LightThemeTokens.colors
    val state = rememberLazyListState()
    WheelScroll(state)

    Column(Modifier.fillMaxSize().background(colors.background)) {
        LightTopBar(
            title = "CONTACTS",
            left = "BACK",
            right = if (candidates.isEmpty()) null else candidates.size.toString(),
            onLeft = onBack,
        )

        if (!hasPermission) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = lightInset() * 2),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                LightText(
                    text = "Rolodex needs the address book to offer you the people already on " +
                        "this phone — and to write the ones you add back, so the dialer knows " +
                        "who is calling.",
                    variant = LightTextVariant.Paragraph,
                    align = TextAlign.Center,
                )
                Spacer(Modifier.height(1f.gridUnitsAsDp()))
                LightText(
                    text = "ALLOW",
                    variant = LightTextVariant.Button,
                    modifier = Modifier.lightClickable(onClick = onGrant),
                )
            }
        } else {
            LazyColumn(
                state = state,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = lightInset()),
            ) {
                items(candidates, key = { it.lookupKey }) { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .lightClickable { onAdd(row) },
                        ) {
                            LightText(row.name, LightTextVariant.Copy, maxLines = 1)
                            val numbers = row.phones.take(2).joinToString(" · ") { Phone.pretty(it) }
                            LightText(
                                text = numbers.ifBlank { "no number" },
                                variant = LightTextVariant.Detail,
                                lighten = true,
                                maxLines = 1,
                            )
                        }
                        LightText(
                            text = "HIDE",
                            variant = LightTextVariant.Micro,
                            color = colors.contentFaint,
                            modifier = Modifier
                                .lightClickable { onHide(row) }
                                .padding(start = 8.dp),
                        )
                    }
                }
                if (candidates.isEmpty()) {
                    item {
                        LightText(
                            text = "Everyone in your contacts is either in the rolodex or hidden.",
                            variant = LightTextVariant.Paragraph,
                            lighten = true,
                            modifier = Modifier.padding(top = 1f.gridUnitsAsDp()),
                        )
                    }
                }
            }
        }

        LightBottomBar(
            items = listOf(
                BarItem("BACK") { onBack() },
                BarItem("NEW") { onNew() },
                BarItem(
                    label = if (hiddenCount > 0) "UNHIDE " + hiddenCount else "UNHIDE",
                    enabled = hiddenCount > 0,
                ) { onUnhideAll() },
            ),
        )
    }
}

/** Archived people: still remembered, out of the deck. */
@Composable
fun ArchiveScreen(
    people: List<Person>,
    onUnhide: (String) -> Unit,
    onBack: () -> Unit,
) {
    val colors = LightThemeTokens.colors
    val state = rememberLazyListState()
    WheelScroll(state)

    Column(Modifier.fillMaxSize().background(colors.background)) {
        LightTopBar(title = "ARCHIVED", left = "BACK", onLeft = onBack)
        LazyColumn(
            state = state,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = lightInset()),
        ) {
            items(people, key = { it.id }) { person ->
                PersonRow(
                    name = person.name,
                    detail = person.howIKnow,
                    face = FaceJson.decode(person.faceJson),
                    initials = person.initials,
                    trailing = "RESTORE",
                    onClick = { onUnhide(person.id) },
                )
            }
            if (people.isEmpty()) {
                item {
                    LightText(
                        text = "Nobody archived.",
                        variant = LightTextVariant.Paragraph,
                        lighten = true,
                        modifier = Modifier.padding(top = 1f.gridUnitsAsDp()),
                    )
                }
            }
        }
        LightBottomBar(items = listOf(BarItem("BACK") { onBack() }))
    }
}

/**
 * MORE: everything that is not one of the three verbs in the bar.
 *
 * A screen rather than a bottom sheet. A Material `ModalBottomSheet` is its own window, so the
 * activity's `dispatchKeyEvent` never fires inside it and the wheel goes dead — and a menu the
 * wheel cannot scroll is the wrong menu on this phone.
 */
@Composable
fun MenuScreen(
    personName: String?,
    tags: List<String>,
    scope: String?,
    onDrawFace: () -> Unit,
    onEdit: () -> Unit,
    onSeen: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    onNew: () -> Unit,
    onContacts: () -> Unit,
    onArchived: () -> Unit,
    onScope: (String?) -> Unit,
    onBack: () -> Unit,
) {
    val colors = LightThemeTokens.colors
    val state = rememberLazyListState()
    WheelScroll(state)

    // Two taps to delete, with the label carrying the warning. A confirm dialog would be a
    // second window — see MenuScreen's own note about the wheel — and this is clearer anyway:
    // the button says what the next tap does.
    var armed by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(colors.background)) {
        LightTopBar(title = "MORE", left = "BACK", onLeft = onBack)
        LazyColumn(
            state = state,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = lightInset()),
        ) {
            if (personName != null) {
                item { MenuHeading(personName) }
                item { MenuRow("Draw their face", onDrawFace) }
                item { MenuRow("Edit details and facts", onEdit) }
                item { MenuRow("Mark as seen today", onSeen) }
                item { MenuRow("Archive them", onArchive) }
                item {
                    MenuRow(
                        label = if (armed) "Tap again to delete them" else "Delete them",
                        onClick = {
                            if (armed) onDelete() else armed = true
                        },
                        lighten = !armed,
                    )
                }
            }

            item { MenuHeading("Rolodex") }
            item { MenuRow("New person", onNew) }
            item { MenuRow("Add from contacts", onContacts) }
            item { MenuRow("Archived people", onArchived) }

            if (tags.isNotEmpty()) {
                item { MenuHeading("Show only") }
                item {
                    MenuRow(
                        label = "Everyone",
                        onClick = { onScope(null) },
                        lighten = scope != null,
                    )
                }
                items(tags) { tag ->
                    MenuRow(
                        label = tag,
                        onClick = { onScope(tag) },
                        lighten = !tag.equals(scope, ignoreCase = true),
                    )
                }
            }

            item { Spacer(Modifier.height(2f.gridUnitsAsDp())) }
            item {
                LightText(
                    text = "Faces are stored as drawings, not pictures, so they can always be " +
                        "edited again.",
                    variant = LightTextVariant.Micro,
                    lighten = true,
                )
            }
        }
        LightBottomBar(items = listOf(BarItem("BACK") { onBack() }))
    }
}

@Composable
private fun MenuHeading(text: String) {
    Column {
        Spacer(Modifier.height(1f.gridUnitsAsDp()))
        LightText(text.uppercase(), LightTextVariant.Micro, lighten = true)
    }
}

@Composable
private fun MenuRow(label: String, onClick: () -> Unit, lighten: Boolean = false) {
    LightText(
        text = label,
        variant = LightTextVariant.Copy,
        lighten = lighten,
        modifier = Modifier
            .fillMaxWidth()
            .lightClickable(onClick = onClick)
            .padding(vertical = 6.dp),
    )
}
