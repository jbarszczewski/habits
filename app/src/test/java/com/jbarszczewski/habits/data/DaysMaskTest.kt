package com.jbarszczewski.habits.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek

class DaysMaskTest {

    @Test
    fun `monday is bit 0 and sunday is bit 6`() {
        assertEquals(1, DaysMask.bit(DayOfWeek.MONDAY))
        assertEquals(64, DaysMask.bit(DayOfWeek.SUNDAY))
    }

    @Test
    fun `every day is 127`() {
        assertEquals(127, DaysMask.EVERY_DAY)
        assertEquals(127, DaysMask.of(*DayOfWeek.entries.toTypedArray()))
        assertEquals(DayOfWeek.entries.toSet(), DaysMask.toDays(DaysMask.EVERY_DAY))
    }

    @Test
    fun `contains checks the right bit`() {
        val mask = DaysMask.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
        assertEquals(0b0010101, mask)
        assertTrue(DaysMask.contains(mask, DayOfWeek.MONDAY))
        assertFalse(DaysMask.contains(mask, DayOfWeek.TUESDAY))
        assertTrue(DaysMask.contains(mask, DayOfWeek.FRIDAY))
        assertFalse(DaysMask.contains(mask, DayOfWeek.SUNDAY))
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY), DaysMask.toDays(mask))
    }

    @Test
    fun `validity requires at least one day and no stray bits`() {
        assertFalse(DaysMask.isValid(0))
        assertTrue(DaysMask.isValid(1))
        assertTrue(DaysMask.isValid(127))
        assertFalse(DaysMask.isValid(128))
    }
}
