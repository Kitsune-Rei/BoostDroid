package com.boostdroid.app

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Premium feature: Uses Accessibility to force stop apps when aggressive boost is enabled.
 * This is the ONLY way to truly "kill" background apps on modern Android.
 * Note: Usage is restricted to system optimization and requires explicit user consent.
 */
class DeepBoostService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString() ?: return
            
            // If we are in Settings and looking at an app we want to kill
            if (packageName == "com.android.settings") {
                val root = rootInActiveWindow ?: return
                
                // Find "Force Stop" or "Durmaya Zorla" (Turkish) button
                val stopButtons = root.findAccessibilityNodeInfosByViewId("com.android.settings:id/force_stop_button")
                if (stopButtons.isNotEmpty()) {
                    val btn = stopButtons[0]
                    if (btn.isEnabled) {
                        btn.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    }
                }
                
                // Find confirmation dialog "OK" or "Tamam"
                val okButtons = root.findAccessibilityNodeInfosByText("OK") + 
                                root.findAccessibilityNodeInfosByText("Tamam") +
                                root.findAccessibilityNodeInfosByViewId("android:id/button1")
                
                if (okButtons.isNotEmpty()) {
                    okButtons[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    // Once clicked, go back to our app
                    performGlobalAction(GLOBAL_ACTION_BACK)
                }
            }
        }
    }

    override fun onInterrupt() {}

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }
}