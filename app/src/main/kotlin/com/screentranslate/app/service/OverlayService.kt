package com.screentranslate.app.service

import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.screentranslate.app.AppContainer
import com.screentranslate.app.R
import com.screentranslate.app.ScreenTranslateApp
import com.screentranslate.app.ai.AiExplainerFactory
import com.screentranslate.app.ai.AiResult
import com.screentranslate.app.data.Flashcard
import com.screentranslate.app.data.HistoryEntry
import com.screentranslate.app.overlay.OverlayManager
import com.screentranslate.app.pipeline.PipelineState
import com.screentranslate.app.translation.TranslationResult
import com.screentranslate.app.ui.MainActivity
import com.screentranslate.app.util.DisplayMetricsHelper
import com.screentranslate.app.util.ShareCardHelper
import com.screentranslate.app.util.appContainer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

class OverlayService : LifecycleService() {

    inner class LocalBinder : Binder() {
        fun getService() = this@OverlayService
    }

    private val binder = LocalBinder()
    private lateinit var container: AppContainer
    private lateinit var overlayManager: OverlayManager
    private var autoCaptureJob: Job? = null
    private val isCapturing = AtomicBoolean(false)

    // Holds translation result from manual input so we can save it as a flashcard
    private var pendingFlashcard: TranslationResult? = null
    private var pendingAiResult: AiResult? = null

    override fun onCreate() {
        super.onCreate()
        container = application.appContainer
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        overlayManager = OverlayManager(this, wm)
        overlayManager.setOnFabPositionSaved { x, y ->
            lifecycleScope.launch {
                container.preferencesRepository.setFabPosition(x, y)
            }
        }
        overlayManager.setOnShareCallback { original, translated, examples ->
            lifecycleScope.launch {
                val targetLang = container.preferencesRepository.targetLanguage.first()
                ShareCardHelper.share(this@OverlayService, original, translated, "ru", targetLang, examples)
            }
        }
        startForegroundWithNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        when (intent?.action) {
            ACTION_START -> handleStart(intent)
            ACTION_STOP -> stopSelf()
            ACTION_CAPTURE -> triggerCapture()
        }

        return START_NOT_STICKY
    }

    private fun handleStart(intent: Intent) {
        val screenSize = DisplayMetricsHelper.getScreenSize(this)
        container.translationPipeline.screenSize = screenSize
        container.translationPipeline.statusBarHeight = DisplayMetricsHelper.getStatusBarHeight(this)

        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, -1)
        val resultData = intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
        if (resultCode != -1 && resultData != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    ScreenTranslateApp.NOTIFICATION_ID,
                    buildNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                )
            }
            container.screenCaptureManager.initialize(resultCode, resultData)
        }

        lifecycleScope.launch {
            val savedX = container.preferencesRepository.fabX.first()
            val savedY = container.preferencesRepository.fabY.first()
            val targetLang = container.preferencesRepository.targetLanguage.first()

            container.translationPipeline.targetLanguage = targetLang

            overlayManager.showFab(
                onTap = { triggerCapture() },
                onLongPress = { openManualInput() },
                savedX = savedX,
                savedY = savedY
            )

            launch {
                container.preferencesRepository.targetLanguage.collect { lang ->
                    container.translationPipeline.targetLanguage = lang
                }
            }
            launch {
                container.preferencesRepository.overlayOpacity.collect { opacity ->
                    overlayManager.setOpacity(opacity)
                }
            }
            launch {
                container.preferencesRepository.autoCapture.collect { enabled ->
                    if (enabled) startAutoCapture() else stopAutoCapture()
                }
            }
            launch {
                container.preferencesRepository.overlayTheme.collect { theme ->
                    overlayManager.applyTheme(theme)
                }
            }
        }
    }

    fun triggerCapture() {
        if (!isCapturing.compareAndSet(false, true)) return

        lifecycleScope.launch {
            overlayManager.clearBubbles()
            overlayManager.setFabProcessing(true)

            container.translationPipeline.executeCapture().collect { state ->
                when (state) {
                    is PipelineState.Complete -> {
                        overlayManager.setFabProcessing(false)
                        overlayManager.showTranslations(
                            results = state.results,
                            onSpeak = { text, lang -> container.ttsManager.speak(text, lang) },
                            onExpand = { result -> handleBubbleExpand(result) }
                        )
                        state.results.forEach { result ->
                            container.historyRepository.save(
                                HistoryEntry(
                                    originalText = result.originalText,
                                    translatedText = result.translatedText,
                                    sourceLang = result.sourceLang,
                                    targetLang = result.targetLang
                                )
                            )
                        }
                        isCapturing.set(false)
                    }
                    is PipelineState.Error -> {
                        overlayManager.setFabProcessing(false)
                        Log.e(TAG, "Pipeline error: ${state.message}", state.cause)
                        Toast.makeText(this@OverlayService, state.message, Toast.LENGTH_LONG).show()
                        isCapturing.set(false)
                    }
                    is PipelineState.NoTextFound -> {
                        overlayManager.setFabProcessing(false)
                        Toast.makeText(this@OverlayService, getString(R.string.no_text_found), Toast.LENGTH_SHORT).show()
                        isCapturing.set(false)
                    }
                    else -> { /* Capturing, Processing, Translating */ }
                }
            }
        }
    }

    private fun handleBubbleExpand(result: TranslationResult) {
        lifecycleScope.launch {
            overlayManager.showAiDetailLoading(result, onClose = { overlayManager.hideAiDetail() })
            val aiResult = fetchAiExamples(result)
            overlayManager.updateAiDetailResult(aiResult)
        }
    }

    private fun openManualInput() {
        pendingFlashcard = null
        pendingAiResult = null
        overlayManager.showManualInput(
            onTranslate = { text -> handleManualTranslate(text) },
            onSaveFlashcard = { saveFlashcard() },
            onClose = { overlayManager.hideManualInput() }
        )
    }

    private fun handleManualTranslate(text: String) {
        lifecycleScope.launch {
            try {
                val targetLang = container.preferencesRepository.targetLanguage.first()
                val result = container.translationRepository.translateText(text, targetLang)
                pendingFlashcard = result
                overlayManager.showManualInputResult(result.translatedText)

                val aiResult = fetchAiExamples(result)
                pendingAiResult = aiResult
                overlayManager.showManualInputAiResult(aiResult)
            } catch (e: Exception) {
                Log.e(TAG, "Manual translate failed", e)
                overlayManager.hideManualInputLoading()
                Toast.makeText(this@OverlayService, "Translation failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun saveFlashcard() {
        val result = pendingFlashcard ?: return
        val ai = pendingAiResult ?: AiResult("", emptyList())
        lifecycleScope.launch {
            container.flashcardRepository.save(
                Flashcard(
                    id = UUID.randomUUID().toString(),
                    originalText = result.originalText,
                    translatedText = result.translatedText,
                    sourceLang = result.sourceLang,
                    targetLang = result.targetLang,
                    definition = ai.definition,
                    examples = ai.examples
                )
            )
            Toast.makeText(this@OverlayService, getString(R.string.flashcard_saved), Toast.LENGTH_SHORT).show()
        }
    }

    private suspend fun fetchAiExamples(result: TranslationResult): AiResult {
        val provider = container.preferencesRepository.aiProvider.first()
        val key = container.preferencesRepository.aiApiKey.first()
        val explainer = AiExplainerFactory.create(provider, key) ?: return AiResult("", emptyList())
        return try {
            explainer.explain(result.originalText, result.translatedText, result.targetLang)
        } catch (e: Exception) {
            Log.e(TAG, "AI explain failed", e)
            AiResult("", emptyList())
        }
    }

    private fun startAutoCapture() {
        autoCaptureJob?.cancel()
        autoCaptureJob = lifecycleScope.launch {
            while (true) {
                val interval = container.preferencesRepository.autoCaptureInterval.first()
                delay(interval)
                triggerCapture()
            }
        }
    }

    private fun stopAutoCapture() {
        autoCaptureJob?.cancel()
        autoCaptureJob = null
    }

    private fun buildNotification() = NotificationCompat.Builder(this, ScreenTranslateApp.NOTIFICATION_CHANNEL_ID)
        .setContentTitle(getString(R.string.notification_title))
        .setContentText(getString(R.string.notification_text))
        .setSmallIcon(R.drawable.ic_translate)
        .setContentIntent(
            PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        )
        .addAction(
            R.drawable.ic_close, getString(R.string.notification_action_stop),
            PendingIntent.getService(
                this, 0,
                Intent(this, OverlayService::class.java).apply { action = ACTION_STOP },
                PendingIntent.FLAG_IMMUTABLE
            )
        )
        .setOngoing(true)
        .setShowWhen(false)
        .build()

    private fun startForegroundWithNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                ScreenTranslateApp.NOTIFICATION_ID,
                buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(ScreenTranslateApp.NOTIFICATION_ID, buildNotification())
        }
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }

    override fun onDestroy() {
        stopAutoCapture()
        overlayManager.destroy()
        container.screenCaptureManager.release()
        container.mlKitTranslator.release()
        container.ttsManager.release()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.screentranslate.app.ACTION_START"
        const val ACTION_STOP = "com.screentranslate.app.ACTION_STOP"
        const val ACTION_CAPTURE = "com.screentranslate.app.ACTION_CAPTURE"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        private const val TAG = "OverlayService"
    }
}
