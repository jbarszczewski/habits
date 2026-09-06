package com.jbarszczewski.habits.data

import androidx.room.TypeConverter
import java.time.LocalDate

/**
 * Room only knows primitives and strings. These converters let entities use [LocalDate]
 * while the database column stays an ISO "yyyy-MM-dd" TEXT column, which sorts and
 * compares correctly with plain string comparison in SQL (`BETWEEN`, `<=`, `ORDER BY`).
 */
class DateConverters {
    @TypeConverter
    fun fromLocalDate(date: LocalDate?): String? = date?.toString()

    @TypeConverter
    fun toLocalDate(value: String?): LocalDate? = value?.let(LocalDate::parse)
}
