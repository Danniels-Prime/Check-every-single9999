package com.screentranslate.app.pipeline

import android.graphics.Point
import android.util.Log
import com.screentranslate.app.capture.CaptureResult
import com.screentranslate.app.capture.ScreenCaptureManager
import com.screentranslate.app.ocr.OcrProcessor
import com.screentranslate.app.ocr.TextBlockMapper
import com.screentranslate.app.translation.TranslationRepository
import com.screentranslate.app.translation.TranslationResult
import com.screentranslate.app.util.CoroutineDispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

sealed class PipelineState {
    object Capturing : PipelineState()
    object Processing : PipelineState()
    data class Translating(val progress: Int, val total: Int) : PipelineState()
    data class Complete(val results: List<TranslationResult>) : PipelineState()
    data class Error(val message: String, val cause: Throwable? = null) : PipelineState()
    object NoTextFound : PipelineState()
}

class TranslationPipeline(
    private val captureManager: ScreenCaptureManager,
    private val ocrProcessor: OcrProcessor,
    private val translationRepository: TranslationRepository,
    private val dispatchers: CoroutineDispatchers
) {

    var targetLanguage: String = "en"
    var screenSize: Point = Point(1080, 1920)
    var statusBarHeight: Int = 0

    fun executeCapture(): Flow<PipelineState> = flow {
        emit(PipelineState.Capturing)

        // Capture screen
        val captureResult = captureManager.captureScreen()
        if (captureResult is CaptureResult.Error || captureResult is CaptureResult.NotInitialized) {
            val msg = when (captureResult) {
                is CaptureResult.Error -> captureResult.message
                else -> "Screen capture not initialized"
            }
            emit(PipelineState.Error(msg))
            return@flow
        }

        val bitmap = (captureResult as CaptureResult.Success).bitmap
        val bitmapWidth = bitmap.width
        val bitmapHeight = bitmap.height

        emit(PipelineState.Processing)

        // Run OCR
        val ocrResult = try {
            ocrProcessor.processImage(bitmap)
        } catch (e: Exception) {
            Log.e(TAG, "OCR failed", e)
            bitmap.recycle()
            emit(PipelineState.Error("Text recognition failed: ${e.message}", e))
            return@flow
        }

        bitmap.recycle()

        if (ocrResult.blocks.isEmpty()) {
            emit(PipelineState.NoTextFound)
            return@flow
        }

        // Scale bounding boxes to screen coordinates
        val scaledBlocks = TextBlockMapper.scaleToScreen(
            blocks = ocrResult.blocks,
            bitmapWidth = bitmapWidth,
            bitmapHeight = bitmapHeight,
            screenWidth = screenSize.x,
            screenHeight = screenSize.y,
            statusBarHeight = statusBarHeight
        )

        emit(PipelineState.Translating(0, scaledBlocks.size))

        // Translate all blocks
        val results = try {
            translationRepository.translateBlocks(scaledBlocks, targetLanguage)
        } catch (e: Exception) {
            Log.e(TAG, "Translation failed", e)
            emit(PipelineState.Error("Translation failed: ${e.message}", e))
            return@flow
        }

        emit(PipelineState.Complete(results))
    }.flowOn(dispatchers.io)

    companion object {
        private const val TAG = "TranslationPipeline"
    }
}
