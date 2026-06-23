package com.screentranslate.app.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.WindowManager
import com.screentranslate.app.R
import com.screentranslate.app.databinding.OverlayClipboardBubbleBinding
import com.screentranslate.app.util.DisplayMetricsHelper

class ClipboardBubbleView(
    private val context: Context,
    private val windowManager: WindowManager
) {
    private val themedContext = ContextThemeWrapper(context, R.style.Theme_ScreenTranslate)
    private val binding = OverlayClipboardBubbleBinding.inflate(LayoutInflater.from(themedContext))
    private val handler = Handler(Looper.getMainLooper())
    private var added = false

    private val dismissRunnable = Runnable { removeFromWindow() }

    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        y = DisplayMetricsHelper.getStatusBarHeight(context) + DisplayMetricsHelper.dpToPx(context, 8f)
    }

    init {
        binding.btnClose.setOnClickListener { removeFromWindow() }
    }

    fun show(original: String, translated: String) {
        removeFromWindow()
        binding.tvClipOriginal.text = if (original.length > 60) original.take(57) + "…" else original
        binding.tvClipTranslated.text = translated
        windowManager.addView(binding.root, params)
        added = true
        handler.postDelayed(dismissRunnable, AUTO_DISMISS_MS)
    }

    fun removeFromWindow() {
        handler.removeCallbacks(dismissRunnable)
        if (added) {
            runCatching { windowManager.removeView(binding.root) }
            added = false
        }
    }

    companion object {
        private const val AUTO_DISMISS_MS = 8_000L
    }
}
