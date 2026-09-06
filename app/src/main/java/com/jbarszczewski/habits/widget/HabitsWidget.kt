package com.jbarszczewski.habits.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.Button
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.CheckBox
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.jbarszczewski.habits.AppContainer
import com.jbarszczewski.habits.HabitsApplication
import com.jbarszczewski.habits.MainActivity
import com.jbarszczewski.habits.R
import com.jbarszczewski.habits.data.CompletionStatus
import com.jbarszczewski.habits.data.HabitRepository
import com.jbarszczewski.habits.data.TaskWithCompletion
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Everything one render of the widget needs. */
private data class WidgetData(
    val today: LocalDate,
    val items: List<TaskWithCompletion>,
    val nowMillis: Long,
)

/**
 * The home-screen widget. Glance turns this Compose-style tree into RemoteViews, which is what
 * the launcher can display.
 *
 * Lifecycle, because it is easy to get wrong: Glance keeps a "session" open for a short while
 * after rendering. `updateAll()` on an open session only recomposes the existing content; it does
 * NOT call [provideGlance] again. So anything computed before `provideContent` would go stale.
 * The fix (and the documented pattern) is to collect a Flow *inside* `provideContent`: the widget
 * then re-renders on every database change while the session is alive, and a fresh
 * [provideGlance] runs when the next `updateAll()` opens a new session.
 */
class HabitsWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as HabitsApplication).container
        val dataFlow = widgetData(container)
        val initial = dataFlow.first()

        provideContent {
            val data by dataFlow.collectAsState(initial)
            GlanceTheme {
                WidgetContent(today = data.today, items = data.items, nowMillis = data.nowMillis)
            }
        }
    }

    /**
     * Reloads on every change to the two tables and once a minute (so a running timer's minutes
     * and the date stay current while a session is open). Each reload is one small query.
     */
    private fun widgetData(container: AppContainer): Flow<WidgetData> {
        val dbChanges = container.database.invalidationTracker.createFlow("tasks", "completions")
        return combine(dbChanges, minuteTicker()) { _, _ ->
            val today = container.dateProvider.today()
            WidgetData(
                today = today,
                items = container.repository.observeTasksForDate(today).first(),
                nowMillis = container.dateProvider.nowMillis(),
            )
        }
    }

    private fun minuteTicker(): Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(60_000)
        }
    }
}

@Composable
private fun WidgetContent(today: LocalDate, items: List<TaskWithCompletion>, nowMillis: Long) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .appWidgetBackground()
            .cornerRadius(16.dp)
            .padding(12.dp),
    ) {
        Header(today)
        Spacer(GlanceModifier.height(8.dp))
        if (items.isEmpty()) {
            Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = LocalContext.current.getString(R.string.today_empty),
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 14.sp),
                )
            }
        } else {
            LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                items(items, itemId = { it.task.id }) { item ->
                    if (item.task.targetMinutes == null) {
                        CheckboxRow(item)
                    } else {
                        TimedRow(item, nowMillis)
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(today: LocalDate) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .clickable(actionStartActivity<MainActivity>()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = LocalContext.current.getString(R.string.today_title),
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold),
        )
        Spacer(GlanceModifier.width(8.dp))
        Text(
            text = today.format(HEADER_DATE),
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
        )
    }
}

@Composable
private fun CheckboxRow(item: TaskWithCompletion) {
    val done = item.completion?.status == CompletionStatus.DONE
    CheckBox(
        checked = done,
        onCheckedChange = actionRunCallback<ToggleDoneAction>(
            actionParametersOf(WidgetActionKeys.taskId to item.task.id)
        ),
        text = item.task.name,
        style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp),
        modifier = GlanceModifier.fillMaxWidth().padding(vertical = 2.dp),
        maxLines = 1,
    )
}

@Composable
private fun TimedRow(item: TaskWithCompletion, nowMillis: Long) {
    val context = LocalContext.current
    val target = item.task.targetMinutes ?: return
    val startedAt = item.task.timerStartedAt
    val running = startedAt != null
    // Minutes already saved today plus whatever the running timer has accumulated so far.
    val liveMinutes = (item.completion?.actualMinutes ?: 0) +
        (startedAt?.let { HabitRepository.elapsedWholeMinutes(it, nowMillis) } ?: 0)

    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = item.task.name,
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp),
                maxLines = 1,
            )
            Text(
                text = context.getString(R.string.today_minutes_progress, liveMinutes, target),
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
            )
        }
        Button(
            text = context.getString(if (running) R.string.widget_stop else R.string.widget_start),
            onClick = actionRunCallback<ToggleTimerAction>(
                actionParametersOf(WidgetActionKeys.taskId to item.task.id)
            ),
        )
    }
}

private val HEADER_DATE: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
