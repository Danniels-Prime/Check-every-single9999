package com.screentranslate.app.ocr

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.system.measureTimeMillis

class OcrProcessor {

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    suspend fun processImage(bitmap: Bitmap): OcrResult {
        val blocks = mutableListOf<TextBlockData>()
        val fullText = StringBuilder()
        var processingTime = 0L

        processingTime = measureTimeMillis {
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            val visionText = suspendCancellableCoroutine { cont ->
                recognizer.process(inputImage)
                    .addOnSuccessListener { cont.resume(it) }
                    .addOnFailureListener { cont.resumeWithException(it) }
            }

            for (block in visionText.textBlocks) {
                val box = block.boundingBox ?: continue
                val blockText = block.text.trim()
                if (blockText.isEmpty()) continue

                blocks.add(TextBlockData(text = blockText, boundingBox = box))
                if (fullText.isNotEmpty()) fullText.append("\n")
                fullText.append(blockText)
            }
        }

        Log.d(TAG, "OCR found ${blocks.size} blocks in ${processingTime}ms")
        return OcrResult(blocks, fullText.toString(), processingTime)
    }

    fun close() {
        recognizer.close()
    }

    companion object {
        private const val TAG = "OcrProcessor"
    }
}
