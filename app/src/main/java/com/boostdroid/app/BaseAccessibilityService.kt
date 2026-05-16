package com.boostdroid.app

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

abstract class BaseAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString() ?: return
            if (packageName == "com.android.settings") {
                handleSettingsWindow(rootInActiveWindow ?: return)
            }
        }
    }

    private fun handleSettingsWindow(root: AccessibilityNodeInfo) {
        // Handle "Force Stop" / "Durmaya Zorla"
        val forceStopButtons = findNodeByText(root, listOf("Force Stop", "Durmaya Zorla"))
        if (forceStopButtons.isNotEmpty()) {
            val btn = forceStopButtons[0]
            if (btn.isEnabled && btn.isClickable) {
                btn.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
        }

        // Handle "Clear Cache" / "Önbelleği Temizle"
        val clearCacheButtons = findNodeByText(root, listOf("Clear Cache", "Önbelleği Temizle"))
        if (clearCacheButtons.isNotEmpty()) {
            val btn = clearCacheButtons[0]
            if (btn.isEnabled && btn.isClickable) {
                btn.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
        }

        // Handle Confirmation Dialogs (OK / Tamam)
        val okButtons = findNodeByText(root, listOf("OK", "Tamam")) +
                root.findAccessibilityNodeInfosByViewId("android:id/button1")
        
        if (okButtons.isNotEmpty()) {
            val btn = okButtons[0]
            if (btn.isEnabled && btn.isClickable) {
                btn.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                // Go back after clicking OK
                performGlobalAction(GLOBAL_ACTION_BACK)
            }
        }
    }

    private fun findNodeByText(root: AccessibilityNodeInfo, texts: List<String>): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        for (text in texts) {
            result.addAll(root.findAccessibilityNodeInfosByText(text))
        }
        return result
    }

    override fun onInterrupt() {}
}
