package com.screentranslate.app.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import com.screentranslate.app.R
import com.screentranslate.app.ai.AiResult
import com.screentranslate.app.databinding.OverlayAiDetailBinding
import com.screentranslate.app.translation.TranslationResult

class AiDetailView(
    context: Context,
    private val windowManager: WindowManager,
    private val result: TranslationResult,
    private val onClose: () -> Unit,
    private val onShare: ((original: String, translated: String, examples: List<String>) -> Unit)? = null
) {

    private val binding = OverlayAiDetailBinding.inflate(
        LayoutInflater.from(ContextThemeWrapper(context, R.style.Theme_ScreenTranslate))
    )

    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT
    ).also {
        it.gravity = Gravity.TOP or Gravity.START
    }

    private var lastAiResult: AiResult? = null

    init {
        binding.tvOriginal.text = result.originalText
        binding.tvTranslated.text = result.translatedText
        binding.btnClose.setOnClickListener { onClose() }
        binding.root.setOnClickListener { onClose() }
        binding.btnShare.setOnClickListener {
            val examples = lastAiResult?.examples ?: emptyList()
            onShare?.invoke(result.originalText, result.translatedText, examples)
        }
    }

    fun showLoading() {
        binding.progressBar.visibility = View.VISIBLE
        binding.layoutAiContent.visibility = View.GONE
        binding.tvAiError.visibility = View.GONE
    }

    fun showResult(aiResult: AiResult) {
        lastAiResult = aiResult
        binding.progressBar.visibility = View.GONE
        if (aiResult.definition.isBlank() && aiResult.examples.isEmpty()) {
            binding.tvAiError.text = binding.root.context.getString(R.string.ai_unavailable)
            binding.tvAiError.visibility = View.VISIBLE
            binding.layoutAiContent.visibility = View.GONE
        } else {
            binding.tvDefinition.text = aiResult.definition
            binding.tvExamples.text = aiResult.examples
                .mapIndexed { i, ex -> "${i + 1}. $ex" }
                .joinToString("\n")
            binding.layoutAiContent.visibility = View.VISIBLE
            binding.tvAiError.visibility = View.GONE
        }
    }

    fun showError(message: String) {
        binding.progressBar.visibility = View.GONE
        binding.tvAiError.text = message
        binding.tvAiError.visibility = View.VISIBLE
        binding.layoutAiContent.visibility = View.GONE
    }

    fun addToWindow() {
        windowManager.addView(binding.root, params)
        showLoading()
    }

    fun removeFromWindow() {
        runCatching { windowManager.removeView(binding.root) }
    }
}
