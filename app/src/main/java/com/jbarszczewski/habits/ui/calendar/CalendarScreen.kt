package com.jbarszczewski.habits.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jbarszczewski.habits.R
import com.jbarszczewski.habits.ui.theme.HabitsTheme
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** Stateful entry point: owns the ViewModel and forwards state to [CalendarContent]. */
@Composable
fun CalendarScreen(
    onBack: () -> Unit,
    viewModel: CalendarViewModel = viewModel(factory = CalendarViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CalendarContent(state = state, onBack = onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarContent(
    state: CalendarUiState,
    onBack: () -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.calendar_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            if (!state.isLoading) {
                ActivityHeatmap(weeks = state.weeks)
                Spacer(Modifier.height(16.dp))
                Legend()
            }
        }
    }
}

// ---------------------------------------------------------------- heatmap

/** The GitHub-style contribution grid: rows = Mon–Sun, columns = weeks (oldest left). */
@Composable
private fun ActivityHeatmap(weeks: List<WeekColumn>) {
    // Horizontal scroll so the full 18-week grid is reachable on narrow screens.
    val scrollState = rememberScrollState()

    Row(modifier = Modifier.horizontalScroll(scrollState)) {
        // Day-of-week labels on the left edge.
        DayLabels()
        Spacer(Modifier.width(4.dp))

        // One column per week.
        Column {
            // Month labels row: show the abbreviated month name in the first column of that month.
            MonthLabelRow(weeks)
            Row(horizontalArrangement = Arrangement.spacedBy(CELL_GAP)) {
                for (week in weeks) {
                    WeekColumnView(week)
                }
            }
        }
    }
}

/** Abbreviated weekday labels (M, W, F) aligned to Monday/Wednesday/Friday rows. */
@Composable
private fun DayLabels() {
    // We only print labels on rows 0 (Mon), 2 (Wed), 4 (Fri) to avoid crowding.
    val locale = Locale.getDefault()
    Column(verticalArrangement = Arrangement.spacedBy(CELL_GAP)) {
        for (dow in DayOfWeek.entries) {
            val label = when (dow) {
                DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY ->
                    dow.getDisplayName(TextStyle.NARROW, locale)
                else -> ""
            }
            Box(
                modifier = Modifier.size(width = 12.dp, height = CELL_SIZE),
                contentAlignment = Alignment.Center,
            ) {
                if (label.isNotEmpty()) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** A single week column: 7 cells stacked from Monday (top) to Sunday (bottom). */
@Composable
private fun WeekColumnView(week: WeekColumn) {
    Column(verticalArrangement = Arrangement.spacedBy(CELL_GAP)) {
        for (day in week.days) {
            if (day == null) {
                // Padding cell (future day or before grid start).
                Spacer(modifier = Modifier.size(CELL_SIZE))
            } else {
                HeatCell(day)
            }
        }
    }
}

/** One square whose colour reflects the day's completion intensity. */
@Composable
private fun HeatCell(day: CalendarDay) {
    val primary = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surfaceVariant
    val cellColor = day.intensity.toColor(primary, surface)

    Box(
        modifier = Modifier
            .size(CELL_SIZE)
            .clip(CELL_SHAPE)
            .background(cellColor),
    )
}

/**
 * Maps each [CellIntensity] level to a concrete colour.
 *
 * [primary] is the theme's primary colour (used at increasing opacity for the filled levels).
 * [surface] is used for unscheduled / empty cells.
 */
private fun CellIntensity.toColor(primary: Color, surface: Color): Color = when (this) {
    CellIntensity.NONE -> surface
    CellIntensity.MISSED -> primary.copy(alpha = 0.10f)
    CellIntensity.LOW -> primary.copy(alpha = 0.30f)
    CellIntensity.MEDIUM -> primary.copy(alpha = 0.55f)
    CellIntensity.HIGH -> primary.copy(alpha = 0.75f)
    CellIntensity.FULL -> primary
}

/**
 * Abbreviated month names above the week columns. A label is only rendered in the leftmost
 * column of each new month so labels never overlap.
 */
@Composable
private fun MonthLabelRow(weeks: List<WeekColumn>) {
    val locale = Locale.getDefault()
    Row(horizontalArrangement = Arrangement.spacedBy(CELL_GAP)) {
        var lastMonth = -1
        for (week in weeks) {
            val month = week.weekStart.monthValue
            val label = if (month != lastMonth) {
                lastMonth = month
                week.weekStart.month.getDisplayName(TextStyle.SHORT, locale)
            } else {
                ""
            }
            Box(
                modifier = Modifier.size(width = CELL_SIZE, height = 16.dp),
                contentAlignment = Alignment.BottomStart,
            ) {
                if (label.isNotEmpty()) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- legend

@Composable
private fun Legend() {
    val primary = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surfaceVariant

    val levels = listOf(
        CellIntensity.NONE to stringResource(R.string.calendar_legend_none),
        CellIntensity.MISSED to stringResource(R.string.calendar_legend_missed),
        CellIntensity.LOW to "25%",
        CellIntensity.MEDIUM to "50%",
        CellIntensity.HIGH to "75%",
        CellIntensity.FULL to "100%",
    )

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.calendar_legend_title),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for ((intensity, label) in levels) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(CELL_SIZE)
                            .clip(CELL_SHAPE)
                            .background(intensity.toColor(primary, surface))
                            .border(
                                width = 0.5.dp,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                shape = CELL_SHAPE,
                            ),
                    )
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- constants

private val CELL_SIZE = 14.dp
private val CELL_GAP = 3.dp
private val CELL_SHAPE = RoundedCornerShape(3.dp)

// ---------------------------------------------------------------- preview

@Preview(showBackground = true)
@Composable
private fun CalendarContentPreview() {
    val today = LocalDate.of(2026, 9, 12)
    val weeks = buildPreviewWeeks(today)
    HabitsTheme {
        CalendarContent(
            state = CalendarUiState(weeks = weeks, isLoading = false),
            onBack = {},
        )
    }
}

private fun buildPreviewWeeks(today: LocalDate): List<WeekColumn> {
    val intensities = CellIntensity.entries
    return (0 until CalendarViewModel.WEEKS_SHOWN).map { w ->
        val weekStart = today.minusWeeks((CalendarViewModel.WEEKS_SHOWN - 1 - w).toLong())
            .with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val days = (0..6).map { d ->
            val date = weekStart.plusDays(d.toLong())
            if (date.isAfter(today)) null
            else CalendarDay(
                date = date,
                intensity = intensities[(w + d) % intensities.size],
                scheduledCount = 3,
                doneCount = d,
            )
        }
        WeekColumn(weekStart, days)
    }
}
