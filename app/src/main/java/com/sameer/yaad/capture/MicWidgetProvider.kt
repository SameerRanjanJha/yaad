package com.sameer.yaad.capture

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.sameer.yaad.R

class MicWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, widgetIds: IntArray) {
        val capture = PendingIntent.getActivity(
            context,
            0,
            Intent(context, CaptureActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val views = RemoteViews(context.packageName, R.layout.widget_mic).apply {
            setOnClickPendingIntent(R.id.widget_root, capture)
        }
        manager.updateAppWidget(widgetIds, views)
    }
}
