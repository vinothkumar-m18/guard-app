package com.vinoth.guardapp

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

class GuardAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        FileLog.write(this, "AccessibilityService connected (scoped to Settings/PackageInstaller)")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        
        val packageName = event.packageName?.toString() ?: return
        val className = event.className?.toString() ?: ""

        // Strictly scope to Settings and Package Installer only
        val isSettings = packageName.contains("settings", ignoreCase = true)
        val isPackageInstaller = packageName.contains("packageinstaller", ignoreCase = true)

        if (!isSettings && !isPackageInstaller) {
            return // Skip all other apps entirely with zero overhead
        }

        val eventText = event.text.joinToString(" | ").trim()
        FileLog.write(this, "SCOPED_EVENT: pkg=$packageName class=$className text=\"$eventText\"")

        // Log window changes without recursive full-node tree traversal
        val root = rootInActiveWindow ?: return
        try {
            val title = root.text?.toString()?.trim() ?: ""
            if (title.isNotEmpty()) {
                FileLog.write(this, "SCREEN_TITLE: $title")
            }
        } finally {
            root.recycle()
        }
    }

    override fun onInterrupt() {}
}