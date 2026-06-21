package com.screentranslate.app.capture

import android.graphics.Bitmap

sealed class CaptureResult {
    data class Success(val bitmap: Bitmap) : CaptureResult()
    data class Error(val message: String, val cause: Throwable? = null) : CaptureResult()
    object NotInitialized : CaptureResult()
}
