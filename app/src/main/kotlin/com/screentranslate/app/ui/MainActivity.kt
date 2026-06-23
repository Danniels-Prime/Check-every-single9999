package com.screentranslate.app.ui

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.screentranslate.app.R
import com.screentranslate.app.databinding.ActivityMainBinding
import com.screentranslate.app.service.OverlayService
import com.screentranslate.app.service.TranslationAccessibilityService
import com.screentranslate.app.util.PermissionHelper
import com.screentranslate.app.util.appContainer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var overlayService: OverlayService? = null
    private var serviceConnected = false
    private var notificationPermissionAsked = false

    // Screen recording consent — optional bonus on top of accessibility service
    private var mediaProjectionResultCode = -1
    private var mediaProjectionData: Intent? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            overlayService = (binder as OverlayService.LocalBinder).getService()
            serviceConnected = true
            refreshUI()
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            overlayService = null
            serviceConnected = false
            refreshUI()
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        refreshUI()
        if (!granted) {
            Toast.makeText(this, "Notification permission needed to keep service running", Toast.LENGTH_LONG).show()
        }
    }

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { refreshUI() }

    private val accessibilitySettingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { refreshUI() }

    // Screen recording — shows the system consent dialog once
    private val screenRecordLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            mediaProjectionResultCode = result.resultCode
            mediaProjectionData = result.data
            refreshUI()
        } else {
            Toast.makeText(this, "Screen recording permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startActivity(Intent(this, CameraTranslationActivity::class.java))
        } else {
            Toast.makeText(this, getString(R.string.camera_permission_denied), Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Apply saved app theme before inflating layout
        lifecycleScope.launch {
            val theme = application.appContainer.preferencesRepository.appTheme.first()
            val mode = when (theme) {
                "light" -> AppCompatDelegate.MODE_NIGHT_NO
                "dark"  -> AppCompatDelegate.MODE_NIGHT_YES
                else    -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
            AppCompatDelegate.setDefaultNightMode(mode)
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupClickListeners()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !PermissionHelper.hasNotificationPermission(this) &&
            !notificationPermissionAsked
        ) {
            notificationPermissionAsked = true
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        refreshUI()

        bindService(Intent(this, OverlayService::class.java), serviceConnection, 0)
    }

    private fun setupClickListeners() {
        binding.btnGrantOverlay.setOnClickListener {
            overlayPermissionLauncher.launch(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    android.net.Uri.parse("package:$packageName"))
            )
        }

        // Accessibility service — primary screen capture method
        binding.btnGrantScreen.setOnClickListener {
            accessibilitySettingsLauncher.launch(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        // Screen recording — optional second capture method
        binding.btnGrantScreenRecord.setOnClickListener {
            val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            screenRecordLauncher.launch(mgr.createScreenCaptureIntent())
        }

        binding.btnStart.setOnClickListener {
            if (serviceConnected) stopTranslation() else startTranslation()
        }

        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        binding.btnFlashcards.setOnClickListener {
            startActivity(Intent(this, FlashcardsActivity::class.java))
        }

        binding.btnHistory.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }

        binding.btnCameraTranslate.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                startActivity(Intent(this, CameraTranslationActivity::class.java))
            } else {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun startTranslation() {
        val intent = Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_START
            // Pass screen recording token if granted — service uses it to activate
            // MediaProjection as a second capture path alongside accessibility service
            if (mediaProjectionResultCode != -1 && mediaProjectionData != null) {
                putExtra(OverlayService.EXTRA_RESULT_CODE, mediaProjectionResultCode)
                putExtra(OverlayService.EXTRA_RESULT_DATA, mediaProjectionData)
            }
        }
        ContextCompat.startForegroundService(this, intent)
        bindService(
            Intent(this, OverlayService::class.java),
            serviceConnection,
            Context.BIND_AUTO_CREATE
        )
        moveTaskToBack(true)
    }

    private fun stopTranslation() {
        startService(Intent(this, OverlayService::class.java).apply { action = OverlayService.ACTION_STOP })
        runCatching { unbindService(serviceConnection) }
        serviceConnected = false
        refreshUI()
    }

    private fun refreshUI() {
        val hasOverlay       = PermissionHelper.hasOverlayPermission(this)
        val hasNotification  = PermissionHelper.hasNotificationPermission(this)
        val hasAccessibility = TranslationAccessibilityService.isEnabled(this)
        val hasScreenRecord  = mediaProjectionResultCode != -1 && mediaProjectionData != null

        // Overlay row
        binding.tvOverlayStatus.text = getString(if (hasOverlay) R.string.permission_granted else R.string.permission_denied)
        binding.tvOverlayStatus.setTextColor(getColor(if (hasOverlay) R.color.permission_granted else R.color.permission_denied))
        binding.btnGrantOverlay.visibility = if (hasOverlay) View.GONE else View.VISIBLE

        // Notification row
        binding.tvNotificationStatus.text = getString(if (hasNotification) R.string.permission_granted else R.string.permission_denied)
        binding.tvNotificationStatus.setTextColor(getColor(if (hasNotification) R.color.permission_granted else R.color.permission_denied))

        // Accessibility row (required)
        binding.tvScreenStatus.text = getString(if (hasAccessibility) R.string.permission_granted else R.string.permission_denied)
        binding.tvScreenStatus.setTextColor(getColor(if (hasAccessibility) R.color.permission_granted else R.color.permission_denied))
        binding.btnGrantScreen.visibility = if (hasAccessibility) View.GONE else View.VISIBLE

        // Screen recording row (optional)
        binding.tvScreenRecordStatus.text = getString(if (hasScreenRecord) R.string.permission_granted else R.string.permission_denied)
        binding.tvScreenRecordStatus.setTextColor(getColor(if (hasScreenRecord) R.color.permission_granted else R.color.permission_denied))
        binding.btnGrantScreenRecord.visibility = if (hasScreenRecord) View.GONE else View.VISIBLE

        val allGranted = hasOverlay && hasNotification && hasAccessibility

        if (serviceConnected) {
            binding.tvStatus.text = getString(R.string.status_running)
            binding.btnStart.text = getString(R.string.btn_stop_translating)
            binding.btnStart.isEnabled = true
        } else {
            binding.tvStatus.text = getString(if (allGranted) R.string.status_ready else R.string.status_not_ready)
            binding.btnStart.text = getString(R.string.btn_start_translating)
            binding.btnStart.isEnabled = allGranted
        }
    }

    override fun onResume() {
        super.onResume()
        refreshUI()
    }

    override fun onDestroy() {
        runCatching { unbindService(serviceConnection) }
        super.onDestroy()
    }
}
