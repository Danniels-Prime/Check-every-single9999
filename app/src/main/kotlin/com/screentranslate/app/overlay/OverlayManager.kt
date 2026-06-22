package com.screentranslate.app.overlay

import android.content.Context
import android.view.WindowManager
import com.screentranslate.app.ai.AiResult
import com.screentranslate.app.translation.TranslationResult

class OverlayManager(
    private val context: Context,
    private val windowManager: WindowManager
) {

    private var fabView: FloatingButtonView? = null
    private val bubbles = mutableListOf<TranslationBubbleView>()
    private var aiDetailView: AiDetailView? = null
    private var manualInputView: ManualInputView? = null
    private var opacity: Float = 0.85f
    private var onFabPositionSaved: ((Int, Int) -> Unit)? = null

    val hasBubbles: Boolean get() = bubbles.isNotEmpty()

    fun setOpacity(value: Float) {
        opacity = value.coerceIn(0.3f, 1.0f)
    }

    fun setOnFabPositionSaved(callback: (Int, Int) -> Unit) {
        onFabPositionSaved = callback
    }

    fun showFab(onTap: () -> Unit, onLongPress: () -> Unit, savedX: Int = -1, savedY: Int = -1) {
        if (fabView != null) return
        fabView = FloatingButtonView(
            context = context,
            windowManager = windowManager,
            onTap = onTap,
            onLongPress = onLongPress,
            onSavedPosition = { x, y -> onFabPositionSaved?.invoke(x, y) }
        ).also {
            if (savedX >= 0 && savedY >= 0) it.setPosition(savedX, savedY)
            it.addToWindow()
        }
    }

    fun hideFab() {
        fabView?.removeFromWindow()
        fabView = null
    }

    fun showTranslations(
        results: List<TranslationResult>,
        onSpeak: (String, String) -> Unit,
        onExpand: (TranslationResult) -> Unit
    ) {
        clearBubbles(updateFabIcon = false)
        results.forEach { result ->
            if (result.translatedText != result.originalText) {
                val bubble = TranslationBubbleView(
                    context, windowManager, result, opacity, onSpeak, onExpand
                )
                bubble.addToWindow()
                bubbles.add(bubble)
            }
        }
        if (bubbles.isNotEmpty()) fabView?.setShowClear(true)
    }

    fun setFabProcessing(isProcessing: Boolean) {
        fabView?.setProcessing(isProcessing)
    }

    fun clearBubbles(updateFabIcon: Boolean = true) {
        bubbles.forEach { it.removeFromWindow() }
        bubbles.clear()
        if (updateFabIcon) fabView?.setShowClear(false)
    }

    // AI detail card

    fun showAiDetailLoading(result: TranslationResult, onClose: () -> Unit) {
        hideAiDetail()
        aiDetailView = AiDetailView(context, windowManager, result, onClose).also {
            it.addToWindow()
        }
    }

    fun updateAiDetailResult(aiResult: AiResult) {
        aiDetailView?.showResult(aiResult)
    }

    fun updateAiDetailError(message: String) {
        aiDetailView?.showError(message)
    }

    fun hideAiDetail() {
        aiDetailView?.removeFromWindow()
        aiDetailView = null
    }

    // Manual input panel

    fun showManualInput(
        onTranslate: (String) -> Unit,
        onSaveFlashcard: () -> Unit,
        onClose: () -> Unit
    ) {
        if (manualInputView != null) return
        manualInputView = ManualInputView(
            context, windowManager, onTranslate, onSaveFlashcard, onClose
        ).also { it.addToWindow() }
    }

    fun showManualInputResult(translated: String) {
        manualInputView?.showResult(translated)
    }

    fun showManualInputAiResult(aiResult: AiResult) {
        manualInputView?.showAiResult(aiResult)
    }

    fun hideManualInput() {
        manualInputView?.removeFromWindow()
        manualInputView = null
    }

    fun destroy() {
        clearBubbles()
        hideFab()
        hideAiDetail()
        hideManualInput()
    }
}
