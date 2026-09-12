package com.jbarszczewski.habits.widget

import android.content.Context
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.glance.GlanceId
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import com.jbarszczewski.habits.HabitsApplication
import kotlinx.coroutines.flow.first

/** A larger widget that adds this week's heatmap above the existing list of today's tasks. */
class WeekHistoryWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as HabitsApplication).container
        val dataFlow = observeWidgetData(container, includeWeekHistory = true)
        val initial = dataFlow.first()

        provideContent {
            val data by dataFlow.collectAsState(initial)
            GlanceTheme {
                WidgetContent(
                    items = data.items,
                    nowMillis = data.nowMillis,
                    weekHistory = data.weekHistory,
                )
            }
        }
    }
}
