package com.screentranslate.app.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager

class TranslationAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        instance = this
        Log.d(TAG, "Service connected")
    }

    fun requestScreenshot(callback: (Bitmap?) -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            takeScreenshot(
                android.view.Display.DEFAULT_DISPLAY,
                mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(result: ScreenshotResult) {
                        val hw = result.hardwareBuffer
                        val bmp = Bitmap.wrapHardwareBuffer(hw, result.colorSpace)
                            ?.copy(Bitmap.Config.ARGB_8888, false)
                        hw.close()
                        callback(bmp)
                    }
                    override fun onFailure(errorCode: Int) {
                        Log.e(TAG, "Screenshot failed: $errorCode")
                        callback(null)
                    }
                }
            )
        } else {
            callback(null)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    companion object {
        private const val TAG = "TranslationA11y"
        var instance: TranslationAccessibilityService? = null

        fun isEnabled(context: Context): Boolean {
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
            return am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                .any { it.id.contains(context.packageName) }
        }
    }
}
