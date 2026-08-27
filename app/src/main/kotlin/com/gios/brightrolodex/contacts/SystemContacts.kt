package com.gios.brightrolodex.contacts

import android.content.ContentProviderOperation
import android.content.ContentResolver
import android.content.Context
import android.provider.ContactsContract
import com.gios.brightrolodex.data.Person
import com.gios.brightrolodex.data.Phone
import com.gios.brightrolodex.face.FaceJson
import com.gios.brightrolodex.face.FaceRaster

/** One row of the address book, as the import picker sees it. */
data class ContactRow(
    val lookupKey: String,
    val contactId: Long,
    val name: String,
    val phones: List<String>,
)

/**
 * The address book, read and written.
 *
 * **Rolodex owns its people, and it writes them to the *local device account*.**
 *
 * The textbook way for an app to own contacts is to register an account type of its own with a
 * stub authenticator and sync adapter. That is also how you lose everything: raw contacts under
 * an app's account type are deleted with the app, so one uninstall — or one bad update that
 * needed a reinstall — takes every neighbour, every relationship and every drawn face with it.
 * A raw contact in the local device account (`ACCOUNT_TYPE = null`) survives all of that, which
 * is the property that matters for data nobody can retype.
 *
 * The consequences are honest ones and worth stating:
 *
 *  - A person entered here with no number *does* appear in LightOS's own Contacts app. That is
 *    the point — "the guy with the van" becomes findable by anything on the phone, not just by
 *    this app — but it is a visible change to the address book.
 *  - The face becomes the contact photo, which is what makes it appear in the stock dialer and
 *    in BrightChat without either of them knowing this app exists.
 *
 * Rolodex only ever writes to raw contacts **it created**, identified by [SOURCE_PREFIX] in
 * `SOURCE_ID`. It never edits a raw contact that came from Google or the SIM: contact
 * aggregation merges ours with theirs on the same person anyway, so setting our photo as the
 * super-primary is enough to make the drawn face win without touching a synced row.
 */
object SystemContacts {

    private const val SOURCE_PREFIX = "brightrolodex:"

    private fun sourceId(personId: String) = SOURCE_PREFIX + personId

    /* ------------------------------------------------------------------ reading */

    /**
     * Every contact with a name, whether or not it has a number.
     *
     * `DISPLAY_NAME_PRIMARY` off the aggregate `Contacts` table and not off raw contacts, so a
     * person who exists in both Google and the SIM appears once. Rows with no name at all are
     * dropped: a bare number cannot be told apart from another bare number in a picker.
     */
    fun all(context: Context): List<ContactRow> {
        val resolver = context.contentResolver
        val numbers = numbersByContactId(resolver)
        val out = ArrayList<ContactRow>()

        resolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.LOOKUP_KEY,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
            ),
            null,
            null,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY + " COLLATE NOCASE ASC",
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                val lookup = cursor.getString(1) ?: continue
                val name = cursor.getString(2)?.trim().orEmpty()
                if (name.isEmpty()) continue
                out += ContactRow(
                    lookupKey = lookup,
                    contactId = id,
                    name = name,
                    phones = numbers[id].orEmpty(),
                )
            }
        }
        return out
    }

    private fun numbersByContactId(resolver: ContentResolver): Map<Long, List<String>> {
        val map = HashMap<Long, MutableList<String>>()
        resolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
            ),
            null,
            null,
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                val number = cursor.getString(1)?.trim().orEmpty()
                if (number.isEmpty()) continue
                val list = map.getOrPut(id) { ArrayList() }
                // De-duplicated on the comparison key, not on the string: the same number
                // formatted two ways in two accounts is one number, and a picker that offers
                // it twice looks broken.
                if (list.none { Phone.sameNumber(it, number) }) list += number
            }
        }
        return map
    }

    /* ------------------------------------------------------------------ writing */

    /**
     * Creates or updates this app's raw contact for [person].
     *
     * Everything is one batch, so a failure leaves the address book as it was rather than with a
     * nameless contact in it. The data rows this app manages are deleted and re-inserted rather
     * than updated in place: an update has to find the row first, and "no row yet" and "row
     * changed" are then two code paths that drift apart. The rows are ours, so re-creating them
     * costs nothing anyone else can see.
     */
    fun push(context: Context, person: Person): Boolean = runCatching {
        val resolver = context.contentResolver
        val existing = rawContactId(resolver, person.id)
        val ops = ArrayList<ContentProviderOperation>()

        val rawIndex: Int
        if (existing == null) {
            rawIndex = ops.size
            ops += ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                // Both null: the local device account. Not an account type of our own — see
                // the class comment for why that would be a data-loss bug.
                .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                .withValue(ContactsContract.RawContacts.SOURCE_ID, sourceId(person.id))
                .build()
        } else {
            rawIndex = -1
            ops += ContentProviderOperation.newDelete(ContactsContract.Data.CONTENT_URI)
                .withSelection(
                    ContactsContract.Data.RAW_CONTACT_ID + " = ? AND " +
                        ContactsContract.Data.MIMETYPE + " IN (?,?,?,?,?)",
                    arrayOf(
                        existing.toString(),
                        ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE,
                        ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
                        ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE,
                        ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE,
                        ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE,
                    ),
                )
                .build()
        }

        fun insertData(mimeType: String): ContentProviderOperation.Builder {
            val builder = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
            if (existing == null) {
                builder.withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawIndex)
            } else {
                builder.withValue(ContactsContract.Data.RAW_CONTACT_ID, existing)
            }
            return builder.withValue(ContactsContract.Data.MIMETYPE, mimeType)
        }

        ops += insertData(ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
            .withValue(
                ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME,
                person.name.ifBlank { "Someone" },
            )
            .build()

        person.phones.filter { it.isNotBlank() }.forEach { number ->
            ops += insertData(ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, number)
                .withValue(
                    ContactsContract.CommonDataKinds.Phone.TYPE,
                    ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE,
                )
                .build()
        }

        // How you know them, in the contact's own Note field, so the stock Contacts app answers
        // "who is this" too. Cheap, and it means the useful half of a card is not locked inside
        // one app.
        val note = listOf(person.howIKnow, person.where)
            .filter { it.isNotBlank() }
            .joinToString(" · ")
        if (note.isNotBlank()) {
            ops += insertData(ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.Note.NOTE, note)
                .build()
        }

        if (person.birthday.isNotBlank()) {
            ops += insertData(ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE)
                .withValue(
                    ContactsContract.CommonDataKinds.Event.START_DATE,
                    // `--MM-DD` is the RFC form for a birthday with no year, and the platform
                    // parses it. Storing a made-up year instead is how people end up 2000 years
                    // old in other apps.
                    if (person.birthday.length <= 5) "--" + person.birthday else person.birthday,
                )
                .withValue(
                    ContactsContract.CommonDataKinds.Event.TYPE,
                    ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY,
                )
                .build()
        }

        val face = FaceJson.decode(person.faceJson)
        // `isDrawn`, not `isBlank`: every new person starts with a head outline, and writing
        // that as a contact photo would put the same empty oval on everyone in the address book.
        if (face.isDrawn) {
            ops += insertData(ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE)
                .withValue(
                    ContactsContract.CommonDataKinds.Photo.PHOTO,
                    FaceRaster.png(face),
                )
                // Makes the drawn face beat a photo on a Google raw contact that aggregation
                // has merged with ours. Without it the face is only a candidate and the
                // synced photo usually wins.
                .withValue(ContactsContract.Data.IS_SUPER_PRIMARY, 1)
                .build()
        }

        resolver.applyBatch(ContactsContract.AUTHORITY, ops)
        true
    }.getOrDefault(false)

    /** Removes this app's raw contact for [personId], leaving any synced raw contact alone. */
    fun remove(context: Context, personId: String): Boolean = runCatching {
        val id = rawContactId(context.contentResolver, personId) ?: return@runCatching true
        context.contentResolver.delete(
            ContactsContract.RawContacts.CONTENT_URI,
            ContactsContract.RawContacts._ID + " = ?",
            arrayOf(id.toString()),
        )
        true
    }.getOrDefault(false)

    private fun rawContactId(resolver: ContentResolver, personId: String): Long? =
        resolver.query(
            ContactsContract.RawContacts.CONTENT_URI,
            arrayOf(ContactsContract.RawContacts._ID),
            ContactsContract.RawContacts.SOURCE_ID + " = ? AND " +
                ContactsContract.RawContacts.DELETED + " = 0",
            arrayOf(sourceId(personId)),
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0) else null
        }
}
