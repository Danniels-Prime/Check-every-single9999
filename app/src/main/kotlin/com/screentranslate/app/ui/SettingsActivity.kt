package com.screentranslate.app.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SeekBarPreference
import androidx.preference.SwitchPreferenceCompat
import com.screentranslate.app.R
import com.screentranslate.app.data.Flashcard
import com.screentranslate.app.data.VocabEntry
import com.screentranslate.app.databinding.ActivitySettingsBinding
import com.screentranslate.app.util.appContainer
import com.screentranslate.app.widget.TranslationWidget
import kotlinx.serialization.json.Json
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.settings_title)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.settings_container, SettingsFragment())
                .commit()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    class SettingsFragment : PreferenceFragmentCompat() {

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            val context = preferenceManager.context
            val screen = preferenceManager.createPreferenceScreen(context)
            val container = requireActivity().application.appContainer

            // Target language
            val languagePref = ListPreference(context).apply {
                key = "target_language"
                title = getString(R.string.pref_target_language)
                entries = resources.getStringArray(R.array.language_names)
                entryValues = resources.getStringArray(R.array.language_codes)
                setOnPreferenceChangeListener { _, newValue ->
                    lifecycleScope.launch {
                        container.preferencesRepository.setTargetLanguage(newValue as String)
                    }
                    true
                }
            }

            // Opacity
            val opacityPref = SeekBarPreference(context).apply {
                key = "overlay_opacity"
                title = getString(R.string.pref_opacity)
                min = 30
                max = 100
                showSeekBarValue = true
                setOnPreferenceChangeListener { _, newValue ->
                    lifecycleScope.launch {
                        container.preferencesRepository.setOverlayOpacity((newValue as Int) / 100f)
                    }
                    true
                }
            }

            // Auto-capture
            val autoCapturePref = SwitchPreferenceCompat(context).apply {
                key = "auto_capture"
                title = getString(R.string.pref_auto_capture)
                summaryOn = getString(R.string.pref_auto_capture_summary)
                summaryOff = getString(R.string.pref_auto_capture_summary)
                setOnPreferenceChangeListener { _, newValue ->
                    lifecycleScope.launch {
                        container.preferencesRepository.setAutoCapture(newValue as Boolean)
                    }
                    true
                }
            }

            // Auto-capture interval
            val intervalPref = ListPreference(context).apply {
                key = "auto_capture_interval"
                title = getString(R.string.pref_auto_capture_interval)
                entries = resources.getStringArray(R.array.auto_capture_intervals)
                entryValues = resources.getStringArray(R.array.auto_capture_interval_values)
                setOnPreferenceChangeListener { _, newValue ->
                    lifecycleScope.launch {
                        container.preferencesRepository.setAutoCaptureInterval((newValue as String).toLong())
                    }
                    true
                }
            }

            // Download models
            val downloadPref = Preference(context).apply {
                key = "download_models"
                title = getString(R.string.pref_download_models)
                summary = getString(R.string.pref_download_models_summary)
                setOnPreferenceClickListener {
                    lifecycleScope.launch {
                        val targetLang = container.preferencesRepository.targetLanguage.first()
                        Toast.makeText(context, getString(R.string.model_downloading), Toast.LENGTH_SHORT).show()
                        val success = container.mlKitTranslator.ensureModelDownloaded("ru", targetLang)
                        Toast.makeText(
                            context,
                            if (success) getString(R.string.model_download_success) else getString(R.string.model_download_failed),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    true
                }
            }

            // AI provider
            val aiProviderPref = ListPreference(context).apply {
                key = "ai_provider"
                title = getString(R.string.pref_ai_provider)
                entries = resources.getStringArray(R.array.ai_provider_names)
                entryValues = resources.getStringArray(R.array.ai_provider_values)
                setOnPreferenceChangeListener { _, newValue ->
                    lifecycleScope.launch {
                        container.preferencesRepository.setAiProvider(newValue as String)
                    }
                    true
                }
            }

            // AI API key
            val aiKeyPref = EditTextPreference(context).apply {
                key = "ai_api_key"
                title = getString(R.string.pref_ai_api_key)
                summary = getString(R.string.pref_ai_api_key_summary)
                setOnPreferenceChangeListener { _, newValue ->
                    lifecycleScope.launch {
                        container.preferencesRepository.setAiApiKey(newValue as String)
                    }
                    true
                }
            }

            // App theme
            val appThemePref = ListPreference(context).apply {
                key = "app_theme"
                title = getString(R.string.pref_app_theme)
                entries = resources.getStringArray(R.array.app_theme_names)
                entryValues = resources.getStringArray(R.array.app_theme_values)
                setOnPreferenceChangeListener { _, newValue ->
                    lifecycleScope.launch {
                        container.preferencesRepository.setAppTheme(newValue as String)
                        val mode = when (newValue) {
                            "light" -> AppCompatDelegate.MODE_NIGHT_NO
                            "dark"  -> AppCompatDelegate.MODE_NIGHT_YES
                            else    -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                        }
                        AppCompatDelegate.setDefaultNightMode(mode)
                        activity?.recreate()
                    }
                    true
                }
            }

            // Overlay color theme
            val overlayThemePref = ListPreference(context).apply {
                key = "overlay_theme"
                title = getString(R.string.pref_overlay_theme)
                entries = resources.getStringArray(R.array.overlay_theme_names)
                entryValues = resources.getStringArray(R.array.overlay_theme_values)
                setOnPreferenceChangeListener { _, newValue ->
                    lifecycleScope.launch {
                        container.preferencesRepository.setOverlayTheme(newValue as String)
                    }
                    true
                }
            }

            // Clipboard monitoring
            val clipboardPref = SwitchPreferenceCompat(context).apply {
                key = "clipboard_monitoring"
                title = getString(R.string.pref_clipboard_monitoring)
                summary = getString(R.string.pref_clipboard_monitoring_summary)
                setOnPreferenceChangeListener { _, newValue ->
                    lifecycleScope.launch {
                        container.preferencesRepository.setClipboardMonitoring(newValue as Boolean)
                    }
                    true
                }
            }

            // Import vocabulary pack
            val importVocabPref = Preference(context).apply {
                key = "import_vocab"
                title = getString(R.string.pref_import_vocab)
                summary = getString(R.string.pref_import_vocab_summary)
                setOnPreferenceClickListener {
                    lifecycleScope.launch {
                        Toast.makeText(context, getString(R.string.vocab_importing), Toast.LENGTH_SHORT).show()
                        try {
                            val jsonStr = context.assets.open("vocab_pack_russian.json").bufferedReader().readText()
                            val jsonLib = Json { ignoreUnknownKeys = true }
                            val entries = jsonLib.decodeFromString<List<VocabEntry>>(jsonStr)
                            var imported = 0
                            entries.forEach { entry ->
                                container.flashcardRepository.save(
                                    Flashcard(
                                        id = "vocab_${entry.english.hashCode()}",
                                        originalText = entry.russian,
                                        translatedText = entry.english,
                                        sourceLang = "ru",
                                        targetLang = "en",
                                        definition = if (entry.pronunciation.isNotBlank()) "Pronunciation: ${entry.pronunciation}" else "",
                                        examples = listOf(entry.exampleRu, entry.exampleEn).filter { it.isNotBlank() }
                                    )
                                )
                                imported++
                            }
                            TranslationWidget.refreshAll(context)
                            Toast.makeText(
                                context,
                                getString(R.string.vocab_imported, imported),
                                Toast.LENGTH_LONG
                            ).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                    true
                }
            }

            screen.addPreference(languagePref)
            screen.addPreference(opacityPref)
            screen.addPreference(autoCapturePref)
            screen.addPreference(intervalPref)
            screen.addPreference(downloadPref)
            screen.addPreference(aiProviderPref)
            screen.addPreference(aiKeyPref)
            screen.addPreference(appThemePref)
            screen.addPreference(overlayThemePref)
            screen.addPreference(clipboardPref)
            screen.addPreference(importVocabPref)

            preferenceScreen = screen

            // Load current values
            lifecycleScope.launch {
                languagePref.value = container.preferencesRepository.targetLanguage.first()
                opacityPref.value = (container.preferencesRepository.overlayOpacity.first() * 100).toInt()
                autoCapturePref.isChecked = container.preferencesRepository.autoCapture.first()
                intervalPref.value = container.preferencesRepository.autoCaptureInterval.first().toString()
                aiProviderPref.value = container.preferencesRepository.aiProvider.first()
                appThemePref.value = container.preferencesRepository.appTheme.first()
                overlayThemePref.value = container.preferencesRepository.overlayTheme.first()
                clipboardPref.isChecked = container.preferencesRepository.clipboardMonitoring.first()
            }
        }
    }
}
