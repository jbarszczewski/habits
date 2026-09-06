package com.jbarszczewski.habits.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * The BroadcastReceiver the launcher talks to (declared in the manifest). Glance handles the
 * update plumbing; we only hook the first-placed / last-removed callbacks to manage the
 * day-rollover refresh job.
 */
class HabitsWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = HabitsWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetRefreshScheduler.scheduleNextDayStart(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetRefreshScheduler.cancel(context)
    }
}
