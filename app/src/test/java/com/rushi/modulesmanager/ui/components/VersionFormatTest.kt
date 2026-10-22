package com.rushi.modulesmanager.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class VersionFormatTest {
    @Test
    fun `strips leading v and suffixes from chip versions`() {
        assertEquals("1.0.4", shortVersion("v1.0.4-diagnostic"))
        assertEquals("1.0.3", shortVersion("1.0.3"))
        assertEquals("1.3.0", shortVersion("1.3.0_beta"))
        assertEquals("9.6", shortVersion("v9.6"))
        assertEquals("26.02.03", shortVersion("26.02.03"))
        assertEquals("1.0.4", shortVersion("1.0.4-rc1"))
    }

    @Test
    fun `keeps a plain non-numeric version short`() {
        assertEquals("stable", shortVersion("stable"))
        assertEquals("", shortVersion(null))
        assertEquals("", shortVersion("  "))
    }
}
