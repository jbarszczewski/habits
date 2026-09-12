package com.jbarszczewski.habits.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

class WeekHistoryWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = WeekHistoryWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetRefreshScheduler.scheduleNextDayStart(context)
    }
}
