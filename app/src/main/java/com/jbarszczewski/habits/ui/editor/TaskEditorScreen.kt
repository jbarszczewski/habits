package com.jbarszczewski.habits.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jbarszczewski.habits.R
import com.jbarszczewski.habits.data.DaysMask
import com.jbarszczewski.habits.ui.theme.HabitsTheme
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun TaskEditorScreen(
    taskId: Long?,
    viewModelKey: String,
    onDone: () -> Unit,
) {
    val viewModel: TaskEditorViewModel = viewModel(key = viewModelKey, factory = TaskEditorViewModel.factory(taskId))
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Leave the screen once the ViewModel reports the task was saved / archived.
    LaunchedEffect(state.isFinished) {
        if (state.isFinished) onDone()
    }

    TaskEditorContent(
        state = state,
        onBack = onDone,
        onNameChange = viewModel::onNameChange,
        onDayToggle = viewModel::onDayToggle,
        onDaysShortcut = viewModel::onDaysShortcut,
        onHasTargetChange = viewModel::onHasTargetChange,
        onTargetMinutesChange = viewModel::onTargetMinutesChange,
        onSave = viewModel::save,
        onArchive = viewModel::archive,
        onUnarchive = viewModel::unarchive,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditorContent(
    state: TaskEditorUiState,
    onBack: () -> Unit,
    onNameChange: (String) -> Unit,
    onDayToggle: (DayOfWeek) -> Unit,
    onDaysShortcut: (Set<DayOfWeek>) -> Unit,
    onHasTargetChange: (Boolean) -> Unit,
    onTargetMinutesChange: (String) -> Unit,
    onSave: () -> Unit,
    onArchive: () -> Unit,
    onUnarchive: () -> Unit,
) {
    var showArchiveDialog by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (state.isExisting) R.string.editor_title_edit else R.string.editor_title_new)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (!state.isLoading) {
                        IconButton(onClick = onSave) {
                            Icon(Icons.Default.Check, contentDescription = stringResource(R.string.action_save))
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        if (state.isLoading) return@Scaffold

        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = onNameChange,
                label = { Text(stringResource(R.string.editor_name)) },
                singleLine = true,
                isError = state.showErrors && state.nameError,
                supportingText = if (state.showErrors && state.nameError) {
                    { Text(stringResource(R.string.editor_name_error)) }
                } else null,
                modifier = Modifier.fillMaxWidth(),
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.editor_days), style = MaterialTheme.typography.titleSmall)
                DaysShortcuts(selected = state.days, onSelect = onDaysShortcut)
                WeekdayPicker(selected = state.days, onToggle = onDayToggle)
                if (state.showErrors && state.daysError) {
                    Text(
                        text = stringResource(R.string.editor_days_error),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.editor_target), style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = stringResource(R.string.editor_target_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = state.hasTarget, onCheckedChange = onHasTargetChange)
                }
                if (state.hasTarget) {
                    OutlinedTextField(
                        value = state.targetMinutes,
                        onValueChange = onTargetMinutesChange,
                        label = { Text(stringResource(R.string.editor_target_minutes)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = state.showErrors && state.minutesError,
                        supportingText = if (state.showErrors && state.minutesError) {
                            { Text(stringResource(R.string.editor_target_minutes_error)) }
                        } else null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            if (state.isExisting) {
                Spacer(Modifier.height(8.dp))
                if (state.isArchived) {
                    OutlinedButton(onClick = onUnarchive, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.editor_unarchive))
                    }
                } else {
                    OutlinedButton(onClick = { showArchiveDialog = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.editor_archive))
                    }
                }
            }
        }
    }

    if (showArchiveDialog) {
        AlertDialog(
            onDismissRequest = { showArchiveDialog = false },
            title = { Text(stringResource(R.string.editor_archive_dialog_title)) },
            text = { Text(stringResource(R.string.editor_archive_dialog_text)) },
            confirmButton = {
                TextButton(onClick = { showArchiveDialog = false; onArchive() }) {
                    Text(stringResource(R.string.editor_archive))
                }
            },
            dismissButton = {
                TextButton(onClick = { showArchiveDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

/**
 * Quick presets for the day picker below: select every day (tapping again clears it),
 * or jump straight to the Mon-Fri / Sat-Sun split.
 */
@Composable
private fun DaysShortcuts(selected: Set<DayOfWeek>, onSelect: (Set<DayOfWeek>) -> Unit) {
    val allDays = remember { DayOfWeek.entries.toSet() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = selected == allDays,
            onClick = { onSelect(if (selected == allDays) emptySet() else allDays) },
            label = { Text(stringResource(R.string.editor_days_all)) },
        )
        FilterChip(
            selected = selected == DaysMask.WEEKDAYS,
            onClick = { onSelect(DaysMask.WEEKDAYS) },
            label = { Text(stringResource(R.string.editor_days_weekdays)) },
        )
        FilterChip(
            selected = selected == DaysMask.WEEKEND,
            onClick = { onSelect(DaysMask.WEEKEND) },
            label = { Text(stringResource(R.string.editor_days_weekend)) },
        )
    }
}

/** Seven toggle chips, Monday first, using the device locale's short day names. */
@Composable
private fun WeekdayPicker(selected: Set<DayOfWeek>, onToggle: (DayOfWeek) -> Unit) {
    val locale = Locale.getDefault()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (day in DayOfWeek.entries) {
            FilterChip(
                selected = day in selected,
                onClick = { onToggle(day) },
                label = {
                    Text(
                        text = day.getDisplayName(TextStyle.SHORT, locale),
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                    )
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TaskEditorPreview() {
    HabitsTheme {
        TaskEditorContent(
            state = TaskEditorUiState(
                isLoading = false,
                isExisting = true,
                name = "Practice singing",
                days = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
                hasTarget = true,
                targetMinutes = "30",
            ),
            onBack = {}, onNameChange = {}, onDayToggle = {}, onDaysShortcut = {}, onHasTargetChange = {},
            onTargetMinutesChange = {}, onSave = {}, onArchive = {}, onUnarchive = {},
        )
    }
}
