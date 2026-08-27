package com.gios.brightrolodex

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gios.brightrolodex.data.archivedOf
import com.gios.brightrolodex.data.candidatesOf
import com.gios.brightrolodex.data.Search
import com.gios.brightrolodex.data.deckOrder
import com.gios.brightrolodex.face.FaceJson
import com.gios.brightrolodex.hw.LightKey
import com.gios.brightrolodex.hw.LightKeys
import com.gios.brightrolodex.hw.LocalWheelBus
import com.gios.brightrolodex.hw.WheelBus
import com.gios.brightrolodex.share.Handoff
import com.gios.brightrolodex.ui.ALL_LABEL
import com.gios.brightrolodex.ui.ArchiveScreen
import com.gios.brightrolodex.ui.DeckScreen
import com.gios.brightrolodex.ui.EditScreen
import com.gios.brightrolodex.ui.FaceEditorScreen
import com.gios.brightrolodex.ui.FindScreen
import com.gios.brightrolodex.ui.MenuScreen
import com.gios.brightrolodex.ui.RolodexViewModel
import com.gios.brightrolodex.ui.RosterScreen
import com.gios.brightrolodex.ui.Stage
import com.gios.brightrolodex.ui.lightInset
import com.gios.brightrolodex.ui.theme.BrightRolodexTheme
import com.gios.brightrolodex.ui.theme.LightText
import com.gios.brightrolodex.ui.theme.LightTextVariant

/**
 * One activity, six screens, and the wheel.
 *
 * [dispatchKeyEvent] is the only place the wheel and the camera button can be seen. LightOS
 * delivers them as ordinary [KeyEvent]s once its patched key layout is in place, but a focused
 * child view eats them before any Compose handler runs — so catching them at the window and
 * publishing to a [WheelBus] is the pattern the whole family uses.
 */
class MainActivity : ComponentActivity() {

    private val wheel = WheelBus()
    private var viewModel: RolodexViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            BrightRolodexTheme {
                val vm: RolodexViewModel = viewModel()
                viewModel = vm

                val stage by vm.stage.collectAsStateWithLifecycle()
                val people by vm.people.collectAsStateWithLifecycle()
                val scope by vm.scope.collectAsStateWithLifecycle()
                val query by vm.query.collectAsStateWithLifecycle()
                val toast by vm.toast.collectAsStateWithLifecycle()
                val contactRows by vm.contacts.collectAsStateWithLifecycle()
                val dismissed by vm.dismissed.collectAsStateWithLifecycle()
                val context = LocalContext.current

                // Both permissions in one prompt. Reading without writing would be a rolodex
                // that can adopt people and never give anything back, which is not a state worth
                // supporting — so they are asked for together and refused together.
                val contactsPermission = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions(),
                ) { granted ->
                    if (granted[Manifest.permission.READ_CONTACTS] == true) {
                        vm.loadContacts()
                        // Everything created before the grant was saved locally and never
                        // pushed; this is the one moment to catch the address book up.
                        vm.syncAllToContacts()
                    } else {
                        vm.say("Rolodex works without contacts, but only inside the app")
                    }
                }

                val callPermission = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission(),
                ) { granted ->
                    if (!granted) vm.say("Calls will open the dialer instead")
                }

                LaunchedEffect(Unit) {
                    if (vm.hasContactsPermission()) vm.loadContacts()
                    handleIntent(intent, vm)
                    // Consumed once: setIntent clears it, so a configuration change does not
                    // re-open the card the deep link named.
                    intent = Intent(this@MainActivity, MainActivity::class.java)
                }

                val deck = deckOrder(people, scope)

                Box(Modifier.fillMaxSize()) {
                    CompositionLocalProvider(LocalWheelBus provides wheel) {
                        when (val current = stage) {
                            is Stage.Deck -> {
                                val index = current.index.coerceIn(
                                    0,
                                    (deck.size - 1).coerceAtLeast(0),
                                )
                                val person = deck.getOrNull(index)
                                DeckScreen(
                                    person = person,
                                    face = FaceJson.decode(person?.faceJson),
                                    index = index,
                                    total = deck.size,
                                    scopeLabel = scope?.uppercase() ?: ALL_LABEL,
                                    onFlip = vm::flip,
                                    // The scope chip cycles rather than opening a picker: with
                                    // two or three tags in practice, cycling is one tap and a
                                    // picker is three.
                                    onScope = {
                                        val tags = vm.tagsIn(people)
                                        if (tags.isEmpty()) {
                                            vm.say("Tag someone to filter the deck")
                                        } else {
                                            val at = tags.indexOf(scope)
                                            vm.setScope(tags.getOrNull(at + 1))
                                        }
                                    },
                                    onMenu = { vm.go(Stage.Menu(index)) },
                                    onFind = { vm.go(Stage.Find) },
                                    onFace = {
                                        person?.let { vm.go(Stage.DrawFace(it.id)) }
                                    },
                                    onEdit = { person?.let { vm.go(Stage.Edit(it.id)) } },
                                    onText = {
                                        person?.let {
                                            if (!Handoff.text(context, it)) {
                                                vm.say("Nothing on this phone can send a text")
                                            }
                                        }
                                    },
                                    onCall = {
                                        person?.let {
                                            if (!Handoff.call(context, it)) {
                                                callPermission.launch(Manifest.permission.CALL_PHONE)
                                            }
                                        }
                                    },
                                    onSeen = { person?.let { vm.seenNow(it.id) } },
                                    onAddSomeone = { vm.go(Stage.Roster) },
                                )
                            }

                            is Stage.Find -> FindScreen(
                                query = query,
                                results = Search.query(people, query),
                                onQuery = vm::setQuery,
                                onOpen = { id ->
                                    vm.setQuery("")
                                    vm.openPerson(id)
                                },
                                onNew = { name ->
                                    vm.setQuery("")
                                    val id = vm.newPerson(name)
                                    vm.go(Stage.Edit(id))
                                },
                                onContacts = { vm.go(Stage.Roster) },
                                onBack = { vm.go(Stage.Deck()) },
                            )

                            is Stage.Edit -> {
                                val person = vm.person(current.personId)
                                if (person == null) {
                                    LaunchedEffect(current.personId) { vm.go(Stage.Deck()) }
                                } else {
                                    EditScreen(
                                        person = person,
                                        onCommit = vm::update,
                                        onDrawFace = { vm.go(Stage.DrawFace(person.id)) },
                                        onNote = {
                                            if (!Handoff.note(context, person)) {
                                                vm.say("LightNotebook isn't installed")
                                            }
                                        },
                                        onBack = { vm.openPerson(person.id) },
                                    )
                                }
                            }

                            is Stage.DrawFace -> {
                                val person = vm.person(current.personId)
                                if (person == null) {
                                    LaunchedEffect(current.personId) { vm.go(Stage.Deck()) }
                                } else {
                                    FaceEditorScreen(
                                        // Reads the stored parts and strokes, never a rendered
                                        // image, so every face is editable again from here for
                                        // as long as the person exists.
                                        initial = vm.face(person.id),
                                        onSave = { face ->
                                            vm.setFace(person.id, face)
                                            vm.openPerson(person.id)
                                        },
                                        onCancel = { vm.openPerson(person.id) },
                                    )
                                }
                            }

                            is Stage.Roster -> RosterScreen(
                                candidates = candidatesOf(contactRows, people, dismissed),
                                hiddenCount = dismissed.size,
                                hasPermission = vm.hasContactsPermission(),
                                onAdd = { row ->
                                    val id = vm.adopt(row)
                                    vm.openPerson(id)
                                },
                                onHide = vm::dismiss,
                                onUnhideAll = vm::undismissAll,
                                onNew = {
                                    val id = vm.newPerson("")
                                    vm.go(Stage.Edit(id))
                                },
                                onGrant = {
                                    contactsPermission.launch(
                                        arrayOf(
                                            Manifest.permission.READ_CONTACTS,
                                            Manifest.permission.WRITE_CONTACTS,
                                        ),
                                    )
                                },
                                onBack = { vm.go(Stage.Deck()) },
                            )

                            is Stage.Archive -> ArchiveScreen(
                                people = archivedOf(people),
                                onUnhide = { vm.setHidden(it, false) },
                                onBack = { vm.go(Stage.Deck()) },
                            )

                            is Stage.Menu -> {
                                val person = deck.getOrNull(current.index)
                                MenuScreen(
                                    personName = person?.name,
                                    tags = vm.tagsIn(people),
                                    scope = scope,
                                    onDrawFace = {
                                        person?.let { vm.go(Stage.DrawFace(it.id)) }
                                    },
                                    onEdit = { person?.let { vm.go(Stage.Edit(it.id)) } },
                                    onSeen = {
                                        person?.let {
                                            vm.seenNow(it.id)
                                            vm.openPerson(it.id)
                                        }
                                    },
                                    onArchive = { person?.let { vm.setHidden(it.id, true) } },
                                    onDelete = { person?.let { vm.delete(it.id) } },
                                    onNew = {
                                        val id = vm.newPerson("")
                                        vm.go(Stage.Edit(id))
                                    },
                                    onContacts = {
                                        if (!vm.hasContactsPermission()) {
                                            contactsPermission.launch(
                                                arrayOf(
                                                    Manifest.permission.READ_CONTACTS,
                                                    Manifest.permission.WRITE_CONTACTS,
                                                ),
                                            )
                                        }
                                        vm.go(Stage.Roster)
                                    },
                                    onArchived = { vm.go(Stage.Archive) },
                                    onScope = vm::setScope,
                                    onBack = { vm.go(Stage.Deck(current.index)) },
                                )
                            }
                        }
                    }

                    // Back always means "up to the deck", never "out of the app" unless the deck
                    // is already what is showing.
                    BackHandler(enabled = stage !is Stage.Deck) {
                        vm.go(Stage.Deck())
                    }

                    toast?.let { message ->
                        LaunchedEffect(message) {
                            kotlinx.coroutines.delay(2600)
                            vm.say(null)
                        }
                        Box(
                            Modifier.align(Alignment.BottomCenter).fillMaxSize(),
                            contentAlignment = Alignment.BottomCenter,
                        ) {
                            LightText(
                                text = message,
                                variant = LightTextVariant.Superfine,
                                align = TextAlign.Center,
                                modifier = Modifier.padding(bottom = lightInset() * 5),
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        viewModel?.let { handleIntent(intent, it) }
    }

    /** `brightrolodex://person/<id>`, from another app. */
    private fun handleIntent(intent: Intent?, vm: RolodexViewModel) {
        val data = intent?.data ?: return
        if (data.scheme != "brightrolodex" || data.host != "person") return
        val id = data.lastPathSegment ?: return
        vm.openPerson(id)
    }

    /**
     * The wheel, and the camera button.
     *
     * Returns false for the wheel so the event carries on — a screen with a scrolling list is
     * also entitled to see it. The camera button is consumed: it means "draw this face", which
     * is the app's one shortcut worth a physical button.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (LightKeys.of(event)) {
                LightKey.WheelUp -> wheel.send(1)
                LightKey.WheelDown -> wheel.send(-1)
                LightKey.Camera -> {
                    viewModel?.let { vm ->
                        val stage = vm.stage.value
                        if (stage is Stage.Deck) {
                            vm.personAt(stage.index)?.let { vm.go(Stage.DrawFace(it.id)) }
                        }
                    }
                    return true
                }
                else -> Unit
            }
        }
        return super.dispatchKeyEvent(event)
    }
}
