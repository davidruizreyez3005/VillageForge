package com.villageforge.core

import android.content.Context

/**
 * Persists the last crash report so it can be shown on the next launch.
 * Used by MainActivity's startup guard and default uncaught-exception handler:
 * instead of a silent "app closed" the exact stack trace survives and is
 * displayed (plain Views only, so it works even when Compose/Filament fail).
 */
object CrashReport {
    private const val FILE = "villageforge_crash.txt"

    fun save(context: Context, text: String) {
        context.openFileOutput(FILE, Context.MODE_PRIVATE).use { out ->
            out.write(text.toByteArray(Charsets.UTF_8))
        }
    }

    fun load(context: Context): String? =
        context.getFileStreamPath(FILE).takeIf { it.exists() }?.readText()

    fun clear(context: Context) {
        context.getFileStreamPath(FILE).delete()
    }
}
