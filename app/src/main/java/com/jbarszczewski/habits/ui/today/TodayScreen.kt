package com.jbarszczewski.habits.ui.today

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jbarszczewski.habits.R
import com.jbarszczewski.habits.data.Completion
import com.jbarszczewski.habits.data.CompletionStatus
import com.jbarszczewski.habits.data.DaysMask
import com.jbarszczewski.habits.data.Task
import com.jbarszczewski.habits.data.TaskWithCompletion
import com.jbarszczewski.habits.ui.theme.HabitsTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Stateful entry point: owns the ViewModel and forwards state + callbacks to [TodayContent]. */
@Composable
fun TodayScreen(
    viewModel: TodayViewModel = viewModel(factory = TodayViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Runs every time the screen comes to the foreground, e.g. after the phone was left
    // overnight, so the list rolls over to the new day.
    LifecycleResumeEffect(Unit) {
        viewModel.refreshDate()
        onPauseOrDispose { }
    }

    TodayContent(state = state, onSetDone = viewModel::setDone)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayContent(
    state: TodayUiState,
    onSetDone: (taskId: Long, done: Boolean) -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.today_title))
                        Text(
                            text = state.date.format(DATE_FORMAT),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            )
        },
    ) { innerPadding ->
        when {
            state.isLoading -> Unit
            state.tasks.isEmpty() -> EmptyState(Modifier.padding(innerPadding))
            else -> TaskList(
                tasks = state.tasks,
                onSetDone = onSetDone,
                contentPadding = innerPadding,
            )
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.today_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TaskList(
    tasks: List<TaskWithCompletion>,
    onSetDone: (taskId: Long, done: Boolean) -> Unit,
    contentPadding: PaddingValues,
) {
    LazyColumn(
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
            start = 16.dp,
            end = 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(tasks, key = { it.task.id }) { item ->
            if (item.task.targetMinutes == null) {
                CheckboxTaskRow(item, onSetDone = { done -> onSetDone(item.task.id, done) })
            } else {
                TimedTaskRow(item)
            }
        }
    }
}

@Composable
private fun CheckboxTaskRow(item: TaskWithCompletion, onSetDone: (Boolean) -> Unit) {
    val done = item.completion?.status == CompletionStatus.DONE
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSetDone(!done) }
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = done, onCheckedChange = onSetDone)
            Spacer(Modifier.width(4.dp))
            Text(
                text = item.task.name,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (done) TextDecoration.LineThrough else null,
                color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/**
 * Timed tasks show today's progress. Start/Stop and manual entry arrive with the timer step;
 * until then the row is read-only.
 */
@Composable
private fun TimedTaskRow(item: TaskWithCompletion) {
    val target = item.task.targetMinutes ?: return
    val actual = item.completion?.actualMinutes ?: 0
    val done = item.completion?.status == CompletionStatus.DONE
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.task.name,
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = if (done) TextDecoration.LineThrough else null,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(R.string.today_minutes_progress, actual, target),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { (actual.toFloat() / target).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)

// ---------------------------------------------------------------- previews

@Preview(showBackground = true)
@Composable
private fun TodayContentPreview() {
    val date = LocalDate.of(2026, 9, 6)
    val tasks = listOf(
        TaskWithCompletion(
            Task(id = 1, name = "Drink water", daysMask = DaysMask.EVERY_DAY, createdAt = date),
            Completion(taskId = 1, date = date, status = CompletionStatus.DONE),
        ),
        TaskWithCompletion(
            Task(id = 2, name = "Read", daysMask = DaysMask.EVERY_DAY, createdAt = date),
            null,
        ),
        TaskWithCompletion(
            Task(id = 3, name = "Practice singing", daysMask = DaysMask.EVERY_DAY, targetMinutes = 30, createdAt = date),
            Completion(taskId = 3, date = date, status = CompletionStatus.PARTIAL, actualMinutes = 12),
        ),
    )
    HabitsTheme {
        TodayContent(state = TodayUiState(date, tasks, isLoading = false), onSetDone = { _, _ -> })
    }
}

@Preview(showBackground = true)
@Composable
private fun TodayEmptyPreview() {
    HabitsTheme {
        TodayContent(
            state = TodayUiState(LocalDate.of(2026, 9, 6), emptyList(), isLoading = false),
            onSetDone = { _, _ -> },
        )
    }
}
