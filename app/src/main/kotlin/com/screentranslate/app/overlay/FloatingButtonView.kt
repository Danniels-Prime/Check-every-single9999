package com.screentranslate.app.overlay

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.PixelFormat
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.WindowManager
import com.screentranslate.app.R
import com.screentranslate.app.databinding.OverlayFabBinding
import com.screentranslate.app.util.DisplayMetricsHelper
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class FloatingButtonView(
    private val context: Context,
    private val windowManager: WindowManager,
    private val onTap: () -> Unit,
    private val onLongPress: () -> Unit = {},
    private val onSavedPosition: (x: Int, y: Int) -> Unit
) {

    private val themedContext = ContextThemeWrapper(context, R.style.Theme_ScreenTranslate)
    private val binding = OverlayFabBinding.inflate(LayoutInflater.from(themedContext))
    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT
    ).also {
        it.gravity = Gravity.TOP or Gravity.START
        it.x = 0
        it.y = 200
    }

    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var initialParamX = 0
    private var initialParamY = 0
    private val touchSlop = DisplayMetricsHelper.dpToPx(context, 8f)

    init {
        binding.root.setOnLongClickListener { onLongPress(); true }
        binding.root.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    initialParamX = params.x
                    initialParamY = params.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialParamX + (event.rawX - initialTouchX).toInt()
                    params.y = initialParamY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(binding.root, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val dx = abs(event.rawX - initialTouchX)
                    val dy = abs(event.rawY - initialTouchY)
                    if (dx < touchSlop && dy < touchSlop) {
                        onTap()
                    } else {
                        snapToEdge()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun snapToEdge() {
        val screenSize = DisplayMetricsHelper.getScreenSize(context)
        val screenMidX = screenSize.x / 2
        params.x = if (params.x + binding.root.width / 2 < screenMidX) {
            0
        } else {
            screenSize.x - binding.root.width
        }
        params.y = max(0, min(params.y, screenSize.y - binding.root.height))
        windowManager.updateViewLayout(binding.root, params)
        onSavedPosition(params.x, params.y)
    }

    fun setShowClear(showClear: Boolean) {
        val icon = if (showClear) R.drawable.ic_close else R.drawable.ic_translate
        binding.fab.setImageResource(icon)
    }

    fun setProcessing(isProcessing: Boolean) {
        val color = if (isProcessing) 0xFF9E9E9E.toInt() else 0xFF1565C0.toInt()
        binding.fab.backgroundTintList = ColorStateList.valueOf(color)
        binding.fab.isEnabled = !isProcessing
    }

    fun setPosition(x: Int, y: Int) {
        if (x >= 0 && y >= 0) {
            params.x = x
            params.y = y
        }
    }

    fun addToWindow() = windowManager.addView(binding.root, params)

    fun removeFromWindow() {
        runCatching { windowManager.removeView(binding.root) }
    }
}
