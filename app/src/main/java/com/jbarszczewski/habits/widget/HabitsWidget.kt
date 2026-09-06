package com.jbarszczewski.habits.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.LinearProgressIndicator
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
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
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
                WidgetContent(items = data.items, nowMillis = data.nowMillis)
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
private fun WidgetContent(items: List<TaskWithCompletion>, nowMillis: Long) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .appWidgetBackground()
            .cornerRadius(20.dp)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Header(items)
        Spacer(GlanceModifier.height(10.dp))
        if (items.isEmpty()) {
            EmptyState()
        } else {
            // Each item wraps its card in a Column with a trailing spacer. A vertical padding on
            // the card itself does not reliably create a gap between rows in a Glance LazyColumn
            // (adjacent RemoteViews list rows can end up touching), so the gap is a real Spacer
            // between rows instead.
            LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                items(items, itemId = { it.task.id }) { item ->
                    Column(modifier = GlanceModifier.fillMaxWidth()) {
                        if (item.task.targetMinutes == null) {
                            CheckboxRow(item)
                        } else {
                            TimedRow(item, nowMillis)
                        }
                        Spacer(GlanceModifier.height(10.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(items: List<TaskWithCompletion>) {
    val doneCount = items.count { it.completion?.status == CompletionStatus.DONE }
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .clickable(actionStartActivity<MainActivity>()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = LocalContext.current.getString(R.string.widget_title),
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold),
            modifier = GlanceModifier.defaultWeight(),
        )
        if (items.isNotEmpty()) {
            ProgressPill(doneCount, items.size)
        }
    }
}

/** Small rounded "2/3" badge summarising today's progress at a glance. */
@Composable
private fun ProgressPill(done: Int, total: Int) {
    Box(
        modifier = GlanceModifier
            .background(GlanceTheme.colors.primaryContainer)
            .cornerRadius(999.dp)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            text = "$done/$total",
            style = TextStyle(
                color = GlanceTheme.colors.onPrimaryContainer,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
    }
}

@Composable
private fun EmptyState() {
    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = LocalContext.current.getString(R.string.today_empty),
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 14.sp),
        )
    }
}

/**
 * A rounded "card" wrapper shared by both row kinds: tonal fill, inner padding. A fixed height
 * keeps a one-line checkbox row and a two-line timed row the same size; content is centered
 * vertically within it via the Row's [Alignment.CenterVertically]. The gap between cards is added
 * by the caller (a trailing Spacer per LazyColumn item), not by this wrapper.
 */
@Composable
private fun TaskCard(onClick: androidx.glance.action.Action, content: @Composable androidx.glance.layout.RowScope.() -> Unit) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(64.dp)
            .background(GlanceTheme.colors.surfaceVariant)
            .cornerRadius(16.dp)
            .clickable(onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
    }
}

@Composable
private fun CheckboxRow(item: TaskWithCompletion) {
    val done = item.completion?.status == CompletionStatus.DONE
    val toggle = actionRunCallback<ToggleDoneAction>(actionParametersOf(WidgetActionKeys.taskId to item.task.id))

    TaskCard(onClick = toggle) {
        StatusDot(done = done)
        Spacer(GlanceModifier.width(10.dp))
        Text(
            text = item.task.name,
            style = TextStyle(
                color = if (done) GlanceTheme.colors.onSurfaceVariant else GlanceTheme.colors.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                textDecoration = if (done) TextDecoration.LineThrough else TextDecoration.None,
            ),
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight(),
        )
    }
}

/** A filled circle with a checkmark when done, or a plain ring when not: replaces the stock checkbox. */
@Composable
private fun StatusDot(done: Boolean) {
    if (done) {
        Box(
            modifier = GlanceModifier.size(22.dp).background(GlanceTheme.colors.primary).cornerRadius(11.dp),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_widget_check),
                contentDescription = null,
                modifier = GlanceModifier.size(12.dp),
                colorFilter = ColorFilter.tint(GlanceTheme.colors.onPrimary),
            )
        }
    } else {
        // Ring effect: an outer tinted circle with a smaller inset circle painted in the card's
        // own background colour on top, since Glance modifiers have no direct "border" primitive.
        Box(
            modifier = GlanceModifier.size(22.dp).background(GlanceTheme.colors.outline).cornerRadius(11.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(modifier = GlanceModifier.size(18.dp).background(GlanceTheme.colors.surfaceVariant).cornerRadius(9.dp)) {}
        }
    }
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
    val progress = (liveMinutes.toFloat() / target).coerceIn(0f, 1f)
    val toggleTimer = actionRunCallback<ToggleTimerAction>(actionParametersOf(WidgetActionKeys.taskId to item.task.id))

    TaskCard(onClick = actionStartActivity<MainActivity>()) {
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = item.task.name,
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium),
                maxLines = 1,
            )
            Spacer(GlanceModifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                LinearProgressIndicator(
                    progress = progress,
                    modifier = GlanceModifier.width(56.dp).height(4.dp).cornerRadius(2.dp),
                    color = GlanceTheme.colors.primary,
                    backgroundColor = GlanceTheme.colors.outline,
                )
                Spacer(GlanceModifier.width(6.dp))
                Text(
                    text = context.getString(R.string.today_minutes_progress, liveMinutes, target),
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp),
                )
            }
        }
        Spacer(GlanceModifier.width(8.dp))
        TimerButton(running = running, onClick = toggleTimer)
    }
}

/** Small round icon button: primary-filled play when stopped, error-tinted stop when running. */
@Composable
private fun TimerButton(running: Boolean, onClick: androidx.glance.action.Action) {
    val container: ColorProvider = if (running) GlanceTheme.colors.errorContainer else GlanceTheme.colors.primary
    val onContainer: ColorProvider = if (running) GlanceTheme.colors.onErrorContainer else GlanceTheme.colors.onPrimary
    Box(
        modifier = GlanceModifier
            .size(36.dp)
            .background(container)
            .cornerRadius(18.dp)
            .clickable(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(if (running) R.drawable.ic_widget_stop else R.drawable.ic_widget_play),
            contentDescription = LocalContext.current.getString(
                if (running) R.string.widget_stop else R.string.widget_start
            ),
            modifier = GlanceModifier.size(16.dp),
            colorFilter = ColorFilter.tint(onContainer),
        )
    }
}

