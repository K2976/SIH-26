package com.fitforge.app.core.startup

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SemVerTest {
    @Test
    fun `installed below minimum`() {
        assertTrue(isVersionBelow(installed = "0.9.0", minimum = "1.0.0"))
    }

    @Test
    fun `installed equal to minimum`() {
        assertFalse(isVersionBelow(installed = "1.0.0", minimum = "1.0.0"))
    }

    @Test
    fun `installed above minimum`() {
        assertFalse(isVersionBelow(installed = "1.2.0", minimum = "1.0.0"))
    }

    @Test
    fun `missing components compare as zero`() {
        assertTrue(isVersionBelow(installed = "1.0", minimum = "1.0.1"))
    }
}
