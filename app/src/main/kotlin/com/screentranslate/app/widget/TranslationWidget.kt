package com.screentranslate.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.screentranslate.app.R
import com.screentranslate.app.data.FlashcardRepository
import com.screentranslate.app.ui.FlashcardsActivity
import kotlinx.coroutines.runBlocking

class TranslationWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { updateWidget(context, appWidgetManager, it) }
    }

    companion object {
        fun updateWidget(context: Context, mgr: AppWidgetManager, widgetId: Int) {
            val flashcards = runBlocking { FlashcardRepository(context).getAll() }
            val card = flashcards.randomOrNull()

            val views = RemoteViews(context.packageName, R.layout.widget_translation).apply {
                setTextViewText(R.id.tvWidgetOriginal, card?.originalText ?: context.getString(R.string.widget_no_cards))
                setTextViewText(R.id.tvWidgetTranslated, card?.translatedText ?: context.getString(R.string.widget_no_cards_sub))

                val intent = Intent(context, FlashcardsActivity::class.java)
                val pending = PendingIntent.getActivity(
                    context, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                setOnClickPendingIntent(R.id.widgetRoot, pending)
            }
            mgr.updateAppWidget(widgetId, views)
        }

        fun refreshAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(
                android.content.ComponentName(context, TranslationWidget::class.java)
            )
            if (ids.isNotEmpty()) {
                ids.forEach { updateWidget(context, mgr, it) }
            }
        }
    }
}
