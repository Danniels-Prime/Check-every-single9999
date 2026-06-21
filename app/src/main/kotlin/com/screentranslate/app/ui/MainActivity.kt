package com.screentranslate.app.ui

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.screentranslate.app.R
import com.screentranslate.app.databinding.ActivityMainBinding
import com.screentranslate.app.service.OverlayService
import com.screentranslate.app.service.TranslationAccessibilityService
import com.screentranslate.app.util.PermissionHelper

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var overlayService: OverlayService? = null
    private var serviceConnected = false

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

    private val accessibilitySettingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        refreshUI()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

        bindService(
            Intent(this, OverlayService::class.java),
            serviceConnection,
            0
        )
    }

    private fun setupClickListeners() {
        binding.btnGrantOverlay.setOnClickListener {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                android.net.Uri.parse("package:$packageName")
            )
            overlayPermissionLauncher.launch(intent)
        }

        binding.btnGrantScreen.setOnClickListener {
            accessibilitySettingsLauncher.launch(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        binding.btnStart.setOnClickListener {
            if (serviceConnected) stopTranslation() else startTranslation()
        }

        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun startTranslation() {
        val intent = Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_START
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
        val hasAccessibility = TranslationAccessibilityService.isEnabled(this)

        binding.tvOverlayStatus.text = getString(
            if (hasOverlay) R.string.permission_granted else R.string.permission_denied
        )
        binding.tvOverlayStatus.setTextColor(
            getColor(if (hasOverlay) R.color.permission_granted else R.color.permission_denied)
        )
        binding.btnGrantOverlay.visibility = if (hasOverlay) View.GONE else View.VISIBLE

        binding.tvNotificationStatus.text = getString(
            if (hasNotification) R.string.permission_granted else R.string.permission_denied
        )
        binding.tvNotificationStatus.setTextColor(
            getColor(if (hasNotification) R.color.permission_granted else R.color.permission_denied)
        )

        binding.tvScreenStatus.text = getString(
            if (hasAccessibility) R.string.permission_granted else R.string.permission_denied
        )
        binding.tvScreenStatus.setTextColor(
            getColor(if (hasAccessibility) R.color.permission_granted else R.color.permission_denied)
        )
        binding.btnGrantScreen.visibility = if (hasAccessibility) View.GONE else View.VISIBLE

        val allGranted = hasOverlay && hasNotification && hasAccessibility

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
