package com.jbarszczewski.habits.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * The app's single Room database. Bump [Database.version] and add a migration whenever an
 * entity changes; the Room Gradle plugin writes each version's schema to `app/schemas/`.
 */
@Database(
    entities = [Task::class, Completion::class],
    version = 2,
    exportSchema = true,
)
@TypeConverters(DateConverters::class)
abstract class HabitsDatabase : RoomDatabase() {

    abstract fun taskDao(): TaskDao

    abstract fun completionDao(): CompletionDao

    companion object {
        const val NAME = "habits.db"

        /** Adds the per-task reminder opt-out; defaults existing rows to enabled. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN notifications_enabled INTEGER NOT NULL DEFAULT 1")
            }
        }

        fun create(context: Context): HabitsDatabase =
            Room.databaseBuilder(context.applicationContext, HabitsDatabase::class.java, NAME)
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
