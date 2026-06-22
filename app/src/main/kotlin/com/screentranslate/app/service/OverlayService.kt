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
import com.screentranslate.app.overlay.OverlayManager
import com.screentranslate.app.pipeline.PipelineState
import com.screentranslate.app.ui.MainActivity
import com.screentranslate.app.util.DisplayMetricsHelper
import com.screentranslate.app.util.appContainer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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

        // Wire up MediaProjection if the user granted screen recording.
        // Must call startForeground() with the MEDIA_PROJECTION type BEFORE getMediaProjection().
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
                savedX = savedX,
                savedY = savedY
            )

            // Observe preferences changes
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
                        overlayManager.showTranslations(state.results)
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
