package com.vinoth.guardapp

import android.content.Context
import java.io.File

object FileLog {
    fun write(context: Context, message: String) {
        val file = File(context.filesDir, "guard_log.txt")
        file.appendText("${System.currentTimeMillis()}: $message\n")
    }

    fun readAll(context: Context): String {
        val file = File(context.filesDir, "guard_log.txt")
        return if (file.exists()) file.readText() else "No log yet"
    }

    fun clear(context: Context) {
        val file = File(context.filesDir, "guard_log.txt")
        if (file.exists()) file.delete()
    }
}
