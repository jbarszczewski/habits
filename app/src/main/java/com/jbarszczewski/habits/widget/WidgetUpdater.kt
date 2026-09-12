package com.jbarszczewski.habits.widget

import android.content.Context
import androidx.glance.appwidget.updateAll

suspend fun updateAllHabitWidgets(context: Context) {
    HabitsWidget().updateAll(context)
    WeekHistoryWidget().updateAll(context)
}
