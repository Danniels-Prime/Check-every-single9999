package com.screentranslate.app.ui

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.screentranslate.app.R
import com.screentranslate.app.databinding.ActivityMainBinding
import com.screentranslate.app.service.OverlayService
import com.screentranslate.app.util.PermissionHelper

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var overlayService: OverlayService? = null
    private var serviceConnected = false
    private var mediaProjectionResultCode = -1
    private var mediaProjectionData: Intent? = null

    // Flag: only auto-request notification permission once per session
    private var notificationPermissionAsked = false

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
        // Just refresh UI — do NOT call updatePermissionStates() recursively
        refreshUI()
        if (!granted) {
            Toast.makeText(this, "Notification permission needed to keep service running", Toast.LENGTH_LONG).show()
        }
    }

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        refreshUI()
    }

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            mediaProjectionResultCode = result.resultCode
            mediaProjectionData = result.data
            refreshUI()
        } else {
            Toast.makeText(this, "Screen capture permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupClickListeners()

        // Request notification permission exactly once, here in onCreate
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !PermissionHelper.hasNotificationPermission(this) &&
            !notificationPermissionAsked
        ) {
            notificationPermissionAsked = true
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        refreshUI()

        // Bind to service if already running
        bindService(
            Intent(this, OverlayService::class.java),
            serviceConnection,
            0
        )
    }

    private fun setupClickListeners() {
        binding.btnGrantOverlay.setOnClickListener {
            val intent = Intent(
                android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                android.net.Uri.parse("package:$packageName")
            )
            overlayPermissionLauncher.launch(intent)
        }

        binding.btnGrantScreen.setOnClickListener {
            val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            screenCaptureLauncher.launch(mgr.createScreenCaptureIntent())
        }

        binding.btnStart.setOnClickListener {
            if (serviceConnected) stopTranslation() else startTranslation()
        }

        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun startTranslation() {
        val resultCode = mediaProjectionResultCode
        val data = mediaProjectionData

        if (resultCode == -1 || data == null) {
            Toast.makeText(this, "Grant screen capture permission first", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_START
            putExtra(OverlayService.EXTRA_RESULT_CODE, resultCode)
            putExtra(OverlayService.EXTRA_RESULT_DATA, data)
        }
        ContextCompat.startForegroundService(this, intent)
        bindService(
            Intent(this, OverlayService::class.java),
            serviceConnection,
            Context.BIND_AUTO_CREATE
        )

        // Minimize to let the overlay be visible
        moveTaskToBack(true)
    }

    private fun stopTranslation() {
        startService(Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_STOP
        })
        runCatching { unbindService(serviceConnection) }
        serviceConnected = false
        refreshUI()
    }

    private fun refreshUI() {
        val hasOverlay      = PermissionHelper.hasOverlayPermission(this)
        val hasNotification = PermissionHelper.hasNotificationPermission(this)
        val hasScreen       = mediaProjectionResultCode != -1 && mediaProjectionData != null

        // Overlay row
        binding.tvOverlayStatus.text = getString(
            if (hasOverlay) R.string.permission_granted else R.string.permission_denied
        )
        binding.tvOverlayStatus.setTextColor(
            getColor(if (hasOverlay) R.color.permission_granted else R.color.permission_denied)
        )
        binding.btnGrantOverlay.visibility = if (hasOverlay) View.GONE else View.VISIBLE

        // Notification row
        binding.tvNotificationStatus.text = getString(
            if (hasNotification) R.string.permission_granted else R.string.permission_denied
        )
        binding.tvNotificationStatus.setTextColor(
            getColor(if (hasNotification) R.color.permission_granted else R.color.permission_denied)
        )

        // Screen capture row
        binding.tvScreenStatus.text = getString(
            if (hasScreen) R.string.permission_granted else R.string.permission_denied
        )
        binding.tvScreenStatus.setTextColor(
            getColor(if (hasScreen) R.color.permission_granted else R.color.permission_denied)
        )
        binding.btnGrantScreen.visibility = if (hasScreen) View.GONE else View.VISIBLE

        val allGranted = hasOverlay && hasNotification && hasScreen

        if (serviceConnected) {
            binding.tvStatus.text = getString(R.string.status_running)
            binding.btnStart.text = getString(R.string.btn_stop_translating)
            binding.btnStart.isEnabled = true
        } else {
            binding.tvStatus.text = getString(
                if (allGranted) R.string.status_ready else R.string.status_not_ready
            )
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
