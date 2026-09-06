package com.jbarszczewski.habits.data

import java.time.DayOfWeek

/**
 * Helpers for the 7-bit weekday mask stored in `tasks.days_mask`.
 *
 * Bit 0 = Monday ... bit 6 = Sunday, so `127` (binary 1111111) means "every day".
 * [DayOfWeek.getValue] is 1 for Monday and 7 for Sunday, which maps onto bit `value - 1`.
 */
object DaysMask {
    const val EVERY_DAY: Int = 0b1111111

    fun bit(day: DayOfWeek): Int = 1 shl (day.value - 1)

    fun contains(mask: Int, day: DayOfWeek): Boolean = (mask and bit(day)) != 0

    fun of(vararg days: DayOfWeek): Int = days.fold(0) { acc, day -> acc or bit(day) }

    fun of(days: Collection<DayOfWeek>): Int = days.fold(0) { acc, day -> acc or bit(day) }

    /** Weekdays contained in [mask], in Monday..Sunday order. */
    fun toDays(mask: Int): Set<DayOfWeek> =
        DayOfWeek.entries.filterTo(linkedSetOf()) { contains(mask, it) }

    fun isValid(mask: Int): Boolean = mask in 1..EVERY_DAY
}
