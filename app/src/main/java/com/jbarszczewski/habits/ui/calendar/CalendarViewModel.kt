package com.jbarszczewski.habits.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jbarszczewski.habits.HabitsApplication
import com.jbarszczewski.habits.data.DateProvider
import com.jbarszczewski.habits.data.HabitRepository
import com.jbarszczewski.habits.data.stats.HeatmapCalculator
import com.jbarszczewski.habits.data.stats.HeatmapWeek
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters

data class CalendarUiState(
    val weeks: List<HeatmapWeek> = emptyList(),
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
            val mondayThisWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val gridStart = mondayThisWeek.minusWeeks((WEEKS_SHOWN - 1).toLong())

            val tasks = repository.getAllTasks()
            val completions = repository.getCompletionsInRange(gridStart, today)
            val weeks = HeatmapCalculator.buildWeeks(tasks, completions, gridStart, today)
            _uiState.value = CalendarUiState(weeks, isLoading = false)
        }
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
