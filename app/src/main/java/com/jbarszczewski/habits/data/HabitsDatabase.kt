package com.jbarszczewski.habits.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * The app's single Room database. Bump [Database.version] and add a migration whenever an
 * entity changes; the Room Gradle plugin writes each version's schema to `app/schemas/`.
 */
@Database(
    entities = [Task::class, Completion::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(DateConverters::class)
abstract class HabitsDatabase : RoomDatabase() {

    abstract fun taskDao(): TaskDao

    abstract fun completionDao(): CompletionDao

    companion object {
        const val NAME = "habits.db"

        fun create(context: Context): HabitsDatabase =
            Room.databaseBuilder(context.applicationContext, HabitsDatabase::class.java, NAME)
                .build()
    }
}
