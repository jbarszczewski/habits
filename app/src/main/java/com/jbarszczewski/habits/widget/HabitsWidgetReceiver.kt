package com.jbarszczewski.habits.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * The BroadcastReceiver the launcher talks to (declared in the manifest). Glance handles the
 * update plumbing; we only hook the first-placed callback to ensure the shared day-rollover
 * refresh job is scheduled.
 */
class HabitsWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = HabitsWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetRefreshScheduler.scheduleNextDayStart(context)
    }
}
