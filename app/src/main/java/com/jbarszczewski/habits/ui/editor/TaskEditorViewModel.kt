package com.jbarszczewski.habits.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jbarszczewski.habits.HabitsApplication
import com.jbarszczewski.habits.data.DaysMask
import com.jbarszczewski.habits.data.HabitRepository
import com.jbarszczewski.habits.data.Task
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek

data class TaskEditorUiState(
    val isLoading: Boolean,
    val isExisting: Boolean = false,
    val isArchived: Boolean = false,
    val name: String = "",
    val days: Set<DayOfWeek> = DayOfWeek.entries.toSet(),
    val hasTarget: Boolean = false,
    val targetMinutes: String = "30",
    // Validation errors are only shown after the first save attempt.
    val showErrors: Boolean = false,
    /** Set once the task was saved or archived; the screen navigates back when it sees this. */
    val isFinished: Boolean = false,
) {
    val nameError: Boolean get() = name.isBlank()
    val daysError: Boolean get() = days.isEmpty()
    val minutesError: Boolean get() = hasTarget && (targetMinutes.toIntOrNull() ?: 0) <= 0
    val isValid: Boolean get() = !nameError && !daysError && !minutesError
}

/** Form state for creating or editing one task. [taskId] null means "new task". */
class TaskEditorViewModel(
    private val repository: HabitRepository,
    private val taskId: Long?,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TaskEditorUiState(isLoading = taskId != null))
    val uiState: StateFlow<TaskEditorUiState> = _uiState.asStateFlow()

    /** The stored row when editing, so unrelated fields (timer, dates) are preserved on save. */
    private var original: Task? = null

    init {
        if (taskId != null) {
            viewModelScope.launch {
                val task = repository.getTask(taskId)
                if (task == null) {
                    _uiState.update { it.copy(isLoading = false, isFinished = true) }
                } else {
                    original = task
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isExisting = true,
                            isArchived = task.archivedAt != null,
                            name = task.name,
                            days = DaysMask.toDays(task.daysMask),
                            hasTarget = task.targetMinutes != null,
                            targetMinutes = task.targetMinutes?.toString() ?: "30",
                        )
                    }
                }
            }
        }
    }

    fun onNameChange(value: String) = _uiState.update { it.copy(name = value) }

    fun onDayToggle(day: DayOfWeek) = _uiState.update {
        it.copy(days = if (day in it.days) it.days - day else it.days + day)
    }

    fun onHasTargetChange(enabled: Boolean) = _uiState.update { it.copy(hasTarget = enabled) }

    fun onTargetMinutesChange(value: String) {
        // Digits only, and keep it short: nobody targets more than 4 digits of minutes a day.
        if (value.length <= 4 && value.all(Char::isDigit)) _uiState.update { it.copy(targetMinutes = value) }
    }

    fun save() {
        val state = _uiState.value
        if (!state.isValid) {
            _uiState.update { it.copy(showErrors = true) }
            return
        }
        val target = if (state.hasTarget) state.targetMinutes.toInt() else null
        val mask = DaysMask.of(state.days)
        viewModelScope.launch {
            val existing = original
            if (existing == null) {
                repository.createTask(name = state.name, daysMask = mask, targetMinutes = target)
            } else {
                repository.updateTask(existing.copy(name = state.name, daysMask = mask, targetMinutes = target))
            }
            _uiState.update { it.copy(isFinished = true) }
        }
    }

    fun archive() {
        val id = taskId ?: return
        viewModelScope.launch {
            repository.archiveTask(id)
            _uiState.update { it.copy(isFinished = true) }
        }
    }

    fun unarchive() {
        val id = taskId ?: return
        viewModelScope.launch {
            repository.unarchiveTask(id)
            _uiState.update { it.copy(isFinished = true) }
        }
    }

    companion object {
        fun factory(taskId: Long?): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as HabitsApplication
                TaskEditorViewModel(app.container.repository, taskId)
            }
        }
    }
}
