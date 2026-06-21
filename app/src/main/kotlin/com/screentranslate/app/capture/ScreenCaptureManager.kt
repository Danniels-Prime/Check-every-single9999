package com.screentranslate.app.capture

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.util.Log
import com.screentranslate.app.service.TranslationAccessibilityService
import com.screentranslate.app.util.DisplayMetricsHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

class ScreenCaptureManager(private val context: Context) {

    private var mediaProjection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var isMediaProjectionSetUp = false

    val isInitialized: Boolean
        get() = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                TranslationAccessibilityService.instance != null) || isMediaProjectionSetUp

    fun initialize(resultCode: Int, data: Intent) {
        val mgr = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        releaseMediaProjection()
        mediaProjection = mgr.getMediaProjection(resultCode, data)
        setupVirtualDisplay()
    }

    private fun setupVirtualDisplay() {
        val mp = mediaProjection ?: return
        val screenSize = DisplayMetricsHelper.getScreenSize(context)
        val density = context.resources.displayMetrics.densityDpi

        imageReader = ImageReader.newInstance(screenSize.x, screenSize.y, PixelFormat.RGBA_8888, 2)
        virtualDisplay = mp.createVirtualDisplay(
            "ScreenTranslateCapture",
            screenSize.x, screenSize.y, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader!!.surface, null, null
        )
        isMediaProjectionSetUp = true
    }

    suspend fun captureScreen(): CaptureResult {
        // Primary: Accessibility service — silent, no dialog, works on API 30+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val a11y = TranslationAccessibilityService.instance
            if (a11y != null) {
                val bitmap: Bitmap? = withContext(Dispatchers.Main) {
                    suspendCancellableCoroutine { cont ->
                        a11y.requestScreenshot { bmp -> cont.resume(bmp) }
                    }
                }
                if (bitmap != null) {
                    val scaled = withContext(Dispatchers.IO) { ImageConverter.scaleBitmapIfNeeded(bitmap) }
                    return CaptureResult.Success(scaled)
                }
            }
        }

        // Fallback: MediaProjection — works on all API levels, requires one-time consent dialog
        if (!isMediaProjectionSetUp || imageReader == null) {
            return CaptureResult.NotInitialized
        }

        delay(100)
        return try {
            val image = imageReader!!.acquireLatestImage()
                ?: return CaptureResult.Error("No image available yet")
            val bitmap = try {
                ImageConverter.imageToBitmap(image)
            } finally {
                image.close()
            }
            CaptureResult.Success(ImageConverter.scaleBitmapIfNeeded(bitmap))
        } catch (e: Exception) {
            Log.e(TAG, "MediaProjection capture failed", e)
            CaptureResult.Error("Capture failed: ${e.message}", e)
        }
    }

    fun release() {
        releaseMediaProjection()
    }

    private fun releaseMediaProjection() {
        isMediaProjectionSetUp = false
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        mediaProjection?.stop()
        mediaProjection = null
    }

    companion object {
        private const val TAG = "ScreenCapture"
    }
}
