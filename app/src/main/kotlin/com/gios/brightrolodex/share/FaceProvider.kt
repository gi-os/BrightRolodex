package com.gios.brightrolodex.share

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import com.gios.brightrolodex.data.Phone
import com.gios.brightrolodex.data.RolodexStore
import com.gios.brightrolodex.face.FaceJson
import com.gios.brightrolodex.face.FaceRaster
import java.io.File

/**
 * The faces, offered to the rest of the family.
 *
 * `content://com.gios.brightrolodex.faces/<number>` returns a PNG of that person's drawn face,
 * or nothing if there is no such person or they have no face yet. The number can be in any
 * shape — E.164, formatted, digits — because it is matched on [Phone.key], the same last-ten
 * digits BrightChat already normalises its handles to.
 *
 * This provider is the *fallback* route, not the main one. The main route is that a drawn face
 * becomes the person's contact photo, which BrightChat and the stock dialer read without knowing
 * this app exists. This exists for the cases that do not go through the address book at all: a
 * handle with no contact, and any app that wants the face at a size a 256px contact photo cannot
 * give it.
 *
 * **What it will not do:** write anything, list anybody, or serve a single fact, note, tag or
 * relationship. A caller can ask "what does the person on this number look like" and nothing
 * else. Callers are gated by package name rather than by a signature permission, because the
 * Bright* apps are not all signed with the same key — BrightChat's keystore is its own — so a
 * signature permission would lock out exactly the app this is for.
 */
class FaceProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    private fun allowed(): Boolean {
        val caller = callingPackage ?: return false
        return caller == context?.packageName || caller.startsWith("com.gios.")
    }

    /**
     * The PNG.
     *
     * Rendered into `cacheDir` and handed over as a read-only descriptor. A pipe would avoid the
     * file, and would also mean holding a thread open for the length of the read; a face is
     * about 6 KB and the cache is allowed to be cleared under us, so a file is the cheaper
     * mechanism by a wide margin.
     */
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        if (!allowed()) throw SecurityException("Rolodex faces are for the Bright* apps")
        if (mode != "r") throw SecurityException("read only")
        val ctx = context ?: return null

        val key = Phone.key(uri.lastPathSegment.orEmpty())
        if (key.isEmpty()) return null

        val person = RolodexStore(ctx.filesDir).read().firstOrNull { candidate ->
            candidate.phones.any { Phone.key(it) == key }
        } ?: return null

        val face = FaceJson.decode(person.faceJson)
        // isDrawn, not isBlank: an untouched head outline is not a face, and serving it would
        // put the same empty oval next to every thread in BrightChat.
        if (!face.isDrawn) return null

        val dir = File(ctx.cacheDir, "faces").apply { mkdirs() }
        val file = File(dir, key + ".png")
        file.writeBytes(FaceRaster.png(face, SERVED_PX))
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    /**
     * Enough of a cursor for a caller to know whether a face exists before opening it, and for
     * the ones that insist on `OpenableColumns` before reading a stream.
     */
    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? {
        if (!allowed()) throw SecurityException("Rolodex faces are for the Bright* apps")
        val ctx = context ?: return null
        val key = Phone.key(uri.lastPathSegment.orEmpty())
        if (key.isEmpty()) return null

        val person = RolodexStore(ctx.filesDir).read().firstOrNull { candidate ->
            candidate.phones.any { Phone.key(it) == key }
        } ?: return null
        if (!FaceJson.decode(person.faceJson).isDrawn) return null

        val columns = arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        return MatrixCursor(columns).apply {
            // The size is not known without rendering, and a caller that trusts it would be
            // misled by a guess. null means "unknown", which every well-behaved reader handles.
            addRow(arrayOf<Any?>(key + ".png", null))
        }
    }

    override fun getType(uri: Uri): String = "image/png"

    override fun insert(uri: Uri, values: ContentValues?): Uri? =
        throw UnsupportedOperationException("read only")

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = throw UnsupportedOperationException("read only")

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException("read only")

    companion object {
        /**
         * 512px rather than the 256 of a contact photo. This route exists partly for callers
         * that want the face bigger than the address book can give it, so serving the same size
         * would remove half the reason for it.
         */
        const val SERVED_PX = 512
    }
}
