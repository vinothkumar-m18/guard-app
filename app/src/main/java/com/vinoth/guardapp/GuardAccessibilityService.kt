package com.vinoth.guardapp

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class GuardAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        FileLog.write(this, "AccessibilityService connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = event.packageName?.toString() ?: "unknown"
        val className = event.className?.toString() ?: "unknown"

        FileLog.write(
            this,
            "SCREEN: package=$packageName class=$className"
        )

        val eventText = event.text
            .joinToString(" | ")
            .trim()

        if (eventText.isNotEmpty()) {
            FileLog.write(this, "EVENT_TEXT: $eventText")
        }

        val root = rootInActiveWindow
        if (root != null) {
            logNodeTree(root, 0)
            root.recycle()
        }
    }

    private fun logNodeTree(node: AccessibilityNodeInfo, depth: Int) {
        if (depth > 8) return

        val text = node.text?.toString()?.trim()
        val description = node.contentDescription?.toString()?.trim()
        val resourceId = node.viewIdResourceName

        if (
            !text.isNullOrEmpty() ||
            !description.isNullOrEmpty() ||
            !resourceId.isNullOrEmpty()
        ) {
            val indent = "  ".repeat(depth)

            FileLog.write(
                this,
                "NODE: $indent" +
                    "text=${text ?: ""} " +
                    "desc=${description ?: ""} " +
                    "id=${resourceId ?: ""}"
            )
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)

            if (child != null) {
                logNodeTree(child, depth + 1)
                child.recycle()
            }
        }
    }

    override fun onInterrupt() {
        // Required override, intentionally empty.
    }
}
