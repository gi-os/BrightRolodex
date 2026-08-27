package com.gios.brightrolodex.ui

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gios.brightrolodex.contacts.ContactRow
import com.gios.brightrolodex.contacts.SystemContacts
import com.gios.brightrolodex.data.Fact
import com.gios.brightrolodex.data.Person
import com.gios.brightrolodex.data.RolodexStore
import com.gios.brightrolodex.data.Search
import com.gios.brightrolodex.data.deckOrder
import com.gios.brightrolodex.data.isSameHuman
import com.gios.brightrolodex.face.Face
import com.gios.brightrolodex.face.FaceJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

sealed interface Stage {
    /** The deck. [index] is a position in the current scope, not an id, because the wheel moves it. */
    data class Deck(val index: Int = 0) : Stage
    data object Find : Stage
    data class Edit(val personId: String) : Stage
    data class DrawFace(val personId: String) : Stage
    data object Roster : Stage
    data object Archive : Stage

    /**
     * MORE. Carries the deck index it was opened from, so the actions on it act on the card
     * that was showing and BACK returns to that card rather than to the front of the deck.
     */
    data class Menu(val index: Int) : Stage
}

class RolodexViewModel(app: Application) : AndroidViewModel(app) {

    /**
     * Held in a field rather than reached through `getApplication()` at each use.
     * `getApplication` is `fun <T : Application> getApplication(): T`, so every call site where
     * the parameter is a `Context` leans on inference to pick T, and the error when it cannot is
     * about type parameters rather than about anything the code is doing.
     */
    private val app: Application = app

    private val store = RolodexStore(app.filesDir)

    private val _people = MutableStateFlow<List<Person>>(emptyList())
    val people: StateFlow<List<Person>> = _people.asStateFlow()

    private val _stage = MutableStateFlow<Stage>(Stage.Deck())
    val stage: StateFlow<Stage> = _stage.asStateFlow()

    /** null means every person; otherwise the deck is scoped to one tag. */
    private val _scope = MutableStateFlow<String?>(null)
    val scope: StateFlow<String?> = _scope.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

    private val _contacts = MutableStateFlow<List<ContactRow>>(emptyList())
    val contacts: StateFlow<List<ContactRow>> = _contacts.asStateFlow()

    private val _dismissed = MutableStateFlow<Set<String>>(emptySet())
    val dismissed: StateFlow<Set<String>> = _dismissed.asStateFlow()

    init {
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) { store.read() }
            val dismissedKeys = withContext(Dispatchers.IO) { store.readDismissed() }
            _people.value = loaded
            _dismissed.value = dismissedKeys
        }
    }

    /* ------------------------------------------------------------------ the deck */

    /**
     * The deck order, for the view model's own imperative use — [flip] and [openPerson] need to
     * know where a person sits.
     *
     * Screens must **not** call this. It reads `_people.value` directly, which Compose cannot
     * observe, so a screen built from it silently stops updating. Screens collect [people] and
     * call [deckOrder] on the value they collected.
     */
    private fun deck(): List<Person> = deckOrder(_people.value, _scope.value)

    fun go(stage: Stage) {
        _stage.value = stage
    }

    fun say(message: String?) {
        _toast.value = message
    }

    fun setQuery(value: String) {
        _query.value = value
    }

    fun setScope(tag: String?) {
        _scope.value = tag
        // Back to the front of the deck. Keeping the index across a scope change lands on a
        // different person than the one on screen, which reads as the app losing your place.
        _stage.value = Stage.Deck(0)
    }

    /**
     * Moves the deck by [delta] cards and stops at the ends.
     *
     * Deliberately not wrapping. A deck that loops has no edge, so "have I seen everyone" stops
     * being answerable by flipping to the end — and the wheel makes it very easy to fly past
     * the join without noticing.
     */
    fun flip(delta: Int) {
        val size = deck().size
        if (size == 0) return
        val current = (_stage.value as? Stage.Deck)?.index ?: 0
        val next = (current + delta).coerceIn(0, size - 1)
        if (next != current) _stage.value = Stage.Deck(next)
    }

    fun openPerson(personId: String) {
        val index = deck().indexOfFirst { it.id == personId }
        if (index >= 0) {
            _stage.value = Stage.Deck(index)
        } else {
            // Archived, or filtered out by the current scope. Clearing the scope is the only
            // way the requested person is actually on screen, which is what opening one means.
            _scope.value = null
            val cleared = deck().indexOfFirst { it.id == personId }
            _stage.value = Stage.Deck(cleared.coerceAtLeast(0))
        }
    }

    fun personAt(index: Int): Person? = deck().getOrNull(index)

    fun person(id: String): Person? = _people.value.firstOrNull { it.id == id }

    /**
     * Every tag in use, most-used first.
     *
     * Takes the list rather than reading `_people.value`, so a screen that calls it recomposes
     * when a tag is added — the same reason [deckOrder] is a free function.
     */
    fun tagsIn(people: List<Person>): List<String> = Search.tags(people)

    /* ------------------------------------------------------------------ mutations */

    private fun commit(people: List<Person>, push: Person? = null) {
        _people.value = people
        viewModelScope.launch(Dispatchers.IO) {
            store.write(people)
            if (push != null && hasContactsPermission()) {
                SystemContacts.push(app, push)
            }
        }
    }

    fun newPerson(name: String): String {
        val person = Person(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            createdAt = System.currentTimeMillis(),
            faceJson = FaceJson.encode(Face.blank()),
        )
        commit(_people.value + person, push = person)
        return person.id
    }

    /** Adds an address-book row to the rolodex, keeping the link back to where it came from. */
    fun adopt(row: ContactRow): String {
        val existing = _people.value.firstOrNull { isSameHuman(it, row) }
        if (existing != null) return existing.id

        val person = Person(
            id = UUID.randomUUID().toString(),
            name = row.name,
            phones = row.phones,
            createdAt = System.currentTimeMillis(),
            faceJson = FaceJson.encode(Face.blank()),
            contactLookupKey = row.lookupKey,
            fromContacts = true,
        )
        // Not pushed back to the address book: this person is already in it. Pushing would
        // create a second raw contact for somebody who is only being adopted, and the whole
        // point of the picker is that it does not touch the contacts you did not pick.
        commit(_people.value + person)
        return person.id
    }

    fun update(person: Person) {
        commit(_people.value.map { if (it.id == person.id) person else it }, push = person)
    }

    /**
     * Replaces the drawing.
     *
     * The face JSON stays the source of truth; the contact photo is re-rendered from it on every
     * save. That is what makes a face re-editable forever — reopening the editor reads these
     * parts and strokes back, never a flattened PNG.
     */
    fun setFace(personId: String, face: Face) {
        val person = person(personId) ?: return
        update(person.copy(faceJson = FaceJson.encode(face)))
    }

    fun face(personId: String): Face = FaceJson.decode(person(personId)?.faceJson)

    fun addFact(personId: String, text: String) {
        val person = person(personId) ?: return
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val fact = Fact(
            id = UUID.randomUUID().toString(),
            text = trimmed,
            createdAt = System.currentTimeMillis(),
        )
        update(person.copy(facts = person.facts + fact))
    }

    fun removeFact(personId: String, factId: String) {
        val person = person(personId) ?: return
        update(person.copy(facts = person.facts.filterNot { it.id == factId }))
    }

    fun seenNow(personId: String) {
        val person = person(personId) ?: return
        update(person.copy(lastSeenAt = System.currentTimeMillis()))
        say("Marked as seen today")
    }

    fun setHidden(personId: String, hidden: Boolean) {
        val person = person(personId) ?: return
        // Everything they remembered is kept; they just stop being in the deck and in search.
        commit(_people.value.map { if (it.id == personId) it.copy(hidden = hidden) else it })
        _stage.value = Stage.Deck(0)
        say(if (hidden) person.name + " archived" else person.name + " back in the deck")
    }

    fun delete(personId: String) {
        val person = person(personId) ?: return
        commit(_people.value.filterNot { it.id == personId })
        viewModelScope.launch(Dispatchers.IO) {
            if (hasContactsPermission()) SystemContacts.remove(app, personId)
        }
        _stage.value = Stage.Deck(0)
        say(person.name + " deleted")
    }

    /* ------------------------------------------------------------------ the address book */

    fun hasContactsPermission(): Boolean =
        ContextCompat.checkSelfPermission(app, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    fun loadContacts() {
        if (!hasContactsPermission()) return
        viewModelScope.launch {
            val rows = withContext(Dispatchers.IO) { SystemContacts.all(app) }
            _contacts.value = rows
        }
    }

    /**
     * "My friend from third grade is not a rolodex person."
     *
     * A dismissal is about the address book, not about a person in the rolodex, so it lives in
     * its own file and is reversible from the same screen. It is not a delete: the contact is
     * untouched and still in the phone.
     */
    fun dismiss(row: ContactRow) {
        val next = _dismissed.value + row.lookupKey
        _dismissed.value = next
        viewModelScope.launch(Dispatchers.IO) { store.writeDismissed(next) }
    }

    fun undismissAll() {
        _dismissed.value = emptySet()
        viewModelScope.launch(Dispatchers.IO) { store.writeDismissed(emptySet()) }
        say("Hidden contacts are back")
    }

    /**
     * Writes every person back to the address book.
     *
     * Called after the contacts permission is granted, because everything created before that
     * point was saved locally and never pushed.
     *
     * Adopted people are included, and that is not an oversight. Rolodex's own raw contact for
     * an adopted person is how the drawn face becomes their *contact photo* — which is the whole
     * reason the face turns up in the stock dialer and in BrightChat without either of them
     * knowing this app exists. Contact aggregation merges our raw contact with the synced one
     * because the name and number match, so it shows up as one person.
     *
     * People with a blank face and nothing added are skipped: there is nothing to contribute
     * yet, and an empty raw contact is the one case that could aggregate badly and read as a
     * duplicate.
     */
    fun syncAllToContacts() {
        if (!hasContactsPermission()) return
        val toPush = _people.value.filter { person ->
            !person.fromContacts || FaceJson.decode(person.faceJson).isDrawn ||
                person.howIKnow.isNotBlank() || person.birthday.isNotBlank()
        }
        viewModelScope.launch(Dispatchers.IO) {
            toPush.forEach { SystemContacts.push(app, it) }
        }
    }
}
