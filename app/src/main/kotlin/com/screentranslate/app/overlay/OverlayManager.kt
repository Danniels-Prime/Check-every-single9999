package com.screentranslate.app.overlay

import android.content.Context
import android.view.WindowManager
import com.screentranslate.app.translation.TranslationResult

class OverlayManager(
    private val context: Context,
    private val windowManager: WindowManager
) {

    private var fabView: FloatingButtonView? = null
    private val bubbles = mutableListOf<TranslationBubbleView>()
    private var opacity: Float = 0.85f
    private var onFabPositionSaved: ((Int, Int) -> Unit)? = null

    fun setOpacity(value: Float) {
        opacity = value.coerceIn(0.3f, 1.0f)
    }

    fun setOnFabPositionSaved(callback: (Int, Int) -> Unit) {
        onFabPositionSaved = callback
    }

    fun showFab(onTap: () -> Unit, savedX: Int = -1, savedY: Int = -1) {
        if (fabView != null) return
        fabView = FloatingButtonView(
            context = context,
            windowManager = windowManager,
            onTap = onTap,
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

    fun showTranslations(results: List<TranslationResult>) {
        clearBubbles()
        results.forEach { result ->
            if (result.translatedText != result.originalText) {
                val bubble = TranslationBubbleView(context, windowManager, result, opacity)
                bubble.addToWindow()
                bubbles.add(bubble)
            }
        }
    }

    fun setFabProcessing(isProcessing: Boolean) {
        fabView?.setProcessing(isProcessing)
    }

    fun clearBubbles() {
        bubbles.forEach { it.removeFromWindow() }
        bubbles.clear()
    }

    fun destroy() {
        clearBubbles()
        hideFab()
    }
}
