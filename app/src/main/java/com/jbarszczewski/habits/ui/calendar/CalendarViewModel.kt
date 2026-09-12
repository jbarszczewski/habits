package com.jbarszczewski.habits.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jbarszczewski.habits.HabitsApplication
import com.jbarszczewski.habits.data.Completion
import com.jbarszczewski.habits.data.CompletionStatus
import com.jbarszczewski.habits.data.DateProvider
import com.jbarszczewski.habits.data.HabitRepository
import com.jbarszczewski.habits.data.Task
import com.jbarszczewski.habits.data.isScheduledOn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** How "done" a day was across all scheduled tasks. Drives the cell colour. */
enum class CellIntensity {
    /** No tasks scheduled on this day (or future day). */
    NONE,
    /** Tasks are scheduled today but none completed yet (today is still in progress). */
    IN_PROGRESS,
    /** Tasks were scheduled in the past but none completed (all missed). */
    MISSED,
    /** 1–25 % of scheduled tasks completed. */
    LOW,
    /** 26–50 % completed. */
    MEDIUM,
    /** 51–75 % completed. */
    HIGH,
    /** 76–100 % completed. */
    FULL,
}

/**
 * One cell in the heatmap grid.
 *
 * @param date the calendar date this cell represents
 * @param intensity how complete the day was
 * @param scheduledCount how many tasks were scheduled
 * @param completedCredit sum of credit earned (1.0 per DONE, fractional for PARTIAL)
 */
data class CalendarDay(
    val date: LocalDate,
    val intensity: CellIntensity,
    val scheduledCount: Int,
    val completedCredit: Double,
)

/**
 * A single week column in the heatmap: 7 cells top-to-bottom (Monday … Sunday).
 * A cell is `null` for days that fall before the grid window (padding in the first column)
 * or after today (padding in the last column).
 */
data class WeekColumn(
    /** The first day of this week (always a Monday). */
    val weekStart: LocalDate,
    val days: List<CalendarDay?>, // size == 7, index 0 = Mon … index 6 = Sun
)

data class CalendarUiState(
    val weeks: List<WeekColumn> = emptyList(),
    val isLoading: Boolean = true,
)

/**
 * Loads all tasks + completions for the last [WEEKS_SHOWN] weeks and produces the heatmap data.
 *
 * The grid always ends on today and starts on the Monday `WEEKS_SHOWN – 1` weeks ago.
 */
class CalendarViewModel(
    private val repository: HabitRepository,
    private val dateProvider: DateProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CalendarUiState())
    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            val today = dateProvider.today()

            // Grid spans from the Monday WEEKS_SHOWN–1 weeks before today's week through today.
            val mondayThisWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val gridStart = mondayThisWeek.minusWeeks((WEEKS_SHOWN - 1).toLong())

            val tasks = repository.getAllTasks()
            val completions = repository.getCompletionsInRange(gridStart, today)

            // Index completions by date for O(1) lookup.
            val completionsByDate = completions.groupBy { it.date }

            val weeks = buildWeeks(tasks, completionsByDate, gridStart, today)
            _uiState.value = CalendarUiState(weeks, isLoading = false)
        }
    }

    private fun buildWeeks(
        tasks: List<Task>,
        completionsByDate: Map<LocalDate, List<Completion>>,
        gridStart: LocalDate,
        today: LocalDate,
    ): List<WeekColumn> {
        val result = mutableListOf<WeekColumn>()
        var weekStart = gridStart
        while (!weekStart.isAfter(today)) {
            val days = (0..6).map { offset ->
                val date = weekStart.plusDays(offset.toLong())
                when {
                    date.isAfter(today) -> null // future padding
                    else -> buildDay(tasks, completionsByDate, date, today)
                }
            }
            result += WeekColumn(weekStart, days)
            weekStart = weekStart.plusWeeks(1)
        }
        return result
    }

    private fun buildDay(
        tasks: List<Task>,
        completionsByDate: Map<LocalDate, List<Completion>>,
        date: LocalDate,
        today: LocalDate,
    ): CalendarDay {
        val dayCompletions = completionsByDate[date] ?: emptyList()
        val doneByTask = dayCompletions.associateBy { it.taskId }

        // A task counts as "scheduled" on this day if it was created on or before the date
        // and not yet archived (or archived on a later date).
        var scheduled = 0
        var creditSum = 0.0
        for (task in tasks) {
            if (task.createdAt.isAfter(date)) continue
            val archivedAt = task.archivedAt
            if (archivedAt != null && !archivedAt.isAfter(date)) continue
            if (!task.isScheduledOn(date)) continue
            scheduled++
            val completion = doneByTask[task.id]
            creditSum += when (completion?.status) {
                CompletionStatus.DONE -> 1.0
                CompletionStatus.PARTIAL -> {
                    // Timed tasks: credit actual/target (capped at 1). Checkbox tasks cannot be
                    // PARTIAL, so this branch is only reached for timed ones.
                    val target = task.targetMinutes
                    val actual = completion.actualMinutes ?: 0
                    if (target != null && target > 0) (actual.toDouble() / target).coerceIn(0.0, 1.0)
                    else 0.5 // fallback: treat as half credit
                }
                else -> 0.0
            }
        }

        val intensity = when {
            scheduled == 0 -> CellIntensity.NONE
            date == today && creditSum == 0.0 -> CellIntensity.IN_PROGRESS // scheduled but not started yet
            creditSum == 0.0 -> CellIntensity.MISSED
            else -> {
                val ratio = creditSum / scheduled
                when {
                    ratio <= 0.25 -> CellIntensity.LOW
                    ratio <= 0.50 -> CellIntensity.MEDIUM
                    ratio <= 0.75 -> CellIntensity.HIGH
                    else -> CellIntensity.FULL
                }
            }
        }

        return CalendarDay(date, intensity, scheduled, completedCredit = creditSum)
    }

    companion object {
        /** Number of week columns to display. */
        const val WEEKS_SHOWN = 18

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as HabitsApplication
                CalendarViewModel(
                    repository = app.container.repository,
                    dateProvider = app.container.dateProvider,
                )
            }
        }
    }
}
