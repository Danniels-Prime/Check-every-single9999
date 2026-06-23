package com.screentranslate.app.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Rect
import android.graphics.RectF
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.screentranslate.app.databinding.ActivityCameraTranslationBinding
import com.screentranslate.app.util.appContainer
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class CameraTranslationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCameraTranslationBinding
    private lateinit var cameraExecutor: ExecutorService
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val isProcessing = AtomicBoolean(false)
    private var lastFrameMs = 0L
    private var analyzeJob: Job? = null

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startCamera()
        } else {
            Toast.makeText(this, getString(com.screentranslate.app.R.string.camera_permission_denied), Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCameraTranslationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.hide()
        binding.btnBack.setOnClickListener { finish() }

        cameraExecutor = Executors.newSingleThreadExecutor()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { analysis ->
                    analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                        val now = System.currentTimeMillis()
                        if (now - lastFrameMs >= 1000L && isProcessing.compareAndSet(false, true)) {
                            lastFrameMs = now
                            analyzeFrame(imageProxy)
                        } else {
                            imageProxy.close()
                        }
                    }
                }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageAnalysis
                )
            } catch (e: Exception) {
                Log.e(TAG, "Camera bind failed", e)
                Toast.makeText(this, "Camera failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun analyzeFrame(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image ?: run {
            imageProxy.close()
            isProcessing.set(false)
            return
        }

        val rotation = imageProxy.imageInfo.rotationDegrees
        val imageWidth = imageProxy.width.toFloat()
        val imageHeight = imageProxy.height.toFloat()
        val inputImage = InputImage.fromMediaImage(mediaImage, rotation)

        recognizer.process(inputImage)
            .addOnSuccessListener { visionText ->
                imageProxy.close()
                val blocks = visionText.textBlocks
                if (blocks.isEmpty()) {
                    isProcessing.set(false)
                    runOnUiThread { binding.overlayView.setResults(emptyList()) }
                    return@addOnSuccessListener
                }

                analyzeJob?.cancel()
                analyzeJob = lifecycleScope.launch {
                    try {
                        val container = application.appContainer
                        val targetLang = container.preferencesRepository.targetLanguage.first()

                        val pvWidth = binding.previewView.width.toFloat().coerceAtLeast(1f)
                        val pvHeight = binding.previewView.height.toFloat().coerceAtLeast(1f)
                        val (effW, effH) = if (rotation == 90 || rotation == 270) {
                            imageHeight to imageWidth
                        } else {
                            imageWidth to imageHeight
                        }
                        val scaleX = pvWidth / effW
                        val scaleY = pvHeight / effH

                        val results = mutableListOf<Pair<RectF, String>>()
                        for (block in blocks) {
                            val box = block.boundingBox ?: continue
                            val text = block.text.trim()
                            if (text.isBlank()) continue

                            val translationResult = container.translationRepository.translateText(text, targetLang)
                            if (translationResult.translatedText != text) {
                                results.add(transformRect(box, rotation, imageWidth, imageHeight, scaleX, scaleY) to translationResult.translatedText)
                            }
                        }

                        binding.overlayView.setResults(results)
                    } catch (e: Exception) {
                        Log.e(TAG, "Frame translation failed", e)
                    } finally {
                        isProcessing.set(false)
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Text recognition failed", e)
                imageProxy.close()
                isProcessing.set(false)
            }
    }

    private fun transformRect(
        rect: Rect,
        rotation: Int,
        imageWidth: Float,
        imageHeight: Float,
        scaleX: Float,
        scaleY: Float
    ): RectF {
        val l = rect.left.toFloat()
        val t = rect.top.toFloat()
        val r = rect.right.toFloat()
        val b = rect.bottom.toFloat()
        return when (rotation) {
            90 -> RectF(t * scaleX, (imageWidth - r) * scaleY, b * scaleX, (imageWidth - l) * scaleY)
            180 -> RectF((imageWidth - r) * scaleX, (imageHeight - b) * scaleY, (imageWidth - l) * scaleX, (imageHeight - t) * scaleY)
            270 -> RectF((imageHeight - b) * scaleX, l * scaleY, (imageHeight - t) * scaleX, r * scaleY)
            else -> RectF(l * scaleX, t * scaleY, r * scaleX, b * scaleY)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        analyzeJob?.cancel()
        cameraExecutor.shutdown()
        recognizer.close()
    }

    companion object {
        private const val TAG = "CameraTranslationActivity"
    }
}
