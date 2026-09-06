package com.jbarszczewski.habits

import android.content.Context
import com.jbarszczewski.habits.data.DateProvider
import com.jbarszczewski.habits.data.HabitRepository
import com.jbarszczewski.habits.data.HabitsDatabase
import com.jbarszczewski.habits.update.UpdateChecker

/**
 * Hand-written dependency container: one place that builds the singletons the rest of the app
 * shares (database, repository, ...). ViewModels, the widget and the timer service reach it via
 * `(context.applicationContext as HabitsApplication).container`.
 *
 * Everything is `lazy` so nothing is created until first use.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val dateProvider: DateProvider by lazy { DateProvider() }

    val database: HabitsDatabase by lazy { HabitsDatabase.create(appContext) }

    val repository: HabitRepository by lazy {
        HabitRepository(
            taskDao = database.taskDao(),
            completionDao = database.completionDao(),
            dateProvider = dateProvider,
        )
    }

    val updateChecker: UpdateChecker by lazy { UpdateChecker() }
}
