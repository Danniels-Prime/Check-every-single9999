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
import android.util.Log
import com.screentranslate.app.util.DisplayMetricsHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class ScreenCaptureManager(private val context: Context) {

    private var mediaProjection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var isSetUp = false

    fun initialize(resultCode: Int, data: Intent) {
        val mgr = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        release()
        mediaProjection = mgr.getMediaProjection(resultCode, data)
        setupVirtualDisplay()
    }

    private fun setupVirtualDisplay() {
        val mp = mediaProjection ?: return
        val screenSize = DisplayMetricsHelper.getScreenSize(context)
        val density = context.resources.displayMetrics.densityDpi

        imageReader = ImageReader.newInstance(
            screenSize.x, screenSize.y,
            PixelFormat.RGBA_8888, 2
        )

        virtualDisplay = mp.createVirtualDisplay(
            "ScreenTranslateCapture",
            screenSize.x, screenSize.y, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader!!.surface, null, null
        )
        isSetUp = true
    }

    suspend fun captureScreen(): CaptureResult = withContext(Dispatchers.IO) {
        if (!isSetUp || imageReader == null) {
            return@withContext CaptureResult.NotInitialized
        }

        // Small delay to let the virtual display render current screen content
        delay(100)

        return@withContext try {
            val image = imageReader!!.acquireLatestImage()
                ?: return@withContext CaptureResult.Error("No image available yet")

            val bitmap = try {
                ImageConverter.imageToBitmap(image)
            } finally {
                image.close()
            }

            val scaled = ImageConverter.scaleBitmapIfNeeded(bitmap)
            CaptureResult.Success(scaled)
        } catch (e: Exception) {
            Log.e(TAG, "Screen capture failed", e)
            CaptureResult.Error("Capture failed: ${e.message}", e)
        }
    }

    val isInitialized: Boolean get() = isSetUp && mediaProjection != null

    fun release() {
        isSetUp = false
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
