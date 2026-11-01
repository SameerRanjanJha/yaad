package com.sameer.yaad.log

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.core.net.toUri
import com.sameer.yaad.YaadPrefs

/**
 * The running document of everything said, at Documents/Yaad/voice-log.md.
 * It lives outside the app so any file manager can open it and it survives an uninstall.
 */
class VoiceLog(private val context: Context, private val prefs: YaadPrefs) {

    fun append(entry: String) {
        val uri = documentUri() ?: createDocument()
        val stream = checkNotNull(context.contentResolver.openOutputStream(uri, "wa")) { "Could not open the log document" }
        stream.use { it.write(entry.toByteArray()) }
    }

    /** The document, if it has been created and is still there. */
    fun documentUri(): Uri? {
        val uri = prefs.logDocumentUri?.toUri() ?: return null
        val stillThere = runCatching {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { true } ?: false
        }.getOrDefault(false)
        return uri.takeIf { stillThere }
    }

    private fun createDocument(): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Files.FileColumns.DISPLAY_NAME, DOCUMENT_NAME)
            put(MediaStore.Files.FileColumns.MIME_TYPE, DOCUMENT_TYPE)
            put(MediaStore.Files.FileColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOCUMENTS}/Yaad")
        }
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = checkNotNull(context.contentResolver.insert(collection, values)) { "Could not create the log document" }
        prefs.logDocumentUri = uri.toString()
        return uri
    }

    companion object {
        const val DOCUMENT_NAME = "voice-log.md"
        const val DOCUMENT_TYPE = "text/markdown"
    }
}
