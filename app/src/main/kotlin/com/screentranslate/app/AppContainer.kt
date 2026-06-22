package com.screentranslate.app

import android.app.Application
import com.screentranslate.app.capture.ScreenCaptureManager
import com.screentranslate.app.data.PreferencesRepository
import com.screentranslate.app.ocr.OcrProcessor
import com.screentranslate.app.pipeline.TranslationPipeline
import com.screentranslate.app.translation.GoogleTranslateApi
import com.screentranslate.app.translation.LanguageDetector
import com.screentranslate.app.translation.MlKitTranslator
import com.screentranslate.app.translation.TranslationRepository
import com.screentranslate.app.util.CoroutineDispatchers
import com.screentranslate.app.util.TtsManager

class AppContainer(val app: Application) {
    val dispatchers = CoroutineDispatchers()
    val preferencesRepository = PreferencesRepository(app)
    val languageDetector = LanguageDetector()
    val ocrProcessor = OcrProcessor()
    val mlKitTranslator = MlKitTranslator()
    val googleTranslateApi = GoogleTranslateApi(BuildConfig.GOOGLE_TRANSLATE_API_KEY)
    val translationRepository = TranslationRepository(mlKitTranslator, googleTranslateApi, languageDetector)
    val ttsManager = TtsManager(app)
    val screenCaptureManager = ScreenCaptureManager(app)
    val translationPipeline = TranslationPipeline(
        screenCaptureManager,
        ocrProcessor,
        translationRepository,
        dispatchers
    )
}
