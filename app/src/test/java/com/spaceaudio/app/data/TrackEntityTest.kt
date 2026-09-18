package com.spaceaudio.app.data

import com.spaceaudio.app.data.local.entity.TrackEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackEntityTest {

    @Test
    fun testIsNewBadgeWithinFiveMinutes() {
        val now = System.currentTimeMillis()
        val track = TrackEntity(
            title = "Test Track",
            filePath = "/storage/downloads/test.mp3",
            addedTimestamp = now,
            isNewUntilTimestamp = now + (5 * 60 * 1000L) // + 5 minutes
        )

        assertTrue(track.isNew)
    }

    @Test
    fun testIsNewBadgeExpiredAfterFiveMinutes() {
        val past = System.currentTimeMillis() - (6 * 60 * 1000L) // 6 minutes ago
        val track = TrackEntity(
            title = "Old Track",
            filePath = "/storage/downloads/old.mp3",
            addedTimestamp = past,
            isNewUntilTimestamp = past + (5 * 60 * 1000L)
        )

        assertFalse(track.isNew)
    }
}
