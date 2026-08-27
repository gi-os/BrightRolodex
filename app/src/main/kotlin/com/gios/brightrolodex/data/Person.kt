package com.gios.brightrolodex.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * One line you remembered about someone.
 *
 * Facts are separate rows rather than one note field, and that is the load-bearing decision in
 * this whole app. Search has to hit each one individually — typing "beagle" has to find Marco —
 * and a single blob makes that a substring match on a paragraph, which finds everything or
 * nothing. It also means adding a fact is one tap and one line, which is the only way facts
 * actually get added.
 *
 * Long-form notes are not here at all: the card's NOTE button opens LightNotebook.
 */
data class Fact(
    val id: String,
    val text: String,
    val createdAt: Long,
)

/** "Denise's ex", "Pete's owner", "works with Ray". */
data class Link(
    val toId: String,
    val label: String,
)

/**
 * A person in the rolodex.
 *
 * There is no "contact" versus "non-contact" distinction anywhere in this model, on purpose.
 * The difference is [phones] being empty, and everything downstream reads it that way: the card
 * shows TEXT and CALL when there is a number and SEEN when there is not. Two entity types would
 * have meant two lists, two searches and two editors, for a distinction that is one nullable
 * field.
 */
data class Person(
    val id: String,
    val name: String,
    /** "the super", "bodega night shift", "Denise's ex". The field that answers "who is this". */
    val howIKnow: String = "",
    /** A place, not an address: "4B", "corner of Clinton", "dog park". */
    val where: String = "",
    val phones: List<String> = emptyList(),
    /** `MM-DD` or `YYYY-MM-DD`. Stored as text because a birthday with no year is normal. */
    val birthday: String = "",
    /** [com.gios.brightrolodex.face.FaceJson] — the editable source of truth for the drawing. */
    val faceJson: String = "",
    val facts: List<Fact> = emptyList(),
    val tags: List<String> = emptyList(),
    val links: List<Link> = emptyList(),
    /**
     * Archived: keeps the person and everything remembered about them, and takes them out of
     * the deck and out of search.
     *
     * Deliberately not the same thing as [Dismissals], which is about address-book rows that
     * were never in the rolodex to begin with. Hiding someone here is "not now"; dismissing a
     * contact there is "this person is not a rolodex person".
     */
    val hidden: Boolean = false,
    /** Manual for people with no number, from the call log for people with one. */
    val lastSeenAt: Long = 0,
    val createdAt: Long = 0,
    /**
     * The system contact this person is written to.
     *
     * Re-resolved on read rather than trusted: contact aggregation changes a lookup key when
     * two raw contacts merge, and a stale key is how a second copy of somebody gets created.
     */
    val contactLookupKey: String? = null,
    /** True if this person came *from* the address book rather than being typed in here. */
    val fromContacts: Boolean = false,
) {
    val hasNumber: Boolean get() = phones.any { it.isNotBlank() }

    /**
     * Initials, for a card with no face drawn yet.
     *
     * Two words at most, and it takes the first and last rather than the first two: "Guy with
     * the van" reads better as GV than as GW.
     */
    val initials: String
        get() {
            val words = name.trim().split(' ', '-').filter { it.isNotBlank() }
            return when (words.size) {
                0 -> "?"
                1 -> words[0].take(1).uppercase()
                else -> (words.first().take(1) + words.last().take(1)).uppercase()
            }
        }
}

object PersonJson {

    fun encodeAll(people: List<Person>): String = JSONObject().apply {
        put("v", 1)
        put("people", JSONArray().apply { people.forEach { put(encode(it)) } })
    }.toString()

    fun decodeAll(json: String?): List<Person> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching { decodeAllStrict(json) }.getOrDefault(emptyList())
    }

    /**
     * Throws if the text is not a rolodex file.
     *
     * The strict twin exists because [decodeAll] cannot tell a caller the difference between an
     * empty rolodex and an unreadable file, and [com.gios.brightrolodex.data.RolodexStore] has
     * to know: one is a new phone, the other is the moment to fall back to the backup. A store
     * that reads a corrupt file as "no people" would quietly present an empty app and then
     * overwrite the backup with it.
     */
    fun decodeAllStrict(json: String): List<Person> {
        val array = JSONObject(json).optJSONArray("people") ?: JSONArray()
        return (0 until array.length()).mapNotNull { i ->
            array.optJSONObject(i)?.let { decode(it) }
        }
    }

    fun encode(person: Person): JSONObject = JSONObject().apply {
        put("id", person.id)
        put("name", person.name)
        putIfSet("how", person.howIKnow)
        putIfSet("where", person.where)
        putIfSet("bday", person.birthday)
        putIfSet("face", person.faceJson)
        if (person.phones.isNotEmpty()) {
            put("phones", JSONArray().apply { person.phones.forEach { put(it) } })
        }
        if (person.tags.isNotEmpty()) {
            put("tags", JSONArray().apply { person.tags.forEach { put(it) } })
        }
        if (person.facts.isNotEmpty()) {
            put(
                "facts",
                JSONArray().apply {
                    person.facts.forEach { fact ->
                        put(
                            JSONObject().apply {
                                put("id", fact.id)
                                put("t", fact.text)
                                put("at", fact.createdAt)
                            },
                        )
                    }
                },
            )
        }
        if (person.links.isNotEmpty()) {
            put(
                "links",
                JSONArray().apply {
                    person.links.forEach { link ->
                        put(
                            JSONObject().apply {
                                put("to", link.toId)
                                put("l", link.label)
                            },
                        )
                    }
                },
            )
        }
        if (person.hidden) put("hidden", true)
        if (person.lastSeenAt != 0L) put("seen", person.lastSeenAt)
        if (person.createdAt != 0L) put("made", person.createdAt)
        person.contactLookupKey?.let { put("lookup", it) }
        if (person.fromContacts) put("fromContacts", true)
    }

    private fun JSONObject.putIfSet(key: String, value: String) {
        if (value.isNotBlank()) put(key, value)
    }

    fun decode(o: JSONObject): Person? {
        val id = o.optString("id").takeIf { it.isNotBlank() } ?: return null
        return Person(
            id = id,
            name = o.optString("name"),
            howIKnow = o.optString("how"),
            where = o.optString("where"),
            phones = o.optJSONArray("phones").toStringList(),
            birthday = o.optString("bday"),
            faceJson = o.optString("face"),
            facts = o.optJSONArray("facts").let { array ->
                if (array == null) {
                    emptyList()
                } else {
                    (0 until array.length()).mapNotNull { i ->
                        val f = array.optJSONObject(i) ?: return@mapNotNull null
                        val text = f.optString("t")
                        if (text.isBlank()) return@mapNotNull null
                        Fact(
                            id = f.optString("id").takeIf { it.isNotBlank() } ?: "$id-$i",
                            text = text,
                            createdAt = f.optLong("at", 0L),
                        )
                    }
                }
            },
            tags = o.optJSONArray("tags").toStringList(),
            links = o.optJSONArray("links").let { array ->
                if (array == null) {
                    emptyList()
                } else {
                    (0 until array.length()).mapNotNull { i ->
                        val l = array.optJSONObject(i) ?: return@mapNotNull null
                        val to = l.optString("to")
                        if (to.isBlank()) return@mapNotNull null
                        Link(toId = to, label = l.optString("l"))
                    }
                }
            },
            hidden = o.optBoolean("hidden", false),
            lastSeenAt = o.optLong("seen", 0L),
            createdAt = o.optLong("made", 0L),
            contactLookupKey = o.optString("lookup").takeIf { it.isNotBlank() },
            fromContacts = o.optBoolean("fromContacts", false),
        )
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return (0 until length()).map { optString(it) }.filter { it.isNotBlank() }
    }
}
