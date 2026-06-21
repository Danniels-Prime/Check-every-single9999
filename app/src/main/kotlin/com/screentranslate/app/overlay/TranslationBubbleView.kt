package com.screentranslate.app.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.WindowManager
import com.screentranslate.app.R
import com.screentranslate.app.databinding.ItemTranslationBubbleBinding
import com.screentranslate.app.translation.TranslationResult

class TranslationBubbleView(
    private val context: Context,
    private val windowManager: WindowManager,
    private val result: TranslationResult,
    private val opacity: Float
) {

    private val binding = ItemTranslationBubbleBinding.inflate(
        LayoutInflater.from(ContextThemeWrapper(context, R.style.Theme_ScreenTranslate))
    )

    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT
    ).also {
        it.x = result.boundingBox.left
        it.y = result.boundingBox.top
        it.gravity = android.view.Gravity.TOP or android.view.Gravity.START
    }

    init {
        binding.tvTranslated.text = result.translatedText
        binding.tvOriginal.text = context.getString(
            com.screentranslate.app.R.string.original_text,
            result.originalText
        )
        binding.root.alpha = opacity

        // Toggle original text visibility on tap
        binding.root.setOnClickListener {
            val isVisible = binding.tvOriginal.visibility == android.view.View.VISIBLE
            binding.tvOriginal.visibility = if (isVisible) {
                android.view.View.GONE
            } else {
                android.view.View.VISIBLE
            }
        }
    }

    fun addToWindow() = windowManager.addView(binding.root, params)

    fun removeFromWindow() {
        runCatching { windowManager.removeView(binding.root) }
    }
}
