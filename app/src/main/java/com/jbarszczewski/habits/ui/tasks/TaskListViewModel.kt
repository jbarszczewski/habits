package com.jbarszczewski.habits.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jbarszczewski.habits.HabitsApplication
import com.jbarszczewski.habits.data.HabitRepository
import com.jbarszczewski.habits.data.Task
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class TaskListUiState(
    val active: List<Task>,
    val archived: List<Task>,
    val isLoading: Boolean,
)

/** All tasks, active and archived, so any of them can be opened in the editor. */
class TaskListViewModel(repository: HabitRepository) : ViewModel() {

    val uiState: StateFlow<TaskListUiState> = repository.observeAllTasks()
        .map { tasks ->
            val (archived, active) = tasks.partition { it.archivedAt != null }
            TaskListUiState(active = active, archived = archived, isLoading = false)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = TaskListUiState(emptyList(), emptyList(), isLoading = true),
        )

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as HabitsApplication
                TaskListViewModel(app.container.repository)
            }
        }
    }
}
