package com.gios.brightrolodex.data

import com.gios.brightrolodex.contacts.ContactRow

/**
 * The orderings and filters the screens draw from.
 *
 * Pure top-level functions rather than methods on the view model, for a Compose reason as much
 * as a testing one: a method that reads `_people.value` is invisible to Compose, so a screen
 * calling it never recomposes when a person changes. Taking the list as a parameter forces the
 * caller to have collected it, which is what makes the deck update when a face is drawn.
 */

/**
 * The deck's order: alphabetical, archived removed, scoped to one tag if one is set.
 *
 * Alphabetical and not most-recently-seen. A deck you flip is a physical object in the user's
 * head, and the reason a rolodex works is that the same person is always in the same place;
 * an order that moves people around defeats the muscle memory that makes flipping fast.
 */
fun deckOrder(people: List<Person>, tag: String?): List<Person> = people
    .filter { !it.hidden }
    .filter { person -> tag == null || person.tags.any { it.equals(tag, ignoreCase = true) } }
    .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

fun archivedOf(people: List<Person>): List<Person> = people
    .filter { it.hidden }
    .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

/**
 * True if [row] is the same human as [person].
 *
 * Two tests, and the number one is the one that earns its keep: somebody typed into the rolodex
 * before the contacts permission was granted has no lookup key, but is obviously already here.
 */
fun isSameHuman(person: Person, row: ContactRow): Boolean {
    if (person.contactLookupKey != null && person.contactLookupKey == row.lookupKey) return true
    return row.phones.any { number -> person.phones.any { Phone.sameNumber(it, number) } }
}

/** Address-book rows not already in the rolodex and not dismissed. */
fun candidatesOf(
    contacts: List<ContactRow>,
    people: List<Person>,
    dismissed: Set<String>,
): List<ContactRow> = contacts.filter { row ->
    row.lookupKey !in dismissed && people.none { isSameHuman(it, row) }
}
