package com.jbarszczewski.habits.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CompletionTest {

    @Test
    fun `status for minutes follows the target rule`() {
        assertNull(Completion.statusForMinutes(actualMinutes = 0, targetMinutes = 30))
        assertEquals(CompletionStatus.PARTIAL, Completion.statusForMinutes(1, 30))
        assertEquals(CompletionStatus.PARTIAL, Completion.statusForMinutes(29, 30))
        assertEquals(CompletionStatus.DONE, Completion.statusForMinutes(30, 30))
        assertEquals(CompletionStatus.DONE, Completion.statusForMinutes(45, 30))
    }

    @Test
    fun `timer elapsed minutes round down`() {
        assertEquals(0, HabitRepository.elapsedWholeMinutes(startedAtMillis = 0, nowMillis = 59_999))
        assertEquals(1, HabitRepository.elapsedWholeMinutes(startedAtMillis = 0, nowMillis = 60_000))
        assertEquals(25, HabitRepository.elapsedWholeMinutes(startedAtMillis = 1_000, nowMillis = 1_000 + 25 * 60_000 + 30_000))
    }

    @Test
    fun `timer elapsed minutes never go negative`() {
        assertEquals(0, HabitRepository.elapsedWholeMinutes(startedAtMillis = 10_000_000, nowMillis = 5_000))
    }
}
