package com.screentranslate.app.capture

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.util.Log
import com.screentranslate.app.service.TranslationAccessibilityService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

class ScreenCaptureManager(private val context: Context) {

    val isInitialized: Boolean
        get() = TranslationAccessibilityService.instance != null

    @Suppress("UNUSED_PARAMETER")
    fun initialize(resultCode: Int, data: Intent) {
        // Screenshots are now taken via AccessibilityService.takeScreenshot() — no setup needed.
    }

    suspend fun captureScreen(): CaptureResult {
        val service = TranslationAccessibilityService.instance
            ?: return CaptureResult.NotInitialized

        val bitmap: Bitmap? = withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                service.requestScreenshot { bmp -> cont.resume(bmp) }
            }
        }

        if (bitmap == null) {
            Log.e(TAG, "Accessibility screenshot returned null")
            return CaptureResult.Error("Screenshot failed — is the Accessibility Service enabled?")
        }

        val scaled = withContext(Dispatchers.IO) {
            ImageConverter.scaleBitmapIfNeeded(bitmap)
        }
        return CaptureResult.Success(scaled)
    }

    fun release() {
        // Accessibility service manages its own lifecycle — nothing to release here.
    }

    companion object {
        private const val TAG = "ScreenCapture"
    }
}
