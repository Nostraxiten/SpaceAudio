package com.spaceaudio.app.player

import org.junit.Assert.assertEquals
import org.junit.Test

class RepeatModeTest {

    @Test
    fun testRepeatModeCycle() {
        var mode = RepeatMode.OFF
        mode = mode.next()
        assertEquals(RepeatMode.ALL, mode)
        mode = mode.next()
        assertEquals(RepeatMode.ONE, mode)
        mode = mode.next()
        assertEquals(RepeatMode.OFF, mode)
    }
}
