package com.gios.brightrolodex.share

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.gios.brightrolodex.data.Person
import com.gios.brightrolodex.data.Phone

/**
 * The three things a card hands to another app.
 *
 * Every one of them checks that something will actually receive the intent before firing it.
 * Roll once shipped a send button that reported "LightChat can't receive photos" on a phone
 * with LightChat installed — the intent resolved perfectly well, the app just could not *see*
 * that it did, because Android 11 hides activities you have not declared a `<queries>` entry
 * for. The manifest declares them; these functions still check, because the answer differs
 * between phones.
 */
object Handoff {

    private const val BRIGHT_CHAT = "com.gios.brightchat"
    private const val LIGHT_CHAT = "com.gios.lightchat"
    private const val NOTEBOOK = "com.gios.lightnotebook"

    /**
     * Texting.
     *
     * First choice is BrightChat directly, with the recipient in an `address` extra — the same
     * shape its share-in path already understands, so a message starts in the right thread with
     * no chooser. That path is `text/plain`, and **BrightChat currently only declares
     * `ACTION_SEND` for `image/*`**, so on today's builds this resolves to nothing and falls
     * through. It is written this way on purpose: adding one `text/plain` filter over there
     * turns this on, and until then `sms:` reaches whatever the phone's messaging app is.
     */
    fun text(context: Context, person: Person): Boolean {
        val number = person.phones.firstOrNull()?.let { Phone.dialable(it) } ?: return false

        for (pkg in listOf(BRIGHT_CHAT, LIGHT_CHAT)) {
            val direct = Intent(Intent.ACTION_SEND).apply {
                setPackage(pkg)
                type = "text/plain"
                putExtra("address", number)
                putExtra(Intent.EXTRA_TEXT, "")
            }
            if (direct.resolveActivity(context.packageManager) != null) {
                return start(context, direct)
            }
        }

        // ACTION_SENDTO with an sms: URI, not ACTION_VIEW: SENDTO is the one every messaging
        // app is required to handle and the one that cannot be answered by a browser.
        val sms = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + number))
        if (sms.resolveActivity(context.packageManager) != null) return start(context, sms)
        return false
    }

    /**
     * Calling.
     *
     * `ACTION_CALL` places the call, which is the point of a card — `ACTION_DIAL` would only
     * type the number into the keypad and wait. The dial intent is still the fallback for a
     * phone where the permission was refused, because a loaded keypad beats nothing happening.
     */
    fun call(context: Context, person: Person): Boolean {
        val number = person.phones.firstOrNull()?.let { Phone.dialable(it) } ?: return false
        val uri = Uri.parse("tel:" + number)
        val place = Intent(Intent.ACTION_CALL, uri)
        if (start(context, place)) return true
        val dial = Intent(Intent.ACTION_DIAL, uri)
        return start(context, dial)
    }

    /**
     * The long note, in LightNotebook.
     *
     * `externalKey` is `rolodex-<person id>` and never the name: renaming somebody would
     * otherwise orphan their note, and a note you cannot find again is worse than one you never
     * wrote. The same trick BrightChat's contact page uses, and the same trade-off — the note
     * lives in Notebook, and this app holds only the link.
     */
    fun note(context: Context, person: Person): Boolean {
        val uri = Uri.parse(
            "lightnotebook://note/rolodex-" + person.id +
                "?title=" + Uri.encode(person.name),
        )
        val intent = Intent(Intent.ACTION_VIEW, uri).apply { setPackage(NOTEBOOK) }
        if (intent.resolveActivity(context.packageManager) != null) return start(context, intent)
        // Without the package set, in case Notebook is installed under a name this build does
        // not know about.
        val loose = Intent(Intent.ACTION_VIEW, uri)
        return start(context, loose)
    }

    private fun start(context: Context, intent: Intent): Boolean = try {
        // FLAG_ACTIVITY_NEW_TASK because these are sometimes started from a context that is not
        // the foreground activity, and the throw it produces otherwise looks like a missing app.
        context.startActivity(intent.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        // ACTION_CALL without CALL_PHONE granted. Not an error worth showing: the caller falls
        // back to the dialer.
        false
    }
}
