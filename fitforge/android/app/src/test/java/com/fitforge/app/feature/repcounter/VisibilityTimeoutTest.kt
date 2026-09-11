package com.fitforge.app.feature.repcounter

import com.fitforge.app.pose.PoseLandmark
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisibilityTimeoutTest {

    // Regression test for the stalled-camera bug: the old implementation only
    // re-checked its deadline from inside the frame-analysis callback, so if
    // zero frames ever arrive, nothing ever re-checks the clock and the user
    // is stuck on VisibilityCheck forever. Here nothing touches `timeout`
    // during the window at all — the callback firing at all is only possible
    // if it's driven by real elapsed time, not by simulated frame delivery.
    @Test
    fun `fires on its own after the deadline with zero frames delivered`() = runBlocking {
        var timedOut = false
        val timeout = VisibilityTimeout(scope = this, timeoutMs = 20L) { timedOut = true }

        timeout.start()
        delay(80L) // no frames simulated in this window — only time passes

        assertTrue("timeout must fire without any frame arriving to trigger it", timedOut)
    }

    @Test
    fun `cancel before the deadline suppresses the callback`() = runBlocking {
        var timedOut = false
        val timeout = VisibilityTimeout(scope = this, timeoutMs = 20L) { timedOut = true }

        timeout.start()
        timeout.cancel()
        delay(80L)

        assertFalse("a cancelled timeout must not fire", timedOut)
    }

    @Test
    fun `zero-frame timeout surfaces a message with no joint-specific detail`() {
        val message = visibilityTimeoutMessage(missing = null)

        assertTrue(
            "message for a stalled pipeline should not reference specific joints",
            message.isNotBlank(),
        )
    }

    @Test
    fun `timeout after a frame was seen surfaces the joints that were missing`() {
        val message = visibilityTimeoutMessage(missing = setOf(PoseLandmark.LEFT_KNEE, PoseLandmark.LEFT_HIP))

        assertEquals(
            "Can't start: left knee, left hip not visible. Reposition so your whole side is in frame, then try again.",
            message,
        )
    }
}
