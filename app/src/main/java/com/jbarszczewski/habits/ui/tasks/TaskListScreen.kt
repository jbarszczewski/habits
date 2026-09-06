package com.jbarszczewski.habits.ui.tasks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jbarszczewski.habits.R
import com.jbarszczewski.habits.data.Task
import com.jbarszczewski.habits.ui.formatSchedule

@Composable
fun TaskListScreen(
    onBack: () -> Unit,
    onAddTask: () -> Unit,
    onEditTask: (taskId: Long) -> Unit,
    viewModel: TaskListViewModel = viewModel(factory = TaskListViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    TaskListContent(state = state, onBack = onBack, onAddTask = onAddTask, onEditTask = onEditTask)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskListContent(
    state: TaskListUiState,
    onBack: () -> Unit,
    onAddTask: () -> Unit,
    onEditTask: (taskId: Long) -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tasks_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddTask) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.action_add_task))
            }
        },
    ) { innerPadding ->
        when {
            state.isLoading -> Unit
            state.active.isEmpty() && state.archived.isEmpty() -> Box(
                modifier = Modifier.padding(innerPadding).fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.tasks_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> LazyColumn(
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding() + 8.dp,
                    bottom = innerPadding.calculateBottomPadding() + 88.dp, // room for the FAB
                    start = 16.dp,
                    end = 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.active, key = { it.id }) { task ->
                    TaskRow(task, onClick = { onEditTask(task.id) })
                }
                if (state.archived.isNotEmpty()) {
                    item(key = "archived-header") {
                        Text(
                            text = stringResource(R.string.tasks_archived_header),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                        )
                    }
                    items(state.archived, key = { it.id }) { task ->
                        TaskRow(task, onClick = { onEditTask(task.id) }, dimmed = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskRow(task: Task, onClick: () -> Unit, dimmed: Boolean = false) {
    val context = LocalContext.current
    val textColor = if (dimmed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(16.dp),
        ) {
            Text(text = task.name, style = MaterialTheme.typography.bodyLarge, color = textColor)
            val schedule = formatSchedule(context, task.daysMask)
            val subtitle = task.targetMinutes
                ?.let { stringResource(R.string.tasks_schedule_with_target, schedule, it) }
                ?: schedule
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
