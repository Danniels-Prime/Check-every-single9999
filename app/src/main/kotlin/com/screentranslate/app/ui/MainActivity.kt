package com.screentranslate.app.ui

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.screentranslate.app.R
import com.screentranslate.app.databinding.ActivityMainBinding
import com.screentranslate.app.service.OverlayService
import com.screentranslate.app.util.PermissionHelper
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var overlayService: OverlayService? = null
    private var serviceConnected = false
    private var mediaProjectionResultCode = -1
    private var mediaProjectionData: Intent? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            overlayService = (binder as OverlayService.LocalBinder).getService()
            serviceConnected = true
            updateStartStopButton()
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            overlayService = null
            serviceConnected = false
            updateStartStopButton()
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        updatePermissionStates()
        if (!granted) {
            Toast.makeText(this, "Notification permission needed for background service", Toast.LENGTH_LONG).show()
        }
    }

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        updatePermissionStates()
    }

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            mediaProjectionResultCode = result.resultCode
            mediaProjectionData = result.data
            updatePermissionStates()
        } else {
            Toast.makeText(this, "Screen capture permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupClickListeners()
        updatePermissionStates()

        // Bind to service if running
        val serviceIntent = Intent(this, OverlayService::class.java)
        bindService(serviceIntent, serviceConnection, 0)
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
            val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as android.media.projection.MediaProjectionManager
            screenCaptureLauncher.launch(mgr.createScreenCaptureIntent())
        }

        binding.btnStart.setOnClickListener {
            if (serviceConnected) {
                stopTranslation()
            } else {
                startTranslation()
            }
        }

        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun startTranslation() {
        val resultCode = mediaProjectionResultCode
        val data = mediaProjectionData

        if (resultCode == -1 || data == null) {
            Toast.makeText(this, "Please grant screen capture permission first", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_START
            putExtra(OverlayService.EXTRA_RESULT_CODE, resultCode)
            putExtra(OverlayService.EXTRA_RESULT_DATA, data)
        }
        ContextCompat.startForegroundService(this, intent)
        bindService(Intent(this, OverlayService::class.java), serviceConnection, Context.BIND_AUTO_CREATE)

        moveTaskToBack(true)
    }

    private fun stopTranslation() {
        val intent = Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_STOP
        }
        startService(intent)
        unbindService(serviceConnection)
        serviceConnected = false
        updateStartStopButton()
    }

    private fun updatePermissionStates() {
        val hasOverlay = PermissionHelper.hasOverlayPermission(this)
        val hasNotification = PermissionHelper.hasNotificationPermission(this)
        val hasScreen = mediaProjectionResultCode != -1 && mediaProjectionData != null

        // Overlay
        binding.tvOverlayStatus.text = if (hasOverlay) getString(R.string.permission_granted) else getString(R.string.permission_denied)
        binding.tvOverlayStatus.setTextColor(getColor(if (hasOverlay) R.color.permission_granted else R.color.permission_denied))
        binding.btnGrantOverlay.visibility = if (hasOverlay) View.GONE else View.VISIBLE

        // Notification
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotification) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        binding.tvNotificationStatus.text = if (hasNotification) getString(R.string.permission_granted) else getString(R.string.permission_denied)
        binding.tvNotificationStatus.setTextColor(getColor(if (hasNotification) R.color.permission_granted else R.color.permission_denied))

        // Screen capture
        binding.tvScreenStatus.text = if (hasScreen) getString(R.string.permission_granted) else getString(R.string.permission_denied)
        binding.tvScreenStatus.setTextColor(getColor(if (hasScreen) R.color.permission_granted else R.color.permission_denied))
        binding.btnGrantScreen.visibility = if (hasScreen) View.GONE else View.VISIBLE

        val allGranted = hasOverlay && hasNotification && hasScreen
        binding.tvStatus.text = if (allGranted) getString(R.string.status_ready) else getString(R.string.status_not_ready)
        binding.btnStart.isEnabled = allGranted

        updateStartStopButton()
    }

    private fun updateStartStopButton() {
        if (serviceConnected) {
            binding.btnStart.text = getString(R.string.btn_stop_translating)
            binding.tvStatus.text = getString(R.string.status_running)
        } else {
            val allGranted = PermissionHelper.hasOverlayPermission(this) &&
                    PermissionHelper.hasNotificationPermission(this) &&
                    mediaProjectionResultCode != -1

            binding.btnStart.text = getString(R.string.btn_start_translating)
            binding.btnStart.isEnabled = allGranted
        }
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStates()
    }

    override fun onDestroy() {
        if (serviceConnected) {
            runCatching { unbindService(serviceConnection) }
        }
        super.onDestroy()
    }
}
