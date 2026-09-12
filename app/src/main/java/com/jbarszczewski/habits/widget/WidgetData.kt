package com.jbarszczewski.habits.widget

import com.jbarszczewski.habits.AppContainer
import com.jbarszczewski.habits.data.TaskWithCompletion
import com.jbarszczewski.habits.data.stats.HeatmapCalculator
import com.jbarszczewski.habits.data.stats.HeatmapWeek
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** Everything one render of a widget needs. */
internal data class WidgetData(
    val today: LocalDate,
    val items: List<TaskWithCompletion>,
    val nowMillis: Long,
    val weekHistory: HeatmapWeek? = null,
)

private data class LiveWidgetData(
    val today: LocalDate,
    val items: List<TaskWithCompletion>,
    val nowMillis: Long,
)

/**
 * Reloads on every change to the two tables and once a minute (so a running timer's minutes and
 * the logical date stay current while a session is open). Each reload is one small query.
 */
internal fun observeWidgetData(container: AppContainer, includeWeekHistory: Boolean): Flow<WidgetData> {
    val dbChanges = container.database.invalidationTracker.createFlow("tasks", "completions")
    val liveData = combine(dbChanges, minuteTicker()) { _, _ ->
        val repository = container.repository
        val today = container.dateProvider.today()
        LiveWidgetData(
            today = today,
            items = repository.observeTasksForDate(today).first(),
            nowMillis = container.dateProvider.nowMillis(),
        )
    }
    val weekHistory = if (includeWeekHistory) {
        dbChanges.map {
            val repository = container.repository
            val today = container.dateProvider.today()
            val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            HeatmapCalculator.buildCurrentWeek(
                tasks = repository.getAllTasks(),
                completions = repository.getCompletionsInRange(weekStart, today),
                today = today,
            )
        }
    } else {
        flowOf<HeatmapWeek?>(null)
    }
    return combine(liveData, weekHistory) { live, history ->
        WidgetData(
            today = live.today,
            items = live.items,
            nowMillis = live.nowMillis,
            weekHistory = history,
        )
    }
}

private fun minuteTicker(): Flow<Unit> = flow {
    while (true) {
        emit(Unit)
        delay(60_000)
    }
}
