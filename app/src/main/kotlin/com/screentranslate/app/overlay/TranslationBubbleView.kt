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
    private val opacity: Float,
    private val onSpeak: (text: String, lang: String) -> Unit,
    private val onExpand: (TranslationResult) -> Unit,
    private val bubbleBgColor: Int = 0xCC1565C0.toInt(),
    private val bubbleTextColor: Int = 0xFFFFFFFF.toInt()
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
        binding.tvOriginal.text = context.getString(R.string.original_text, result.originalText)
        binding.root.alpha = opacity
        binding.root.setBackgroundColor(bubbleBgColor)
        binding.tvTranslated.setTextColor(bubbleTextColor)

        binding.btnSpeak.setOnClickListener {
            onSpeak(result.translatedText, result.targetLang)
        }
        binding.root.setOnClickListener {
            onExpand(result)
        }
    }

    fun addToWindow() = windowManager.addView(binding.root, params)

    fun removeFromWindow() {
        runCatching { windowManager.removeView(binding.root) }
    }
}
