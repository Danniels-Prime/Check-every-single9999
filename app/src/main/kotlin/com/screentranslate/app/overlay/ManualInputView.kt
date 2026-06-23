package com.screentranslate.app.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import com.screentranslate.app.R
import com.screentranslate.app.ai.AiResult
import com.screentranslate.app.databinding.OverlayManualInputBinding

class ManualInputView(
    private val context: Context,
    private val windowManager: WindowManager,
    private val onTranslate: (String) -> Unit,
    private val onSaveFlashcard: () -> Unit,
    private val onClose: () -> Unit,
    private val onShare: ((original: String, translated: String, examples: List<String>) -> Unit)? = null
) {

    private val themedContext = ContextThemeWrapper(context, R.style.Theme_ScreenTranslate)
    private val binding = OverlayManualInputBinding.inflate(LayoutInflater.from(themedContext))

    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM,
        PixelFormat.TRANSLUCENT
    ).also {
        it.gravity = Gravity.CENTER
        it.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
    }

    private var lastOriginal = ""
    private var lastTranslated = ""
    private var lastExamples = listOf<String>()

    init {
        binding.btnClose.setOnClickListener {
            hideKeyboard()
            onClose()
        }
        binding.btnTranslate.setOnClickListener {
            val text = binding.etInput.text.toString().trim()
            if (text.isNotBlank()) {
                lastOriginal = text
                hideKeyboard()
                showLoading()
                onTranslate(text)
            }
        }
        binding.btnSaveFlashcard.setOnClickListener { onSaveFlashcard() }
        binding.btnShare.setOnClickListener {
            onShare?.invoke(lastOriginal, lastTranslated, lastExamples)
        }
    }

    fun showLoading() {
        binding.progressBar.visibility = View.VISIBLE
        binding.btnTranslate.isEnabled = false
        binding.layoutResult.visibility = View.GONE
    }

    fun hideLoading() {
        binding.progressBar.visibility = View.GONE
        binding.btnTranslate.isEnabled = true
    }

    fun showResult(translated: String) {
        hideLoading()
        lastTranslated = translated
        binding.tvTranslated.text = translated
        binding.layoutResult.visibility = View.VISIBLE
        binding.layoutAi.visibility = View.GONE
        binding.btnSaveFlashcard.visibility = View.VISIBLE
        binding.btnShare.visibility = View.VISIBLE
    }

    fun showAiResult(aiResult: AiResult) {
        if (aiResult.definition.isNotBlank() || aiResult.examples.isNotEmpty()) {
            lastExamples = aiResult.examples
            binding.tvDefinition.text = aiResult.definition
            binding.tvExamples.text = aiResult.examples
                .mapIndexed { i, ex -> "${i + 1}. $ex" }
                .joinToString("\n")
            binding.layoutAi.visibility = View.VISIBLE
        }
    }

    private fun hideKeyboard() {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.etInput.windowToken, 0)
    }

    fun addToWindow() = windowManager.addView(binding.root, params)

    fun removeFromWindow() {
        hideKeyboard()
        runCatching { windowManager.removeView(binding.root) }
    }
}
