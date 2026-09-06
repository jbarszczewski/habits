package com.jbarszczewski.habits.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jbarszczewski.habits.BuildConfig
import com.jbarszczewski.habits.HabitsApplication
import com.jbarszczewski.habits.data.DateProvider
import com.jbarszczewski.habits.data.HabitRepository
import com.jbarszczewski.habits.data.TaskWithCompletion
import com.jbarszczewski.habits.update.UpdateChecker
import com.jbarszczewski.habits.update.UpdateInfo
import com.jbarszczewski.habits.update.isNewerVersion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

data class TodayUiState(
    val date: LocalDate,
    val tasks: List<TaskWithCompletion>,
    val isLoading: Boolean,
)

/**
 * State holder for the Today screen. A ViewModel survives configuration changes (rotation),
 * so the list does not have to be re-queried every time the Activity is recreated.
 */
class TodayViewModel(
    private val repository: HabitRepository,
    private val dateProvider: DateProvider,
    private val updateChecker: UpdateChecker,
    private val currentVersionName: String,
) : ViewModel() {

    /**
     * "Today" is held in a flow rather than read once, so the screen can roll over when the
     * app is resumed after midnight (see [refreshDate]).
     */
    private val date = MutableStateFlow(dateProvider.today())

    private val _updateInfo = MutableStateFlow<UpdateInfo?>(null)

    /** Non-null once a newer GitHub release than [currentVersionName] is found. Checked once per app start. */
    val updateInfo: StateFlow<UpdateInfo?> = _updateInfo.asStateFlow()

    init {
        viewModelScope.launch {
            val release = runCatching { withContext(Dispatchers.IO) { updateChecker.latestRelease() } }
                .getOrNull() ?: return@launch
            if (isNewerVersion(currentVersionName, release.versionName)) {
                _updateInfo.value = release
            }
        }
    }

    fun dismissUpdate() {
        _updateInfo.value = null
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val tasks = date.flatMapLatest { repository.observeTasksForDate(it) }

    val uiState: StateFlow<TodayUiState> =
        combine(date, tasks) { date, tasks -> TodayUiState(date, tasks, isLoading = false) }
            .stateIn(
                scope = viewModelScope,
                // Stop collecting 5 s after the UI goes away; restart when it comes back.
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = TodayUiState(date.value, emptyList(), isLoading = true),
            )

    /** Call on resume: if the logical date changed since the last check, reload for the new day. */
    fun refreshDate() {
        val now = dateProvider.today()
        if (now != date.value) date.value = now
    }

    fun setDone(taskId: Long, done: Boolean) {
        viewModelScope.launch { repository.setDone(taskId, date.value, done) }
    }

    companion object {
        /**
         * Builds the ViewModel with its dependencies from [HabitsApplication]'s container.
         * This is the hand-rolled alternative to a DI framework: the Compose side calls
         * `viewModel(factory = TodayViewModel.Factory)`.
         */
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as HabitsApplication
                TodayViewModel(
                    repository = app.container.repository,
                    dateProvider = app.container.dateProvider,
                    updateChecker = app.container.updateChecker,
                    currentVersionName = BuildConfig.VERSION_NAME,
                )
            }
        }
    }
}
