package com.lifemax.rpg.app.shell.audio

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

/** A document the owner picked: its address, display name, size (-1 when unknown) and type. */
data class PickedAudio(val uri: Uri, val name: String, val size: Long, val mime: String?)

/**
 * Android's document picker for an audiobook, started from a native tap. A launcher can only be
 * registered while the activity is being created, and a native tap has no plugin call to start an
 * activity for result with, so the owner of the plugin makes this in its `load()` and keeps it.
 * Nothing is persisted: the grant the system gives for the picked document lasts until the app's
 * process ends, and the page reads the file at once. [onResult] gets null when the owner backed
 * out. Main thread.
 */
class AudioFilePicker(
    private val activity: AppCompatActivity,
    private val onResult: (PickedAudio?) -> Unit,
) {
    private val launcher: ActivityResultLauncher<Array<String>> =
        activity.registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            onResult(uri?.let(::describe))
        }

    fun pick() = launcher.launch(AUDIO_PICKER_TYPES)

    private fun describe(uri: Uri): PickedAudio {
        var name = uri.lastPathSegment.orEmpty()
        var size = -1L
        runCatching {
            activity.contentResolver
                .query(
                    uri,
                    arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                    null,
                    null,
                    null,
                )
                ?.use { c ->
                    if (c.moveToFirst()) {
                        c.getString(0)?.let { name = it }
                        if (!c.isNull(1)) size = c.getLong(1)
                    }
                }
        }
        return PickedAudio(uri, name, size, activity.contentResolver.getType(uri))
    }
}
