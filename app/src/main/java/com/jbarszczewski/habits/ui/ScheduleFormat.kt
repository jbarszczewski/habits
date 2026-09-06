package com.jbarszczewski.habits.ui

import android.content.Context
import com.jbarszczewski.habits.R
import com.jbarszczewski.habits.data.DaysMask
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/** "Every day", "Weekdays", "Mon, Wed, Fri", ... */
fun formatSchedule(context: Context, daysMask: Int): String {
    val days = DaysMask.toDays(daysMask)
    return when (days) {
        DayOfWeek.entries.toSet() -> context.getString(R.string.schedule_every_day)
        WEEKDAYS -> context.getString(R.string.schedule_weekdays)
        WEEKEND -> context.getString(R.string.schedule_weekend)
        else -> days.joinToString(", ") { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
    }
}

private val WEEKDAYS = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
private val WEEKEND = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
