package com.rushi.modulesmanager.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionTest {

    @Test
    fun `parses version code and name tags`() {
        val version = ModuleVersion.parse("520-3.8.3.r520.9605640")

        assertEquals(520L, version?.code)
        assertEquals("3.8.3.r520.9605640", version?.name)
    }

    @Test
    fun `parses plain tags without a version code`() {
        val version = ModuleVersion.parse("v1.2.3")

        assertEquals(0L, version?.code)
        assertEquals("v1.2.3", version?.name)
    }

    @Test
    fun `compares version codes and plain version names`() {
        assertTrue(ModuleVersion.parse("521-3.8.4")!!.isNewerThan(520, "3.8.3"))
        assertFalse(ModuleVersion.parse("520-3.8.3")!!.isNewerThan(520, "3.8.3"))

        assertTrue(ModuleVersion.parse("v1.3.0")!!.isNewerThan(0, "1.2.0"))
        assertFalse(ModuleVersion.parse("v1.2.0")!!.isNewerThan(0, "1.2.0"))
        assertTrue(ModuleVersion.parse("v1.2.1")!!.isNewerThan(0, "1.2"))
    }
}
