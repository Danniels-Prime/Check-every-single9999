package com.screentranslate.app.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import androidx.core.content.FileProvider
import com.screentranslate.app.R
import com.screentranslate.app.databinding.ShareCardBinding
import java.io.File
import java.io.FileOutputStream

object ShareCardHelper {

    fun share(
        context: Context,
        original: String,
        translated: String,
        sourceLang: String,
        targetLang: String,
        examples: List<String> = emptyList()
    ) {
        val bitmap = renderCard(context, original, translated, sourceLang, targetLang, examples)
        val file = File(context.cacheDir, "translation_card.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 90, it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

        Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }.also { intent ->
            context.startActivity(Intent.createChooser(intent, "Share Translation").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }

    private fun renderCard(
        context: Context,
        original: String,
        translated: String,
        sourceLang: String,
        targetLang: String,
        examples: List<String>
    ): Bitmap {
        val themedContext = ContextThemeWrapper(context, R.style.Theme_ScreenTranslate)
        val binding = ShareCardBinding.inflate(LayoutInflater.from(themedContext))

        binding.tvShareOriginal.text = original
        binding.tvShareTranslated.text = translated
        binding.tvShareLangs.text = "${sourceLang.uppercase()} → ${targetLang.uppercase()}"

        if (examples.isNotEmpty()) {
            binding.tvShareExamples.text = examples
                .mapIndexed { i, ex -> "${i + 1}. $ex" }
                .joinToString("\n")
            binding.tvShareExamples.visibility = View.VISIBLE
            binding.tvShareExamplesLabel.visibility = View.VISIBLE
        } else {
            binding.tvShareExamples.visibility = View.GONE
            binding.tvShareExamplesLabel.visibility = View.GONE
        }

        val density = context.resources.displayMetrics.density
        val widthPx = (360 * density).toInt()
        binding.root.measure(
            View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        binding.root.layout(0, 0, binding.root.measuredWidth, binding.root.measuredHeight)

        val bitmap = Bitmap.createBitmap(binding.root.measuredWidth, binding.root.measuredHeight, Bitmap.Config.ARGB_8888)
        binding.root.draw(Canvas(bitmap))
        return bitmap
    }
}
