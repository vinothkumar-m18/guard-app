package com.vinoth.guardapp

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader

object BlocklistLoader {

    private var domains: HashSet<String>? = null

    fun load(context: Context): HashSet<String> {
        domains?.let { return it }

        val set = HashSet<String>(80000)
        try {
            val inputStream = context.assets.open("porn_blocklist.txt")
            val reader = BufferedReader(InputStreamReader(inputStream))

            reader.forEachLine { line ->
                val trimmed = line.trim()
                if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEachLine

                // Expected format: "0.0.0.0 domain.com"
                val parts = trimmed.split(Regex("\\s+"))
                if (parts.size >= 2) {
                    val domain = parts[1].lowercase()
                    if (domain.isNotEmpty() && domain != "0.0.0.0") {
                        set.add(domain)
                    }
                }
            }
            reader.close()
            FileLog.write(context, "Blocklist loaded: ${set.size} domains")
        } catch (e: Exception) {
            FileLog.write(context, "Blocklist load failed: ${e.message}")
        }

        domains = set
        return set
    }
}
