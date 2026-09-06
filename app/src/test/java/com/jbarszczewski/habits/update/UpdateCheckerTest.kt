package com.jbarszczewski.habits.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {

    @Test
    fun `candidate with higher minor version is newer`() {
        assertTrue(isNewerVersion(current = "1.0.0", candidate = "1.1.0"))
    }

    @Test
    fun `candidate with higher patch version is newer`() {
        assertTrue(isNewerVersion(current = "1.0.0", candidate = "1.0.1"))
    }

    @Test
    fun `equal versions are not newer`() {
        assertFalse(isNewerVersion(current = "1.2.0", candidate = "1.2.0"))
    }

    @Test
    fun `missing segments are treated as zero`() {
        assertFalse(isNewerVersion(current = "1.2.0", candidate = "1.2"))
        assertTrue(isNewerVersion(current = "1.0", candidate = "1.0.1"))
    }

    @Test
    fun `older candidate is not newer`() {
        assertFalse(isNewerVersion(current = "2.0.0", candidate = "1.9.9"))
    }
}
