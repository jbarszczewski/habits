package com.jbarszczewski.habits.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class DateProviderTest {

    private val zone: ZoneId = ZoneId.of("Europe/Warsaw")

    private fun providerAt(localDateTime: String, dayStartHour: Int = 0): DateProvider {
        val instant = LocalDateTime.parse(localDateTime).atZone(zone).toInstant()
        return DateProvider(clock = Clock.fixed(instant, zone), dayStartHour = { dayStartHour })
    }

    @Test
    fun `default day start hour is midnight`() {
        assertEquals(LocalDate.parse("2026-09-06"), providerAt("2026-09-06T00:00").today())
        assertEquals(LocalDate.parse("2026-09-06"), providerAt("2026-09-06T23:59").today())
    }

    @Test
    fun `before the day start hour still counts as the previous date`() {
        assertEquals(LocalDate.parse("2026-09-05"), providerAt("2026-09-06T03:59", dayStartHour = 4).today())
        assertEquals(LocalDate.parse("2026-09-06"), providerAt("2026-09-06T04:00", dayStartHour = 4).today())
        assertEquals(LocalDate.parse("2026-09-06"), providerAt("2026-09-06T23:30", dayStartHour = 4).today())
    }

    @Test
    fun `day start hour crosses month boundaries`() {
        assertEquals(LocalDate.parse("2026-08-31"), providerAt("2026-09-01T01:00", dayStartHour = 3).today())
    }

    @Test
    fun `today follows the clock's zone not UTC`() {
        // 23:30 in Warsaw on the 5th is 21:30 UTC; the logical date must be the 5th.
        val instant = LocalDateTime.parse("2026-09-05T23:30").atZone(zone).toInstant()
        val warsaw = DateProvider(clock = Clock.fixed(instant, zone))
        val utc = DateProvider(clock = Clock.fixed(instant, ZoneOffset.UTC))
        assertEquals(LocalDate.parse("2026-09-05"), warsaw.today())
        assertEquals(LocalDate.parse("2026-09-05"), utc.today())
    }

    @Test
    fun `nowMillis reflects the clock`() {
        val provider = providerAt("2026-09-06T12:00")
        assertEquals(LocalDateTime.parse("2026-09-06T12:00").atZone(zone).toInstant().toEpochMilli(), provider.nowMillis())
    }
}

class DateProviderNextDayTest {
    private val zone: ZoneId = ZoneId.of("Europe/Warsaw")

    private fun providerAt(localDateTime: String, dayStartHour: Int = 0): DateProvider {
        val instant = LocalDateTime.parse(localDateTime).atZone(zone).toInstant()
        return DateProvider(clock = Clock.fixed(instant, zone), dayStartHour = { dayStartHour })
    }

    @Test
    fun `next day start is midnight by default`() {
        assertEquals(2 * 60 * 60 * 1000L, providerAt("2026-09-06T22:00").millisUntilNextDayStart())
    }

    @Test
    fun `next day start honours the day start hour`() {
        // 01:00 with a 4 am boundary is still logically the 5th, so the next boundary is 04:00 today.
        assertEquals(3 * 60 * 60 * 1000L, providerAt("2026-09-06T01:00", dayStartHour = 4).millisUntilNextDayStart())
        // 05:00 is logically the 6th; next boundary is 04:00 on the 7th.
        assertEquals(23 * 60 * 60 * 1000L, providerAt("2026-09-06T05:00", dayStartHour = 4).millisUntilNextDayStart())
    }
}
