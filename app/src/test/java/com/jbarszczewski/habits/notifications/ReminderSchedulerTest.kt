package com.jbarszczewski.habits.notifications

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId

class ReminderSchedulerTest {

    private val zone: ZoneId = ZoneId.of("Europe/Warsaw")

    private fun clockAt(localDateTime: String): Clock =
        Clock.fixed(LocalDateTime.parse(localDateTime).atZone(zone).toInstant(), zone)

    @Test
    fun `before noon waits until noon today`() {
        assertEquals(2 * 60 * 60 * 1000L, ReminderScheduler.millisUntilNextTrigger(clockAt("2026-09-06T10:00")))
    }

    @Test
    fun `between noon and 4pm waits until 4pm today`() {
        assertEquals(3 * 60 * 60 * 1000L, ReminderScheduler.millisUntilNextTrigger(clockAt("2026-09-06T13:00")))
    }

    @Test
    fun `right at noon counts as already past, waits for 4pm`() {
        assertEquals(4 * 60 * 60 * 1000L, ReminderScheduler.millisUntilNextTrigger(clockAt("2026-09-06T12:00")))
    }

    @Test
    fun `after 4pm waits until noon tomorrow`() {
        assertEquals(20 * 60 * 60 * 1000L, ReminderScheduler.millisUntilNextTrigger(clockAt("2026-09-06T16:00")))
        assertEquals(16 * 60 * 60 * 1000L, ReminderScheduler.millisUntilNextTrigger(clockAt("2026-09-06T20:00")))
    }
}
