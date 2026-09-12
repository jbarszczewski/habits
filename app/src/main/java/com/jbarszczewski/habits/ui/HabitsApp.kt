package com.jbarszczewski.habits.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import com.jbarszczewski.habits.ui.calendar.CalendarScreen
import com.jbarszczewski.habits.ui.editor.TaskEditorScreen
import com.jbarszczewski.habits.ui.tasks.TaskListScreen
import com.jbarszczewski.habits.ui.today.TodayScreen

/**
 * The screens of the app. A tiny hand-rolled navigator keeps the project free of a navigation
 * library: the back stack is a list of these, the last one is shown, and the system back button
 * pops it. Swap for Navigation Compose later if the graph grows.
 */
sealed interface Screen {
    data object Today : Screen
    data object TaskList : Screen
    data object Calendar : Screen

    /**
     * [taskId] null = create a new task. [instance] makes each visit a fresh screen so its
     * ViewModel (keyed on it) starts from a clean state instead of reusing an old one.
     */
    data class Editor(val taskId: Long?, val instance: Long = System.nanoTime()) : Screen
}

@Composable
fun HabitsApp() {
    // rememberSaveable keeps the back stack across rotation and process death.
    val backStack = rememberSaveable(saver = BackStackSaver) { mutableListOf<Screen>(Screen.Today).toMutableStateList() }

    fun push(screen: Screen) = backStack.add(screen)
    fun pop() { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) }

    BackHandler(enabled = backStack.size > 1) { pop() }

    when (val screen = backStack.last()) {
        Screen.Today -> TodayScreen(
            onAddTask = { push(Screen.Editor(taskId = null)) },
            onEditTask = { push(Screen.Editor(taskId = it)) },
            onOpenTaskList = { push(Screen.TaskList) },
            onOpenCalendar = { push(Screen.Calendar) },
        )
        Screen.TaskList -> TaskListScreen(
            onBack = ::pop,
            onAddTask = { push(Screen.Editor(taskId = null)) },
            onEditTask = { push(Screen.Editor(taskId = it)) },
        )
        Screen.Calendar -> CalendarScreen(onBack = ::pop)
        is Screen.Editor -> TaskEditorScreen(
            taskId = screen.taskId,
            viewModelKey = "editor-${screen.instance}",
            onDone = ::pop,
        )
    }
}

/** Serialises each screen to a short string so the stack fits in the saved-instance Bundle. */
private val BackStackSaver = listSaver<SnapshotStateList<Screen>, String>(
    save = { stack -> stack.map(::encode) },
    restore = { saved -> saved.map(::decode).toMutableStateList() },
)

private fun encode(screen: Screen): String = when (screen) {
    Screen.Today -> "today"
    Screen.TaskList -> "tasks"
    Screen.Calendar -> "calendar"
    is Screen.Editor -> "editor:${screen.taskId ?: ""}:${screen.instance}"
}

private fun decode(value: String): Screen = when {
    value == "today" -> Screen.Today
    value == "tasks" -> Screen.TaskList
    value == "calendar" -> Screen.Calendar
    value.startsWith("editor:") -> {
        val (_, id, instance) = value.split(":")
        Screen.Editor(taskId = id.toLongOrNull(), instance = instance.toLong())
    }
    else -> Screen.Today
}
